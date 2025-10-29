package com.sohook;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.sohook.test.TestMemoryCpp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * SoHook leak_json 模块测试
 * 测试 JSON 格式的内存泄漏报告生成功能
 */
@RunWith(AndroidJUnit4.class)
public class SoHookLeakJsonTest {
    private static final String TAG = "SoHookLeakJsonTest";
    private static final String TEST_LIB = "libtest_memory.so";

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
        
        // Unhook
        SoHook.unhookAll();
        
        Log.i(TAG, "Teardown completed");
    }

    @Test
    public void testGetLeaksJsonEmpty() throws JSONException {
        Log.i(TAG, "=== testGetLeaksJsonEmpty ===");
        
        // 没有泄漏时应该返回空数组
        String json = SoHook.nativeGetLeaksJson();
        assertNotNull("JSON should not be null", json);
        
        JSONArray leaks = new JSONArray(json);
        assertEquals("Should have no leaks", 0, leaks.length());
        
        Log.i(TAG, "Empty leaks JSON: " + json);
        Log.i(TAG, "testGetLeaksJsonEmpty passed");
    }

    @Test
    public void testGetLeaksJsonWithSingleLeak() throws JSONException {
        Log.i(TAG, "=== testGetLeaksJsonWithSingleLeak ===");
        
        // 创建一个泄漏
        long ptr = TestMemoryCpp.nativeLeakNew(1024);
        assertTrue("Leak allocation should succeed", ptr != 0);
        
        // 获取 JSON 格式的泄漏列表
        String json = SoHook.nativeGetLeaksJson();
        assertNotNull("JSON should not be null", json);
        Log.i(TAG, "Leaks JSON: " + json);
        
        // 解析 JSON
        JSONArray leaks = new JSONArray(json);
        assertEquals("Should have 1 leak", 1, leaks.length());
        
        // 检查第一个泄漏的字段
        JSONObject leak = leaks.getJSONObject(0);
        assertTrue("Should have ptr field", leak.has("ptr"));
        assertTrue("Should have size field", leak.has("size"));
        assertTrue("Should have backtrace field", leak.has("backtrace"));
        
        // 检查 size 值
        long size = leak.getLong("size");
        assertEquals("Size should be 1024", 1024, size);
        
        // 检查 backtrace 数组
        JSONArray backtrace = leak.getJSONArray("backtrace");
        assertTrue("Backtrace should not be empty", backtrace.length() > 0);
        
        Log.i(TAG, "Leak details: ptr=" + leak.getString("ptr") + 
                   ", size=" + size + 
                   ", backtrace_frames=" + backtrace.length());
        Log.i(TAG, "testGetLeaksJsonWithSingleLeak passed");
    }

    @Test
    public void testGetLeaksJsonWithMultipleLeaks() throws JSONException {
        Log.i(TAG, "=== testGetLeaksJsonWithMultipleLeaks ===");
        
        // 创建多个泄漏
        TestMemoryCpp.nativeLeakNew(512);
        TestMemoryCpp.nativeLeakNew(1024);
        TestMemoryCpp.nativeLeakObject();
        TestMemoryCpp.nativeLeakObjectArray(3);
        
        // 获取 JSON 格式的泄漏列表
        String json = SoHook.nativeGetLeaksJson();
        assertNotNull("JSON should not be null", json);
        Log.i(TAG, "Leaks JSON length: " + json.length() + " bytes");
        
        // 解析 JSON
        JSONArray leaks = new JSONArray(json);
        assertEquals("Should have 4 leaks", 4, leaks.length());
        
        // 验证每个泄漏都有必需的字段
        long totalSize = 0;
        for (int i = 0; i < leaks.length(); i++) {
            JSONObject leak = leaks.getJSONObject(i);
            assertTrue("Leak " + i + " should have ptr", leak.has("ptr"));
            assertTrue("Leak " + i + " should have size", leak.has("size"));
            assertTrue("Leak " + i + " should have backtrace", leak.has("backtrace"));
            
            long size = leak.getLong("size");
            totalSize += size;
            
            Log.i(TAG, "Leak " + i + ": size=" + size);
        }
        
        Log.i(TAG, "Total leaked size: " + totalSize + " bytes");
        Log.i(TAG, "testGetLeaksJsonWithMultipleLeaks passed");
    }

    @Test
    public void testGetLeaksAggregatedJsonEmpty() throws JSONException {
        Log.i(TAG, "=== testGetLeaksAggregatedJsonEmpty ===");
        
        // 没有泄漏时应该返回空数组
        String json = SoHook.nativeGetLeaksAggregatedJson();
        assertNotNull("JSON should not be null", json);
        
        JSONArray groups = new JSONArray(json);
        assertEquals("Should have no groups", 0, groups.length());
        
        Log.i(TAG, "Empty aggregated JSON: " + json);
        Log.i(TAG, "testGetLeaksAggregatedJsonEmpty passed");
    }

    @Test
    public void testGetLeaksAggregatedJsonWithSingleGroup() throws JSONException {
        Log.i(TAG, "=== testGetLeaksAggregatedJsonWithSingleGroup ===");
        
        // 创建多个相同调用栈的泄漏
        for (int i = 0; i < 5; i++) {
            TestMemoryCpp.nativeLeakNew(1024);
        }
        
        // 获取聚合后的 JSON
        String json = SoHook.nativeGetLeaksAggregatedJson();
        assertNotNull("JSON should not be null", json);
        Log.i(TAG, "Aggregated JSON: " + json);
        
        // 解析 JSON
        JSONArray groups = new JSONArray(json);
        assertTrue("Should have at least 1 group", groups.length() >= 1);
        
        // 检查第一个组
        JSONObject group = groups.getJSONObject(0);
        assertTrue("Should have count field", group.has("count"));
        assertTrue("Should have totalSize field", group.has("totalSize"));
        assertTrue("Should have backtrace field", group.has("backtrace"));
        
        int count = group.getInt("count");
        long totalSize = group.getLong("totalSize");
        
        assertEquals("Count should be 5", 5, count);
        assertEquals("Total size should be 5120", 5 * 1024, totalSize);
        
        // 检查 backtrace
        JSONArray backtrace = group.getJSONArray("backtrace");
        assertTrue("Backtrace should not be empty", backtrace.length() > 0);
        
        Log.i(TAG, "Group: count=" + count + 
                   ", totalSize=" + totalSize + 
                   ", backtrace_frames=" + backtrace.length());
        Log.i(TAG, "testGetLeaksAggregatedJsonWithSingleGroup passed");
    }

    @Test
    public void testGetLeaksAggregatedJsonWithMultipleGroups() throws JSONException {
        Log.i(TAG, "=== testGetLeaksAggregatedJsonWithMultipleGroups ===");
        
        // 创建不同调用栈的泄漏
        // 组1: nativeLeakNew
        for (int i = 0; i < 3; i++) {
            TestMemoryCpp.nativeLeakNew(512);
        }
        
        // 组2: nativeLeakObject
        for (int i = 0; i < 2; i++) {
            TestMemoryCpp.nativeLeakObject();
        }
        
        // 组3: nativeLeakObjectArray
        for (int i = 0; i < 4; i++) {
            TestMemoryCpp.nativeLeakObjectArray(2);
        }
        
        // 获取聚合后的 JSON
        String json = SoHook.nativeGetLeaksAggregatedJson();
        assertNotNull("JSON should not be null", json);
        Log.i(TAG, "Aggregated JSON length: " + json.length() + " bytes");
        
        // 解析 JSON
        JSONArray groups = new JSONArray(json);
        assertTrue("Should have multiple groups", groups.length() >= 3);
        
        // 验证每个组的字段
        long totalLeakedSize = 0;
        int totalLeakCount = 0;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            assertTrue("Group " + i + " should have count", group.has("count"));
            assertTrue("Group " + i + " should have totalSize", group.has("totalSize"));
            assertTrue("Group " + i + " should have backtrace", group.has("backtrace"));
            
            int count = group.getInt("count");
            long totalSize = group.getLong("totalSize");
            JSONArray backtrace = group.getJSONArray("backtrace");
            
            totalLeakCount += count;
            totalLeakedSize += totalSize;
            
            Log.i(TAG, "Group " + i + ": count=" + count + 
                       ", totalSize=" + totalSize + 
                       ", backtrace_frames=" + backtrace.length());
        }
        
        assertEquals("Total leak count should be 9", 9, totalLeakCount);
        Log.i(TAG, "Total groups: " + groups.length() + 
                   ", total leaks: " + totalLeakCount + 
                   ", total size: " + totalLeakedSize);
        Log.i(TAG, "testGetLeaksAggregatedJsonWithMultipleGroups passed");
    }

    @Test
    public void testGetLeaksAggregatedJsonSortedBySize() throws JSONException {
        Log.i(TAG, "=== testGetLeaksAggregatedJsonSortedBySize ===");
        
        // 创建不同大小的泄漏组
        // 小组: 2 x 128 bytes = 256 bytes
        for (int i = 0; i < 2; i++) {
            TestMemoryCpp.nativeLeakObject();
        }
        
        // 大组: 5 x 2048 bytes = 10240 bytes
        for (int i = 0; i < 5; i++) {
            TestMemoryCpp.nativeLeakNew(2048);
        }
        
        // 中组: 3 x 512 bytes = 1536 bytes
        for (int i = 0; i < 3; i++) {
            TestMemoryCpp.nativeLeakObjectArray(10);
        }
        
        // 获取聚合后的 JSON
        String json = SoHook.nativeGetLeaksAggregatedJson();
        assertNotNull("JSON should not be null", json);
        
        // 解析 JSON
        JSONArray groups = new JSONArray(json);
        assertTrue("Should have at least 3 groups", groups.length() >= 3);
        
        // 验证按 totalSize 降序排序
        long prevTotalSize = Long.MAX_VALUE;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            long totalSize = group.getLong("totalSize");
            
            assertTrue("Groups should be sorted by totalSize descending", 
                      totalSize <= prevTotalSize);
            prevTotalSize = totalSize;
            
            Log.i(TAG, "Group " + i + ": totalSize=" + totalSize);
        }
        
        Log.i(TAG, "testGetLeaksAggregatedJsonSortedBySize passed");
    }

    @Test
    public void testJsonBufferOverflowHandling() throws JSONException {
        Log.i(TAG, "=== testJsonBufferOverflowHandling ===");
        
        // 创建大量泄漏,测试缓冲区动态扩展
        int leakCount = 100;
        for (int i = 0; i < leakCount; i++) {
            TestMemoryCpp.nativeLeakNew(1024);
        }
        
        // 获取 JSON (应该触发缓冲区扩展)
        String json = SoHook.nativeGetLeaksJson();
        assertNotNull("JSON should not be null", json);
        assertTrue("JSON should not be empty", json.length() > 0);
        
        // 验证 JSON 有效性
        JSONArray leaks = new JSONArray(json);
        assertEquals("Should have all leaks", leakCount, leaks.length());
        
        Log.i(TAG, "Generated JSON with " + leakCount + " leaks, size=" + json.length() + " bytes");
        
        // 获取聚合 JSON
        String aggregatedJson = SoHook.nativeGetLeaksAggregatedJson();
        assertNotNull("Aggregated JSON should not be null", aggregatedJson);
        assertTrue("Aggregated JSON should not be empty", aggregatedJson.length() > 0);
        
        JSONArray groups = new JSONArray(aggregatedJson);
        assertTrue("Should have at least 1 group", groups.length() >= 1);
        
        // 验证聚合后的总数
        int totalCount = 0;
        for (int i = 0; i < groups.length(); i++) {
            totalCount += groups.getJSONObject(i).getInt("count");
        }
        assertEquals("Aggregated count should match leak count", leakCount, totalCount);
        
        Log.i(TAG, "Aggregated JSON size=" + aggregatedJson.length() + " bytes, groups=" + groups.length());
        Log.i(TAG, "testJsonBufferOverflowHandling passed");
    }

    @Test
    public void testJsonWithBacktraceDisabled() throws JSONException {
        Log.i(TAG, "=== testJsonWithBacktraceDisabled ===");
        
        // 禁用 backtrace
        SoHook.setBacktraceEnabled(false);
        assertFalse("Backtrace should be disabled", SoHook.isBacktraceEnabled());
        
        // 创建泄漏
        TestMemoryCpp.nativeLeakNew(1024);
        
        // 获取 JSON
        String json = SoHook.nativeGetLeaksJson();
        assertNotNull("JSON should not be null", json);
        
        // 解析并检查 backtrace
        JSONArray leaks = new JSONArray(json);
        assertEquals("Should have 1 leak", 1, leaks.length());
        
        JSONObject leak = leaks.getJSONObject(0);
        JSONArray backtrace = leak.getJSONArray("backtrace");
        assertEquals("Backtrace should be empty when disabled", 0, backtrace.length());
        
        Log.i(TAG, "Leak without backtrace: " + leak.toString());
        
        // 重新启用 backtrace
        SoHook.setBacktraceEnabled(true);
        
        Log.i(TAG, "testJsonWithBacktraceDisabled passed");
    }

    @Test
    public void testJsonBacktraceSymbolResolution() throws JSONException {
        Log.i(TAG, "=== testJsonBacktraceSymbolResolution ===");
        
        // 确保 backtrace 已启用
        SoHook.setBacktraceEnabled(true);
        assertTrue("Backtrace should be enabled", SoHook.isBacktraceEnabled());
        
        // 创建泄漏
        TestMemoryCpp.nativeLeakNew(2048);
        
        // 获取 JSON
        String json = SoHook.nativeGetLeaksJson();
        assertNotNull("JSON should not be null", json);
        
        // 解析并检查 backtrace 符号
        JSONArray leaks = new JSONArray(json);
        JSONObject leak = leaks.getJSONObject(0);
        JSONArray backtrace = leak.getJSONArray("backtrace");
        
        assertTrue("Backtrace should have frames", backtrace.length() > 0);
        
        // 检查符号格式
        boolean hasSymbols = false;
        for (int i = 0; i < backtrace.length(); i++) {
            String frame = backtrace.getString(i);
            Log.i(TAG, "Frame " + i + ": " + frame);
            
            // 检查是否包含符号信息 (格式: symbol+offset 或地址)
            if (frame.contains("+") || frame.startsWith("0x")) {
                hasSymbols = true;
            }
        }
        
        assertTrue("Backtrace should contain symbol information", hasSymbols);
        Log.i(TAG, "testJsonBacktraceSymbolResolution passed");
    }

    @Test
    public void testCompareJsonAndAggregatedJson() throws JSONException {
        Log.i(TAG, "=== testCompareJsonAndAggregatedJson ===");
        
        // 创建相同调用栈的多个泄漏
        int count = 10;
        for (int i = 0; i < count; i++) {
            TestMemoryCpp.nativeLeakNew(256);
        }
        
        // 获取两种格式的 JSON
        String json = SoHook.nativeGetLeaksJson();
        String aggregatedJson = SoHook.nativeGetLeaksAggregatedJson();
        
        assertNotNull("JSON should not be null", json);
        assertNotNull("Aggregated JSON should not be null", aggregatedJson);
        
        // 解析
        JSONArray leaks = new JSONArray(json);
        JSONArray groups = new JSONArray(aggregatedJson);
        
        // 验证数量关系
        assertEquals("Should have 10 individual leaks", count, leaks.length());
        assertTrue("Should have at least 1 group", groups.length() >= 1);
        
        // 计算总泄漏数
        int aggregatedCount = 0;
        long aggregatedSize = 0;
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i);
            aggregatedCount += group.getInt("count");
            aggregatedSize += group.getLong("totalSize");
        }
        
        // 计算个别泄漏总大小
        long individualSize = 0;
        for (int i = 0; i < leaks.length(); i++) {
            individualSize += leaks.getJSONObject(i).getLong("size");
        }
        
        assertEquals("Aggregated count should match individual count", count, aggregatedCount);
        assertEquals("Aggregated size should match individual size", individualSize, aggregatedSize);
        
        Log.i(TAG, "Individual leaks: " + leaks.length() + ", size=" + individualSize);
        Log.i(TAG, "Aggregated groups: " + groups.length() + ", count=" + aggregatedCount + ", size=" + aggregatedSize);
        Log.i(TAG, "testCompareJsonAndAggregatedJson passed");
    }
}
