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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * SoHook 内存泄漏检测测试
 * 测试内存泄漏检测、报告生成等功能
 */
@RunWith(AndroidJUnit4.class)
public class SoHookMemoryLeakTest {
    private static final String TAG = "SoHook-MemoryLeakTest";
    private Context context;
    private File testDir;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testDir = context.getExternalCacheDir();
        if (testDir == null) {
            testDir = context.getCacheDir();
        }
        
        // 初始化SoHook
        SoHook.init(true);
        SoHook.resetStats();
        
        Log.i(TAG, "Test setup completed, test dir: " + testDir);
    }

    @After
    public void tearDown() {
        SoHook.resetStats();
        
        // 清理测试文件
        if (testDir != null) {
            File[] files = testDir.listFiles((dir, name) -> name.startsWith("leak_report_test"));
            if (files != null) {
                for (File file : files) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted test file: " + file.getName());
                    }
                }
            }
        }
        
        Log.i(TAG, "Test teardown completed");
    }

    /**
     * 测试导出泄漏报告到文件
     */
    @Test
    public void testDumpLeakReport() throws IOException {
        File reportFile = new File(testDir, "leak_report_test.txt");
        
        // 删除已存在的文件
        if (reportFile.exists()) {
            reportFile.delete();
        }
        
        // 导出报告
        int result = SoHook.dumpLeakReport(reportFile.getAbsolutePath());
        assertEquals("Dump leak report should succeed", 0, result);
        
        // 验证文件已创建
        assertTrue("Report file should exist", reportFile.exists());
        assertTrue("Report file should not be empty", reportFile.length() > 0);
        
        // 读取文件内容验证
        byte[] buffer = new byte[(int) reportFile.length()];
        try (FileInputStream fis = new FileInputStream(reportFile)) {
            int bytesRead = fis.read(buffer);
            assertEquals("Should read entire file", reportFile.length(), bytesRead);
        }
        
        String content = new String(buffer);
        assertTrue("Report should contain header", content.contains("Memory Leak Report"));
        
        Log.i(TAG, "✓ Dump leak report test passed, file size: " + reportFile.length() + " bytes");
        
        // 清理
        reportFile.delete();
    }

    /**
     * 测试导出报告到无效路径
     */
    @Test
    public void testDumpLeakReportInvalidPath() {
        // 使用无效路径
        int result = SoHook.dumpLeakReport("/invalid/path/report.txt");
        assertEquals("Dump to invalid path should fail", -1, result);
        
        Log.i(TAG, "✓ Dump leak report invalid path test passed");
    }

    /**
     * 测试导出报告到null路径
     */
    @Test
    public void testDumpLeakReportNullPath() {
        int result = SoHook.dumpLeakReport(null);
        assertEquals("Dump to null path should fail", -1, result);
        
        Log.i(TAG, "✓ Dump leak report null path test passed");
    }

    /**
     * 测试导出报告到空字符串路径
     */
    @Test
    public void testDumpLeakReportEmptyPath() {
        int result = SoHook.dumpLeakReport("");
        assertEquals("Dump to empty path should fail", -1, result);
        
        Log.i(TAG, "✓ Dump leak report empty path test passed");
    }

    /**
     * 测试泄漏报告格式
     */
    @Test
    public void testLeakReportFormat() {
        String report = SoHook.getLeakReport();
        assertNotNull("Report should not be null", report);
        
        // 验证报告包含必要的部分
        assertTrue("Report should contain title", 
                   report.contains("Memory Leak Report"));
        assertTrue("Report should contain total allocations", 
                   report.contains("Total Allocations"));
        assertTrue("Report should contain total frees", 
                   report.contains("Total Frees"));
        assertTrue("Report should contain current leaks", 
                   report.contains("Current Leaks"));
        
        Log.i(TAG, "✓ Leak report format test passed");
        Log.d(TAG, "Report preview:\n" + report.substring(0, Math.min(500, report.length())));
    }

    /**
     * 测试Hook后的统计信息
     * 注意：此测试只验证 Hook API 不会导致崩溃，不验证实际捕获
     */
    @Test
    public void testStatsAfterHook() {
        // 重置统计
        SoHook.resetStats();
        
        // Hook 一个系统库（只测试 API）
        int hookResult = SoHook.hook(Collections.singletonList("libm.so"));
        assertEquals("Hook should succeed", 0, hookResult);
        
        // 等待一小段时间让Hook生效
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 获取统计信息
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertNotNull("Stats should not be null", stats);
        
        Log.i(TAG, "✓ Stats after hook test passed");
        Log.d(TAG, "Stats after hook: " + stats);
    }

    /**
     * 测试连续多次获取报告
     */
    @Test
    public void testMultipleGetLeakReport() {
        String report1 = SoHook.getLeakReport();
        assertNotNull("First report should not be null", report1);
        
        String report2 = SoHook.getLeakReport();
        assertNotNull("Second report should not be null", report2);
        
        String report3 = SoHook.getLeakReport();
        assertNotNull("Third report should not be null", report3);
        
        // 所有报告都应该包含基本信息
        assertTrue("All reports should contain header", 
                   report1.contains("Memory Leak Report") &&
                   report2.contains("Memory Leak Report") &&
                   report3.contains("Memory Leak Report"));
        
        Log.i(TAG, "✓ Multiple get leak report test passed");
    }

    /**
     * 测试重置后的泄漏报告
     */
    @Test
    public void testLeakReportAfterReset() {
        // 获取初始报告
        String reportBefore = SoHook.getLeakReport();
        assertNotNull("Report before reset should not be null", reportBefore);
        
        // 重置统计
        SoHook.resetStats();
        
        // 获取重置后的报告
        String reportAfter = SoHook.getLeakReport();
        assertNotNull("Report after reset should not be null", reportAfter);
        
        // 验证报告包含0泄漏信息
        assertTrue("Report after reset should show 0 leaks", 
                   reportAfter.contains("Current Leaks: 0"));
        
        Log.i(TAG, "✓ Leak report after reset test passed");
    }

    /**
     * 测试导出多个报告文件
     */
    @Test
    public void testDumpMultipleReports() throws IOException {
        File report1 = new File(testDir, "leak_report_test_1.txt");
        File report2 = new File(testDir, "leak_report_test_2.txt");
        File report3 = new File(testDir, "leak_report_test_3.txt");
        
        // 导出多个报告
        assertEquals("First dump should succeed", 0, 
                     SoHook.dumpLeakReport(report1.getAbsolutePath()));
        assertEquals("Second dump should succeed", 0, 
                     SoHook.dumpLeakReport(report2.getAbsolutePath()));
        assertEquals("Third dump should succeed", 0, 
                     SoHook.dumpLeakReport(report3.getAbsolutePath()));
        
        // 验证所有文件都已创建
        assertTrue("Report 1 should exist", report1.exists());
        assertTrue("Report 2 should exist", report2.exists());
        assertTrue("Report 3 should exist", report3.exists());
        
        assertTrue("Report 1 should not be empty", report1.length() > 0);
        assertTrue("Report 2 should not be empty", report2.length() > 0);
        assertTrue("Report 3 should not be empty", report3.length() > 0);
        
        Log.i(TAG, "✓ Dump multiple reports test passed");
        
        // 清理
        report1.delete();
        report2.delete();
        report3.delete();
    }

    /**
     * 测试覆盖已存在的报告文件
     */
    @Test
    public void testOverwriteExistingReport() throws IOException {
        File reportFile = new File(testDir, "leak_report_test_overwrite.txt");
        
        // 第一次导出
        int result1 = SoHook.dumpLeakReport(reportFile.getAbsolutePath());
        assertEquals("First dump should succeed", 0, result1);
        assertTrue("Report file should exist", reportFile.exists());
        long firstSize = reportFile.length();
        
        // 等待一小段时间
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // 第二次导出（覆盖）
        int result2 = SoHook.dumpLeakReport(reportFile.getAbsolutePath());
        assertEquals("Second dump should succeed", 0, result2);
        assertTrue("Report file should still exist", reportFile.exists());
        
        Log.i(TAG, "✓ Overwrite existing report test passed");
        Log.d(TAG, "First size: " + firstSize + ", Second size: " + reportFile.length());
        
        // 清理
        reportFile.delete();
    }
}
