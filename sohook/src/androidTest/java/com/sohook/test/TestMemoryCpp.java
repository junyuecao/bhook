package com.sohook.test;

import android.util.Log;

/**
 * C++ 内存测试辅助类
 * 提供 C++ new/delete 操作的 JNI 接口
 */
public class TestMemoryCpp {
    private static final String TAG = "SoHook-TestMemoryCpp";

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

    public static boolean isLibraryLoaded() {
        return sLibraryLoaded;
    }

    // operator new/delete
    public static native long nativeNew(long size);
    public static native void nativeDelete(long ptr);

    // operator new[]/delete[]
    public static native long nativeNewArray(long size);
    public static native void nativeDeleteArray(long ptr);

    // 对象操作
    public static native long nativeNewObject();
    public static native void nativeDeleteObject(long ptr);

    // 对象数组操作
    public static native long nativeNewObjectArray(int count);
    public static native void nativeDeleteObjectArray(long ptr);

    // 多次分配
    public static native long nativeNewMultiple(int count, long size);

    // 多个对象
    public static native long[] nativeNewObjects(int count);
    public static native void nativeDeleteObjects(long[] ptrs);

    // 泄漏测试
    public static native long nativeLeakNew(long size);
    public static native long nativeLeakObject();
    public static native long nativeLeakObjectArray(int count);
}
