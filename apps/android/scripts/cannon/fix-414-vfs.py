import pathlib

BASE = pathlib.Path("/home/taichang/chichu-toolbox-build/cannon-414/work/ksu/kernel")


def patch(rel, replacements):
    p = BASE / rel
    text = p.read_text()
    for old, new in replacements:
        if new in text:
            continue  # already applied; safe on re-runs
        assert text.count(old) == 1, (rel, old[:60])
        text = text.replace(old, new)
    p.write_text(text)
    print("patched", rel)


patch("hook/arm64/patch_memory.c", [
    ("    return __pte_to_phys(*pte) + ((addr & ~PAGE_MASK));\n\nfail:",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 4, 0)\n    return __pte_to_phys(*pte) + ((addr & ~PAGE_MASK));\n"
     "#else\n    // __pte_to_phys appeared in 5.4; 4.14 derives it from the pfn.\n"
     "    return __pfn_to_phys(pte_pfn(*pte)) + ((addr & ~PAGE_MASK));\n#endif\n\nfail:"),
    ("    ret = (int)copy_to_kernel_nofault(map, src, len);\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 8, 0)\n    ret = (int)copy_to_kernel_nofault(map, src, len);\n"
     "#else\n    ret = (int)probe_kernel_write(map, src, len);\n#endif\n"),
    ("#define ksu_flush_icache(start, end) __flush_icache_range(start, end)\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)\n#define ksu_flush_icache(start, end) __flush_icache_range(start, end)\n"
     "#else\n#define ksu_flush_icache(start, end) flush_icache_range(start, end)\n#endif\n"),
])

patch("infra/file_wrapper.c", [
    # f_op->iopoll exists from 5.1 only.
    ("#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)\nstatic int ksu_wrapper_iopoll(struct kiocb *kiocb, struct io_comp_batch *icb, unsigned int v)",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0)\n"
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)\n"
     "static int ksu_wrapper_iopoll(struct kiocb *kiocb, struct io_comp_batch *icb, unsigned int v)"),
    ("    return orig->f_op->iopoll(kiocb, spin);\n}\n#endif\n",
     "    return orig->f_op->iopoll(kiocb, spin);\n}\n#endif\n#endif\n"),
    # __poll_t exists from 4.16 only.
    ("static __poll_t ksu_wrapper_poll(struct file *fp, struct poll_table_struct *pts)",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 16, 0)\n"
     "static __poll_t ksu_wrapper_poll(struct file *fp, struct poll_table_struct *pts)\n"
     "#else\nstatic unsigned int ksu_wrapper_poll(struct file *fp, struct poll_table_struct *pts)\n#endif"),
    # f_op->remap_file_range / REMAP_FILE_DEDUP exist from 4.20 only.
    ("static loff_t ksu_wrapper_remap_file_range(struct file *file_in, loff_t pos_in, struct file *file_out, loff_t pos_out,",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 20, 0)\n"
     "static loff_t ksu_wrapper_remap_file_range(struct file *file_in, loff_t pos_in, struct file *file_out, loff_t pos_out,"),
    ("        return orig->f_op->remap_file_range(orig, pos_in, file_out, pos_out, len, remap_flags);\n    }\n}\n",
     "        return orig->f_op->remap_file_range(orig, pos_in, file_out, pos_out, len, remap_flags);\n    }\n}\n#endif\n"),
    # f_op->fadvise exists from 5.1 only.
    ("static int ksu_wrapper_fadvise(struct file *fp, loff_t off1, loff_t off2, int flags)",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0)\n"
     "static int ksu_wrapper_fadvise(struct file *fp, loff_t off1, loff_t off2, int flags)"),
    ("    return -EINVAL;\n}\n\nstatic void ksu_release_file_wrapper",
     "    return -EINVAL;\n}\n#endif\n\nstatic void ksu_release_file_wrapper"),
    ("    p->ops.iopoll = fp->f_op->iopoll ? ksu_wrapper_iopoll : NULL;\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0)\n"
     "    p->ops.iopoll = fp->f_op->iopoll ? ksu_wrapper_iopoll : NULL;\n#endif\n"),
    ("#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 12, 0)\n    p->ops.fop_flags = fp->f_op->fop_flags;\n"
     "#else\n    p->ops.mmap_supported_flags = fp->f_op->mmap_supported_flags;\n#endif",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 12, 0)\n    p->ops.fop_flags = fp->f_op->fop_flags;\n"
     "#elif LINUX_VERSION_CODE >= KERNEL_VERSION(4, 20, 0)\n    p->ops.mmap_supported_flags = fp->f_op->mmap_supported_flags;\n#endif"),
    ("    p->ops.remap_file_range = fp->f_op->remap_file_range ? ksu_wrapper_remap_file_range : NULL;\n"
     "    p->ops.fadvise = fp->f_op->fadvise ? ksu_wrapper_fadvise : NULL;\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 20, 0)\n"
     "    p->ops.remap_file_range = fp->f_op->remap_file_range ? ksu_wrapper_remap_file_range : NULL;\n#endif\n"
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0)\n"
     "    p->ops.fadvise = fp->f_op->fadvise ? ksu_wrapper_fadvise : NULL;\n#endif\n"),
    # 4.14 has neither alloc_file_pseudo (4.19+) nor security_inode_init_security_anon
    # (5.17+ / GKI backport); fall back to a plain anonymous inode.
    ("#define ksu_anon_inode_create_getfile_compat anon_inode_getfile_secure\n#else",
     "#define ksu_anon_inode_create_getfile_compat anon_inode_getfile_secure\n#else\n"
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 19, 0)"),
    ("#endif\n\nint ksu_install_file_wrapper(int fd)",
     "#else\n// 4.14 has neither alloc_file_pseudo nor the anon-inode security hook; a plain\n"
     "// anonymous inode keeps the wrapped file in the standard anonymous class.\n"
     "#define ksu_anon_inode_create_getfile_compat(name, fops, priv, flags, context_inode) \\\n"
     "    anon_inode_getfile(name, fops, priv, flags)\n#endif\n#endif\n\nint ksu_install_file_wrapper(int fd)"),
])
print("done")


