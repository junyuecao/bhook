package com.sohook;

import android.util.Log;

import java.util.List;

public class SoHook {
    private static final String TAG = "SoHook";
    private static boolean sInitialized = false;

    static {
        try {
            System.loadLibrary("sohook");
            Log.i(TAG, "SoHook native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load SoHook native library", e);
        }
    }

    /**
     * 初始化内存泄漏检测库
     * @param debug 是否开启调试模式
     * @return 0表示成功，其他值表示失败
     */
    public static int init(boolean debug) {
        return init(debug, false);
    }

    /**
     * 初始化内存泄漏检测库
     * @param debug 是否开启调试模式
     * @param enableBacktrace 是否启用栈回溯（会显著影响性能，约10-20x慢）
     * @return 0表示成功，其他值表示失败
     */
    public static int init(boolean debug, boolean enableBacktrace) {
        if (sInitialized) {
            Log.w(TAG, "SoHook already initialized");
            return 0;
        }
        int ret = nativeInit(debug, enableBacktrace);
        if (ret == 0) {
            sInitialized = true;
            Log.i(TAG, "SoHook initialized successfully (backtrace=" + enableBacktrace + ")");
        } else {
            Log.e(TAG, "SoHook initialization failed with code: " + ret);
        }
        return ret;
    }

