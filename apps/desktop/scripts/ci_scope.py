"""Select desktop checks from the complete PR/push diff, including deletions."""

import os
import subprocess


def scope(paths, full=False):
    frontend = native = package = full
    for path in paths:
        if path == '.github/workflows/desktop.yml' or path.startswith('apps/desktop/scripts/'):
            frontend = native = package = True
        elif path == 'docs/assets/app-icon.svg':
            frontend = native = package = True
        elif not path.startswith('apps/desktop/') or path.endswith('.md'):
            continue
        elif path in ('apps/desktop/.gitignore',):
            continue
        elif path.startswith('apps/desktop/src-tauri/src/'):
            native = True
        elif path.startswith('apps/desktop/src-tauri/'):
            native = package = True
        elif path == 'apps/desktop/rust-toolchain.toml':
            native = package = True
        elif path.startswith(('apps/desktop/src/', 'apps/desktop/public/')):
            frontend = True
            if path.startswith('apps/desktop/src/bridge/'):
                native = True
        elif path in ('apps/desktop/package.json', 'apps/desktop/package-lock.json'):
            frontend = native = package = True
        else:
            # Root build inputs (Vite, Node, HTML, TS config) affect the shipped app.
            frontend = native = package = True
    return dict(frontend=frontend, native=native, package=package, build=frontend or native)


def main():
    full = os.environ['EVENT'] == 'workflow_dispatch'
    if full:
        paths = []
    else:
        base, head = os.environ['BASE'], os.environ['HEAD']
        if os.environ['EVENT'] == 'pull_request':
            base = subprocess.check_output(['git', 'merge-base', base, head], text=True).strip()
        paths = subprocess.check_output(
            ['git', 'diff', '--name-only', '--no-renames', '-z', base, head],
        ).decode().split('\0')
    selected = scope(paths, full)
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        for name, enabled in selected.items():
            print(f'{name}={str(enabled).lower()}', file=output)
    print(selected)


if __name__ == '__main__':
    main()