patch("infra/event_queue.h", [
    ("#include <linux/wait.h>\n",
     "#include <linux/wait.h>\n#include <linux/version.h>\n"
     "#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 16, 0)\n"
     "// __poll_t was introduced in 4.16; 4.14 uses plain unsigned int poll masks.\n"
     "typedef unsigned int __poll_t;\n#endif\n"),
])

patch("infra/event_queue.c", [
    ("#include <linux/poll.h>\n",
     "#include <linux/poll.h>\n#include <linux/eventpoll.h>\n"),
])

patch("infra/file_wrapper.c", [
    # The 4.14 fallback uses anon_inode_getfile; the mnt capture needs the compat block.
    ("void __init ksu_file_wrapper_init(void)\n{\n#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 16, 0)",
     "void __init ksu_file_wrapper_init(void)\n{\n"
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 19, 0) && LINUX_VERSION_CODE < KERNEL_VERSION(5, 16, 0)"),
])

patch("infra/su_mount_ns.c", [
    # uapi/linux/mount.h exists from 4.17 only; 4.14 goes through path_mount.
    ("#include <uapi/linux/mount.h>\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 17, 0)\n#include <uapi/linux/mount.h>\n#endif\n"),
])

patch("infra/seccomp_cache.c", [
    # The seccomp action cache exists from 5.0; before that decisions are
    # recomputed per syscall, so the invalidation becomes a no-op.
    ("#include \"infra/seccomp_cache.h\"\n",
     "#include \"infra/seccomp_cache.h\"\n#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)"),
    ("    if (nr >= 0 && nr < SECCOMP_ARCH_COMPAT_NR) {\n        set_bit(nr, filter->cache.allow_compat);\n    }\n#endif\n}\n",
     "    if (nr >= 0 && nr < SECCOMP_ARCH_COMPAT_NR) {\n        set_bit(nr, filter->cache.allow_compat);\n    }\n#endif\n}\n"
     "#else\n// 4.14 has no seccomp action cache; decisions are recomputed per syscall.\n"
     "void ksu_seccomp_clear_cache(struct seccomp_filter *filter, int nr) { }\n"
     "void ksu_seccomp_allow_cache(struct seccomp_filter *filter, int nr) { }\n#endif\n"),
])

patch("manager/pkg_observer.c", [
    ("            if (!task_work_add(current, cb, TWA_RESUME))\n                return;\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0)\n"
     "            if (!task_work_add(current, cb, TWA_RESUME))\n                return;\n"
     "#else\n            if (!task_work_add(current, cb, true))\n                return;\n#endif\n"),
    ("static int ksu_handle_inode_event(struct fsnotify_mark *mark, u32 mask, struct inode *inode, struct inode *dir,\n"
     "                                  const struct qstr *file_name, u32 cookie)\n{\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0)\n"
     "static int ksu_handle_inode_event(struct fsnotify_mark *mark, u32 mask, struct inode *inode, struct inode *dir,\n"
     "                                  const struct qstr *file_name, u32 cookie)\n{\n"),
    ("    return 0;\n}\n\nstatic const struct fsnotify_ops ksu_ops = {\n    .handle_inode_event = ksu_handle_inode_event,\n};\n",
     "    return 0;\n}\n#else\n"
     "// 4.14 fsnotify_ops use handle_event; dentry names are consumed with strlen\n"
     "// like inotify (d_alloc NUL-terminates d_name.name).\n"
     "static int ksu_handle_event(struct fsnotify_group *group, struct inode *inode,\n"
     "                            struct fsnotify_mark *inode_mark, struct fsnotify_mark *vfsmount_mark,\n"
     "                            u32 mask, const void *data, int data_type,\n"
     "                            const unsigned char *file_name, u32 cookie,\n"
     "                            struct fsnotify_iter_info *iter_info)\n{\n"
     "    if (!file_name)\n        return 0;\n    if (mask & FS_ISDIR)\n        return 0;\n"
     "    if (strlen(file_name) == 13 && !memcmp(file_name, \"packages.list\", 13)) {\n"
     "        pr_info(\"packages.list detected: %d\\n\", mask);\n        ksu_defer_track_throne();\n    }\n"
     "    return 0;\n}\n#endif\n"
     "\nstatic const struct fsnotify_ops ksu_ops = {\n"
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0)\n    .handle_inode_event = ksu_handle_inode_event,\n"
     "#else\n    .handle_event = ksu_handle_event,\n#endif\n};\n"),
    ("    if (fsnotify_add_inode_mark(m, inode, 0)) {",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n    if (fsnotify_add_inode_mark(m, inode, 0)) {\n"
     "#else\n    if (fsnotify_add_mark(m, inode, NULL, 0)) {\n#endif"),
])

