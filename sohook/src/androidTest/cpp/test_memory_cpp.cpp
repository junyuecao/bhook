// Copyright (c) 2024 SoHook Memory Leak Detection
// Test C++ memory allocation (new/delete)

#include "test_memory_cpp.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "SoHook-TestMemoryCpp"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// 测试类
class TestObject {
public:
    int data[10];
    
    TestObject() {
        LOGD("TestObject constructor called");
        for (int i = 0; i < 10; i++) {
            data[i] = i;
        }
    }
    
    ~TestObject() {
        LOGD("TestObject destructor called");
    }
};

// 测试 new/delete
extern "C" void* test_cpp_new(size_t size) {
    LOGD("test_cpp_new: allocating %zu bytes", size);
    void* ptr = ::operator new(size);
    LOGD("test_cpp_new: allocated at %p", ptr);
    return ptr;
}

extern "C" void test_cpp_delete(void* ptr) {
    LOGD("test_cpp_delete: freeing %p", ptr);
    ::operator delete(ptr);
}

// 测试 new[]/delete[]
extern "C" void* test_cpp_new_array(size_t size) {
    LOGD("test_cpp_new_array: allocating %zu bytes", size);
    void* ptr = ::operator new[](size);
    LOGD("test_cpp_new_array: allocated at %p", ptr);
    return ptr;
}

extern "C" void test_cpp_delete_array(void* ptr) {
    LOGD("test_cpp_delete_array: freeing %p", ptr);
    ::operator delete[](ptr);
}

// 测试单个对象
extern "C" void* test_cpp_new_object() {
    LOGI("test_cpp_new_object: creating TestObject");
    TestObject* obj = new TestObject();
    LOGI("test_cpp_new_object: created at %p", obj);
    return obj;
}

extern "C" void test_cpp_delete_object(void* ptr) {
    LOGI("test_cpp_delete_object: deleting TestObject at %p", ptr);
    TestObject* obj = static_cast<TestObject*>(ptr);
    delete obj;
}

// 测试对象数组
extern "C" void* test_cpp_new_object_array(int count) {
    LOGI("test_cpp_new_object_array: creating %d TestObjects", count);
    TestObject* arr = new TestObject[count];
    LOGI("test_cpp_new_object_array: created at %p", arr);
    return arr;
}

extern "C" void test_cpp_delete_object_array(void* ptr) {
    LOGI("test_cpp_delete_object_array: deleting TestObject array at %p", ptr);
    TestObject* arr = static_cast<TestObject*>(ptr);
    delete[] arr;
}

// 分配多次（new）
extern "C" void* test_cpp_new_multiple(int count, size_t size) {
    LOGI("test_cpp_new_multiple: allocating %d times, %zu bytes each", count, size);
    void* last_ptr = nullptr;
    
    for (int i = 0; i < count; i++) {
        last_ptr = ::operator new(size);
        if (last_ptr) {
            // 写入一些数据
            std::memset(last_ptr, i & 0xFF, size);
        }
    }
    
    LOGI("test_cpp_new_multiple: completed, last ptr = %p", last_ptr);
    return last_ptr;
}

// 分配多个对象
extern "C" void** test_cpp_new_objects(int count) {
    LOGI("test_cpp_new_objects: creating %d TestObjects", count);
    
    void** ptrs = new void*[count];
    for (int i = 0; i < count; i++) {
        ptrs[i] = new TestObject();
    }
    
    LOGI("test_cpp_new_objects: completed");
    return ptrs;
}

// 释放多个对象
extern "C" void test_cpp_delete_objects(void** ptrs, int count) {
    LOGI("test_cpp_delete_objects: deleting %d TestObjects", count);
    
    if (ptrs == nullptr) {
        LOGD("test_cpp_delete_objects: ptrs is nullptr");
        return;
    }
    
    for (int i = 0; i < count; i++) {
        if (ptrs[i] != nullptr) {
            TestObject* obj = static_cast<TestObject*>(ptrs[i]);
            delete obj;
            ptrs[i] = nullptr;
        }
    }
    
    delete[] ptrs;
    LOGI("test_cpp_delete_objects: completed");
}

// 故意泄漏内存（new）
extern "C" void* test_cpp_leak_new(size_t size) {
    LOGI("test_cpp_leak_new: intentionally leaking %zu bytes", size);
    void* ptr = ::operator new(size);
    LOGI("test_cpp_leak_new: leaked at %p (DO NOT DELETE)", ptr);
    // 故意不释放，用于测试泄漏检测
    return ptr;
}

// 故意泄漏对象
extern "C" void* test_cpp_leak_object() {
    LOGI("test_cpp_leak_object: intentionally leaking TestObject");
    TestObject* obj = new TestObject();
    LOGI("test_cpp_leak_object: leaked at %p (DO NOT DELETE)", obj);
    // 故意不释放，用于测试泄漏检测
    return obj;
}

// 故意泄漏对象数组
extern "C" void* test_cpp_leak_object_array(int count) {
    LOGI("test_cpp_leak_object_array: intentionally leaking %d TestObjects", count);
    TestObject* arr = new TestObject[count];
    LOGI("test_cpp_leak_object_array: leaked at %p (DO NOT DELETE)", arr);
    // 故意不释放，用于测试泄漏检测
    return arr;
}
