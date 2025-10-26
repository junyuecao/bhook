package com.sohook;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.sohook.test.TestMemoryCpp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * SoHook C++ new/delete 测试
 * 测试 C++ 内存分配的 Hook 功能
 */
@RunWith(AndroidJUnit4.class)
public class SoHookCppTest {
    private static final String TAG = "SoHookCppTest";
    private static final String TEST_LIB = "libtest_memory_cpp.so";

    @Before
    public void setUp() {
        Log.i(TAG, "=== Test Setup ===");

        if (!TestMemoryCpp.isLibraryLoaded()) {
            Log.w(TAG, "test_memory_cpp library not loaded, some tests may be skipped");
            return;
        }
        
        // 初始化 SoHook（启用 backtrace）
        int ret = SoHook.init(true, true);
        assertEquals("SoHook init should succeed", 0, ret);
        
        // Hook 测试库
        ret = SoHook.hook(Arrays.asList(TEST_LIB));
        assertEquals("Hook should succeed", 0, ret);
        
        // 重置统计
        SoHook.resetStats();
        
        Log.i(TAG, "Setup completed");
    }

    @After
    public void tearDown() {
        Log.i(TAG, "=== Test Teardown ===");
        
        // 打印泄漏报告
        String report = SoHook.getLeakReport();
        Log.i(TAG, "Leak Report:\n" + report);
        
        // Unhook
        SoHook.unhookAll();
        
        Log.i(TAG, "Teardown completed");
    }

    @Test
    public void testOperatorNew() {
        Log.i(TAG, "=== testOperatorNew ===");
        
        // 分配内存
        long ptr = TestMemoryCpp.nativeNew(1024);
        assertTrue("Allocation should succeed", ptr != 0);
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have allocations", 1, stats.currentAllocCount);
        assertEquals("Should have allocated size", 1024, stats.currentAllocSize);
        
        // 释放内存
        TestMemoryCpp.nativeDelete(ptr);
        
        // 检查统计
        stats = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats.currentAllocCount);
        assertEquals("Should have no leaked size", 0, stats.currentAllocSize);
        
