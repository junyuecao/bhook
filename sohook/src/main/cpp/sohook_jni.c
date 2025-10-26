// Copyright (c) 2024 SoHook Memory Leak Detection
// JNI implementation

#include <android/log.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "memory_tracker.h"

#define LOG_TAG "SoHook-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define SOHOOK_JNI_VERSION JNI_VERSION_1_6
#define SOHOOK_JNI_CLASS_NAME "com/sohook/SoHook"

// Native method: init
static jint sohook_jni_init(JNIEnv *env, jclass clazz, jboolean debug, jboolean enable_backtrace) {
  (void)env;
  (void)clazz;

  LOGI("Initializing SoHook (debug=%d, enable_backtrace=%d)", debug, enable_backtrace);
  return memory_tracker_init((bool)debug, (bool)enable_backtrace);
}

// Native method: hook
static jint sohook_jni_hook(JNIEnv *env, jclass clazz, jobjectArray so_names) {
  (void)clazz;

  if (so_names == NULL) {
    LOGE("so_names is null");
    return -1;
  }

  jsize count = (*env)->GetArrayLength(env, so_names);
  if (count <= 0) {
    LOGE("so_names array is empty");
    return -1;
  }

  // 转换Java字符串数组为C字符串数组
  const char **c_so_names = (const char **)malloc(sizeof(char *) * count);
  if (c_so_names == NULL) {
    LOGE("Failed to allocate memory for so_names");
    return -1;
  }

  for (jsize i = 0; i < count; i++) {
    jstring jstr = (jstring)(*env)->GetObjectArrayElement(env, so_names, i);
    if (jstr == NULL) {
      c_so_names[i] = NULL;
      continue;
    }
    c_so_names[i] = (*env)->GetStringUTFChars(env, jstr, NULL);
    (*env)->DeleteLocalRef(env, jstr);
  }

  // 调用memory_tracker_hook
  int ret = memory_tracker_hook(c_so_names, count);

  // 释放字符串
  for (jsize i = 0; i < count; i++) {
    if (c_so_names[i] != NULL) {
      jstring jstr = (jstring)(*env)->GetObjectArrayElement(env, so_names, i);
      (*env)->ReleaseStringUTFChars(env, jstr, c_so_names[i]);
      (*env)->DeleteLocalRef(env, jstr);
    }
  }

  free(c_so_names);
  return ret;
}

// Native method: unhook
static jint sohook_jni_unhook(JNIEnv *env, jclass clazz, jobjectArray so_names) {
  (void)clazz;

  if (so_names == NULL) {
    LOGE("so_names is null");
    return -1;
  }

  jsize count = (*env)->GetArrayLength(env, so_names);
  if (count <= 0) {
    LOGE("so_names array is empty");
    return -1;
  }

  // 转换Java字符串数组为C字符串数组
  const char **c_so_names = (const char **)malloc(sizeof(char *) * count);
  if (c_so_names == NULL) {
    LOGE("Failed to allocate memory for so_names");
    return -1;
  }

  for (jsize i = 0; i < count; i++) {
    jstring jstr = (jstring)(*env)->GetObjectArrayElement(env, so_names, i);
    if (jstr == NULL) {
      c_so_names[i] = NULL;
      continue;
    }
    c_so_names[i] = (*env)->GetStringUTFChars(env, jstr, NULL);
    (*env)->DeleteLocalRef(env, jstr);
  }

  // 调用memory_tracker_unhook
  int ret = memory_tracker_unhook(c_so_names, count);

  // 释放字符串
  for (jsize i = 0; i < count; i++) {
    if (c_so_names[i] != NULL) {
      jstring jstr = (jstring)(*env)->GetObjectArrayElement(env, so_names, i);
      (*env)->ReleaseStringUTFChars(env, jstr, c_so_names[i]);
      (*env)->DeleteLocalRef(env, jstr);
    }
  }

  free(c_so_names);
  return ret;
}

// Native method: unhookAll
static jint sohook_jni_unhook_all(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;

  return memory_tracker_unhook_all();
}

// Native method: getLeakReport
static jstring sohook_jni_get_leak_report(JNIEnv *env, jclass clazz) {
  (void)clazz;

  char *report = memory_tracker_get_leak_report();
  if (report == NULL) {
    return (*env)->NewStringUTF(env, "Failed to generate leak report");
  }

  jstring jreport = (*env)->NewStringUTF(env, report);
  free(report);
  return jreport;
}

// Native method: dumpLeakReport
static jint sohook_jni_dump_leak_report(JNIEnv *env, jclass clazz, jstring file_path) {
  (void)clazz;

  if (file_path == NULL) {
    LOGE("file_path is null");
    return -1;
  }

  const char *c_file_path = (*env)->GetStringUTFChars(env, file_path, NULL);
  if (c_file_path == NULL) {
    LOGE("Failed to get file_path string");
    return -1;
  }

  int ret = memory_tracker_dump_leak_report(c_file_path);
  (*env)->ReleaseStringUTFChars(env, file_path, c_file_path);
  return ret;
}

