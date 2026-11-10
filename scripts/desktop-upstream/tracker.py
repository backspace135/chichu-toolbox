#!/usr/bin/env python3
"""GitHub-only maintenance tracking. Never executes upstream code."""
import argparse
import base64
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone
from typing import Any
from urllib.parse import quote

ROOT = Path(__file__).parent
STATE_BRANCH = "desktop-upstream-state"
STATE_PATH = "scripts/desktop-upstream/state.json"


class ApiError(RuntimeError):
    pass


class GitHub:
    def api(self, path, method="GET", data=None) -> Any:
        command = ["gh", "api", path, "--method", method]
        if data is not None:
            command += ["--input", "-"]
        result = subprocess.run(command, input=json.dumps(data) if data is not None else None,
                                text=True, capture_output=True)
        if result.returncode:
            raise ApiError(f"{method} {path}: {result.stderr.strip()}")
        return json.loads(result.stdout) if result.stdout.strip() else None

    def pages(self, path):
        result = []
        page = 1
        while True:
            items = self.api(f"{path}{'&' if '?' in path else '?'}per_page=100&page={page}")
            result.extend(items)
            if len(items) < 100:
                return result
            page += 1


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def snapshot(api, source):
    repo = source["repo"]
    branch = api.api(f"repos/{repo}/branches/{quote(source['branch'], safe='')}")
    tags = {}
    for tag in api.pages(f"repos/{repo}/git/matching-refs/tags/"):
        target = tag["object"]
        direct = copy.deepcopy(target)
        while target["type"] == "tag":
            target = api.api(f"repos/{repo}/git/tags/{target['sha']}")["object"]
        tags[tag["ref"]] = {"ref": direct, "target": target}
    releases = {}
    for release in api.pages(f"repos/{repo}/releases"):
        # Include the complete metadata and separately paginated assets. API URLs,
        # digest, ID, size and updated_at expose replacements even at the same name.
        release = copy.deepcopy(release)
        release.pop("assets", None)
        release["assets"] = api.pages(f"repos/{repo}/releases/{release['id']}/assets")
        for asset in release["assets"]:
            asset.pop("download_count", None) # Downloads are not resource replacements.
        releases[str(release["id"])] = release
    return {"sha": branch["commit"]["sha"], "tags": tags, "releases": releases}


def difference(api, source, before, after):
    if before is None:
        return {"kind": "first-observation", "to": after["sha"],
                "tags": sorted(after["tags"]), "releases": sorted(after["releases"])}
    result = {"from": before["sha"], "to": after["sha"], "changes": {}}
    for field in ("tags", "releases"):
        result["changes"][field] = [key for key in sorted(before[field].keys() | after[field].keys())
                                     if before[field].get(key) != after[field].get(key)]
    if before["sha"] != after["sha"]:
        comparison = api.api(f"repos/{source['repo']}/compare/{before['sha']}...{after['sha']}")
        result["comparison"] = comparison
        result["history_rewritten"] = comparison["status"] not in ("ahead", "identical")
        # GitHub caps comparison files at 300; explicitly require manual full diff.
        result["file_list_may_be_truncated"] = len(comparison["files"]) >= 300
    return result


def candidate(source, observed, adopted, api):
    return {"source": source, "snapshot": observed, "identity": fingerprint(observed),
            "against_adopted": difference(api, source, adopted, observed),
            "adopted_identity": fingerprint(adopted), "frozen": False,
            "validation": [], "review": {
                category: {"status": "required", "owner": source["owner"]}
                for category in ("devices-firmware", "steps", "dependencies-resources",
                                 "windows-macos", "license", "all-features-new-and-changed")}}


