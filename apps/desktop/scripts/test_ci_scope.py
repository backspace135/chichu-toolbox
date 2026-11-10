import unittest

from ci_scope import scope


class ScopeTest(unittest.TestCase):
    def test_docs_and_android_skip_desktop(self):
        self.assertFalse(scope(['apps/desktop/README.md', 'apps/android/app/build.gradle.kts'])['build'])

    def test_ui_skips_native_and_packaging(self):
        self.assertEqual(scope(['apps/desktop/src/app/App.tsx']),
                         dict(frontend=True, native=False, package=False, build=True))

    def test_shared_rust_checks_native_without_packaging(self):
        self.assertEqual(scope(['apps/desktop/src-tauri/src/commands.rs']),
                         dict(frontend=False, native=True, package=False, build=True))

    def test_bridge_checks_both_sides(self):
        result = scope(['apps/desktop/src/bridge/workspace.ts'])
        self.assertTrue(result['frontend'] and result['native'])
        self.assertFalse(result['package'])

    def test_packaging_inputs_check_native_without_release_build(self):
        for path in ['apps/desktop/src-tauri/Cargo.lock', 'apps/desktop/src-tauri/capabilities/main.json',
                     'apps/desktop/package-lock.json', 'docs/assets/app-icon.svg',
                     '.github/workflows/desktop.yml', 'apps/desktop/vite.config.ts']:
            with self.subTest(path=path):
                self.assertTrue(scope([path])['native'])
                self.assertFalse(scope([path])['package'])

    def test_mixed_changes_union_and_manual_full(self):
        self.assertTrue(all(scope([], full=True).values()))
        self.assertEqual(scope(['apps/desktop/src/app/App.tsx', 'apps/desktop/src-tauri/src/main.rs']),
                         dict(frontend=True, native=True, package=False, build=True))
