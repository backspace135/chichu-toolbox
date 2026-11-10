import base64
import copy
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("tracker", Path(__file__).with_name("tracker.py"))
assert spec is not None and spec.loader is not None
t = importlib.util.module_from_spec(spec)
spec.loader.exec_module(t)

SOURCE = {"id": "fixture", "repo": "example/source", "branch": "main",
          "mode": "integration", "owner": "#17"}
EMPTY = {"sources": {}}
NOW = "2026-11-10T00:00:00+00:00"


class FakeAPI:
    def __init__(self):
        self.sha = "a" * 40
        self.tags = [{"ref": "refs/tags/v1", "object": {"type": "tag", "sha": "tag-object"}}]
        self.tag_target = {"type": "commit", "sha": self.sha}
        self.releases = [{"id": 1, "body": "release notes", "tag_name": "v1"}]
        self.assets = [{"id": 1, "name": "tool.zip", "size": 123, "digest": "sha256:one", "download_count": 1}]
        self.failure: str | None = None
        self.status = "ahead"
        self.writes = []
        self.prs = []
        self.existing = None

    def api(self, path, method="GET", data=None):
        if self.failure and self.failure in path:
            raise t.ApiError(f"GET {path}: HTTP 404 or API denied (controlled fixture)")
        if method != "GET":
            self.writes.append((path, method, data))
            return {"number": 7}
        if path == "repos/example/product":
            return {"default_branch": "main"}
        if "/git/ref/heads/" in path:
            return {"object": {"sha": "base"}}
        if "/contents/" in path:
            return {"content": base64.b64encode(json.dumps(self.existing).encode()).decode()}
        if "/branches/" in path:
            return {"commit": {"sha": self.sha}}
        if "/git/tags/" in path:
            return {"object": copy.deepcopy(self.tag_target)}
        if "/compare/" in path:
            return {"status": self.status, "files": [{"filename": "new-feature.py", "status": "added"}],
                    "commits": [{"sha": self.sha, "commit": {"message": "new feature"}}]}
        if "/git/commits/" in path:
            return {"tree": {"sha": "tree"}}
        if "/git/trees/" in path:
            return {"truncated": False, "tree": [{"type": "blob", "path": "LICENSE"}]}
        raise AssertionError(path)

    def pages(self, path):
        if self.failure and self.failure in path:
            raise t.ApiError(f"GET {path}: API denied (controlled fixture)")
        if "/git/matching-refs/tags/" in path:
            return copy.deepcopy(self.tags)
        if path.endswith("/releases"):
            return copy.deepcopy(self.releases)
        if path.endswith("/assets"):
            return copy.deepcopy(self.assets)
        if "/pulls?" in path:
            return self.prs
        if path.endswith("/branches"):
            return [{"name": "main"}]
        raise AssertionError(path)


