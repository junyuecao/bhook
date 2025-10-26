package com.sohook.test;

import android.util.Log;

/**
 * 文件描述符测试工具类
 * 提供C层的文件操作函数用于测试FD泄漏检测
 */
public class TestFd {
    private static final String TAG = "TestFd";
    private static boolean sLibraryLoaded = false;

    static {
        try {
            System.loadLibrary("test_memory");
            sLibraryLoaded = true;
            Log.i(TAG, "test_memory library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load test_memory library", e);
        }
    }

    /**
     * 检查库是否加载成功
     */
    public static boolean isLibraryLoaded() {
        return sLibraryLoaded;
    }

    /**
     * 使用open打开文件
     * @param path 文件路径
     * @param flags 打开标志（如O_RDONLY, O_WRONLY等）
     * @return 文件描述符，失败返回-1
     */
    public static native int nativeOpenFile(String path, int flags);

    /**
     * 使用close关闭文件
     * @param fd 文件描述符
     * @return 0表示成功，-1表示失败
     */
    public static native int nativeCloseFile(int fd);

    /**
     * 使用fopen打开文件
     * @param path 文件路径
     * @param mode 打开模式（如"r", "w"等）
     * @return FILE指针（作为long），失败返回0
     */
    public static native long nativeFopenFile(String path, String mode);

    /**
     * 使用fclose关闭文件
     * @param fp FILE指针
     * @return 0表示成功，-1表示失败
     */
    public static native int nativeFcloseFile(long fp);

    /**
     * 打开多个文件
     * @param pathPrefix 文件路径前缀
     * @param count 要打开的文件数量
     * @param fds 输出的文件描述符数组
     * @return 成功打开的文件数量
     */
    public static native int nativeOpenMultiple(String pathPrefix, int count, int[] fds);

    /**
     * 关闭多个文件
     * @param fds 文件描述符数组
     * @param count 数量
     * @return 成功关闭的文件数量
     */
    public static native int nativeCloseMultiple(int[] fds, int count);

    /**
     * 故意泄漏一个文件描述符（用于测试）
     * @param path 文件路径
     * @return 泄漏的文件描述符
     */
    public static native int nativeLeakFd(String path);

    /**
     * 故意泄漏一个FILE指针（用于测试）
     * @param path 文件路径
     * @return 泄漏的FILE指针
     */
    public static native long nativeLeakFile(String path);

    /**
     * 写入文件并关闭
     * @param path 文件路径
     * @param data 要写入的数据
     * @return 0表示成功，-1表示失败
     */
    public static native int nativeWriteAndClose(String path, String data);

    /**
     * 读取文件并关闭
     * @param path 文件路径
     * @return 读取的内容，失败返回null
     */
    public static native String nativeReadAndClose(String path);

    // 文件打开标志常量（对应fcntl.h中的定义）
    public static final int O_RDONLY = 0x0000;
    public static final int O_WRONLY = 0x0001;
    public static final int O_RDWR = 0x0002;
    public static final int O_CREAT = 0x0040;
    public static final int O_TRUNC = 0x0200;
    public static final int O_APPEND = 0x0400;
}