patch("policy/allowlist.c", [
    ("#include <linux/kref.h>\n\n#include \"klog.h\" // IWYU pragma: keep\n",
     "#include <linux/kref.h>\n#include <linux/sched/task.h>\n\n"
     "#ifndef fallthrough\n"
     "// fallthrough pseudo-keyword exists from 5.1; a no-op keeps 4.14 switch behavior.\n"
     "#define fallthrough do { } while (0)\n#endif\n"
     "#include \"klog.h\" // IWYU pragma: keep\n"),
    ("    if (task_work_add(tsk, cb, TWA_RESUME)) {\n        kfree(cb);\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0)\n    if (task_work_add(tsk, cb, TWA_RESUME)) {\n"
     "#else\n    if (task_work_add(tsk, cb, true)) {\n#endif\n        kfree(cb);\n"),
])

patch("policy/app_profile.c", [
    # seccomp.filter_count exists from 5.x only; mode and filter are enough on 4.14.
    ("    current->seccomp.mode = 0;\n    current->seccomp.filter = NULL;\n    atomic_set(&current->seccomp.filter_count, 0);\n",
     "    current->seccomp.mode = 0;\n    current->seccomp.filter = NULL;\n"
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)\n    atomic_set(&current->seccomp.filter_count, 0);\n#endif\n"),
])

patch("runtime/ksud_integration.c", [
    # copy_{from,to}_user_nofault exist from 5.5; 4.14 has the probe_ equivalents.
    ("        if (!copy_from_user_nofault(&size, st_size_ptr, sizeof(long))) {\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 5, 0)\n"
     "        if (!copy_from_user_nofault(&size, st_size_ptr, sizeof(long))) {\n"
     "#else\n        if (!probe_kernel_read(&size, st_size_ptr, sizeof(long))) {\n#endif\n"),
    ("            if (!copy_to_user_nofault(st_size_ptr, &new_size, sizeof(long))) {\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 5, 0)\n"
     "            if (!copy_to_user_nofault(st_size_ptr, &new_size, sizeof(long))) {\n"
     "#else\n            if (!probe_kernel_write(st_size_ptr, &new_size, sizeof(long))) {\n#endif\n"),
])

patch("selinux/sepolicy.h", [
    ("#include <linux/types.h>\n\n#include \"ss/policydb.h\"\n\nstruct selinux_policy *ksu_dup_sepolicy(struct selinux_policy *old_pol);",
     "#include <linux/types.h>\n#include <linux/version.h>\n\n#include \"ss/policydb.h\"\n\n"
     "#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 0, 0)\n"
     "// 4.14 keeps policydb/sidtab/latest_granting in struct selinux_ss; this port\n"
     "// uses the same member names, so the 6.x selinux_policy alias maps onto it.\n"
     "#define selinux_policy selinux_ss\n"
     "#endif\n"
     "\nstruct selinux_policy *ksu_dup_sepolicy(struct selinux_policy *old_pol);"),
])

