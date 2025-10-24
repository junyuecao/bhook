#include <android/api-level.h>
#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <inttypes.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/system_properties.h>
#include <unistd.h>

#include "bytehook.h"
#include "hacker_bytehook.h"

#define HACKER_JNI_VERSION    JNI_VERSION_1_6
#define HACKER_JNI_CLASS_NAME "com/bytedance/android/bytehook/sample/NativeHacker"

static int hacker_jni_bytehook_hook(JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;

  return hacker_bytehook_hook();
}

static int hacker_jni_bytehook_unhook(JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;

  return hacker_bytehook_unhook();
}

static void hacker_jni_dump_records(JNIEnv *env, jobject thiz, jstring pathname) {
  (void)thiz;

  const char *c_pathname = (*env)->GetStringUTFChars(env, pathname, 0);
  if (NULL == c_pathname) return;

  int fd = open(c_pathname, O_CREAT | O_WRONLY | O_CLOEXEC | O_TRUNC | O_APPEND, S_IRUSR | S_IWUSR);
  if (fd >= 0) {
    bytehook_dump_records(fd, BYTEHOOK_RECORD_ITEM_ALL);
    //        bytehook_dump_records(fd, BYTEHOOK_RECORD_ITEM_CALLER_LIB_NAME | BYTEHOOK_RECORD_ITEM_OP |
    //        BYTEHOOK_RECORD_ITEM_LIB_NAME | BYTEHOOK_RECORD_ITEM_SYM_NAME | BYTEHOOK_RECORD_ITEM_ERRNO |
    //        BYTEHOOK_RECORD_ITEM_STUB);
    close(fd);
  }

  (*env)->ReleaseStringUTFChars(env, pathname, c_pathname);
}

static void *libsample_handle = NULL;
typedef void (*sample_test_strlen_t)(int);
typedef void (*sample_alloc_memory_t)(int);
typedef void (*sample_free_memory_t)(int);
typedef void (*sample_free_all_memory_t)(void);
typedef void (*sample_run_perf_tests_t)(void);
typedef void (*sample_quick_benchmark_t)(int);

static sample_test_strlen_t sample_test_strlen = NULL;
static sample_alloc_memory_t sample_alloc_memory = NULL;
static sample_free_memory_t sample_free_memory = NULL;
static sample_free_all_memory_t sample_free_all_memory = NULL;
static sample_run_perf_tests_t sample_run_perf_tests = NULL;
static sample_quick_benchmark_t sample_quick_benchmark = NULL;

static void hacker_jni_do_dlopen(JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;

  //  void *libc = dlopen("libc.so", RTLD_NOW);
  //  if (NULL != libc) dlclose(libc);

  if (NULL == libsample_handle) {
    libsample_handle = dlopen("libsample.so", RTLD_NOW);
    if (NULL != libsample_handle) {
      sample_test_strlen = (sample_test_strlen_t)dlsym(libsample_handle, "sample_test_strlen");
      sample_alloc_memory = (sample_alloc_memory_t)dlsym(libsample_handle, "sample_alloc_memory");
      sample_free_memory = (sample_free_memory_t)dlsym(libsample_handle, "sample_free_memory");
      sample_free_all_memory = (sample_free_all_memory_t)dlsym(libsample_handle, "sample_free_all_memory");
      sample_run_perf_tests = (sample_run_perf_tests_t)dlsym(libsample_handle, "sample_run_perf_tests");
      sample_quick_benchmark = (sample_quick_benchmark_t)dlsym(libsample_handle, "sample_quick_benchmark");
    }
  }
}

static void hacker_jni_do_run(JNIEnv *env, jobject thiz, jint benchmark) {
  (void)env;
  (void)thiz;

  if (NULL != sample_test_strlen) sample_test_strlen(benchmark);
}

static void hacker_jni_alloc_memory(JNIEnv *env, jobject thiz, jint count) {
  (void)env;
  (void)thiz;

  if (NULL != sample_alloc_memory) sample_alloc_memory(count);
}

static void hacker_jni_free_memory(JNIEnv *env, jobject thiz, jint count) {
  (void)env;
  (void)thiz;

  if (NULL != sample_free_memory) sample_free_memory(count);
}

static void hacker_jni_free_all_memory(JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;

  if (NULL != sample_free_all_memory) sample_free_all_memory();
}

static void hacker_jni_run_perf_tests(JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;

  if (NULL != sample_run_perf_tests) sample_run_perf_tests();
}

static void hacker_jni_quick_benchmark(JNIEnv *env, jobject thiz, jint iterations) {
  (void)env;
  (void)thiz;

  if (NULL != sample_quick_benchmark) sample_quick_benchmark(iterations);
}

static void hacker_jni_do_dlclose(JNIEnv *env, jobject thiz) {
  (void)env;
  (void)thiz;

  //  void *libc = dlopen("libc.so", RTLD_NOW);
  //  if (NULL != libc) dlclose(libc);

  if (NULL != libsample_handle) {
    sample_test_strlen = NULL;
    sample_alloc_memory = NULL;
    sample_free_memory = NULL;
    sample_free_all_memory = NULL;
    sample_run_perf_tests = NULL;
    sample_quick_benchmark = NULL;
    dlclose(libsample_handle);
    libsample_handle = NULL;
  }
}

static JNINativeMethod hacker_jni_methods[] = {
    {"nativeBytehookHook", "()I", (void *)hacker_jni_bytehook_hook},
    {"nativeBytehookUnhook", "()I", (void *)hacker_jni_bytehook_unhook},
    {"nativeDumpRecords", "(Ljava/lang/String;)V", (void *)hacker_jni_dump_records},
    {"nativeDoDlopen", "()V", (void *)hacker_jni_do_dlopen},
    {"nativeDoDlclose", "()V", (void *)hacker_jni_do_dlclose},
    {"nativeDoRun", "(I)V", (void *)hacker_jni_do_run},
    {"nativeAllocMemory", "(I)V", (void *)hacker_jni_alloc_memory},
    {"nativeFreeMemory", "(I)V", (void *)hacker_jni_free_memory},
    {"nativeFreeAllMemory", "()V", (void *)hacker_jni_free_all_memory},
    {"nativeRunPerfTests", "()V", (void *)hacker_jni_run_perf_tests},
    {"nativeQuickBenchmark", "(I)V", (void *)hacker_jni_quick_benchmark}};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  jclass cls;

  (void)reserved;

  if (NULL == vm) return JNI_ERR;
  if (JNI_OK != (*vm)->GetEnv(vm, (void **)&env, HACKER_JNI_VERSION)) return JNI_ERR;
  if (NULL == env || NULL == *env) return JNI_ERR;
  if (NULL == (cls = (*env)->FindClass(env, HACKER_JNI_CLASS_NAME))) return JNI_ERR;
  if (0 != (*env)->RegisterNatives(env, cls, hacker_jni_methods,
                                   sizeof(hacker_jni_methods) / sizeof(hacker_jni_methods[0])))
    return JNI_ERR;

  return HACKER_JNI_VERSION;
}