// Native method: getMemoryStats
static jobject sohook_jni_get_memory_stats(JNIEnv *env, jclass clazz) {
  (void)clazz;

  memory_stats_t stats;
  memory_tracker_get_stats(&stats);

  // 查找MemoryStats类
  jclass stats_class = (*env)->FindClass(env, "com/sohook/SoHook$MemoryStats");
  if (stats_class == NULL) {
    LOGE("Failed to find MemoryStats class");
    return NULL;
  }

  // 获取构造函数
  jmethodID constructor = (*env)->GetMethodID(env, stats_class, "<init>", "()V");
  if (constructor == NULL) {
    LOGE("Failed to find MemoryStats constructor");
    (*env)->DeleteLocalRef(env, stats_class);
    return NULL;
  }

  // 创建MemoryStats对象
  jobject stats_obj = (*env)->NewObject(env, stats_class, constructor);
  if (stats_obj == NULL) {
    LOGE("Failed to create MemoryStats object");
    (*env)->DeleteLocalRef(env, stats_class);
    return NULL;
  }

  // 设置字段值
  jfieldID field_id;

  field_id = (*env)->GetFieldID(env, stats_class, "totalAllocCount", "J");
  (*env)->SetLongField(env, stats_obj, field_id, (jlong)stats.total_alloc_count);

  field_id = (*env)->GetFieldID(env, stats_class, "totalAllocSize", "J");
  (*env)->SetLongField(env, stats_obj, field_id, (jlong)stats.total_alloc_size);

  field_id = (*env)->GetFieldID(env, stats_class, "totalFreeCount", "J");
  (*env)->SetLongField(env, stats_obj, field_id, (jlong)stats.total_free_count);

  field_id = (*env)->GetFieldID(env, stats_class, "totalFreeSize", "J");
  (*env)->SetLongField(env, stats_obj, field_id, (jlong)stats.total_free_size);

  field_id = (*env)->GetFieldID(env, stats_class, "currentAllocCount", "J");
  (*env)->SetLongField(env, stats_obj, field_id, (jlong)stats.current_alloc_count);

  field_id = (*env)->GetFieldID(env, stats_class, "currentAllocSize", "J");
  (*env)->SetLongField(env, stats_obj, field_id, (jlong)stats.current_alloc_size);

  (*env)->DeleteLocalRef(env, stats_class);
  return stats_obj;
}

// Native method: resetStats
static void sohook_jni_reset_stats(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;

  memory_tracker_reset_stats();
}

// Native method: set backtrace enabled
static void sohook_jni_set_backtrace_enabled(JNIEnv *env, jclass clazz, jboolean enable) {
  (void)env;
  (void)clazz;
  
  memory_tracker_set_backtrace_enabled((bool)enable);
}

// Native method: is backtrace enabled
static jboolean sohook_jni_is_backtrace_enabled(JNIEnv *env, jclass clazz) {
  (void)env;
  (void)clazz;
  
  return (jboolean)memory_tracker_is_backtrace_enabled();
}

// Native method: get leaks json
static jstring sohook_jni_get_leaks_json(JNIEnv *env, jclass clazz) {
  (void)clazz;

  char *json = memory_tracker_get_leaks_json();
  if (json == NULL) {
    return (*env)->NewStringUTF(env, "[]");
  }

  jstring jjson = (*env)->NewStringUTF(env, json);
  free(json);
  return jjson;
}

// JNI方法注册表
static JNINativeMethod sohook_jni_methods[] = {
    {"nativeInit", "(ZZ)I", (void *)sohook_jni_init},
    {"nativeHook", "([Ljava/lang/String;)I", (void *)sohook_jni_hook},
    {"nativeUnhook", "([Ljava/lang/String;)I", (void *)sohook_jni_unhook},
    {"nativeUnhookAll", "()I", (void *)sohook_jni_unhook_all},
    {"nativeGetLeakReport", "()Ljava/lang/String;", (void *)sohook_jni_get_leak_report},
    {"nativeDumpLeakReport", "(Ljava/lang/String;)I", (void *)sohook_jni_dump_leak_report},
    {"nativeGetMemoryStats", "()Lcom/sohook/SoHook$MemoryStats;",
     (void *)sohook_jni_get_memory_stats},
    {"nativeResetStats", "()V", (void *)sohook_jni_reset_stats},
    {"nativeSetBacktraceEnabled", "(Z)V", (void *)sohook_jni_set_backtrace_enabled},
    {"nativeIsBacktraceEnabled", "()Z", (void *)sohook_jni_is_backtrace_enabled},
    {"nativeGetLeaksJson", "()Ljava/lang/String;", (void *)sohook_jni_get_leaks_json}};

// JNI_OnLoad
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  JNIEnv *env;
  jclass cls;

  (void)reserved;

  LOGI("JNI_OnLoad called");

  if (vm == NULL) {
    LOGE("JavaVM is null");
    return JNI_ERR;
  }

  if ((*vm)->GetEnv(vm, (void **)&env, SOHOOK_JNI_VERSION) != JNI_OK) {
    LOGE("Failed to get JNIEnv");
    return JNI_ERR;
  }

  if (env == NULL || *env == NULL) {
    LOGE("JNIEnv is null");
    return JNI_ERR;
  }

  cls = (*env)->FindClass(env, SOHOOK_JNI_CLASS_NAME);
  if (cls == NULL) {
    LOGE("Failed to find class: %s", SOHOOK_JNI_CLASS_NAME);
    return JNI_ERR;
  }

  if ((*env)->RegisterNatives(env, cls, sohook_jni_methods,
                              sizeof(sohook_jni_methods) / sizeof(sohook_jni_methods[0])) != 0) {
    LOGE("Failed to register native methods");
    (*env)->DeleteLocalRef(env, cls);
    return JNI_ERR;
  }

  (*env)->DeleteLocalRef(env, cls);
  LOGI("JNI methods registered successfully");
  return SOHOOK_JNI_VERSION;
}