patch("selinux/rules.c", [
    ("#include <linux/uaccess.h>\n#include <linux/types.h>\n",
     "#include <linux/uaccess.h>\n#include <linux/types.h>\n#include <linux/vmalloc.h>\n"),
    ("void apply_kernelsu_rules()\n{\n    struct selinux_policy *pol, *old_pol;\n    struct policydb *db;\n",
     "#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 0, 0)\n"
     "// 4.14 keeps the live policy in the static selinux_ss; reload by re-serializing\n"
     "// the modified database and handing it to the kernel's own security_load_policy,\n"
     "// which swaps the ss and resets the avc.\n"
     "static void ksu_reload_sepolicy(struct policydb *db)\n{\n"
     "    size_t len = db->len + (size_t)db->p_types.nprim * (sizeof(u32) + sizeof(u64));\n"
     "    void *data = vmalloc(len);\n    struct policy_file fp;\n"
     "    if (!data) {\n        pr_err(\"sepolicy: alloc reload buffer len %zu\\n\", len);\n        return;\n    }\n"
     "    fp.data = data;\n    fp.len = len;\n"
     "    if (policydb_write(db, &fp)) {\n        pr_err(\"sepolicy: reload policydb_write failed\\n\");\n        kvfree(data);\n        return;\n    }\n"
     "    security_load_policy(&selinux_state, data, len - fp.len);\n"
     "    kvfree(data);\n}\n#endif\n\n"
     "void apply_kernelsu_rules()\n{\n    struct selinux_policy *pol, *old_pol;\n    struct policydb *db;\n"),
    ("    mutex_lock(&selinux_state.policy_mutex);\n\n    old_pol = rcu_dereference_protected(selinux_state.policy, lockdep_is_held(&selinux_state.policy_mutex));\n    backup_sepolicy = ksu_dup_sepolicy(old_pol);\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)\n"
     "    mutex_lock(&selinux_state.policy_mutex);\n\n"
     "    old_pol = rcu_dereference_protected(selinux_state.policy, lockdep_is_held(&selinux_state.policy_mutex));\n"
     "#else\n"
     "    // 4.14: the ss holds the live policy; the kernel's own load_mutex\n    // serializes the reload inside security_load_policy.\n"
     "    old_pol = selinux_state.ss;\n#endif\n"
     "    backup_sepolicy = ksu_dup_sepolicy(old_pol);\n"),
    ("    rcu_assign_pointer(selinux_state.policy, pol);\n    synchronize_rcu();\n    ksu_destroy_sepolicy(old_pol);\n\n    reset_avc_cache(pol->latest_granting);\nout_unlock:\n    mutex_unlock(&selinux_state.policy_mutex);\n}\n\n#define KSU_SEPOLICY_MAX_BATCH_SIZE",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)\n"
     "    rcu_assign_pointer(selinux_state.policy, pol);\n    synchronize_rcu();\n    ksu_destroy_sepolicy(old_pol);\n\n    reset_avc_cache(pol->latest_granting);\n"
     "#else\n"
     "    // 4.14: old_pol is the live ss and stays kernel-owned; pol is scratch.\n"
     "    ksu_reload_sepolicy(&pol->policydb);\n    ksu_destroy_sepolicy(pol);\n#endif\n"
     "out_unlock:\n#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)\n    mutex_unlock(&selinux_state.policy_mutex);\n#else\n    ;\n#endif\n}\n\n#define KSU_SEPOLICY_MAX_BATCH_SIZE"),
    ("    mutex_lock(&selinux_state.policy_mutex);\n\n    old_pol = rcu_dereference_protected(selinux_state.policy, lockdep_is_held(&selinux_state.policy_mutex));\n    pol = ksu_dup_sepolicy(old_pol);\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)\n"
     "    mutex_lock(&selinux_state.policy_mutex);\n\n"
     "    old_pol = rcu_dereference_protected(selinux_state.policy, lockdep_is_held(&selinux_state.policy_mutex));\n"
     "#else\n    old_pol = selinux_state.ss;\n#endif\n"
     "    pol = ksu_dup_sepolicy(old_pol);\n"),
    ("    rcu_assign_pointer(selinux_state.policy, pol);\n    synchronize_rcu();\n    ksu_destroy_sepolicy(old_pol);\n\n    reset_avc_cache(pol->latest_granting);\n    ret = success_cmd_count;\n    goto out_unlock;\n\nout_drop_new_policy:\n    ksu_destroy_sepolicy(pol);\nout_unlock:\n    mutex_unlock(&selinux_state.policy_mutex);\nout_free:\n    kvfree(payload);\n\n    return ret;\n}\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)\n"
     "    rcu_assign_pointer(selinux_state.policy, pol);\n    synchronize_rcu();\n    ksu_destroy_sepolicy(old_pol);\n\n    reset_avc_cache(pol->latest_granting);\n"
     "#else\n    ksu_reload_sepolicy(&pol->policydb);\n    ksu_destroy_sepolicy(pol);\n#endif\n"
     "    ret = success_cmd_count;\n    goto out_unlock;\n\nout_drop_new_policy:\n    ksu_destroy_sepolicy(pol);\nout_unlock:\n#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)\n    mutex_unlock(&selinux_state.policy_mutex);\n#endif\nout_free:\n    kvfree(payload);\n\n    return ret;\n}\n"),
])

