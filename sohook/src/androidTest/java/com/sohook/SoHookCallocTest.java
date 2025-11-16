package com.sohook;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.sohook.test.TestMemoryHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * SoHook calloc 函数测试
 * 专门测试 calloc 内存分配函数的 Hook 功能
 */
@RunWith(AndroidJUnit4.class)
public class SoHookCallocTest {
    private static final String TAG = "SoHookCallocTest";
    private static final String TEST_LIB = "libtest_memory.so";

    @Before
    public void setUp() {
        Log.i(TAG, "=== Test Setup ===");

        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "test_memory library not loaded, some tests may be skipped");
            return;
        }
        
        // 初始化 SoHook
        int ret = SoHook.init(true);
        assertEquals("SoHook init should succeed", 0, ret);
        
        // Hook 测试库
        ret = SoHook.hook(Collections.singletonList(TEST_LIB));
        assertEquals("Hook should succeed", 0, ret);
        
        // 重置统计
        SoHook.resetStats();
        
        Log.i(TAG, "Setup completed");
    }

    @After
    public void tearDown() {
        Log.i(TAG, "=== Test Teardown ===");
        
        // Unhook
        SoHook.unhookAll();
        
        Log.i(TAG, "Teardown completed");
    }

    /**
     * 测试基本的calloc分配
     */
    @Test
    public void testBasicCalloc() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testBasicCalloc ===");
        
        // 使用calloc分配内存（10个元素，每个1024字节）
        int nmemb = 10;
        int size = 1024;
        long ptr = TestMemoryHelper.calloc(nmemb, size);
        assertTrue("Calloc should succeed", ptr != 0);
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have 1 allocation", 1, stats.currentAllocCount);
        assertEquals("Should have allocated size", nmemb * size, stats.currentAllocSize);
        Log.i(TAG, "Allocated: " + (nmemb * size) + " bytes, Stats: " + stats);
        
        // 释放内存
        TestMemoryHelper.free(ptr);
        
        // 检查统计
        stats = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats.currentAllocCount);
        assertEquals("Should have no leaked size", 0, stats.currentAllocSize);
        
        Log.i(TAG, "✓ testBasicCalloc passed");
    }

    /**
     * 测试calloc分配零大小
     */
    @Test
    public void testCallocZeroSize() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testCallocZeroSize ===");
        
        SoHook.resetStats();
        
        // calloc(10, 0) 应该分配0字节
        long ptr = TestMemoryHelper.calloc(10, 0);
        // 注意：calloc(10, 0) 可能返回NULL或有效指针，取决于实现
        
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        Log.i(TAG, "Calloc(10, 0) result: " + ptr + ", Stats: " + stats);
        
        if (ptr != 0) {
            TestMemoryHelper.free(ptr);
        }
        
        Log.i(TAG, "✓ testCallocZeroSize passed");
    }

    /**
     * 测试calloc分配零元素
     */
    @Test
    public void testCallocZeroElements() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testCallocZeroElements ===");
        
        SoHook.resetStats();
        
        // calloc(0, 1024) 应该分配0字节
        long ptr = TestMemoryHelper.calloc(0, 1024);
        // 注意：calloc(0, 1024) 可能返回NULL或有效指针，取决于实现
        
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        Log.i(TAG, "Calloc(0, 1024) result: " + ptr + ", Stats: " + stats);
        
        if (ptr != 0) {
            TestMemoryHelper.free(ptr);
        }
        
        Log.i(TAG, "✓ testCallocZeroElements passed");
    }

    /**
     * 测试多次calloc分配
     */
    @Test
    public void testMultipleCalloc() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testMultipleCalloc ===");
        
        SoHook.resetStats();
        
        // 多次calloc分配
        int count = 5;
        long[] ptrs = new long[count];
        int nmemb = 20;
        int size = 256;
        long expectedSize = nmemb * size;
        
        for (int i = 0; i < count; i++) {
            ptrs[i] = TestMemoryHelper.calloc(nmemb, size);
            assertTrue("Calloc " + i + " should succeed", ptrs[i] != 0);
        }
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have " + count + " allocations", count, stats.currentAllocCount);
        assertEquals("Should have allocated size", count * expectedSize, stats.currentAllocSize);
        Log.i(TAG, "Allocated " + count + " blocks, total: " + (count * expectedSize) + " bytes");
        
        // 释放所有内存
        for (int i = 0; i < count; i++) {
            TestMemoryHelper.free(ptrs[i]);
        }
        
        // 检查统计
        stats = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats.currentAllocCount);
        
        Log.i(TAG, "✓ testMultipleCalloc passed");
    }

    /**
     * 测试calloc与malloc的混合使用
     */
    @Test
    public void testCallocMixedWithMalloc() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testCallocMixedWithMalloc ===");
        
        SoHook.resetStats();
        
        // 混合使用calloc和malloc
        long ptr1 = TestMemoryHelper.alloc(1024);
        long ptr2 = TestMemoryHelper.calloc(10, 128);
        long ptr3 = TestMemoryHelper.alloc(2048);
        long ptr4 = TestMemoryHelper.calloc(5, 256);
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have 4 allocations", 4, stats.currentAllocCount);
        long expectedSize = 1024 + (10 * 128) + 2048 + (5 * 256);
        assertEquals("Should have allocated size", expectedSize, stats.currentAllocSize);
        Log.i(TAG, "Mixed allocations: " + stats);
        
        // 释放所有内存
        TestMemoryHelper.free(ptr1);
        TestMemoryHelper.free(ptr2);
        TestMemoryHelper.free(ptr3);
        TestMemoryHelper.free(ptr4);
        
        // 检查统计
        stats = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats.currentAllocCount);
        
        Log.i(TAG, "✓ testCallocMixedWithMalloc passed");
    }

    /**
     * 测试calloc分配大块内存
     */
    @Test
    public void testCallocLargeAllocation() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testCallocLargeAllocation ===");
        
        SoHook.resetStats();
        
        // 分配大块内存（1000个元素，每个4096字节 = 4MB）
        int nmemb = 1000;
        int size = 4096;
        long ptr = TestMemoryHelper.calloc(nmemb, size);
        assertTrue("Large calloc should succeed", ptr != 0);
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have 1 allocation", 1, stats.currentAllocCount);
        assertEquals("Should have allocated size", (long)nmemb * size, stats.currentAllocSize);
        Log.i(TAG, "Large allocation: " + (nmemb * size) + " bytes");
        
        // 释放内存
        TestMemoryHelper.free(ptr);
        
        // 检查统计
        stats = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats.currentAllocCount);
        
        Log.i(TAG, "✓ testCallocLargeAllocation passed");
    }

    /**
     * 测试calloc泄漏检测
     */
    @Test
    public void testCallocLeakDetection() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testCallocLeakDetection ===");
        
        SoHook.resetStats();
        
        // 故意泄漏calloc分配的内存
        int nmemb = 5;
        int size = 512;
        long ptr = TestMemoryHelper.calloc(nmemb, size);
        assertTrue("Calloc leak should succeed", ptr != 0);
        
        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should detect 1 leak", 1, stats.currentAllocCount);
        assertEquals("Should detect leaked size", (long)nmemb * size, stats.currentAllocSize);
        
        // 获取泄漏报告
        String report = SoHook.getLeakReport();
        assertNotNull("Leak report should not be null", report);
        assertTrue("Report should contain leak info", report.contains("Memory Leak Report"));
        
        Log.i(TAG, "Detected calloc leak: " + stats.currentAllocSize + " bytes");
        Log.i(TAG, "✓ testCallocLeakDetection passed");
        
        // 注意：不释放ptr，这是故意的泄漏
    }

    /**
     * 测试calloc统计的准确性
     */
    @Test
    public void testCallocStatsAccuracy() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testCallocStatsAccuracy ===");
        
        SoHook.resetStats();
        
        // 分配calloc内存
        int nmemb = 8;
        int size = 1024;
        long expectedSize = (long)nmemb * size;
        
        long ptr = TestMemoryHelper.calloc(nmemb, size);
        assertTrue("Calloc should succeed", ptr != 0);
        
        // 获取统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        // 验证统计准确性
        assertTrue("Should have at least 1 allocation", stats.totalAllocCount >= 1);
        assertTrue("Should have allocated at least " + expectedSize + " bytes", 
                   stats.totalAllocSize >= expectedSize);
        assertEquals("Current alloc count should be 1", 1, stats.currentAllocCount);
        assertEquals("Current alloc size should be " + expectedSize, 
                     expectedSize, stats.currentAllocSize);
        
        // 释放内存
        TestMemoryHelper.free(ptr);
        
        // 验证释放后的统计
        stats = SoHook.getMemoryStats();
        assertTrue("Should have at least 1 free", stats.totalFreeCount >= 1);
        assertEquals("Should have no current leaks", 0, stats.currentAllocCount);
        
        // 验证一致性
        assertEquals("Current should equal total - free", 
                     stats.totalAllocCount - stats.totalFreeCount, 
                     stats.currentAllocCount);
        
        Log.i(TAG, "✓ testCallocStatsAccuracy passed");
        Log.d(TAG, "Final stats: " + stats);
    }

    /**
     * 测试calloc与realloc的组合使用
     */
    @Test
    public void testCallocWithRealloc() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testCallocWithRealloc ===");
        
        SoHook.resetStats();
        
        // 先用calloc分配
        int nmemb = 10;
        int size = 256;
        long ptr = TestMemoryHelper.calloc(nmemb, size);
        assertTrue("Calloc should succeed", ptr != 0);
        
        // 检查初始统计
        SoHook.MemoryStats stats1 = SoHook.getMemoryStats();
        assertEquals("Should have 1 allocation", 1, stats1.currentAllocCount);
        assertEquals("Should have allocated size", (long)nmemb * size, stats1.currentAllocSize);
        
        // 使用realloc扩大
        int newSize = 4096;
        long newPtr = TestMemoryHelper.realloc(ptr, newSize);
        assertTrue("Realloc should succeed", newPtr != 0);
        
        // 检查realloc后的统计
        SoHook.MemoryStats stats2 = SoHook.getMemoryStats();
        assertEquals("Should still have 1 allocation", 1, stats2.currentAllocCount);
        assertEquals("Should have new size", newSize, stats2.currentAllocSize);
        
        // 释放内存
        TestMemoryHelper.free(newPtr);
        
        // 检查最终统计
        SoHook.MemoryStats stats3 = SoHook.getMemoryStats();
        assertEquals("Should have no leaks", 0, stats3.currentAllocCount);
        
        Log.i(TAG, "✓ testCallocWithRealloc passed");
    }
}

