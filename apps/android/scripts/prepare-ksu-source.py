"""Apply toolbox integration to an expendable, pinned KernelSU build checkout."""

import argparse
import pathlib
import subprocess

SOURCE = "730f644615969cfd4a84b45d7f62a667b7b95a50"


def prepare(root):
    revision = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    if revision != SOURCE:
        raise ValueError(f"Expected KernelSU {SOURCE}, got {revision}")
    replacements = {
        "userspace/ksud/src/module.rs": (
            "ScriptWait::Forever => command.status().map(|_| ()),",
            'ScriptWait::Forever => command.status().and_then(|status| {\n'
            '            if status.success() {\n'
            '                Ok(())\n'
            '            } else {\n'
            '                Err(std::io::Error::other(format!("script exited with {status}")))\n'
            '            }\n'
            '        }),',
        ),
        "userspace/ksud/src/late_load.rs": (
            '&format!("{package_name}/me.weishu.kernelsu.ui.MainActivity"),',
            '&format!("{package_name}/com.chichu.toolbox.MainActivity"),',
        ),
    }
    for name, (before, after) in replacements.items():
        path = root / name
        content = path.read_text()
        if content.count(before) != 1:
            raise ValueError(f"Pinned integration context mismatch: {name}")
        path.write_text(content.replace(before, after))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=pathlib.Path)
    prepare(parser.parse_args().source.resolve())