patch("selinux/selinux.c", [
    ("    tsec = selinux_cred(cred);\n    if (!tsec) {\n        pr_err(\"tsec == NULL!\\n\");\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)\n    tsec = selinux_cred(cred);\n"
     "#else\n    tsec = cred->security;\n#endif\n"
     "    if (!tsec) {\n        pr_err(\"tsec == NULL!\\n\");\n"),
    ("#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 18, 0)\n    const struct task_security_struct *tsec = selinux_cred(cred);\n#else\n    const struct cred_security_struct *tsec = selinux_cred(cred);\n#endif\n",
     "#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 0, 0)\n    const struct task_security_struct *tsec = cred->security;\n"
     "#elif LINUX_VERSION_CODE < KERNEL_VERSION(6, 18, 0)\n    const struct task_security_struct *tsec = selinux_cred(cred);\n"
     "#else\n    const struct cred_security_struct *tsec = selinux_cred(cred);\n#endif\n"),
])

patch("selinux/inode.c", [
    ("#include <linux/fs.h>\n#include <linux/uaccess.h>\n",
     "#include <linux/fs.h>\n#include <linux/uaccess.h>\n#include <linux/version.h>\n"),
    ("    return security;\n#else\n    return selinux_inode(inode);\n#endif\n}",
     "    return security;\n#else\n#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)\n    return selinux_inode(inode);\n"
     "#else\n    return inode->i_security;\n#endif\n#endif\n}"),
])

patch("selinux/sepolicy.c", [
    # avtab.htable is a flex_array below 5.9; slot reads go through
    # flex_array_get_ptr and slot writes through flex_array_put.
    ("        for (n = db->te_avtab.htable[i]; n; prev = n, n = n->next) {\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n"
     "        for (n = db->te_avtab.htable[i]; n; prev = n, n = n->next) {\n"
     "#else\n        for (n = flex_array_get_ptr(db->te_avtab.htable, i); n; prev = n, n = n->next) {\n#endif\n"),
    ("                db->te_avtab.htable[i] = n->next;\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n                db->te_avtab.htable[i] = n->next;\n"
     "#else\n                flex_array_put(db->te_avtab.htable, i, &n->next, GFP_KERNEL);\n#endif\n"),
    ("            removed.htable[0] = n;\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n            removed.htable[0] = n;\n"
     "#else\n            flex_array_put(removed.htable, 0, &n, GFP_KERNEL);\n#endif\n"),
    # filename_trans: below 5.9 this Android tree already uses a hashtab keyed by
    # the flat filename_trans {stype, ttype, tclass, name}; the 5.9 impl uses
    # filename_trans_key + stypes bitmaps which do not exist here.
    ("static bool add_filename_trans(struct policydb *db, const char *s, const char *t, const char *c, const char *d,\n"
     "                               const char *o)\n{\n    struct type_datum *src, *tgt, *def;\n    struct class_datum *cls;\n    struct filename_trans_key *new_key = NULL;\n    int rc;\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n"
     "static bool add_filename_trans(struct policydb *db, const char *s, const char *t, const char *c, const char *d,\n"
     "                               const char *o)\n{\n    struct type_datum *src, *tgt, *def;\n    struct class_datum *cls;\n    struct filename_trans_key *new_key = NULL;\n    int rc;\n"),
    ("out:\n    return false;\n}\n\nstatic bool add_genfscon(",
     "out:\n    return false;\n}\n#else\n"
     "static bool add_filename_trans(struct policydb *db, const char *s, const char *t, const char *c, const char *d,\n"
     "                               const char *o)\n{\n"
     "    struct type_datum *src, *tgt, *def;\n    struct class_datum *cls;\n"
     "    struct filename_trans key = { .stype = 0, .ttype = 0, .tclass = 0, .name = (char *)o };\n"
     "    struct filename_trans_datum *datum;\n    struct filename_trans *new_key;\n"
     "    src = symtab_search(&db->p_types, s);\n    if (!src) {\n        pr_warn(\"source type %s does not exist\\n\", s);\n        return false;\n    }\n"
     "    tgt = symtab_search(&db->p_types, t);\n    if (!tgt) {\n        pr_warn(\"target type %s does not exist\\n\", t);\n        return false;\n    }\n"
     "    cls = symtab_search(&db->p_classes, c);\n    if (!cls) {\n        pr_warn(\"class %s does not exist\\n\", c);\n        return false;\n    }\n"
     "    def = symtab_search(&db->p_types, d);\n    if (!def) {\n        pr_warn(\"default type %s does not exist\\n\", d);\n        return false;\n    }\n"
     "    key.stype = src->value;\n    key.ttype = tgt->value;\n    key.tclass = cls->value;\n"
     "    datum = hashtab_search(db->filename_trans, &key);\n"
     "    if (datum) {\n        datum->otype = def->value;\n        return true;\n    }\n"
     "    new_key = kmemdup(&key, sizeof(key), GFP_KERNEL);\n    datum = kcalloc(1, sizeof(*datum), GFP_KERNEL);\n"
     "    if (!new_key || !datum) {\n        kfree(new_key);\n        kfree(datum);\n        return false;\n    }\n"
     "    new_key->name = kstrdup(o, GFP_KERNEL);\n    if (!new_key->name) {\n        kfree(new_key);\n        kfree(datum);\n        return false;\n    }\n"
     "    datum->otype = def->value;\n"
     "    if (ebitmap_set_bit(&db->filename_trans_ttypes, tgt->value, 1)) {\n        kfree(new_key->name);\n        kfree(new_key);\n        kfree(datum);\n        return false;\n    }\n"
     "    if (hashtab_insert(db->filename_trans, new_key, datum)) {\n"
     "        kfree(new_key->name);\n        kfree(new_key);\n        kfree(datum);\n        return false;\n    }\n"
     "    return true;\n}\n#endif\n\nstatic bool add_genfscon("),
])

