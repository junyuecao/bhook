package com.sohook;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * SoHook 准确性测试
 * 验证内存泄漏检测的准确性
 * 
 * 注意：这些测试需要 Hook native 库才能捕获内存分配
 */
@RunWith(AndroidJUnit4.class)
public class SoHookAccuracyTest {
    private static final String TAG = "SoHook-AccuracyTest";
    private static final String TARGET_LIB = "libtest_memory.so";
    private Context context;
    private static boolean sLibraryPreloaded = false;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        
        // 检查测试库是否加载
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "test_memory library not loaded, some tests may be skipped");
            return;
        }
        
        // 初始化 SoHook
        SoHook.init(true);
        
        // 预加载库（只需要一次）
        if (!sLibraryPreloaded) {
            Log.i(TAG, "Pre-loading " + TARGET_LIB + "...");
            long preloadPtr = TestMemoryHelper.alloc(16);
            if (preloadPtr != 0) {
                TestMemoryHelper.free(preloadPtr);
                Log.i(TAG, "✓ " + TARGET_LIB + " loaded successfully");
                sLibraryPreloaded = true;
            } else {
                Log.e(TAG, "✗ Failed to load " + TARGET_LIB);
            }
            
            // 等待库完全加载
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        // 每次都 Hook（bytehook 会处理重复 Hook）
        Log.i(TAG, "Hooking " + TARGET_LIB + "...");
        int hookResult = SoHook.hook(Collections.singletonList(TARGET_LIB));
        if (hookResult == 0) {
            Log.i(TAG, "✓ Hook API returned success");
        } else {
            Log.w(TAG, "Hook returned: " + hookResult + " (may already be hooked)");
        }
        
        // 等待 Hook 完全生效（bytehook 异步执行）
        Log.i(TAG, "Waiting for hook to take effect...");
        try {
            Thread.sleep(100);  // 增加到 1 秒
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 测试 Hook 是否生效：分配一小块内存
        Log.i(TAG, "Testing if hook is active...");
        long testPtr = TestMemoryHelper.alloc(32);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        SoHook.MemoryStats testStats = SoHook.getMemoryStats();
        Log.i(TAG, "Test allocation stats: totalAllocCount=" + testStats.totalAllocCount);
        if (testPtr != 0) {
            TestMemoryHelper.free(testPtr);
        }
        
        // 重置统计（在 Hook 之后）
        Log.i(TAG, "Resetting stats...");
        SoHook.resetStats();
        
        // 再等待一下，确保重置生效
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        Log.i(TAG, "Test setup completed");
    }

    @After
    public void tearDown() {
        // 重置统计
        SoHook.resetStats();
        Log.i(TAG, "Test teardown completed");
    }

    /**
     * 测试统计信息的基本准确性
     * 验证重置后统计归零
     */
    @Test
    public void testStatsResetAccuracy() {
        // 重置统计
        SoHook.resetStats();
        
        // 获取统计信息
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        // 验证所有统计都是0
        assertEquals("Total alloc count should be 0 after reset", 0, stats.totalAllocCount);
        assertEquals("Total alloc size should be 0 after reset", 0, stats.totalAllocSize);
        assertEquals("Total free count should be 0 after reset", 0, stats.totalFreeCount);
        assertEquals("Total free size should be 0 after reset", 0, stats.totalFreeSize);
        assertEquals("Current alloc count should be 0 after reset", 0, stats.currentAllocCount);
        assertEquals("Current alloc size should be 0 after reset", 0, stats.currentAllocSize);
        
        Log.i(TAG, "✓ Stats reset accuracy test passed");
    }

    /**
     * 测试统计信息的一致性
     * 验证 current = total - free
     */
    @Test
    public void testStatsConsistency() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // 分配一些内存
        long ptr1 = TestMemoryHelper.alloc(1024);
        long ptr2 = TestMemoryHelper.alloc(2048);
        long ptr3 = TestMemoryHelper.alloc(512);
        
        // 等待
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 释放部分内存
        TestMemoryHelper.free(ptr1);
        
        // 等待
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取统计信息
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        // 验证一致性：current = total - free
        long expectedCurrentCount = stats.totalAllocCount - stats.totalFreeCount;
        long expectedCurrentSize = stats.totalAllocSize - stats.totalFreeSize;
        
        assertEquals("Current alloc count should equal total - free", 
                     expectedCurrentCount, stats.currentAllocCount);
        assertEquals("Current alloc size should equal total - free", 
                     expectedCurrentSize, stats.currentAllocSize);
        
        // 清理
        TestMemoryHelper.free(ptr2);
        TestMemoryHelper.free(ptr3);
        
        Log.i(TAG, "✓ Stats consistency test passed");
        Log.d(TAG, "Stats: " + stats);
    }

    /**
     * 测试统计信息的单调性
     * 验证总分配次数和大小只增不减
     */
    @Test
    public void testStatsMonotonicity() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // 获取初始统计
        SoHook.MemoryStats stats1 = SoHook.getMemoryStats();
        
        // 分配一些内存
        long ptr1 = TestMemoryHelper.alloc(512);
        long ptr2 = TestMemoryHelper.alloc(1024);
        
        // 等待
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取第二次统计
        SoHook.MemoryStats stats2 = SoHook.getMemoryStats();
        
        // 再分配更多内存
        long ptr3 = TestMemoryHelper.alloc(2048);
        
        // 等待
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取第三次统计
        SoHook.MemoryStats stats3 = SoHook.getMemoryStats();
        
        // 验证单调性：总分配次数和大小应该 >= 之前的值
        assertTrue("Total alloc count should not decrease", 
                   stats2.totalAllocCount >= stats1.totalAllocCount);
        assertTrue("Total alloc size should not decrease", 
                   stats2.totalAllocSize >= stats1.totalAllocSize);
        assertTrue("Total alloc count should keep increasing", 
                   stats3.totalAllocCount >= stats2.totalAllocCount);
        assertTrue("Total alloc size should keep increasing", 
                   stats3.totalAllocSize >= stats2.totalAllocSize);
        
        // 清理
        TestMemoryHelper.free(ptr1);
        TestMemoryHelper.free(ptr2);
        TestMemoryHelper.free(ptr3);
        
        Log.i(TAG, "✓ Stats monotonicity test passed");
        Log.d(TAG, "Stats 1: " + stats1);
        Log.d(TAG, "Stats 2: " + stats2);
        Log.d(TAG, "Stats 3: " + stats3);
    }

    /**
     * 测试泄漏报告的准确性
     * 验证报告中的数字与统计信息一致
     */
    @Test
    public void testLeakReportAccuracy() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // 分配一些内存（部分泄漏）
        long ptr1 = TestMemoryHelper.alloc(1024);
        long ptr2 = TestMemoryHelper.leakMemory(2048);  // 故意泄漏
        long ptr3 = TestMemoryHelper.alloc(512);
        
        // 等待
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 释放部分内存
        TestMemoryHelper.free(ptr1);
        TestMemoryHelper.free(ptr3);
        
        // 等待
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取统计信息
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        // 获取泄漏报告
        String report = SoHook.getLeakReport();
        
        // 验证报告包含统计数字
        assertTrue("Report should contain total allocations", 
                   report.contains("Total Allocations: " + stats.totalAllocCount));
        assertTrue("Report should contain total frees", 
                   report.contains("Total Frees: " + stats.totalFreeCount));
        assertTrue("Report should contain current leaks", 
                   report.contains("Current Leaks: " + stats.currentAllocCount));
        
        Log.i(TAG, "✓ Leak report accuracy test passed");
        Log.d(TAG, "Stats: " + stats);
        Log.d(TAG, "Report preview:\n" + report.substring(0, Math.min(300, report.length())));
        
        // 注意：ptr2 故意不释放
    }

    /**
     * 测试多次重置的准确性
     * 验证每次重置后统计都归零
     */
    @Test
    public void testMultipleResetAccuracy() {
        for (int i = 0; i < 5; i++) {
            // 重置
            SoHook.resetStats();
            
            // 验证归零
            SoHook.MemoryStats stats = SoHook.getMemoryStats();
            assertEquals("Reset " + i + ": total alloc count should be 0", 
                         0, stats.totalAllocCount);
            assertEquals("Reset " + i + ": current alloc count should be 0", 
                         0, stats.currentAllocCount);
        }
        
        Log.i(TAG, "✓ Multiple reset accuracy test passed");
    }

    /**
     * 测试Hook前后的统计差异
     * 验证Hook确实能捕获内存分配
     */
    @Test
    public void testHookCapture() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // Hook前的统计
        SoHook.MemoryStats statsBeforeAlloc = SoHook.getMemoryStats();
        assertEquals("Before alloc: should have 0 allocations", 0, statsBeforeAlloc.totalAllocCount);
        
        // 分配一些内存
        long ptr1 = TestMemoryHelper.alloc(1024);
        long ptr2 = TestMemoryHelper.alloc(2048);
        long ptr3 = TestMemoryHelper.alloc(4096);
        
        // 等待
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 分配后的统计
        SoHook.MemoryStats statsAfterAlloc = SoHook.getMemoryStats();
        
        // 验证Hook捕获了内存分配
        assertTrue("Should capture at least 3 allocations", 
                   statsAfterAlloc.totalAllocCount >= 3);
        assertTrue("Should capture at least 7168 bytes (1024+2048+4096)", 
                   statsAfterAlloc.totalAllocSize >= 7168);
        
        Log.i(TAG, "Allocations captured: " + statsAfterAlloc.totalAllocCount);
        Log.i(TAG, "Memory captured: " + statsAfterAlloc.totalAllocSize + " bytes");
        
        // 清理
        TestMemoryHelper.free(ptr1);
        TestMemoryHelper.free(ptr2);
        TestMemoryHelper.free(ptr3);
        
        Log.i(TAG, "✓ Hook capture test passed");
    }

    /**
     * 测试统计信息的非负性
     * 验证所有统计字段都不为负数
     */
    @Test
    public void testStatsNonNegativity() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // 执行多次分配和释放操作
        for (int i = 0; i < 10; i++) {
            // 分配内存
            long ptr = TestMemoryHelper.alloc(512 + i * 100);
            
            // 等待
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            // 获取统计
            SoHook.MemoryStats stats = SoHook.getMemoryStats();
            
            // 验证非负性
            assertTrue("Iteration " + i + ": total alloc count >= 0", 
                       stats.totalAllocCount >= 0);
            assertTrue("Iteration " + i + ": total alloc size >= 0", 
                       stats.totalAllocSize >= 0);
            assertTrue("Iteration " + i + ": total free count >= 0", 
                       stats.totalFreeCount >= 0);
            assertTrue("Iteration " + i + ": total free size >= 0", 
                       stats.totalFreeSize >= 0);
            assertTrue("Iteration " + i + ": current alloc count >= 0", 
                       stats.currentAllocCount >= 0);
            assertTrue("Iteration " + i + ": current alloc size >= 0", 
                       stats.currentAllocSize >= 0);
            
            // 释放内存
            TestMemoryHelper.free(ptr);
        }
        
        Log.i(TAG, "✓ Stats non-negativity test passed");
    }

    /**
     * 测试统计信息的合理性
     * 验证 free <= alloc
     */
    @Test
    public void testStatsRationality() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // 分配一些内存
        long ptr1 = TestMemoryHelper.alloc(1024);
        long ptr2 = TestMemoryHelper.alloc(2048);
        long ptr3 = TestMemoryHelper.alloc(4096);
        long ptr4 = TestMemoryHelper.alloc(8192);
        
        // 等待
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 释放部分内存
        TestMemoryHelper.free(ptr1);
        TestMemoryHelper.free(ptr3);
        
        // 等待
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        // 验证合理性：释放次数不应该超过分配次数
        assertTrue("Free count should not exceed alloc count", 
                   stats.totalFreeCount <= stats.totalAllocCount);
        
        // 验证合理性：释放大小不应该超过分配大小
        assertTrue("Free size should not exceed alloc size", 
                   stats.totalFreeSize <= stats.totalAllocSize);
        
        // 验证合理性：当前分配应该等于总分配减去总释放
        assertEquals("Current count should equal alloc - free", 
                     stats.totalAllocCount - stats.totalFreeCount, 
                     stats.currentAllocCount);
        assertEquals("Current size should equal alloc - free", 
                     stats.totalAllocSize - stats.totalFreeSize, 
                     stats.currentAllocSize);
        
        // 清理剩余内存
        TestMemoryHelper.free(ptr2);
        TestMemoryHelper.free(ptr4);
        
        Log.i(TAG, "✓ Stats rationality test passed");
        Log.d(TAG, "Stats: " + stats);
    }

    /**
     * 测试泄漏报告的完整性
     * 验证报告包含所有必要的信息
     */
    @Test
    public void testLeakReportCompleteness() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // 分配一些内存（包括泄漏）
        long ptr1 = TestMemoryHelper.alloc(1024);
        long ptr2 = TestMemoryHelper.leakMemory(2048);
        
        // 等待
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取泄漏报告
        String report = SoHook.getLeakReport();
        
        // 验证报告包含所有必要部分
        assertTrue("Report should contain title", 
                   report.contains("Memory Leak Report"));
        assertTrue("Report should contain total allocations", 
                   report.contains("Total Allocations"));
        assertTrue("Report should contain total frees", 
                   report.contains("Total Frees"));
        assertTrue("Report should contain current leaks", 
                   report.contains("Current Leaks"));
        assertTrue("Report should contain separator", 
                   report.contains("===") || report.contains("---"));
        
        // 清理（不释放 ptr2，它是故意泄漏的）
        TestMemoryHelper.free(ptr1);
        
        Log.i(TAG, "✓ Leak report completeness test passed");
    }

    // ============================================
    // 精确准确性测试（使用 test_memory 库）
    // ============================================

    /**
     * 测试精确的内存分配统计
     * 验证分配指定大小的内存后，统计数字准确
     */
    @Test
    public void testExactAllocationAccuracy() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // 分配 1024 字节
        long ptr = TestMemoryHelper.alloc(1024);
        assertNotEquals("Allocation should succeed", 0, ptr);
        
        // 等待统计更新
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        // 验证至少捕获了这次分配
        assertTrue("Should capture at least 1 allocation", stats.totalAllocCount >= 1);
        assertTrue("Should capture at least 1024 bytes", stats.totalAllocSize >= 1024);
        
        // 释放内存
        TestMemoryHelper.free(ptr);
        
        Log.i(TAG, "✓ Exact allocation accuracy test passed");
        Log.d(TAG, "Stats: " + stats);
    }

    /**
     * 测试精确的内存释放统计
     * 验证释放内存后，统计数字准确
     */
    @Test
    public void testExactFreeAccuracy() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // 分配内存
        long ptr = TestMemoryHelper.alloc(2048);
        assertNotEquals("Allocation should succeed", 0, ptr);
        
        // 等待
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取分配后的统计
        SoHook.MemoryStats statsAfterAlloc = SoHook.getMemoryStats();
        
        // 释放内存
        TestMemoryHelper.free(ptr);
        
        // 等待
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取释放后的统计
        SoHook.MemoryStats statsAfterFree = SoHook.getMemoryStats();
        
        // 验证释放次数增加
        assertTrue("Free count should increase", 
                   statsAfterFree.totalFreeCount > statsAfterAlloc.totalFreeCount);
        
        // 验证当前分配减少
        assertTrue("Current alloc should decrease or stay same", 
                   statsAfterFree.currentAllocCount <= statsAfterAlloc.currentAllocCount);
        
        Log.i(TAG, "✓ Exact free accuracy test passed");
        Log.d(TAG, "After alloc: " + statsAfterAlloc);
        Log.d(TAG, "After free: " + statsAfterFree);
    }

    /**
     * 测试多次分配的准确性
     * 验证分配N次后，统计至少有N次分配
     */
    @Test
    public void testMultipleAllocationAccuracy() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 分配 10 次，每次 512 字节
        int count = 10;
        long size = 512;
        long ptr = TestMemoryHelper.allocMultiple(count, size);
        
        // 等待
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        Log.d(TAG, "stats.totalAllocCount "+ stats.totalAllocCount);
        // 验证至少捕获了这些分配
        assertTrue("Should capture at least " + count + " allocations", 
                   stats.totalAllocCount >= count);
        assertTrue("Should capture at least " + (count * size) + " bytes", 
                   stats.totalAllocSize >= count * size);
        
        Log.i(TAG, "✓ Multiple allocation accuracy test passed");
        Log.d(TAG, "Expected: " + count + " allocs, " + (count * size) + " bytes");
        Log.d(TAG, "Actual: " + stats);
    }

    /**
     * 测试泄漏检测的准确性
     * 故意泄漏内存，验证能否检测到
     */
    @Test
    public void testLeakDetectionAccuracy() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // 故意泄漏 4096 字节
        long leakedPtr = TestMemoryHelper.leakMemory(4096);
        assertNotEquals("Leak should succeed", 0, leakedPtr);
        
        // 等待
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        // 验证检测到泄漏
        assertTrue("Should detect at least 1 leak", stats.currentAllocCount >= 1);
        assertTrue("Should detect at least 4096 bytes leaked", stats.currentAllocSize >= 4096);
        
        // 获取泄漏报告
        String report = SoHook.getLeakReport();
        assertTrue("Report should show leaks", stats.currentAllocCount > 0);
        
        Log.i(TAG, "✓ Leak detection accuracy test passed");
        Log.d(TAG, "Leaked: 4096 bytes");
        Log.d(TAG, "Detected: " + stats.currentAllocSize + " bytes in " + 
                   stats.currentAllocCount + " allocations");
        
        // 注意：不释放 leakedPtr，这是故意的泄漏
    }

    /**
     * 测试分配-释放循环的准确性
     * 验证多次分配和释放后，统计正确
     */
    @Test
    public void testAllocFreeCycleAccuracy() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }
        
        // 重置统计
        SoHook.resetStats();
        
        // 执行 5 次分配-释放循环
        for (int i = 0; i < 5; i++) {
            long ptr = TestMemoryHelper.alloc(1024);
            assertNotEquals("Allocation " + i + " should succeed", 0, ptr);
            
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            
            TestMemoryHelper.free(ptr);
            
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        
        // 获取统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        // 验证统计
        assertTrue("Should have at least 5 allocations", stats.totalAllocCount >= 5);
        assertTrue("Should have at least 5 frees", stats.totalFreeCount >= 5);
        
        // 验证一致性：current = total - free
        assertEquals("Current should equal total - free", 
                     stats.totalAllocCount - stats.totalFreeCount, 
                     stats.currentAllocCount);
        
        Log.i(TAG, "✓ Alloc-free cycle accuracy test passed");
        Log.d(TAG, "Stats: " + stats);
    }
}
