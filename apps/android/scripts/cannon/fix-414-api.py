import pathlib

BASE = pathlib.Path("/home/taichang/chichu-toolbox-build/cannon-414/work/ksu/kernel")

ALIAS = (
    "#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 8, 0)\n"
    "// strncpy_from_user_nofault exists only on 5.8+; the plain 4.14 variant has\n"
    "// the same count/-EFAULT return semantics for these user-pointer reads.\n"
    "#define strncpy_from_user_nofault strncpy_from_user\n"
    "#endif\n"
)


def patch(rel, replacements):
    p = BASE / rel
    text = p.read_text()
    for old, new in replacements:
        assert text.count(old) == 1, (rel, old[:60])
        text = text.replace(old, new)
    p.write_text(text)
    print("patched", rel)


patch("feature/sucompat.c", [
    ('#include <linux/mm.h>\n#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 8, 0)',
     '#include <linux/mm.h>\n#include <linux/version.h>\n#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 8, 0)'),
    ('#include "util.h"\n', '#include "util.h"\n\n' + ALIAS),
])
patch("runtime/ksud_integration.c", [
    ('#include "preinit/preinit.h"\n', '#include "preinit/preinit.h"\n\n' + ALIAS),
])
patch("sulog/event.c", [
    ('#include "sulog/event.h"\n', '#include "sulog/event.h"\n\n' + ALIAS),
])
print("done")