patch("selinux/sepolicy.c", [
    # 4.14 keeps the policydb value-index arrays in flex_arrays; grow by copying
    # into a larger one and replacing the pointer.
    ("static bool add_type(struct policydb *db, const char *type_name, bool attr)\n{\n",
     "#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 9, 0)\n"
     "static struct flex_array *ksu_flex_array_grow(struct flex_array *old, size_t element_size,\n"
     "                                            unsigned int new_count)\n{\n"
     "    struct flex_array *grown = flex_array_alloc(element_size, new_count, GFP_KERNEL);\n"
     "    unsigned int i;\n"
     "    if (!grown)\n        return NULL;\n"
     "    if (flex_array_prealloc(grown, 0, new_count, GFP_KERNEL)) {\n"
     "        flex_array_free(grown);\n        return NULL;\n    }\n"
     "    for (i = 0; i + 1 < new_count; i++) {\n"
     "        void *element = flex_array_get(old, i);\n"
     "        if (!element || flex_array_put(grown, i, element, GFP_KERNEL)) {\n"
     "            flex_array_free(grown);\n            return NULL;\n        }\n    }\n"
     "    flex_array_free(old);\n    return grown;\n}\n#endif\n\n"
     "static bool add_type(struct policydb *db, const char *type_name, bool attr)\n{\n"),
    ("    struct ebitmap *new_type_attr_map_array =\n        ksu_kvrealloc(db->type_attr_map_array, value * sizeof(struct ebitmap), (value - 1) * sizeof(struct ebitmap));\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n"
     "    struct ebitmap *new_type_attr_map_array =\n        ksu_kvrealloc(db->type_attr_map_array, value * sizeof(struct ebitmap), (value - 1) * sizeof(struct ebitmap));\n"),
    ("    db->sym_val_to_name[SYM_TYPES][value - 1] = key;\n\n    int i;\n",
     "    db->sym_val_to_name[SYM_TYPES][value - 1] = key;\n"
     "#else\n"
     "    struct flex_array *new_type_attr_map_array =\n"
     "        ksu_flex_array_grow(db->type_attr_map_array, sizeof(struct ebitmap), value);\n"
     "    struct flex_array *new_type_val_to_struct =\n"
     "        ksu_flex_array_grow(db->type_val_to_struct_array, sizeof(struct type_datum *), value);\n"
     "    struct flex_array *new_val_to_name_types =\n"
     "        ksu_flex_array_grow(db->sym_val_to_name[SYM_TYPES], sizeof(char *), value);\n"
     "    struct ebitmap *sattr;\n    struct type_datum **type_slot;\n    char **name_slot;\n"
     "    if (!new_type_attr_map_array || !new_type_val_to_struct || !new_val_to_name_types) {\n"
     "        if (new_type_attr_map_array)\n            flex_array_free(new_type_attr_map_array);\n"
     "        if (new_type_val_to_struct)\n            flex_array_free(new_type_val_to_struct);\n"
     "        if (new_val_to_name_types)\n            flex_array_free(new_val_to_name_types);\n"
     "        pr_err(\"add_type: grow flex arrays failed\\n\");\n        return false;\n    }\n"
     "    db->type_attr_map_array = new_type_attr_map_array;\n"
     "    db->type_val_to_struct_array = new_type_val_to_struct;\n"
     "    db->sym_val_to_name[SYM_TYPES] = new_val_to_name_types;\n"
     "    sattr = flex_array_get(db->type_attr_map_array, value - 1);\n"
     "    if (!sattr)\n        return false;\n"
     "    ebitmap_init(sattr);\n    ebitmap_set_bit(sattr, value - 1, 1);\n"
     "    type_slot = flex_array_get(db->type_val_to_struct_array, value - 1);\n"
     "    if (!type_slot)\n        return false;\n"
     "    *type_slot = type;\n"
     "    name_slot = flex_array_get(db->sym_val_to_name[SYM_TYPES], value - 1);\n"
     "    if (!name_slot)\n        return false;\n"
     "    *name_slot = key;\n#endif\n\n    int i;\n"),
    ("    struct ebitmap *sattr = &db->type_attr_map_array[type->value - 1];\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n"
     "    struct ebitmap *sattr = &db->type_attr_map_array[type->value - 1];\n"
     "#else\n    struct ebitmap *sattr = flex_array_get(db->type_attr_map_array, type->value - 1);\n#endif\n"),
])

