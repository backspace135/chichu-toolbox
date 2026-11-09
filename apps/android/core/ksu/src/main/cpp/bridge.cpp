// KernelSU 730f644615969cfd4a84b45d7f62a667b7b95a50 UAPI 6.
// Derived from manager/ksu.cc and kernel/include/uapi (GPL-3.0-only).
#include <jni.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <linux/ioctl.h>
#include <sys/ioctl.h>
#include <unistd.h>

struct RootProfile {
    int uid, gid;
    unsigned groups_count;
    int groups[32];
    alignas(8) unsigned long long effective, permitted, inheritable;
    char domain[64];
    int namespaces;
    alignas(8) unsigned long long flags;
};
struct AppProfile {
    unsigned version;
    char key[256];
    int current_uid;
    bool allow;
    union {
        struct { bool use_default; char template_name[256]; RootProfile profile; } root;
        struct { bool use_default; bool umount; } nonroot;
    };
};
struct Info { unsigned version, flags, features, uapi; };
static_assert(sizeof(AppProfile) == 784);
static_assert(__builtin_offsetof(AppProfile, root.profile) == 536);
static_assert(__builtin_offsetof(RootProfile, effective) == 144);
static_assert(__builtin_offsetof(RootProfile, flags) == 240);

// The manager receives this descriptor from the kernel on process creation.
// Do not invoke the reboot syscall to install a descriptor: Android app seccomp
// can kill the caller, and an absent descriptor is a meaningful probe result.
static int driver() {
    DIR *dir = opendir("/proc/self/fd");
    if (!dir) return -errno;
    int found = -ENODEV;
    while (auto *entry = readdir(dir)) {
        char *end;
        long number = strtol(entry->d_name, &end, 10);
        if (*end || number < 0) continue;
        char path[320], target[128];
        snprintf(path, sizeof(path), "/proc/self/fd/%s", entry->d_name);
        auto size = readlink(path, target, sizeof(target) - 1);
        if (size < 0) continue;
        target[size] = 0;
        if (strstr(target, "[ksu_driver]")) { found = static_cast<int>(number); break; }
    }
    closedir(dir);
    return found;
}
static int call(unsigned long command, void *value) {
    int fd = driver();
    if (fd < 0) return fd;
    return ioctl(fd, command, value) < 0 ? -errno : 0;
}
static void fail(JNIEnv *env, int code) {
    char message[160];
    snprintf(message, sizeof(message), "KernelSU ioctl: %s (errno %d)", strerror(-code), -code);
    env->ThrowNew(env->FindClass("java/io/IOException"), message);
}
extern "C" JNIEXPORT jintArray JNICALL
Java_com_chichu_toolbox_core_ksu_KernelBridge_probe(JNIEnv *env, jobject) {
    Info info{};
    int status = call(_IOR('K', 2, Info), &info);
    jint values[] = {status, static_cast<int>(info.version), static_cast<int>(info.flags), static_cast<int>(info.uapi)};
    auto result = env->NewIntArray(4);
    env->SetIntArrayRegion(result, 0, 4, values);
    return result;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_chichu_toolbox_core_ksu_KernelBridge_safeMode(JNIEnv *env, jobject) {
    unsigned char safe = 0;
    int status = call(_IOC(_IOC_READ, 'K', 5, 0), &safe);
    if (status < 0) { fail(env, status); return false; }
    return safe != 0;
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_chichu_toolbox_core_ksu_KernelBridge_profile(JNIEnv *env, jobject, jstring key, jint uid) {
    AppProfile profile{};
    profile.version = 4;
    const char *name = env->GetStringUTFChars(key, nullptr);
    size_t length = strlen(name);
    if (length >= sizeof(profile.key)) {
        env->ReleaseStringUTFChars(key, name);
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Package name exceeds KernelSU UAPI limit");
        return nullptr;
    }
    memcpy(profile.key, name, length + 1);
    env->ReleaseStringUTFChars(key, name);
    profile.current_uid = uid;
    int status = call(_IOC(_IOC_READ | _IOC_WRITE, 'K', 11, 0), &profile);
    // ENOENT is the kernel's explicit "no per-UID policy" response, not a communication failure.
    if (status == -ENOENT) return env->NewByteArray(0);
    if (status < 0) { fail(env, status); return nullptr; }
    auto result = env->NewByteArray(sizeof(profile));
    env->SetByteArrayRegion(result, 0, sizeof(profile), reinterpret_cast<jbyte *>(&profile));
    return result;
}
extern "C" JNIEXPORT void JNICALL
Java_com_chichu_toolbox_core_ksu_KernelBridge_setProfile(JNIEnv *env, jobject, jbyteArray bytes) {
    AppProfile profile{};
    if (env->GetArrayLength(bytes) != sizeof(profile)) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "Invalid app profile size");
        return;
    }
    env->GetByteArrayRegion(bytes, 0, sizeof(profile), reinterpret_cast<jbyte *>(&profile));
    int status = call(_IOC(_IOC_WRITE, 'K', 12, 0), &profile);
    if (status < 0) fail(env, status);
}
