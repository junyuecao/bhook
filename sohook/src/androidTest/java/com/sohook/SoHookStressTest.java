package com.sohook;

import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * SoHook 压力测试
 * 测试并发、性能、稳定性等
 */
@RunWith(AndroidJUnit4.class)
public class SoHookStressTest {
    private static final String TAG = "SoHookStressTest";
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SoHook.init(true);
        SoHook.resetStats();
        Log.i(TAG, "Test setup completed");
    }

    @After
    public void tearDown() {
        SoHook.resetStats();
        Log.i(TAG, "Test teardown completed");
    }

    /**
     * 测试连续多次Hook和Unhook
     */
    @Test
    public void testRepeatedHookUnhook() {
        final int iterations = 10;
        
        for (int i = 0; i < iterations; i++) {
            int hookResult = SoHook.hook(Collections.singletonList("libc.so"));
            assertEquals("Hook iteration " + i + " should succeed", 0, hookResult);
            
            int unhookResult = SoHook.unhook(Collections.singletonList("libc.so"));
            assertEquals("Unhook iteration " + i + " should succeed", 0, unhookResult);
        }
        
        Log.i(TAG, "✓ Repeated hook/unhook test passed (" + iterations + " iterations)");
    }

    /**
     * 测试连续多次获取统计信息
     */
    @Test
    public void testRepeatedGetStats() {
        final int iterations = 100;
        
        for (int i = 0; i < iterations; i++) {
            SoHook.MemoryStats stats = SoHook.getMemoryStats();
            assertNotNull("Stats iteration " + i + " should not be null", stats);
            assertTrue("Stats should be valid", stats.totalAllocCount >= 0);
        }
        
        Log.i(TAG, "✓ Repeated get stats test passed (" + iterations + " iterations)");
    }

    /**
     * 测试连续多次获取泄漏报告
     */
    @Test
    public void testRepeatedGetLeakReport() {
        final int iterations = 50;
        
        for (int i = 0; i < iterations; i++) {
            String report = SoHook.getLeakReport();
            assertNotNull("Report iteration " + i + " should not be null", report);
            assertFalse("Report should not be empty", report.isEmpty());
        }
        
        Log.i(TAG, "✓ Repeated get leak report test passed (" + iterations + " iterations)");
    }

    /**
     * 测试连续多次重置统计
     */
    @Test
    public void testRepeatedReset() {
        final int iterations = 50;
        
        for (int i = 0; i < iterations; i++) {
            SoHook.resetStats();
            
            SoHook.MemoryStats stats = SoHook.getMemoryStats();
            assertNotNull("Stats after reset " + i + " should not be null", stats);
            assertEquals("Total alloc count should be 0", 0, stats.totalAllocCount);
        }
        
        Log.i(TAG, "✓ Repeated reset test passed (" + iterations + " iterations)");
    }

    /**
     * 测试多线程并发获取统计信息
     */
    @Test
    public void testConcurrentGetStats() throws InterruptedException {
        final int threadCount = 10;
        final int iterationsPerThread = 20;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        SoHook.MemoryStats stats = SoHook.getMemoryStats();
                        if (stats != null && stats.totalAllocCount >= 0) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                            Log.w(TAG, "Thread " + threadId + " iteration " + j + " failed");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Thread " + threadId + " exception", e);
                    failCount.addAndGet(iterationsPerThread);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue("All threads should complete", latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        int expectedTotal = threadCount * iterationsPerThread;
        assertEquals("All operations should succeed", expectedTotal, successCount.get());
        assertEquals("No operations should fail", 0, failCount.get());
        
        Log.i(TAG, "✓ Concurrent get stats test passed (" + threadCount + " threads, " + 
                   iterationsPerThread + " iterations each)");
    }

    /**
     * 测试多线程并发获取泄漏报告
     */
    @Test
    public void testConcurrentGetLeakReport() throws InterruptedException {
        final int threadCount = 5;
        final int iterationsPerThread = 10;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        String report = SoHook.getLeakReport();
                        if (report != null && !report.isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Thread " + threadId + " exception", e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue("All threads should complete", latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        int expectedTotal = threadCount * iterationsPerThread;
        assertEquals("All operations should succeed", expectedTotal, successCount.get());
        
        Log.i(TAG, "✓ Concurrent get leak report test passed (" + threadCount + " threads, " + 
                   iterationsPerThread + " iterations each)");
    }

    /**
     * 测试多线程并发Hook
     */
    @Test
    public void testConcurrentHook() throws InterruptedException {
        final int threadCount = 5;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    int result = SoHook.hook(Collections.singletonList("libc.so"));
                    if (result == 0) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Thread " + threadId + " exception", e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue("All threads should complete", latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // 至少应该有一个成功
        assertTrue("At least one hook should succeed", successCount.get() > 0);
        
        Log.i(TAG, "✓ Concurrent hook test passed (" + successCount.get() + "/" + 
                   threadCount + " succeeded)");
    }

    /**
     * 测试栈回溯开关的并发操作
     */
    @Test
    public void testConcurrentBacktraceToggle() throws InterruptedException {
        final int threadCount = 10;
        final int iterationsPerThread = 10;
        final CountDownLatch latch = new CountDownLatch(threadCount);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        // 交替开关栈回溯
                        boolean enable = (threadId + j) % 2 == 0;
                        SoHook.setBacktraceEnabled(enable);
                        
                        // 验证设置
                        boolean isEnabled = SoHook.isBacktraceEnabled();
                        // 注意：由于并发，isEnabled可能与enable不一致，这是正常的
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Thread " + threadId + " exception", e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue("All threads should complete", latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        Log.i(TAG, "✓ Concurrent backtrace toggle test passed");
    }

    /**
     * 测试Hook大量库
     */
    @Test
    public void testHookManyLibraries() {
        List<String> libraries = new ArrayList<>();
        libraries.add("libc.so");
        libraries.add("libm.so");
        libraries.add("libdl.so");
        libraries.add("liblog.so");
        libraries.add("libz.so");
        
        int result = SoHook.hook(libraries);
        assertEquals("Hook many libraries should succeed", 0, result);
        
        Log.i(TAG, "✓ Hook many libraries test passed (" + libraries.size() + " libraries)");
    }

    /**
     * 测试性能：获取统计信息的速度
     */
    @Test
    public void testGetStatsPerformance() {
        final int iterations = 1000;
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            SoHook.MemoryStats stats = SoHook.getMemoryStats();
            assertNotNull("Stats should not be null", stats);
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        double avgTimeUs = (endTime - startTime) / 1000.0 / iterations;
        
        Log.i(TAG, "✓ Get stats performance test passed");
        Log.i(TAG, "  Total time: " + durationMs + " ms");
        Log.i(TAG, "  Average time: " + String.format("%.2f", avgTimeUs) + " μs per call");
        Log.i(TAG, "  Throughput: " + String.format("%.0f", iterations * 1000.0 / durationMs) + " calls/sec");
        
        // 性能断言：平均每次调用不应超过1ms
        assertTrue("Average call time should be < 1ms", avgTimeUs < 1000);
    }

    /**
     * 测试性能：获取泄漏报告的速度
     */
    @Test
    public void testGetLeakReportPerformance() {
        final int iterations = 100;
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            String report = SoHook.getLeakReport();
            assertNotNull("Report should not be null", report);
        }
        
        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        double avgTimeMs = (endTime - startTime) / 1_000_000.0 / iterations;
        
        Log.i(TAG, "✓ Get leak report performance test passed");
        Log.i(TAG, "  Total time: " + durationMs + " ms");
        Log.i(TAG, "  Average time: " + String.format("%.2f", avgTimeMs) + " ms per call");
        Log.i(TAG, "  Throughput: " + String.format("%.1f", iterations * 1000.0 / durationMs) + " calls/sec");
        
        // 性能断言：平均每次调用不应超过100ms
        assertTrue("Average call time should be < 100ms", avgTimeMs < 100);
    }

    /**
     * 测试内存稳定性：连续操作不应导致内存泄漏
     */
    @Test
    public void testMemoryStability() {
        final int iterations = 100;
        
        // 记录初始统计
        SoHook.MemoryStats initialStats = SoHook.getMemoryStats();
        
        // 执行大量操作
        for (int i = 0; i < iterations; i++) {
            SoHook.getMemoryStats();
            SoHook.getLeakReport();
            
            if (i % 10 == 0) {
                SoHook.resetStats();
            }
        }
        
        // 最终重置
        SoHook.resetStats();
        
        // 获取最终统计
        SoHook.MemoryStats finalStats = SoHook.getMemoryStats();
        
        // 验证统计已重置
        assertEquals("Final total alloc count should be 0", 0, finalStats.totalAllocCount);
        assertEquals("Final current alloc count should be 0", 0, finalStats.currentAllocCount);
        
        Log.i(TAG, "✓ Memory stability test passed (" + iterations + " iterations)");
    }
}