class TrackingTest(unittest.TestCase):
    def setUp(self):
        self.api = FakeAPI()

    def observe(self, state=EMPTY, source=SOURCE, adopted=EMPTY, freeze=""):
        return t.observe(self.api, [source], state, adopted, NOW, freeze)

    def test_first_then_unchanged_does_not_reset_validation(self):
        first, errors = self.observe()
        self.assertEqual(errors, [])
        entry = first["sources"]["fixture"]
        self.assertEqual(entry["attempt"]["status"], "changed")
        self.assertEqual(entry["last_success"]["incremental"]["kind"], "first-observation")
        entry["candidate"]["validation"] = ["evidence for exact identity"]
        second, errors = self.observe(first)
        self.assertEqual(errors, [])
        self.assertEqual(second["sources"]["fixture"]["attempt"]["status"], "unchanged")
        self.assertEqual(second["sources"]["fixture"]["candidate"]["validation"], entry["candidate"]["validation"])
        self.assertEqual(EMPTY, {"sources": {}})

    def test_new_sha_has_dual_diff_and_no_inherited_validation(self):
        first, _ = self.observe()
        baseline = copy.deepcopy(first["sources"]["fixture"]["last_success"]["snapshot"])
        baseline["sha"] = "0" * 40
        first["sources"]["fixture"]["candidate"]["validation"] = ["old evidence"]
        self.api.sha = "b" * 40
        second, errors = self.observe(first, adopted={"sources": {"fixture": {"snapshot": baseline, "evidence": ["record"]}}})
        self.assertEqual(errors, [])
        entry = second["sources"]["fixture"]
        self.assertEqual(entry["last_success"]["incremental"]["from"], "a" * 40)
        self.assertEqual(entry["candidate"]["against_adopted"]["from"], "0" * 40)
        self.assertEqual(entry["candidate"]["validation"], [])
        self.assertIn("all-features-new-and-changed", entry["candidate"]["review"])

    def test_tag_object_target_release_and_asset_changes(self):
        first, _ = self.observe()
        changes = (
            lambda: self.api.tags[0]["object"].update(sha="moved-object"),
            lambda: self.api.tag_target.update(sha="moved-commit"),
            lambda: self.api.releases[0].update(body="edited notes"),
            lambda: self.api.assets[0].update(id=2, digest="sha256:replacement"),
        )
        for change in changes:
            with self.subTest(change=change):
                change()
                second, errors = self.observe(first)
                self.assertEqual(errors, [])
                self.assertEqual(second["sources"]["fixture"]["attempt"]["status"], "changed")
                self.assertNotEqual(second["sources"]["fixture"]["candidate"]["identity"], first["sources"]["fixture"]["candidate"]["identity"])
                first = second

    def test_download_count_is_not_update(self):
        first, _ = self.observe()
        self.api.assets[0]["download_count"] += 1
        second, _ = self.observe(first)
        self.assertEqual(second["sources"]["fixture"]["attempt"]["status"], "unchanged")

    def test_api_failure_and_disappeared_branch_preserve_success_and_candidate(self):
        first, _ = self.observe()
        for boundary in ("/branches/", "/releases", "/compare/"):
            with self.subTest(boundary=boundary):
                self.api.failure = boundary
                self.api.sha = "b" * 40
                second, errors = self.observe(first)
                self.assertTrue(errors)
                entry = second["sources"]["fixture"]
                self.assertEqual(entry["attempt"]["status"], "failed")
                self.assertEqual(entry["last_success"], first["sources"]["fixture"]["last_success"])
                self.assertEqual(entry["candidate"], first["sources"]["fixture"]["candidate"])

    def test_history_rewrite_is_explicit_and_does_not_change_adoption(self):
        first, _ = self.observe()
        adopted = {"sources": {"fixture": {"snapshot": first["sources"]["fixture"]["last_success"]["snapshot"], "evidence": ["record"]}}}
        saved = copy.deepcopy(adopted)
        self.api.sha = "b" * 40
        self.api.status = "diverged"
        second, _ = self.observe(first, adopted=adopted)
        self.assertIn("HISTORY REWRITTEN", second["sources"]["fixture"]["attempt"]["warning"])
        self.assertEqual(adopted, saved)

    def test_frozen_candidate_queues_next_batch_until_adopted(self):
        first, _ = self.observe(freeze="")
        frozen = copy.deepcopy(first["sources"]["fixture"]["candidate"])
        frozen["frozen"] = True
        self.api.sha = "b" * 40
        second, _ = self.observe(first, freeze="fixture")
        self.assertEqual(second["sources"]["fixture"]["candidate"], frozen)
        self.assertEqual(second["sources"]["fixture"]["next_batch"]["snapshot"]["sha"], self.api.sha)
        third, _ = self.observe(second, adopted={"sources": {"fixture": {"snapshot": frozen["snapshot"], "evidence": ["record"]}}})
        self.assertFalse(third["sources"]["fixture"]["candidate"]["frozen"])
        self.assertEqual(third["sources"]["fixture"]["candidate"]["snapshot"]["sha"], self.api.sha)

    def test_investigation_has_no_candidate_or_pr(self):
        source = dict(SOURCE, mode="investigation")
        state, _ = self.observe(source=source)
        self.assertIsNone(state["sources"]["fixture"]["candidate"])
        with patch.object(t, "commit_files", return_value="head"):
            self.assertEqual(t.publish(self.api, "example/product", state, "run"), [])
        self.assertEqual(self.api.writes, [])

    def test_create_then_reuse_single_pr_and_schedule_explicit_checks(self):
        state, _ = self.observe()
        with patch.object(t, "commit_files", return_value="new-head"):
            checks = t.publish(self.api, "example/product", state, "run")
        self.assertEqual(checks[0]["pr"], 7)
        self.assertEqual(self.api.writes[0][1], "POST")
        self.api.prs = [{"number": 7, "head": {"sha": "old-head"}}]
        self.api.existing = state["sources"]["fixture"]["candidate"]
        self.api.sha = "b" * 40
        updated, _ = self.observe(state)
        self.api.writes.clear()
        with patch.object(t, "commit_files", return_value="newer-head"):
            checks = t.publish(self.api, "example/product", updated, "run")
        self.assertEqual(checks[0]["head"], "newer-head")
        self.assertEqual(self.api.writes[0][0], "repos/example/product/pulls/7")
        self.assertEqual(self.api.writes[0][1], "PATCH")

    def test_unchanged_or_frozen_pr_keeps_head_but_reruns_checks(self):
        state, _ = self.observe()
        self.api.prs = [{"number": 7, "head": {"sha": "exact-head"}}]
        self.api.existing = state["sources"]["fixture"]["candidate"]
        for frozen in (False, True):
            state["sources"]["fixture"]["candidate"]["frozen"] = frozen
            with patch.object(t, "commit_files", return_value="state-head") as commit:
                checks = t.publish(self.api, "example/product", state, "run")
            self.assertEqual(commit.call_count, 1) # state branch only
            self.assertEqual(checks[0]["head"], "exact-head")
            self.assertEqual(self.api.writes, [])

    def test_actual_candidate_static_check_and_identity_rejection(self):
        state, _ = self.observe()
        self.api.existing = state["sources"]["fixture"]["candidate"]
        item = {"id": "fixture", "head": "head", "identity": self.api.existing["identity"]}
        result = t.check_candidate(self.api, "example/product", item)
        self.assertEqual(result["licenses"], ["LICENSE"])
        self.assertIn("pending", result["device_platform_build_validation"])
        self.api.existing["snapshot"]["sha"] = "tampered"
        with self.assertRaisesRegex(ValueError, "identity"):
            t.check_candidate(self.api, "example/product", item)

    def test_missing_state_branch_bootstraps_but_api_failure_does_not(self):
        self.assertEqual(t.load_state(self.api, "example/product"), EMPTY)
        with patch.object(self.api, "pages", side_effect=t.ApiError("403")):
            with self.assertRaises(t.ApiError):
                t.load_state(self.api, "example/product")


if __name__ == "__main__":
    unittest.main()
