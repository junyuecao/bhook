package com.sohook.test;

/**
 * C++ 内存测试辅助类
 * 提供 C++ new/delete 操作的 JNI 接口
 */
public class TestMemoryCpp {

    static boolean isLoaded = false;
    static {
        System.loadLibrary("test_memory_cpp");
        isLoaded = true;
    }

    public static boolean isLibraryLoaded() {
        return isLoaded;
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
