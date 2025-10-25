package com.sohook;

import android.util.Log;

/**
 * 测试内存分配辅助类
 * 用于准确性测试，提供可控的内存分配和释放
 */
public class TestMemoryHelper {
    private static final String TAG = "TestMemoryHelper";
    private static boolean sLibraryLoaded = false;

    static {
        try {
            System.loadLibrary("test_memory");
            sLibraryLoaded = true;
            Log.i(TAG, "test_memory library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load test_memory library", e);
            sLibraryLoaded = false;
        }
    }

    /**
     * 检查测试库是否已加载
     */
    public static boolean isLibraryLoaded() {
        return sLibraryLoaded;
    }

    /**
     * 分配指定大小的内存
     * @param size 要分配的字节数
     * @return 内存指针（作为long）
     */
    public static long alloc(long size) {
        if (!sLibraryLoaded) {
            Log.e(TAG, "Library not loaded");
            return 0;
        }
        return nativeAlloc(size);
    }

    /**
     * 释放之前分配的内存
     * @param ptr 内存指针
     */
    public static void free(long ptr) {
        if (!sLibraryLoaded) {
            Log.e(TAG, "Library not loaded");
            return;
        }
        if (ptr == 0) {
            Log.w(TAG, "Attempting to free null pointer");
            return;
        }
        nativeFree(ptr);
    }

    /**
     * 分配多次内存（用于测试）
     * @param count 分配次数
     * @param size 每次分配的大小
     * @return 最后一次分配的指针
     */
    public static long allocMultiple(int count, long size) {
        if (!sLibraryLoaded) {
            Log.e(TAG, "Library not loaded");
            return 0;
        }
        return nativeAllocMultiple(count, size);
    }

    /**
     * 故意泄漏内存（用于测试泄漏检测）
     * @param size 泄漏的大小
     * @return 泄漏的内存指针（不应该被释放）
     */
    public static long leakMemory(long size) {
        if (!sLibraryLoaded) {
            Log.e(TAG, "Library not loaded");
            return 0;
        }
        return nativeLeakMemory(size);
    }

    /**
     * 分配并清零内存
     * @param nmemb 元素个数
     * @param size 每个元素的大小
     * @return 内存指针
     */
    public static long calloc(long nmemb, long size) {
        if (!sLibraryLoaded) {
            Log.e(TAG, "Library not loaded");
            return 0;
        }
        return nativeCalloc(nmemb, size);
    }

    /**
     * 重新分配内存
     * @param ptr 原内存指针
     * @param size 新的大小
     * @return 新的内存指针
     */
    public static long realloc(long ptr, long size) {
        if (!sLibraryLoaded) {
            Log.e(TAG, "Library not loaded");
            return 0;
        }
        return nativeRealloc(ptr, size);
    }

    // Native methods
    private static native long nativeAlloc(long size);
    private static native void nativeFree(long ptr);
    private static native long nativeAllocMultiple(int count, long size);
    private static native long nativeLeakMemory(long size);
    private static native long nativeCalloc(long nmemb, long size);
    private static native long nativeRealloc(long ptr, long size);
}