def observe(api, sources, state, adopted, now, freeze=""):
    state = copy.deepcopy(state)
    failures = []
    for source in sources:
        sid = source["id"]
        entry = state["sources"].setdefault(sid, {"last_success": None, "candidate": None})
        if freeze == sid:
            if entry["candidate"] is None:
                raise ValueError(f"Cannot freeze {sid}: no candidate")
            entry["candidate"]["frozen"] = True
        try:
            current = snapshot(api, source)
            previous = entry["last_success"]
            incremental = difference(api, source, previous["snapshot"] if previous else None, current)
            changed = previous is None or previous["snapshot"] != current
            # Construct all boundary-dependent values before advancing success.
            proposed = None
            adoption = adopted["sources"].get(sid)
            baseline = adoption["snapshot"] if adoption else None
            if source["mode"] == "integration":
                if baseline != current:
                    proposed = candidate(source, current, baseline, api)
            entry["last_success"] = {"at": now, "snapshot": current, "incremental": incremental}
            entry["attempt"] = {"at": now, "status": "changed" if changed else "unchanged"}
            if incremental.get("history_rewritten"):
                entry["attempt"]["warning"] = "HISTORY REWRITTEN: manual investigation required"
            if proposed is not None:
                active = entry["candidate"]
                if active and adoption and active["identity"] == fingerprint(baseline):
                    active = None # Explicit adopted manifest retires the frozen batch.
                    entry.pop("next_batch", None)
                if active is None or (not active["frozen"] and
                                      (active["identity"], active["adopted_identity"]) !=
                                      (proposed["identity"], proposed["adopted_identity"])):
                    entry["candidate"] = proposed
                elif active["frozen"] and active["identity"] != proposed["identity"]:
                    entry["next_batch"] = proposed
            elif source["mode"] == "integration":
                entry["candidate"] = None
                entry.pop("next_batch", None)
        except (ApiError, json.JSONDecodeError, KeyError) as error:
            entry["attempt"] = {"at": now, "status": "failed", "error": str(error)}
            failures.append(f"{sid}: {error}")
    if freeze and freeze not in {source["id"] for source in sources}:
        raise ValueError(f"Unknown freeze source: {freeze}")
    return state, failures


def load_state(api, repo):
    # A missing branch is the only valid bootstrap condition; auth/API failures
    # are not interpreted as an empty state.
    branches = api.pages(f"repos/{repo}/branches")
    if STATE_BRANCH not in {branch["name"] for branch in branches}:
        return {"sources": {}}
    response = api.api(f"repos/{repo}/contents/{STATE_PATH}?ref={STATE_BRANCH}")
    return json.loads(base64.b64decode(response["content"]))


def commit_files(api, repo, branch, files, base):
    refs = api.pages(f"repos/{repo}/git/matching-refs/heads/{branch}")
    exact = [ref for ref in refs if ref["ref"] == f"refs/heads/{branch}"]
    parent = exact[0]["object"]["sha"] if exact else base
    tree = api.api(f"repos/{repo}/git/commits/{parent}")["tree"]["sha"]
    entries = [{"path": path, "mode": "100644", "type": "blob", "content": content}
               for path, content in files.items()]
    new_tree = api.api(f"repos/{repo}/git/trees", "POST", {"base_tree": tree, "tree": entries})
    if new_tree["sha"] == tree:
        return parent
    commit = api.api(f"repos/{repo}/git/commits", "POST", {
        "message": f"chore(desktop): track upstream ({branch})", "tree": new_tree["sha"], "parents": [parent]})
    if exact:
        api.api(f"repos/{repo}/git/refs/heads/{branch}", "PATCH", {"sha": commit["sha"], "force": False})
    else:
        api.api(f"repos/{repo}/git/refs", "POST", {"ref": f"refs/heads/{branch}", "sha": commit["sha"]})
    return commit["sha"]


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n"


def summary(sid, entry):
    return (f"# Upstream: {sid}\n\n"
            "Discovery is not adoption or device support. New/changed features belong to the recorded owner.\n\n"
            "## Incremental vs last successful observation\n```json\n" +
            encoded(entry["last_success"]["incremental"]) + "```\n\n"
            "## Candidate vs adopted baseline / mandatory review\n```json\n" +
            encoded(entry["candidate"]) + "```\n")


