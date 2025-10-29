package com.sohook;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

import com.sohook.test.TestMemoryHelper;

/**
 * SoHook 基础功能测试
 * 测试初始化、Hook、统计等基本功能
 */
@RunWith(AndroidJUnit4.class)
public class SoHookBasicTest {
    private static final String TAG = "SoHook-BasicTest";
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Log.i(TAG, "Test setup completed");
        // 检查测试库是否加载
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "test_memory library not loaded, some tests may be skipped");
            return;
        }
    }

    @After
    public void tearDown() {
        // 重置统计信息
        try {
            SoHook.resetStats();
        } catch (Exception e) {
            Log.w(TAG, "Failed to reset stats in tearDown", e);
        }
        Log.i(TAG, "Test teardown completed");
    }

    /**
     * 测试库加载
     */
    @Test
    public void testLibraryLoaded() {
        // 如果库加载失败，init会返回错误
        int result = SoHook.init(true);
        assertTrue("Library should be loaded", result == 0 || result == -2); // -2 表示已初始化
        Log.i(TAG, "✓ Library loaded successfully");
    }

    /**
     * 测试初始化功能
     */
    @Test
    public void testInit() {
        int result = SoHook.init(true);
        assertTrue("Init should succeed or already initialized", result == 0 || result == -2);
        Log.i(TAG, "✓ Init test passed");
    }

    /**
     * 测试栈回溯功能
     * 注意：此测试验证栈回溯开关功能，而不是初始化时的栈回溯参数
     */
    @Test
    public void testBacktraceFeature() {
        // 先确保已初始化
        SoHook.init(true);

        // 测试启用栈回溯
        SoHook.setBacktraceEnabled(true);
        boolean enabled = SoHook.isBacktraceEnabled();
        Log.d(TAG, "Backtrace enabled: " + enabled);
        assertTrue("Backtrace enable should work", enabled);
        // 测试禁用栈回溯
        SoHook.setBacktraceEnabled(false);
        boolean disabled = !SoHook.isBacktraceEnabled();
        Log.d(TAG, "Backtrace disabled: " + disabled);
        assertTrue("Backtrace disable should work", disabled);

        Log.i(TAG, "✓ Backtrace feature test passed");
    }

    /**
     * 测试重复初始化
     */
    @Test
    public void testDoubleInit() {
        int result1 = SoHook.init(true);
        assertTrue("First init should succeed", result1 == 0 || result1 == -2);

        int result2 = SoHook.init(true);
        assertTrue("Second init should succeed (already initialized)",
                   result2 == 0 || result2 == -2);
        Log.i(TAG, "✓ Double init test passed");
    }

    /**
     * 测试Hook功能
     * 注意：此测试验证 Hook API 的基本功能，不验证实际捕获
     */
    @Test
    public void testHook() {
        SoHook.init(true);

        // Hook 一个存在的系统库（只测试 API 是否工作）
        int result = SoHook.hook(Collections.singletonList("libtest_memory.so"));
        assertEquals("Hook should succeed", 0, result);
        Log.i(TAG, "✓ Hook test passed");

        // 测试 Hook 是否生效：分配一小块内存
        Log.i(TAG, "Testing if hook is active...");
        long testPtr = TestMemoryHelper.alloc(32000);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        SoHook.MemoryStats testStats = SoHook.getMemoryStats();
        Log.i(TAG, "Test allocation stats: totalAllocCount=" + testStats.totalAllocCount);
        assertTrue("Total allocation should be greater than 0", testStats.totalAllocCount > 0);
        if (testPtr != 0) {
            TestMemoryHelper.free(testPtr);
        }
    }

    /**
     * 测试Hook多个库
     */
    @Test
    public void testHookMultiple() {
        SoHook.init(true);

        // Hook多个系统库（只测试 API 是否工作）
        int result = SoHook.hook(Arrays.asList("libm.so", "libdl.so", "liblog.so"));
        assertEquals("Hook multiple libraries should succeed", 0, result);
        Log.i(TAG, "✓ Hook multiple test passed");
    }

    /**
     * 测试Hook空列表
     */
    @Test
    public void testHookEmptyList() {
        SoHook.init(true);

        int result = SoHook.hook(Collections.<String>emptyList());
        assertEquals("Hook empty list should fail", -1, result);
        Log.i(TAG, "✓ Hook empty list test passed");
    }

    /**
     * 测试Hook null
     */
    @Test
    public void testHookNull() {
        SoHook.init(true);

        int result = SoHook.hook(null);
        assertEquals("Hook null should fail", -1, result);
        Log.i(TAG, "✓ Hook null test passed");
    }

    /**
     * 测试 unhook 功能
     */
    @Test
    public void testUnhook() {
        SoHook.init(true);
        SoHook.hook(Collections.singletonList("libm.so"));

        int result = SoHook.unhook(Collections.singletonList("libm.so"));
        assertTrue("Unhook should succeed", result == 0);
        Log.i(TAG, "✓ Unhook test passed");
    }

    /**
     * 测试 unhookAll 功能
     */
    @Test
    public void testUnhookAll() {
        SoHook.init(true);

        // Hook 多个库
        SoHook.hook(Arrays.asList("libm.so", "libdl.so", "liblog.so"));

        // Unhook 所有
        int result = SoHook.unhookAll();
        assertTrue("UnhookAll should succeed", result == 0);
        Log.i(TAG, "✓ UnhookAll test passed");
    }

    /**
     * 测试获取内存统计信息
     */
    @Test
    public void testGetMemoryStats() {
        SoHook.init(true);

        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertNotNull("Stats should not be null", stats);

        // 验证统计字段存在
        assertTrue("Total alloc count should be >= 0", stats.totalAllocCount >= 0);
        assertTrue("Total alloc size should be >= 0", stats.totalAllocSize >= 0);
        assertTrue("Total free count should be >= 0", stats.totalFreeCount >= 0);
        assertTrue("Total free size should be >= 0", stats.totalFreeSize >= 0);
        assertTrue("Current alloc count should be >= 0", stats.currentAllocCount >= 0);
        assertTrue("Current alloc size should be >= 0", stats.currentAllocSize >= 0);

        Log.i(TAG, "✓ Get memory stats test passed: " + stats);
    }

    /**
     * 测试重置统计信息
     */
    @Test
    public void testResetStats() {
        SoHook.init(true);

        // 先获取统计信息
        SoHook.MemoryStats statsBefore = SoHook.getMemoryStats();
        assertNotNull("Stats before reset should not be null", statsBefore);

        // 重置
        SoHook.resetStats();

        // 再次获取统计信息
        SoHook.MemoryStats statsAfter = SoHook.getMemoryStats();
        assertNotNull("Stats after reset should not be null", statsAfter);

        // 验证统计信息已重置
        assertEquals("Total alloc count should be 0 after reset", 0, statsAfter.totalAllocCount);
        assertEquals("Total alloc size should be 0 after reset", 0, statsAfter.totalAllocSize);
        assertEquals("Total free count should be 0 after reset", 0, statsAfter.totalFreeCount);
        assertEquals("Total free size should be 0 after reset", 0, statsAfter.totalFreeSize);

        Log.i(TAG, "✓ Reset stats test passed");
    }

    /**
     * 测试获取泄漏报告
     */
    @Test
    public void testGetLeakReport() {
        SoHook.init(true);

        String report = SoHook.getLeakReport();
        assertNotNull("Leak report should not be null", report);
        assertFalse("Leak report should not be empty", report.isEmpty());
        assertTrue("Leak report should contain header",
                   report.contains("Memory Leak Report"));

        Log.i(TAG, "✓ Get leak report test passed");
        Log.d(TAG, "Leak report preview:\n" + report.substring(0, Math.min(200, report.length())));
    }

    /**
     * 测试栈回溯开关
     */
    @Test
    public void testBacktraceToggle() {
        SoHook.init(true, false);

        // 确保初始状态为禁用
        SoHook.setBacktraceEnabled(false);
        assertFalse("Backtrace should be disabled initially",
                    SoHook.isBacktraceEnabled());

        // 启用栈回溯
        SoHook.setBacktraceEnabled(true);
        assertTrue("Backtrace should be enabled after setBacktraceEnabled(true)",
                   SoHook.isBacktraceEnabled());

        // 禁用栈回溯
        SoHook.setBacktraceEnabled(false);
        assertFalse("Backtrace should be disabled after setBacktraceEnabled(false)",
                    SoHook.isBacktraceEnabled());

        Log.i(TAG, "✓ Backtrace toggle test passed");
    }

    /**
     * 测试MemoryStats的toString方法
     */
    @Test
    public void testMemoryStatsToString() {
        SoHook.MemoryStats stats = new SoHook.MemoryStats(100, 1024, 50, 512, 50, 512);

        String str = stats.toString();
        assertNotNull("toString should not return null", str);
        assertTrue("toString should contain totalAllocCount", str.contains("totalAllocCount=100"));
        assertTrue("toString should contain totalAllocSize", str.contains("totalAllocSize=1024"));
        assertTrue("toString should contain currentAllocCount", str.contains("currentAllocCount=50"));

        Log.i(TAG, "✓ MemoryStats toString test passed: " + str);
    }

    /**
     * 测试未初始化时调用API
     */
    @Test
    public void testUninitializedAccess() {
        // 注意：由于static初始化，这个测试可能不会真正测试未初始化状态
        // 但我们仍然可以验证API不会崩溃

        String report = SoHook.getLeakReport();
        assertNotNull("Leak report should not be null even if uninitialized", report);

        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertNotNull("Stats should not be null even if uninitialized", stats);

        Log.i(TAG, "✓ Uninitialized access test passed");
    }

    /**
     * 测试 realloc 基本功能
     * 验证 realloc 能够正确调整内存大小并更新统计信息
     */
    @Test
    public void testRealloc() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testRealloc ===");

        // 初始化并 Hook
        SoHook.init(true);
        SoHook.hook(Collections.singletonList("libtest_memory.so"));
        SoHook.resetStats();

        // 分配初始内存
        long ptr = TestMemoryHelper.alloc(1024);
        assertTrue("Initial allocation should succeed", ptr != 0);

        // 检查初始统计
        SoHook.MemoryStats stats1 = SoHook.getMemoryStats();
        assertEquals("Should have 1 allocation", 1, stats1.currentAllocCount);
        assertEquals("Should have 1024 bytes allocated", 1024, stats1.currentAllocSize);
        Log.i(TAG, "Initial allocation: count=" + stats1.currentAllocCount + 
                   ", size=" + stats1.currentAllocSize);

        // 扩大内存 (1024 -> 2048)
        long newPtr = TestMemoryHelper.realloc(ptr, 2048);
        assertTrue("Realloc should succeed", newPtr != 0);

        // 检查扩大后的统计
        SoHook.MemoryStats stats2 = SoHook.getMemoryStats();
        assertEquals("Should still have 1 allocation", 1, stats2.currentAllocCount);
        assertEquals("Should have 2048 bytes allocated", 2048, stats2.currentAllocSize);
        Log.i(TAG, "After realloc (expand): count=" + stats2.currentAllocCount + 
                   ", size=" + stats2.currentAllocSize);

        // 缩小内存 (2048 -> 512)
        long shrinkPtr = TestMemoryHelper.realloc(newPtr, 512);
        assertTrue("Realloc shrink should succeed", shrinkPtr != 0);

        // 检查缩小后的统计
        SoHook.MemoryStats stats3 = SoHook.getMemoryStats();
        assertEquals("Should still have 1 allocation", 1, stats3.currentAllocCount);
        assertEquals("Should have 512 bytes allocated", 512, stats3.currentAllocSize);
        Log.i(TAG, "After realloc (shrink): count=" + stats3.currentAllocCount + 
                   ", size=" + stats3.currentAllocSize);

        // 释放内存
        TestMemoryHelper.free(shrinkPtr);

        // 检查释放后的统计
        SoHook.MemoryStats stats4 = SoHook.getMemoryStats();
        assertEquals("Should have no allocations", 0, stats4.currentAllocCount);
        assertEquals("Should have no allocated bytes", 0, stats4.currentAllocSize);
        Log.i(TAG, "After free: count=" + stats4.currentAllocCount + 
                   ", size=" + stats4.currentAllocSize);

        Log.i(TAG, "✓ Realloc test passed");
    }

    /**
     * 测试 realloc 从 NULL 指针分配
     * realloc(NULL, size) 应该等同于 malloc(size)
     */
    @Test
    public void testReallocFromNull() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testReallocFromNull ===");

        // 初始化并 Hook
        SoHook.init(true);
        SoHook.hook(Collections.singletonList("libtest_memory.so"));
        SoHook.resetStats();

        // realloc(NULL, size) 应该等同于 malloc(size)
        long ptr = TestMemoryHelper.realloc(0, 1024);
        assertTrue("Realloc from NULL should succeed", ptr != 0);

        // 检查统计
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have 1 allocation", 1, stats.currentAllocCount);
        assertEquals("Should have 1024 bytes allocated", 1024, stats.currentAllocSize);
        Log.i(TAG, "Realloc from NULL: count=" + stats.currentAllocCount + 
                   ", size=" + stats.currentAllocSize);

        // 释放内存
        TestMemoryHelper.free(ptr);

        // 验证释放
        SoHook.MemoryStats stats2 = SoHook.getMemoryStats();
        assertEquals("Should have no allocations", 0, stats2.currentAllocCount);

        Log.i(TAG, "✓ Realloc from NULL test passed");
    }

    /**
     * 测试 realloc 到大小为 0
     * realloc(ptr, 0) 应该等同于 free(ptr)
     */
    @Test
    public void testReallocToZero() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testReallocToZero ===");

        // 初始化并 Hook
        SoHook.init(true);
        SoHook.hook(Collections.singletonList("libtest_memory.so"));
        SoHook.resetStats();

        // 先分配内存
        long ptr = TestMemoryHelper.alloc(1024);
        assertTrue("Initial allocation should succeed", ptr != 0);

        // 检查初始统计
        SoHook.MemoryStats stats1 = SoHook.getMemoryStats();
        assertEquals("Should have 1 allocation", 1, stats1.currentAllocCount);
        Log.i(TAG, "Before realloc to zero: count=" + stats1.currentAllocCount);

        // realloc(ptr, 0) 应该等同于 free(ptr)
        long result = TestMemoryHelper.realloc(ptr, 0);
        Log.i(TAG, "Realloc to zero returned: " + result);

        // 检查统计 - 应该已经释放
        SoHook.MemoryStats stats2 = SoHook.getMemoryStats();
        assertEquals("Should have no allocations after realloc to zero", 
                    0, stats2.currentAllocCount);
        assertEquals("Should have no allocated bytes", 0, stats2.currentAllocSize);
        Log.i(TAG, "After realloc to zero: count=" + stats2.currentAllocCount);

        Log.i(TAG, "✓ Realloc to zero test passed");
    }

    /**
     * 测试 realloc 多次调整
     * 验证连续多次 realloc 的统计准确性
     */
    @Test
    public void testReallocMultipleTimes() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testReallocMultipleTimes ===");

        // 初始化并 Hook
        SoHook.init(true);
        SoHook.hook(Collections.singletonList("libtest_memory.so"));
        SoHook.resetStats();

        // 初始分配
        long ptr = TestMemoryHelper.alloc(256);
        assertTrue("Initial allocation should succeed", ptr != 0);
        Log.i(TAG, "Initial: 256 bytes");

        // 多次 realloc
        long[] sizes = {512, 1024, 2048, 4096, 2048, 1024, 512};
        for (long size : sizes) {
            ptr = TestMemoryHelper.realloc(ptr, size);
            assertTrue("Realloc to " + size + " should succeed", ptr != 0);

            SoHook.MemoryStats stats = SoHook.getMemoryStats();
            assertEquals("Should still have 1 allocation", 1, stats.currentAllocCount);
            assertEquals("Should have " + size + " bytes", size, stats.currentAllocSize);
            Log.i(TAG, "Realloc to " + size + " bytes: count=" + stats.currentAllocCount + 
                       ", size=" + stats.currentAllocSize);
        }

        // 最终释放
        TestMemoryHelper.free(ptr);

        // 验证释放
        SoHook.MemoryStats finalStats = SoHook.getMemoryStats();
        assertEquals("Should have no allocations", 0, finalStats.currentAllocCount);
        assertEquals("Should have no allocated bytes", 0, finalStats.currentAllocSize);

        Log.i(TAG, "✓ Realloc multiple times test passed");
    }

    /**
     * 测试 realloc 后的堆栈捕获
     * 验证 realloc 后能够正确捕获调用栈信息
     */
    @Test
    public void testReallocWithBacktrace() {
        if (!TestMemoryHelper.isLibraryLoaded()) {
            Log.w(TAG, "Skipping test: test_memory library not loaded");
            return;
        }

        Log.i(TAG, "=== testReallocWithBacktrace ===");

        // 初始化并启用 backtrace
        SoHook.init(true, true);
        SoHook.setBacktraceEnabled(true);
        assertTrue("Backtrace should be enabled", SoHook.isBacktraceEnabled());

        // Hook 测试库
        SoHook.hook(Collections.singletonList("libtest_memory.so"));
        SoHook.resetStats();

        // 分配初始内存
        long ptr = TestMemoryHelper.alloc(1024);
        assertTrue("Initial allocation should succeed", ptr != 0);
        Log.i(TAG, "Initial allocation: 1024 bytes");

        // 执行 realloc 扩大内存
        long newPtr = TestMemoryHelper.realloc(ptr, 4096);
        assertTrue("Realloc should succeed", newPtr != 0);
        Log.i(TAG, "Realloc to 4096 bytes");

        // 获取 JSON 格式的泄漏列表（包含 backtrace）
        String json = SoHook.nativeGetLeaksJson();
        assertNotNull("JSON should not be null", json);
        Log.i(TAG, "Leaks JSON length: " + json.length() + " bytes");

        // 验证 JSON 包含 backtrace 信息
        assertTrue("JSON should contain backtrace field", json.contains("backtrace"));
        assertTrue("JSON should contain size field", json.contains("\"size\":4096"));

        // 获取文本格式的泄漏报告
        String report = SoHook.getLeakReport();
        assertNotNull("Leak report should not be null", report);
        Log.i(TAG, "Leak report preview:\n" + 
                   report.substring(0, Math.min(500, report.length())));

        // 验证报告包含调用栈信息
        assertTrue("Report should contain backtrace or stack info",
                   report.contains("backtrace") || 
                   report.contains("Backtrace") || 
                   report.contains("#") ||
                   report.contains("at "));

        // 再次 realloc 缩小内存
        long shrinkPtr = TestMemoryHelper.realloc(newPtr, 2048);
        assertTrue("Realloc shrink should succeed", shrinkPtr != 0);
        Log.i(TAG, "Realloc to 2048 bytes");

        // 获取聚合后的 JSON（应该只有一个组，因为是同一个指针）
        String aggregatedJson = SoHook.nativeGetLeaksAggregatedJson();
        assertNotNull("Aggregated JSON should not be null", aggregatedJson);
        Log.i(TAG, "Aggregated JSON: " + aggregatedJson);

        // 验证聚合 JSON 包含正确的信息
        assertTrue("Aggregated JSON should contain count field", 
                   aggregatedJson.contains("\"count\":1"));
        assertTrue("Aggregated JSON should contain totalSize field", 
                   aggregatedJson.contains("\"totalSize\":2048"));
        assertTrue("Aggregated JSON should contain backtrace field", 
                   aggregatedJson.contains("backtrace"));

        // 检查统计信息
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("Should have 1 allocation", 1, stats.currentAllocCount);
        assertEquals("Should have 2048 bytes", 2048, stats.currentAllocSize);
        Log.i(TAG, "Stats: count=" + stats.currentAllocCount + 
                   ", size=" + stats.currentAllocSize);

        // 释放内存
        TestMemoryHelper.free(shrinkPtr);

        // 验证释放后无泄漏
        SoHook.MemoryStats finalStats = SoHook.getMemoryStats();
        assertEquals("Should have no allocations", 0, finalStats.currentAllocCount);

        // 禁用 backtrace
        SoHook.setBacktraceEnabled(false);

        Log.i(TAG, "✓ Realloc with backtrace test passed");
    }
}
