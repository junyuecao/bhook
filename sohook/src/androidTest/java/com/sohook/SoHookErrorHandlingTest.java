package com.sohook;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * SoHook 错误处理和边界情况测试
 * 测试各种错误场景和边界条件
 */
@RunWith(AndroidJUnit4.class)
public class SoHookErrorHandlingTest {
    private static final String TAG = "SoHook-ErrorHandlingTest";
    private Context context;
    private File testDir;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testDir = context.getCacheDir();
        Log.i(TAG, "Test setup completed");
    }

    @After
    public void tearDown() {
        // 确保清理
        try {
            SoHook.unhookAll();
        } catch (Exception e) {
            Log.w(TAG, "Failed to unhook in tearDown", e);
        }
        Log.i(TAG, "Test teardown completed");
    }

    /**
     * 测试hook不存在的so库
     */
    @Test
    public void testHookNonExistentLibrary() {
        SoHook.init(true);
        
        // Hook一个不存在的库
        int result = SoHook.hook(Collections.singletonList("lib_nonexistent_so.so"));
        // 可能返回0（API成功但实际未hook）或错误码
        Log.i(TAG, "Hook non-existent library result: " + result);
        
        // 验证不会崩溃
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertNotNull("Stats should not be null", stats);
        
        Log.i(TAG, "✓ Hook non-existent library test passed");
    }

    /**
     * 测试unhook未hook的库
     */
    @Test
    public void testUnhookNotHookedLibrary() {
        SoHook.init(true);
        
        // 尝试unhook一个未hook的库
        int result = SoHook.unhook(Collections.singletonList("libm.so"));
        // 可能返回0（成功）或错误码，取决于实现
        Log.i(TAG, "Unhook not-hooked library result: " + result);
        
        // 验证不会崩溃
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertNotNull("Stats should not be null", stats);
        
        Log.i(TAG, "✓ Unhook not-hooked library test passed");
    }

    /**
     * 测试dumpFdLeakReport到无效路径
     */
    @Test
    public void testDumpFdLeakReportInvalidPath() {
        SoHook.init(true);
        
        // 使用无效路径
        int result = SoHook.dumpFdLeakReport("/invalid/path/fd_report.txt");
        assertEquals("Dump to invalid path should fail", -1, result);
        
        Log.i(TAG, "✓ Dump FD leak report invalid path test passed");
    }

    /**
     * 测试dumpFdLeakReport到null路径
     */
    @Test
    public void testDumpFdLeakReportNullPath() {
        SoHook.init(true);
        
        int result = SoHook.dumpFdLeakReport(null);
        assertEquals("Dump to null path should fail", -1, result);
        
        Log.i(TAG, "✓ Dump FD leak report null path test passed");
    }

    /**
     * 测试dumpFdLeakReport到空字符串路径
     */
    @Test
    public void testDumpFdLeakReportEmptyPath() {
        SoHook.init(true);
        
        int result = SoHook.dumpFdLeakReport("");
        assertEquals("Dump to empty path should fail", -1, result);
        
        Log.i(TAG, "✓ Dump FD leak report empty path test passed");
    }

    /**
     * 测试FdStats的toString方法
     */
    @Test
    public void testFdStatsToString() {
        SoHook.FdStats stats = new SoHook.FdStats(100, 50, 50);
        
        String str = stats.toString();
        assertNotNull("toString should not return null", str);
        assertTrue("toString should contain totalOpenCount", str.contains("totalOpenCount=100"));
        assertTrue("toString should contain totalCloseCount", str.contains("totalCloseCount=50"));
        assertTrue("toString should contain currentOpenCount", str.contains("currentOpenCount=50"));
        
        Log.i(TAG, "✓ FdStats toString test passed: " + str);
    }

    /**
     * 测试FdStats的构造函数
     */
    @Test
    public void testFdStatsConstructor() {
        // 测试无参构造函数
        SoHook.FdStats stats1 = new SoHook.FdStats();
        assertEquals("Default totalOpenCount should be 0", 0, stats1.totalOpenCount);
        assertEquals("Default totalCloseCount should be 0", 0, stats1.totalCloseCount);
        assertEquals("Default currentOpenCount should be 0", 0, stats1.currentOpenCount);
        
        // 测试有参构造函数
        SoHook.FdStats stats2 = new SoHook.FdStats(10, 5, 5);
        assertEquals("Constructor totalOpenCount should be 10", 10, stats2.totalOpenCount);
        assertEquals("Constructor totalCloseCount should be 5", 5, stats2.totalCloseCount);
        assertEquals("Constructor currentOpenCount should be 5", 5, stats2.currentOpenCount);
        
        Log.i(TAG, "✓ FdStats constructor test passed");
    }

    /**
     * 测试MemoryStats的构造函数
     */
    @Test
    public void testMemoryStatsConstructor() {
        // 测试无参构造函数
        SoHook.MemoryStats stats1 = new SoHook.MemoryStats();
        assertEquals("Default totalAllocCount should be 0", 0, stats1.totalAllocCount);
        assertEquals("Default totalAllocSize should be 0", 0, stats1.totalAllocSize);
        
        // 测试有参构造函数
        SoHook.MemoryStats stats2 = new SoHook.MemoryStats(100, 1024, 50, 512, 50, 512);
        assertEquals("Constructor totalAllocCount should be 100", 100, stats2.totalAllocCount);
        assertEquals("Constructor totalAllocSize should be 1024", 1024, stats2.totalAllocSize);
        assertEquals("Constructor totalFreeCount should be 50", 50, stats2.totalFreeCount);
        assertEquals("Constructor totalFreeSize should be 512", 512, stats2.totalFreeSize);
        assertEquals("Constructor currentAllocCount should be 50", 50, stats2.currentAllocCount);
        assertEquals("Constructor currentAllocSize should be 512", 512, stats2.currentAllocSize);
        
        Log.i(TAG, "✓ MemoryStats constructor test passed");
    }

    /**
     * 测试未初始化时调用各种方法
     * 注意：由于静态变量sInitialized在测试套件中可能已被其他测试初始化，
     * 此测试主要验证API不会崩溃，而不是严格测试未初始化状态
     */
    @Test
    public void testUninitializedAccess() {
        // 测试各种方法在未初始化时的行为（或已初始化时的行为）
        // 这些方法应该不会崩溃，无论是否初始化
        
        String report = SoHook.getLeakReport();
        assertNotNull("Leak report should not be null", report);
        
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertNotNull("Stats should not be null", stats);
        
        SoHook.FdStats fdStats = SoHook.getFdStats();
        assertNotNull("FD stats should not be null", fdStats);
        
        String fdReport = SoHook.getFdLeakReport();
        assertNotNull("FD leak report should not be null", fdReport);
        
        // 尝试hook/unhook，验证不会崩溃
        // 注意：如果已经初始化，hook可能成功；如果未初始化，应该返回-1
        int hookResult = SoHook.hook(Collections.singletonList("libm.so"));
        // hookResult可能是0（已初始化且hook成功）或-1（未初始化或hook失败）
        assertTrue("Hook result should be valid (0 or -1)", hookResult == 0 || hookResult == -1);
        
        int unhookResult = SoHook.unhook(Collections.singletonList("libm.so"));
        // unhookResult可能是0（已初始化且unhook成功）或-1（未初始化或unhook失败）
        assertTrue("Unhook result should be valid (0 or -1)", unhookResult == 0 || unhookResult == -1);
        
        Log.i(TAG, "✓ Uninitialized access test passed (hookResult=" + hookResult + 
                   ", unhookResult=" + unhookResult + ")");
    }

    /**
     * 测试hook多个库后unhook部分库
     */
    @Test
    public void testHookMultipleThenUnhookPartial() {
        SoHook.init(true);
        
        // Hook多个库
        int result1 = SoHook.hook(Arrays.asList("libm.so", "libdl.so", "liblog.so"));
        assertEquals("Hook multiple libraries should succeed", 0, result1);
        
        // Unhook部分库
        int result2 = SoHook.unhook(Collections.singletonList("libm.so"));
        assertTrue("Unhook partial should succeed", result2 == 0);
        
        // 验证不会崩溃
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertNotNull("Stats should not be null", stats);
        
        Log.i(TAG, "✓ Hook multiple then unhook partial test passed");
    }

    /**
     * 测试重复hook同一个库
     */
    @Test
    public void testRepeatedHookSameLibrary() {
        SoHook.init(true);
        
        // 第一次hook
        int result1 = SoHook.hook(Collections.singletonList("libm.so"));
        assertTrue("First hook should succeed", result1 == 0 || result1 == -2);
        
        // 第二次hook同一个库
        int result2 = SoHook.hook(Collections.singletonList("libm.so"));
        assertTrue("Second hook should succeed (may be already hooked)", 
                   result2 == 0 || result2 == -2);
        
        // 验证不会崩溃
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertNotNull("Stats should not be null", stats);
        
        Log.i(TAG, "✓ Repeated hook same library test passed");
    }

    /**
     * 测试hook空字符串列表
     */
    @Test
    public void testHookWithEmptyStringInList() {
        SoHook.init(true);
        
        // Hook包含空字符串的列表
        int result = SoHook.hook(Arrays.asList("libm.so", "", "libdl.so"));
        // 可能返回0（忽略空字符串）或错误码
        Log.i(TAG, "Hook with empty string result: " + result);
        
        // 验证不会崩溃
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertNotNull("Stats should not be null", stats);
        
        Log.i(TAG, "✓ Hook with empty string test passed");
    }

    /**
     * 测试hook包含null的列表
     */
    @Test
    public void testHookWithNullInList() {
        SoHook.init(true);
        
        // 注意：Java不允许List中包含null，但我们可以测试其他边界情况
        // 这里测试null列表本身
        int result = SoHook.hook(null);
        assertEquals("Hook null list should fail", -1, result);
        
        Log.i(TAG, "✓ Hook null list test passed");
    }

    /**
     * 测试init时enableBacktrace参数
     */
    @Test
    public void testInitWithBacktraceEnabled() {
        // 测试初始化时启用backtrace
        int result1 = SoHook.init(true, true);
        assertTrue("Init with backtrace enabled should succeed", result1 == 0 || result1 == -2);
        
        boolean enabled1 = SoHook.isBacktraceEnabled();
        // 注意：初始化时的backtrace状态可能不同，这里只验证不会崩溃
        Log.i(TAG, "Backtrace enabled after init(true, true): " + enabled1);
        
        // 测试初始化时禁用backtrace
        int result2 = SoHook.init(true, false);
        assertTrue("Init with backtrace disabled should succeed", result2 == 0 || result2 == -2);
        
        boolean enabled2 = SoHook.isBacktraceEnabled();
        Log.i(TAG, "Backtrace enabled after init(true, false): " + enabled2);
        
        Log.i(TAG, "✓ Init with backtrace parameter test passed");
    }

    /**
     * 测试大量泄漏时的报告生成
     */
    @Test
    public void testLargeLeakReportGeneration() {
        SoHook.init(true);
        SoHook.resetStats();
        
        // 获取初始报告
        String report1 = SoHook.getLeakReport();
        assertNotNull("Initial report should not be null", report1);
        
        // 模拟大量泄漏（通过多次获取报告，实际泄漏由其他测试产生）
        for (int i = 0; i < 100; i++) {
            String report = SoHook.getLeakReport();
            assertNotNull("Report " + i + " should not be null", report);
            assertFalse("Report " + i + " should not be empty", report.isEmpty());
        }
        
        Log.i(TAG, "✓ Large leak report generation test passed");
    }

    /**
     * 测试统计信息的边界值
     */
    @Test
    public void testStatsBoundaryValues() {
        SoHook.init(true);
        SoHook.resetStats();
        
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        // 验证所有统计字段都是非负数
        assertTrue("totalAllocCount should be >= 0", stats.totalAllocCount >= 0);
        assertTrue("totalAllocSize should be >= 0", stats.totalAllocSize >= 0);
        assertTrue("totalFreeCount should be >= 0", stats.totalFreeCount >= 0);
        assertTrue("totalFreeSize should be >= 0", stats.totalFreeSize >= 0);
        assertTrue("currentAllocCount should be >= 0", stats.currentAllocCount >= 0);
        assertTrue("currentAllocSize should be >= 0", stats.currentAllocSize >= 0);
        
        // 验证一致性
        assertTrue("currentAllocCount should <= totalAllocCount", 
                   stats.currentAllocCount <= stats.totalAllocCount);
        assertTrue("currentAllocSize should <= totalAllocSize", 
                   stats.currentAllocSize <= stats.totalAllocSize);
        assertTrue("totalFreeCount should <= totalAllocCount", 
                   stats.totalFreeCount <= stats.totalAllocCount);
        assertTrue("totalFreeSize should <= totalAllocSize", 
                   stats.totalFreeSize <= stats.totalAllocSize);
        
        Log.i(TAG, "✓ Stats boundary values test passed");
    }

    /**
     * 测试FD统计信息的边界值
     */
    @Test
    public void testFdStatsBoundaryValues() {
        SoHook.init(true);
        SoHook.resetFdStats();
        
        SoHook.FdStats stats = SoHook.getFdStats();
        
        // 验证所有统计字段都是非负数
        assertTrue("totalOpenCount should be >= 0", stats.totalOpenCount >= 0);
        assertTrue("totalCloseCount should be >= 0", stats.totalCloseCount >= 0);
        assertTrue("currentOpenCount should be >= 0", stats.currentOpenCount >= 0);
        
        // 验证一致性
        assertTrue("currentOpenCount should <= totalOpenCount", 
                   stats.currentOpenCount <= stats.totalOpenCount);
        assertTrue("totalCloseCount should <= totalOpenCount", 
                   stats.totalCloseCount <= stats.totalOpenCount);
        
        Log.i(TAG, "✓ FD stats boundary values test passed");
    }

    /**
     * 测试连续多次unhookAll
     */
    @Test
    public void testRepeatedUnhookAll() {
        SoHook.init(true);
        
        // Hook一些库
        SoHook.hook(Arrays.asList("libm.so", "libdl.so"));
        
        // 多次unhookAll
        for (int i = 0; i < 5; i++) {
            int result = SoHook.unhookAll();
            assertTrue("UnhookAll " + i + " should succeed", result == 0);
        }
        
        // 验证不会崩溃
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertNotNull("Stats should not be null", stats);
        
        Log.i(TAG, "✓ Repeated unhookAll test passed");
    }

    /**
     * 测试在未hook的情况下调用unhookAll
     */
    @Test
    public void testUnhookAllWhenNothingHooked() {
        SoHook.init(true);
        
        // 直接调用unhookAll（没有hook任何库）
        int result = SoHook.unhookAll();
        assertTrue("UnhookAll when nothing hooked should succeed", result == 0);
        
        Log.i(TAG, "✓ UnhookAll when nothing hooked test passed");
    }
}