patch("selinux/sepolicy.c", [
    # This 4.14 policydb has android_netlink_route only; the getneigh member and
    # its config bit came with later Android policydb versions.
    ("        if (old_pol->policydb.android_netlink_getneigh) {\n"
     "            pr_info(\"adding POLICYDB_CONFIG_ANDROID_NETLINK_GETNEIGH\\n\");\n"
     "            *config_ptr |= POLICYDB_CONFIG_ANDROID_NETLINK_GETNEIGH;\n        }\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)\n"
     "        if (old_pol->policydb.android_netlink_getneigh) {\n"
     "            pr_info(\"adding POLICYDB_CONFIG_ANDROID_NETLINK_GETNEIGH\\n\");\n"
     "            *config_ptr |= POLICYDB_CONFIG_ANDROID_NETLINK_GETNEIGH;\n        }\n#endif\n"),
])

patch("sulog/event.c", [
    # linux/minmax.h exists from 5.0; this file does not use its macros directly.
    ("#include <linux/gfp.h>\n#include <linux/minmax.h>\n#include <linux/overflow.h>\n",
     "#include <linux/gfp.h>\n#include <linux/version.h>\n"
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)\n#include <linux/minmax.h>\n#endif\n"
     "#include <linux/overflow.h>\n"),
])

patch("supercall/dispatch.c", [
    # tasklist_lock and init_task live in linux/sched/task.h here; kvcalloc
    # exists from 4.18 only, kvmalloc_array with __GFP_ZERO is equivalent.
    ("#include <linux/version.h>\n#include <linux/thread_info.h>\n",
     "#include <linux/version.h>\n#include <linux/sched/task.h>\n#include <linux/thread_info.h>\n"
     "#if LINUX_VERSION_CODE < KERNEL_VERSION(4, 18, 0)\n"
     "#define kvcalloc(n, size, flags) kvmalloc_array(n, size, (flags) | __GFP_ZERO)\n#endif\n"),
])

patch("supercall/supercall.c", [
    ("        if (task_work_add(current, &tw->cb, TWA_RESUME)) {\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 1, 0)\n"
     "        if (task_work_add(current, &tw->cb, TWA_RESUME)) {\n"
     "#else\n        if (task_work_add(current, &tw->cb, true)) {\n#endif\n"),
])

