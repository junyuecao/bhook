// Copyright (c) 2024 SoHook File Descriptor Leak Detection
// JNI interface for test_fd (Static Registration)

#include <jni.h>
#include <string.h>
#include "test_fd.h"

// JNI静态注册：Java_包名_类名_方法名
// 包名：com.sohook.test
// 类名：TestFd

JNIEXPORT jint JNICALL
Java_com_sohook_test_TestFd_nativeOpenFile(JNIEnv* env, jclass clazz, jstring path, jint flags) {
  (void)clazz;
  
  if (path == NULL) {
    return -1;
  }
  
  const char* c_path = (*env)->GetStringUTFChars(env, path, NULL);
  if (c_path == NULL) {
    return -1;
  }
  
  int fd = test_open_file(c_path, flags);
  (*env)->ReleaseStringUTFChars(env, path, c_path);
  
  return fd;
}

JNIEXPORT jint JNICALL
Java_com_sohook_test_TestFd_nativeCloseFile(JNIEnv* env, jclass clazz, jint fd) {
  (void)env;
  (void)clazz;
  
  return test_close_file(fd);
}

JNIEXPORT jlong JNICALL
Java_com_sohook_test_TestFd_nativeFopenFile(JNIEnv* env, jclass clazz, jstring path, jstring mode) {
  (void)clazz;
  
  if (path == NULL || mode == NULL) {
    return 0;
  }
  
  const char* c_path = (*env)->GetStringUTFChars(env, path, NULL);
  const char* c_mode = (*env)->GetStringUTFChars(env, mode, NULL);
  
  if (c_path == NULL || c_mode == NULL) {
    if (c_path) (*env)->ReleaseStringUTFChars(env, path, c_path);
    if (c_mode) (*env)->ReleaseStringUTFChars(env, mode, c_mode);
    return 0;
  }
  
  void* fp = test_fopen_file(c_path, c_mode);
  
  (*env)->ReleaseStringUTFChars(env, path, c_path);
  (*env)->ReleaseStringUTFChars(env, mode, c_mode);
  
  return (jlong)(uintptr_t)fp;
}

JNIEXPORT jint JNICALL
Java_com_sohook_test_TestFd_nativeFcloseFile(JNIEnv* env, jclass clazz, jlong fp) {
  (void)env;
  (void)clazz;
  
  return test_fclose_file((void*)(uintptr_t)fp);
}

JNIEXPORT jint JNICALL
Java_com_sohook_test_TestFd_nativeOpenMultiple(JNIEnv* env, jclass clazz, jstring path_prefix, jint count, jintArray fds) {
  (void)clazz;
  
  if (path_prefix == NULL || fds == NULL || count <= 0) {
    return 0;
  }
  
  const char* c_path_prefix = (*env)->GetStringUTFChars(env, path_prefix, NULL);
  if (c_path_prefix == NULL) {
    return 0;
  }
  
  jint* c_fds = (*env)->GetIntArrayElements(env, fds, NULL);
  if (c_fds == NULL) {
    (*env)->ReleaseStringUTFChars(env, path_prefix, c_path_prefix);
    return 0;
  }
  
  int opened = test_open_multiple(c_path_prefix, count, c_fds);
  
  (*env)->ReleaseIntArrayElements(env, fds, c_fds, 0);
  (*env)->ReleaseStringUTFChars(env, path_prefix, c_path_prefix);
  
  return opened;
}

JNIEXPORT jint JNICALL
Java_com_sohook_test_TestFd_nativeCloseMultiple(JNIEnv* env, jclass clazz, jintArray fds, jint count) {
  (void)clazz;
  
  if (fds == NULL || count <= 0) {
    return 0;
  }
  
  jint* c_fds = (*env)->GetIntArrayElements(env, fds, NULL);
  if (c_fds == NULL) {
    return 0;
  }
  
  int closed = test_close_multiple(c_fds, count);
  
  (*env)->ReleaseIntArrayElements(env, fds, c_fds, JNI_ABORT);
  
  return closed;
}

JNIEXPORT jint JNICALL
Java_com_sohook_test_TestFd_nativeLeakFd(JNIEnv* env, jclass clazz, jstring path) {
  (void)clazz;
  
  if (path == NULL) {
    return -1;
  }
  
  const char* c_path = (*env)->GetStringUTFChars(env, path, NULL);
  if (c_path == NULL) {
    return -1;
  }
  
  int fd = test_leak_fd(c_path);
  (*env)->ReleaseStringUTFChars(env, path, c_path);
  
  return fd;
}

JNIEXPORT jlong JNICALL
Java_com_sohook_test_TestFd_nativeLeakFile(JNIEnv* env, jclass clazz, jstring path) {
  (void)clazz;
  
  if (path == NULL) {
    return 0;
  }
  
  const char* c_path = (*env)->GetStringUTFChars(env, path, NULL);
  if (c_path == NULL) {
    return 0;
  }
  
  void* fp = test_leak_file(c_path);
  (*env)->ReleaseStringUTFChars(env, path, c_path);
  
  return (jlong)(uintptr_t)fp;
}

JNIEXPORT jint JNICALL
Java_com_sohook_test_TestFd_nativeWriteAndClose(JNIEnv* env, jclass clazz, jstring path, jstring data) {
  (void)clazz;
  
  if (path == NULL || data == NULL) {
    return -1;
  }
  
  const char* c_path = (*env)->GetStringUTFChars(env, path, NULL);
  const char* c_data = (*env)->GetStringUTFChars(env, data, NULL);
  
  if (c_path == NULL || c_data == NULL) {
    if (c_path) (*env)->ReleaseStringUTFChars(env, path, c_path);
    if (c_data) (*env)->ReleaseStringUTFChars(env, data, c_data);
    return -1;
  }
  
  int ret = test_write_and_close(c_path, c_data, strlen(c_data));
  
  (*env)->ReleaseStringUTFChars(env, path, c_path);
  (*env)->ReleaseStringUTFChars(env, data, c_data);
  
  return ret;
}

JNIEXPORT jstring JNICALL
Java_com_sohook_test_TestFd_nativeReadAndClose(JNIEnv* env, jclass clazz, jstring path) {
  (void)clazz;
  
  if (path == NULL) {
    return NULL;
  }
  
  const char* c_path = (*env)->GetStringUTFChars(env, path, NULL);
  if (c_path == NULL) {
    return NULL;
  }
  
  char buffer[1024];
  int bytes_read = test_read_and_close(c_path, buffer, sizeof(buffer));
  
  (*env)->ReleaseStringUTFChars(env, path, c_path);
  
  if (bytes_read < 0) {
    return NULL;
  }
  
  return (*env)->NewStringUTF(env, buffer);
}
