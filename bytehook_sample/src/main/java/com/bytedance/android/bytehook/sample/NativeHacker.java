package com.bytedance.android.bytehook.sample;

public class NativeHacker {
    public static void bytehookHook() {
        nativeBytehookHook();
    }
    public static void bytehookUnhook() {
        nativeBytehookUnhook();
    }
    public static void doDlopen() {
        nativeDoDlopen();
    }
    public static void doDlclose() {
        nativeDoDlclose();
    }
    public static void doRun(boolean benchmark) {
        nativeDoRun(benchmark ? 1 : 0);
    }
    
    public static void allocMemory(int count) {
        nativeAllocMemory(count);
    }
    
    public static void freeMemory(int count) {
        nativeFreeMemory(count);
    }
    
    public static void freeAllMemory() {
        nativeFreeAllMemory();
    }
    
    public static void runPerfTests() {
        nativeRunPerfTests();
    }
    
    public static void quickBenchmark(int iterations) {
        nativeQuickBenchmark(iterations);
    }
    
    // C++ new/delete 测试函数
    public static void allocWithNew(int count) {
        nativeAllocWithNew(count);
    }
    
    public static void allocWithNewArray(int count) {
        nativeAllocWithNewArray(count);
    }
    
    public static void allocObjects(int count) {
        nativeAllocObjects(count);
    }
    
    public static void allocObjectArrays(int count) {
        nativeAllocObjectArrays(count);
    }
    
    // FD 泄漏测试函数
    public static void leakFileDescriptors(int count, String pathPrefix) {
        nativeLeakFileDescriptors(count, pathPrefix);
    }
    
    public static void leakFilePointers(int count, String pathPrefix) {
        nativeLeakFilePointers(count, pathPrefix);
    }

    private static native int nativeBytehookHook();
    private static native int nativeBytehookUnhook();
    public static native void nativeDumpRecords(String pathname);

    private static native void nativeDoDlopen();
    private static native void nativeDoDlclose();
    private static native void nativeDoRun(int benchmark);
    private static native void nativeAllocMemory(int count);
    private static native void nativeFreeMemory(int count);
    private static native void nativeFreeAllMemory();
    private static native void nativeRunPerfTests();
    private static native void nativeQuickBenchmark(int iterations);
    
    // C++ new/delete native 方法
    private static native void nativeAllocWithNew(int count);
    private static native void nativeAllocWithNewArray(int count);
    private static native void nativeAllocObjects(int count);
    private static native void nativeAllocObjectArrays(int count);
    
    // FD 泄漏 native 方法
    private static native void nativeLeakFileDescriptors(int count, String pathPrefix);
    private static native void nativeLeakFilePointers(int count, String pathPrefix);
}