patch("feature/selinux_hide.c", [
    ("    tsec = selinux_cred(cred);\n    return tsec->sid;\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)\n    tsec = selinux_cred(cred);\n"
     "#else\n    tsec = cred->security;\n#endif\n    return tsec->sid;\n"),
    ("    tsec = selinux_cred(new);\n    error = -EINVAL;\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 0, 0)\n    tsec = selinux_cred(new);\n"
     "#else\n    tsec = new->security;\n#endif\n    error = -EINVAL;\n"),
    ("    mutex_lock(&selinux_state.status_lock);\n    if (fake_status)\n        goto out;\n    if (!selinux_state.status_page) {\n"
     "        pr_warn(\"initialize_fake_status: status_page not exist\\n\");\n        goto out;\n    }\n\n"
     "    struct selinux_kernel_status *status = page_address(selinux_state.status_page);\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n    mutex_lock(&selinux_state.status_lock);\n"
     "#else\n    mutex_lock(&selinux_state.ss->status_lock);\n#endif\n"
     "    if (fake_status)\n        goto out;\n"
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n    if (!selinux_state.status_page) {\n"
     "#else\n    if (!selinux_state.ss->status_page) {\n#endif\n"
     "        pr_warn(\"initialize_fake_status: status_page not exist\\n\");\n        goto out;\n    }\n\n"
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n    struct selinux_kernel_status *status = page_address(selinux_state.status_page);\n"
     "#else\n    struct selinux_kernel_status *status = page_address(selinux_state.ss->status_page);\n#endif\n"),
    ("out:\n    mutex_unlock(&selinux_state.status_lock);\n}\n\ntypedef int (*sel_open_handle_status_fn)(struct inode *inode, struct file *filp);\n",
     "out:\n#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n    mutex_unlock(&selinux_state.status_lock);\n"
     "#else\n    mutex_unlock(&selinux_state.ss->status_lock);\n#endif\n}\n\n"
     "typedef int (*sel_open_handle_status_fn)(struct inode *inode, struct file *filp);\n"),
])

patch("feature/selinux_hide.c", [
    ("#define ksu_task_security_struct task_security_struct\n",
     "#define ksu_task_security_struct task_security_struct\n"
     "#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 9, 0)\n"
     "// symtab_search exists from 5.9; 4.14 goes straight to the hashtab.\n"
     "#define symtab_search(s, name) hashtab_search((s)->table, name)\n#endif\n"),
    ("    mutex_lock(&selinux_state.status_lock);\n    if (fake_status)\n        __free_page(fake_status);\n    fake_status = NULL;\n    mutex_unlock(&selinux_state.status_lock);\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n    mutex_lock(&selinux_state.status_lock);\n"
     "#else\n    mutex_lock(&selinux_state.ss->status_lock);\n#endif\n"
     "    if (fake_status)\n        __free_page(fake_status);\n    fake_status = NULL;\n"
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n    mutex_unlock(&selinux_state.status_lock);\n"
     "#else\n    mutex_unlock(&selinux_state.ss->status_lock);\n#endif\n"),
    ("    orig_policy = rcu_dereference(selinux_state.policy);\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 0, 0)\n    orig_policy = rcu_dereference(selinux_state.policy);\n"
     "#else\n    orig_policy = rcu_dereference(selinux_state.ss);\n#endif\n"),
])

patch("feature/selinux_hide.c", [
    ("static int sidtab_entry_to_string(struct policydb *p, struct sidtab *sidtab, struct sidtab_entry *entry,\n"
     "                                  char **scontext, u32 *scontext_len)\n{\n"
     "    int rc = sidtab_sid2str_get(sidtab, entry, scontext, scontext_len);\n\n"
     "    if (rc != -ENOENT)\n        return rc;\n\n"
     "    rc = context_struct_to_string(p, &entry->context, scontext, scontext_len);\n"
     "    if (!rc && scontext)\n        sidtab_sid2str_put(sidtab, entry, *scontext, *scontext_len);\n    return rc;\n}\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n"
     "static int sidtab_entry_to_string(struct policydb *p, struct sidtab *sidtab, struct sidtab_entry *entry,\n"
     "                                  char **scontext, u32 *scontext_len)\n{\n"
     "    int rc = sidtab_sid2str_get(sidtab, entry, scontext, scontext_len);\n\n"
     "    if (rc != -ENOENT)\n        return rc;\n\n"
     "    rc = context_struct_to_string(p, &entry->context, scontext, scontext_len);\n"
     "    if (!rc && scontext)\n        sidtab_sid2str_put(sidtab, entry, *scontext, *scontext_len);\n    return rc;\n}\n"
     "#else\n// 4.14 sidtab entries are plain contexts; no sid2str cache exists.\n"
     "static int sidtab_entry_to_string(struct policydb *p, struct sidtab *sidtab, struct context *context,\n"
     "                                  char **scontext, u32 *scontext_len)\n{\n"
     "    return context_struct_to_string(p, context, scontext, scontext_len);\n}\n#endif\n"),
    ("    struct sidtab_entry *entry;\n    int rc = 0;\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n    struct sidtab_entry *entry;\n"
     "#else\n    struct context *context;\n#endif\n    int rc = 0;\n"),
    ("    entry = sidtab_search_entry(sidtab, sid);\n    if (!entry) {\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n    entry = sidtab_search_entry(sidtab, sid);\n    if (!entry) {\n"
     "#else\n    context = sidtab_search(sidtab, sid);\n    if (!context) {\n#endif\n"),
    ("    rc = sidtab_entry_to_string(policydb, sidtab, entry, scontext, scontext_len);\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n"
     "    rc = sidtab_entry_to_string(policydb, sidtab, entry, scontext, scontext_len);\n"
     "#else\n    rc = sidtab_entry_to_string(policydb, sidtab, context, scontext, scontext_len);\n#endif\n"),
    ("    source = policydb->type_val_to_struct[scontext->type - 1];\n    BUG_ON(!source);\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n"
     "    source = policydb->type_val_to_struct[scontext->type - 1];\n"
     "#else\n    source = (struct type_datum *)flex_array_get_ptr(policydb->type_val_to_struct_array, scontext->type - 1);\n#endif\n"
     "    BUG_ON(!source);\n"),
    ("    target = policydb->type_val_to_struct[tcontext->type - 1];\n    BUG_ON(!target);\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n"
     "    target = policydb->type_val_to_struct[tcontext->type - 1];\n"
     "#else\n    target = (struct type_datum *)flex_array_get_ptr(policydb->type_val_to_struct_array, tcontext->type - 1);\n#endif\n"
     "    BUG_ON(!target);\n"),
])

patch("feature/selinux_hide.c", [
    ("    sattr = &policydb->type_attr_map_array[scontext->type - 1];\n"
     "    tattr = &policydb->type_attr_map_array[tcontext->type - 1];\n",
     "#if LINUX_VERSION_CODE >= KERNEL_VERSION(5, 9, 0)\n"
     "    sattr = &policydb->type_attr_map_array[scontext->type - 1];\n"
     "    tattr = &policydb->type_attr_map_array[tcontext->type - 1];\n"
     "#else\n    sattr = flex_array_get(policydb->type_attr_map_array, scontext->type - 1);\n"
     "    tattr = flex_array_get(policydb->type_attr_map_array, tcontext->type - 1);\n#endif\n"),
])