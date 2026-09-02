#include <jni.h>
#include <android/log.h>
#include <cerrno>
#include <csignal>
#include <setjmp.h>
#include <cstring>
#include <cstdint>
#include <fcntl.h>
#if __has_include(<linux/kvm.h>)
#include <linux/kvm.h>
#else
#define KVMIO 0xAE
#define KVM_GET_API_VERSION _IO(KVMIO, 0x00)
#endif
#include <string>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>

namespace {
std::string errno_string(const char* prefix) {
    return std::string(prefix) + ": errno=" + std::to_string(errno) + " (" + std::strerror(errno) + ")";
}

jstring to_jstring(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

#if defined(__aarch64__)
thread_local sigjmp_buf* g_jit_probe_env = nullptr;
thread_local volatile sig_atomic_t g_jit_probe_signal = 0;

void jit_probe_signal_handler(int signo) {
    if (g_jit_probe_env != nullptr) {
        g_jit_probe_signal = signo;
        siglongjmp(*g_jit_probe_env, 1);
    }
    _exit(128 + signo);
}
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_virtualandroid_core_NativeProbe_probeKvm(JNIEnv* env, jobject) {
    int fd = open("/dev/kvm", O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        return to_jstring(env, errno_string("DENIED open(/dev/kvm)"));
    }

    errno = 0;
    int api = ioctl(fd, KVM_GET_API_VERSION, 0);
    if (api < 0) {
        auto result = errno_string("OPENED but KVM_GET_API_VERSION failed");
        close(fd);
        return to_jstring(env, result);
    }

    close(fd);
    return to_jstring(env, "OK KVM api=" + std::to_string(api));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_virtualandroid_core_NativeProbe_probeExecutableMemory(JNIEnv* env, jobject) {
#if !defined(__aarch64__)
    return to_jstring(env, "UNSUPPORTED JIT execution probe requires AArch64 host");
#else
    constexpr size_t kPage = 4096;
    void* p = mmap(nullptr, kPage, PROT_READ | PROT_WRITE,
                   MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (p == MAP_FAILED) {
        return to_jstring(env, errno_string("DENIED mmap(RW)"));
    }

    // AArch64 test function:
    //   bti c          ; valid indirect-call landing pad on BTI systems, NOP elsewhere
    //   mov w0, #42
    //   ret
    const uint32_t code[] = {0xd503245fU, 0x52800540U, 0xd65f03c0U};
    std::memcpy(p, code, sizeof(code));
    __builtin___clear_cache(reinterpret_cast<char*>(p),
                            reinterpret_cast<char*>(p) + sizeof(code));

    errno = 0;
    if (mprotect(p, kPage, PROT_READ | PROT_EXEC) != 0) {
        auto result = errno_string("DENIED mprotect(RX)");
        munmap(p, kPage);
        return to_jstring(env, result);
    }

    struct sigaction action{};
    struct sigaction old_ill{}, old_segv{}, old_bus{};
    sigemptyset(&action.sa_mask);
    action.sa_handler = jit_probe_signal_handler;
    action.sa_flags = 0;
    sigaction(SIGILL, &action, &old_ill);
    sigaction(SIGSEGV, &action, &old_segv);
    sigaction(SIGBUS, &action, &old_bus);

    sigjmp_buf envbuf;
    g_jit_probe_env = &envbuf;
    g_jit_probe_signal = 0;
    int result = -1;
    bool executed = false;
    if (sigsetjmp(envbuf, 1) == 0) {
        using TestFn = int (*)();
        result = reinterpret_cast<TestFn>(p)();
        executed = true;
    }
    const int trapped_signal = g_jit_probe_signal;
    g_jit_probe_env = nullptr;

    sigaction(SIGILL, &old_ill, nullptr);
    sigaction(SIGSEGV, &old_segv, nullptr);
    sigaction(SIGBUS, &old_bus, nullptr);
    munmap(p, kPage);

    if (!executed) {
        return to_jstring(env, "DENIED generated-code execution trapped signal=" + std::to_string(trapped_signal));
    }
    if (result != 42) {
        return to_jstring(env, "FAILED generated-code execution returned=" + std::to_string(result));
    }
    return to_jstring(env, "OK anonymous RW->RX + generated AArch64 execution result=42");
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_virtualandroid_core_NativeProbe_probeDevice(JNIEnv* env, jobject, jstring path) {
    const char* raw = env->GetStringUTFChars(path, nullptr);
    std::string p(raw ? raw : "");
    if (raw) env->ReleaseStringUTFChars(path, raw);

    int fd = open(p.c_str(), O_RDWR | O_CLOEXEC);
    if (fd < 0) {
        return to_jstring(env, errno_string(("DENIED open(" + p + ")").c_str()));
    }
    close(fd);
    return to_jstring(env, "OK opened " + p);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_virtualandroid_core_NativeProbe_pageSizeBytes(JNIEnv*, jobject) {
    const long value = sysconf(_SC_PAGESIZE);
    return value > 0 ? static_cast<jlong>(value) : 0;
}