        Log.i(TAG, "testOperatorNew passed");
    }

    @Test
    public void testOperatorNewArray() {
        Log.i(TAG, "=== testOperatorNewArray ===");
        
        // 分配数组
        long ptr = TestMemoryCpp.nativeNewArray(2048);
        assertTrue("Array allocation should succeed", ptr != 0);
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have allocations", 1, stats.currentAllocCount);
        assertEquals("Should have allocated size", 2048, stats.currentAllocSize);
        
        // 释放数组
        TestMemoryCpp.nativeDeleteArray(ptr);
        
        // 检查统计
        stats = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats.currentAllocCount);
        assertEquals("Should have no leaked size", 0, stats.currentAllocSize);
        
        Log.i(TAG, "testOperatorNewArray passed");
    }

    @Test
    public void testNewObject() {
        Log.i(TAG, "=== testNewObject ===");
        
        // 创建对象
        long ptr = TestMemoryCpp.nativeNewObject();
        assertTrue("Object creation should succeed", ptr != 0);
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have allocations", 1, stats.currentAllocCount);
        
        // 删除对象
        TestMemoryCpp.nativeDeleteObject(ptr);
        
        // 检查统计
        stats = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats.currentAllocCount);
        
        Log.i(TAG, "testNewObject passed");
    }

    @Test
    public void testNewObjectArray() {
        Log.i(TAG, "=== testNewObjectArray ===");
        
        int count = 5;
        
        // 创建对象数组
        long ptr = TestMemoryCpp.nativeNewObjectArray(count);
        assertTrue("Object array creation should succeed", ptr != 0);
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have allocations", 1, stats.currentAllocCount);
        
        // 删除对象数组
        TestMemoryCpp.nativeDeleteObjectArray(ptr);
        
        // 检查统计
        stats = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats.currentAllocCount);
        
        Log.i(TAG, "testNewObjectArray passed");
    }

    @Test
    public void testNewMultiple() {
        Log.i(TAG, "=== testNewMultiple ===");
        
        int count = 10;
        long size = 512;
        
        // 多次分配（会泄漏前面的分配）
        long lastPtr = TestMemoryCpp.nativeNewMultiple(count, size);
        assertTrue("Multiple allocations should succeed", lastPtr != 0);
        
        // 检查统计（应该有 count 次分配）
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have " + count + " allocations", count, stats.currentAllocCount);
        assertEquals("Should have allocated size", count * size, stats.currentAllocSize);
        
        Log.i(TAG, "testNewMultiple passed (intentional leaks for testing)");
    }

    @Test
    public void testNewDeleteObjects() {
        Log.i(TAG, "=== testNewDeleteObjects ===");
        
        int count = 3;
        
        // 创建多个对象
        long[] ptrs = TestMemoryCpp.nativeNewObjects(count);
        assertNotNull("Object creation should succeed", ptrs);
        assertEquals("Should have " + count + " objects", count, ptrs.length);
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have allocations", count, stats.currentAllocCount);
        
        // 删除所有对象
        TestMemoryCpp.nativeDeleteObjects(ptrs);
        
        // 检查统计
        stats = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats.currentAllocCount);
        
        Log.i(TAG, "testNewDeleteObjects passed");
    }

    @Test
    public void testLeakDetection() {
        Log.i(TAG, "=== testLeakDetection ===");
        
        // 故意泄漏内存
        long ptr1 = TestMemoryCpp.nativeLeakNew(1024);
        assertTrue("Leak allocation should succeed", ptr1 != 0);
        
        // 故意泄漏对象
        long ptr2 = TestMemoryCpp.nativeLeakObject();
        assertTrue("Leak object should succeed", ptr2 != 0);
        
        // 故意泄漏对象数组
        long ptr3 = TestMemoryCpp.nativeLeakObjectArray(3);
        assertTrue("Leak object array should succeed", ptr3 != 0);
        
        // 检查统计（3个泄漏：1个new + 1个对象 + 1个对象数组）
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should detect leaks", 3, stats.currentAllocCount);
        assertTrue("Should have leaked size", stats.currentAllocSize > 0);
        
        Log.i(TAG, "Detected " + stats.currentAllocCount + " leaks, total size: " + stats.currentAllocSize + " bytes");
        
        // 获取泄漏报告
        String report = SoHook.getLeakReport();
        assertNotNull("Leak report should not be null", report);
        assertTrue("Leak report should contain leak info", report.contains("Memory Leak Report"));
        
        Log.i(TAG, "testLeakDetection passed");
    }

    @Test
    public void testMixedAllocations() {
        Log.i(TAG, "=== testMixedAllocations ===");
        
        // 混合使用 new 和 new[]
        long ptr1 = TestMemoryCpp.nativeNew(512);
        long ptr2 = TestMemoryCpp.nativeNewArray(1024);
        long ptr3 = TestMemoryCpp.nativeNewObject();
        long ptr4 = TestMemoryCpp.nativeNewObjectArray(2);
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have allocations", 4, stats.currentAllocCount);
        
        // 释放（使用正确的 delete）
        TestMemoryCpp.nativeDelete(ptr1);
        TestMemoryCpp.nativeDeleteArray(ptr2);
        TestMemoryCpp.nativeDeleteObject(ptr3);
        TestMemoryCpp.nativeDeleteObjectArray(ptr4);
        
        // 检查统计
        stats = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats.currentAllocCount);
        
        Log.i(TAG, "testMixedAllocations passed");
    }

    @Test
    public void testBacktraceCapture() {
        Log.i(TAG, "=== testBacktraceCapture ===");
        
        // 确保 backtrace 已启用
        assertTrue("Backtrace should be enabled", SoHook.isBacktraceEnabled());
        
        // 泄漏一些内存
        TestMemoryCpp.nativeLeakNew(256);
        TestMemoryCpp.nativeLeakObject();
        
        // 获取泄漏报告
        String report = SoHook.getLeakReport();
        assertNotNull("Leak report should not be null", report);
        
        // 检查是否包含调用栈信息
        assertTrue("Report should contain backtrace", 
                   report.contains("backtrace") || report.contains("Backtrace") || report.contains("#"));
        
        Log.i(TAG, "Leak Report with Backtrace:\n" + report);
        Log.i(TAG, "testBacktraceCapture passed");
    }
}
