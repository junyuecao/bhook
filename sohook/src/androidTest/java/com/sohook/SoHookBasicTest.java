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
}