    /**
     * 通过Hook soNames中的so库，实现针对这些so文件的内存和文件描述符泄漏检测功能
     * @param soNames 需要监控的so文件名列表
     * @return 0表示成功，其他值表示失败
     */
    public static int hook(List<String> soNames) {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized, call init() first");
            return -1;
        }
        if (soNames == null || soNames.isEmpty()) {
            Log.w(TAG, "soNames is null or empty");
            return -1;
        }
        String[] soArray = soNames.toArray(new String[0]);
        return nativeHook(soArray);
    }

    /**
     * 停止对指定so库的内存和文件描述符监控
     * @param soNames 需要停止监控的so文件名列表
     * @return 0表示成功，其他值表示失败
     */
    public static int unhook(List<String> soNames) {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return -1;
        }
        if (soNames == null || soNames.isEmpty()) {
            Log.w(TAG, "soNames is null or empty");
            return -1;
        }
        String[] soArray = soNames.toArray(new String[0]);
        return nativeUnhook(soArray);
    }

    /**
     * 停止对所有已hook的so库的内存和文件描述符监控
     * @return 0表示成功，其他值表示失败
     */
    public static int unhookAll() {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return -1;
        }
        Log.i(TAG, "Unhooking all libraries");
        return nativeUnhookAll();
    }

    /**
     * 获取内存泄漏报告
     * @return 内存泄漏报告字符串
     */
    public static String getLeakReport() {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return "SoHook not initialized";
        }
        return nativeGetLeakReport();
    }

    /**
     * 将内存泄漏报告导出到文件
     * @param filePath 文件路径
     * @return 0表示成功，其他值表示失败
     */
    public static int dumpLeakReport(String filePath) {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return -1;
        }
        if (filePath == null || filePath.isEmpty()) {
            Log.w(TAG, "filePath is null or empty");
            return -1;
        }
        return nativeDumpLeakReport(filePath);
    }

    /**
     * 获取当前内存统计信息
     * @return MemoryStats对象，包含内存分配和释放的统计信息
     */
    public static MemoryStats getMemoryStats() {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return new MemoryStats();
        }
        return nativeGetMemoryStats();
    }

    /**
     * 重置内存统计信息
     */
    public static void resetStats() {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return;
        }
        nativeResetStats();
    }

    /**
     * 启用或禁用栈回溯
     * @param enable true 启用，false 禁用
     * 注意：启用栈回溯会显著影响性能（约10-20x慢）
     */
    public static void setBacktraceEnabled(boolean enable) {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return;
        }
        nativeSetBacktraceEnabled(enable);
        Log.i(TAG, "Backtrace " + (enable ? "enabled" : "disabled"));
    }

    /**
     * 检查栈回溯是否启用
     * @return true 已启用，false 未启用
     */
    public static boolean isBacktraceEnabled() {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return false;
        }
        return nativeIsBacktraceEnabled();
    }

    // ============================================
    // 文件描述符监控相关接口
    // ============================================

    /**
     * 获取文件描述符泄漏报告
     * @return 文件描述符泄漏报告字符串
     */
    public static String getFdLeakReport() {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return "SoHook not initialized";
        }
        return nativeGetFdLeakReport();
    }

    /**
     * 将文件描述符泄漏报告导出到文件
     * @param filePath 文件路径
     * @return 0表示成功，其他值表示失败
     */
    public static int dumpFdLeakReport(String filePath) {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return -1;
        }
        if (filePath == null || filePath.isEmpty()) {
            Log.w(TAG, "filePath is null or empty");
            return -1;
        }
        return nativeDumpFdLeakReport(filePath);
    }

    /**
     * 获取当前文件描述符统计信息
     * @return FdStats对象，包含文件打开和关闭的统计信息
     */
    public static FdStats getFdStats() {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return new FdStats();
        }
        return nativeGetFdStats();
    }

    /**
     * 重置文件描述符统计信息
     */
    public static void resetFdStats() {
        if (!sInitialized) {
            Log.e(TAG, "SoHook not initialized");
            return;
        }
        nativeResetFdStats();
    }

    // Native methods - 内存和文件描述符跟踪
    private static native int nativeInit(boolean debug, boolean enableBacktrace);
    private static native int nativeHook(String[] soNames);
    private static native int nativeUnhook(String[] soNames);
    private static native int nativeUnhookAll();
    private static native String nativeGetLeakReport();
    private static native int nativeDumpLeakReport(String filePath);
    private static native MemoryStats nativeGetMemoryStats();
    private static native void nativeResetStats();
    private static native void nativeSetBacktraceEnabled(boolean enable);
    private static native boolean nativeIsBacktraceEnabled();
    static native String nativeGetLeaksJson();
    static native String nativeGetLeaksAggregatedJson();

    // Native methods - 文件描述符专用
    private static native String nativeGetFdLeakReport();
    private static native int nativeDumpFdLeakReport(String filePath);
    private static native FdStats nativeGetFdStats();
    private static native void nativeResetFdStats();
    static native String nativeGetFdLeaksJson();

    /**
     * 内存统计信息类
     */
    public static class MemoryStats {
        public long totalAllocCount = 0;      // 总分配次数
        public long totalAllocSize = 0;       // 总分配大小（字节）
        public long totalFreeCount = 0;       // 总释放次数
        public long totalFreeSize = 0;        // 总释放大小（字节）
        public long currentAllocCount = 0;    // 当前未释放的分配次数
        public long currentAllocSize = 0;     // 当前未释放的内存大小（字节）

        public MemoryStats() {}
        public MemoryStats(long totalAllocCount, long totalAllocSize, long totalFreeCount, long totalFreeSize, long currentAllocCount, long currentAllocSize) {
            this.totalAllocCount = totalAllocCount;
            this.totalAllocSize = totalAllocSize;
            this.totalFreeCount = totalFreeCount;
            this.totalFreeSize = totalFreeSize;
            this.currentAllocCount = currentAllocCount;
            this.currentAllocSize = currentAllocSize;
        }
        @Override
        public String toString() {
            return "MemoryStats{" +
                    "totalAllocCount=" + totalAllocCount +
                    ", totalAllocSize=" + totalAllocSize +
                    ", totalFreeCount=" + totalFreeCount +
                    ", totalFreeSize=" + totalFreeSize +
                    ", currentAllocCount=" + currentAllocCount +
                    ", currentAllocSize=" + currentAllocSize +
                    '}';
        }
    }

    /**
     * 文件描述符统计信息类
     */
    public static class FdStats {
        public long totalOpenCount = 0;       // 总打开次数
        public long totalCloseCount = 0;      // 总关闭次数
        public long currentOpenCount = 0;     // 当前未关闭的文件描述符数量

        public FdStats() {}
        public FdStats(long totalOpenCount, long totalCloseCount, long currentOpenCount) {
            this.totalOpenCount = totalOpenCount;
            this.totalCloseCount = totalCloseCount;
            this.currentOpenCount = currentOpenCount;
        }
        @Override
        public String toString() {
            return "FdStats{" +
                    "totalOpenCount=" + totalOpenCount +
                    ", totalCloseCount=" + totalCloseCount +
                    ", currentOpenCount=" + currentOpenCount +
                    '}';
        }
    }
}