def publish(api, repo, state, run_url):
    metadata = api.api(f"repos/{repo}")
    base_branch = metadata["default_branch"]
    base = api.api(f"repos/{repo}/git/ref/heads/{base_branch}")["object"]["sha"]
    checks = []
    # Persist successful observations AND failed attempts even if PR publishing fails.
    commit_files(api, repo, STATE_BRANCH, {STATE_PATH: encoded(state)}, base)
    for sid, entry in state["sources"].items():
        item = entry["candidate"]
        if item is None or entry["attempt"]["status"] == "failed":
            continue
        branch = f"desktop-upstream/{sid}"
        prs = api.pages(f"repos/{repo}/pulls?state=open&head={repo.split('/')[0]}:{branch}")
        if len(prs) > 1:
            raise ValueError(f"Multiple active PRs for {sid}")
        path = f"scripts/desktop-upstream/candidates/{sid}.json"
        if item["frozen"] and prs:
            # Even the incremental report in this PR must remain frozen.
            checks.append({"id": sid, "head": prs[0]["head"]["sha"], "pr": prs[0]["number"], "identity": item["identity"]})
            continue
        if prs:
            existing = api.api(f"repos/{repo}/contents/{path}?ref={prs[0]['head']['sha']}")
            existing_item = json.loads(base64.b64decode(existing["content"]))
            if existing_item == item:
                checks.append({"id": sid, "head": prs[0]["head"]["sha"], "pr": prs[0]["number"], "identity": item["identity"]})
                continue
        head = commit_files(api, repo, branch, {path: encoded(item),
                            f"scripts/desktop-upstream/candidates/{sid}.md": summary(sid, entry)}, base)
        body = (f"Upstream candidate `{item['identity']}`; source SHA `{item['snapshot']['sha']}`.\n\n"
                f"Explicit static checks: {run_url} (candidate-checks job).\n\n"
                "See committed incremental/adopted differences and required review dimensions. "
                "No device validation, signing, integration or adoption is implied. "
                "Adoption requires a separate reviewed change to adopted.json with evidence. No automatic merge.")
        if prs:
            pr = api.api(f"repos/{repo}/pulls/{prs[0]['number']}", "PATCH", {"body": body})
        else:
            pr = api.api(f"repos/{repo}/pulls", "POST", {"head": branch, "base": base_branch,
                         "title": f"chore(desktop): upstream candidate {sid}", "body": body})
        checks.append({"id": sid, "head": head, "pr": pr["number"], "identity": item["identity"]})
    return checks


def check_candidate(api, repo, item):
    path = f"scripts/desktop-upstream/candidates/{item['id']}.json"
    response = api.api(f"repos/{repo}/contents/{path}?ref={item['head']}")
    value = json.loads(base64.b64decode(response["content"]))
    if fingerprint(value["snapshot"]) != item["identity"] or value["identity"] != item["identity"]:
        raise ValueError("Candidate identity does not match published snapshot")
    source = value["source"]
    commit = api.api(f"repos/{source['repo']}/git/commits/{value['snapshot']['sha']}")
    tree = api.api(f"repos/{source['repo']}/git/trees/{commit['tree']['sha']}?recursive=1")
    if tree["truncated"]:
        raise ValueError("Upstream tree truncated: complete static inventory required")
    paths = [item["path"] for item in tree["tree"] if item["type"] == "blob"]
    licenses = [path for path in paths if re.search(r"(^|/)(license|copying|notice)", path, re.I)]
    if not licenses:
        raise ValueError("No license/notice file found: manual license investigation required")
    return {"identity": item["identity"], "sha": value["snapshot"]["sha"],
            "licenses": licenses, "files": len(paths), "scope": "snapshot integrity + static license inventory only",
            "device_platform_build_validation": "pending; no upstream code executed"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=("observe", "publish", "check"))
    parser.add_argument("--repo", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--freeze", default="")
    args = parser.parse_args()
    api = GitHub()
    if args.operation == "observe":
        sources = json.loads((ROOT / "sources.json").read_text())["sources"]
        for source in sources:
            if source["mode"] not in ("investigation", "integration") or not re.fullmatch(r"[a-z0-9-]+", source["id"]):
                raise ValueError(f"Invalid source configuration: {source}")
        state, failures = observe(api, sources, load_state(api, args.repo),
                                  json.loads((ROOT / "adopted.json").read_text()),
                                  datetime.now(timezone.utc).isoformat(), args.freeze)
        args.output.write_text(encoded(state))
        if failures:
            sys.exit("\n".join(failures))
    elif args.operation == "publish":
        state = json.loads(args.output.read_text())
        checks = publish(api, args.repo, state, os.environ["RUN_URL"])
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write("checks=" + json.dumps(checks) + "\n")
    else:
        item = json.loads(os.environ["CANDIDATE"])
        run = api.api(f"repos/{args.repo}/check-runs", "POST", {
            "name": "desktop-upstream/static-candidate", "head_sha": item["head"], "status": "in_progress"})
        try:
            result = check_candidate(api, args.repo, item)
        except (ApiError, ValueError, KeyError) as error:
            api.api(f"repos/{args.repo}/check-runs/{run['id']}", "PATCH", {
                "conclusion": "failure", "output": {"title": "Candidate check failed", "summary": str(error)}})
            raise
        args.output.write_text(encoded(result))
        api.api(f"repos/{args.repo}/check-runs/{run['id']}", "PATCH", {
            "conclusion": "success", "output": {"title": "Static candidate check only", "summary": encoded(result)}})


if __name__ == "__main__":
    main()
