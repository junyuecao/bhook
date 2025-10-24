package com.bytedance.android.bytehook.sample;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.sohook.SoHook;

import java.util.Arrays;
import java.util.List;

public class SoHookTestActivity extends AppCompatActivity {

    private static final String TAG = "SoHookTest";
    private TextView tvStats;
    private boolean isHooked = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateStatsRunnable;
    
    // 缓存上次的统计数据，避免重复打印日志
    private SoHook.MemoryStats lastStats = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sohook_test);

        tvStats = findViewById(R.id.tv_stats);

        // 初始化SoHook
        int ret = SoHook.init(true, true);
        if (ret == 0) {
            Log.i(TAG, "SoHook初始化成功");
            showToast("SoHook初始化成功");
        } else {
            Log.e(TAG, "SoHook初始化失败: " + ret);
            showToast("SoHook初始化失败: " + ret);
        }

        // 定时更新统计信息
        updateStatsRunnable = new Runnable() {
            @Override
            public void run() {
                if (isHooked) {
                    updateStats();
                    handler.postDelayed(this, 1000); // 每秒更新一次
                }
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateStatsRunnable);
        if (isHooked) {
            stopHook();
        }
    }

    public void onStartHookClick(View view) {
        if (isHooked) {
            showToast("已经在监控中");
            return;
        }

        // 先加载libsample.so
        NativeHacker.doDlopen();
        Log.i(TAG, "已加载libsample.so");
        
        // 等待一下确保so加载完成
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 监控libsample.so（不要监控libc.so，会导致死锁）
        List<String> soNames = Arrays.asList("libsample.so");
        int ret = SoHook.hook(soNames);
        
        if (ret == 0) {
            isHooked = true;
            Log.i(TAG, "开始监控内存分配");
            showToast("开始监控: " + soNames);
            
            // 开始定时更新统计
            handler.post(updateStatsRunnable);
            
            findViewById(R.id.btn_start_hook).setEnabled(false);
            findViewById(R.id.btn_stop_hook).setEnabled(true);
        } else {
            Log.e(TAG, "启动监控失败: " + ret);
            showToast("启动监控失败: " + ret);
        }
    }

    public void onStopHookClick(View view) {
        stopHook();
    }

    private void stopHook() {
        if (!isHooked) {
            showToast("未在监控中");
            return;
        }

        List<String> soNames = Arrays.asList("libsample.so");
        int ret = SoHook.unhook(soNames);
        
        if (ret == 0) {
            isHooked = false;
            Log.i(TAG, "停止监控内存分配");
            showToast("停止监控");
            
            handler.removeCallbacks(updateStatsRunnable);
            
            findViewById(R.id.btn_start_hook).setEnabled(true);
            findViewById(R.id.btn_stop_hook).setEnabled(false);
        } else {
            Log.e(TAG, "停止监控失败: " + ret);
            showToast("停止监控失败: " + ret);
        }
    }

    public void onGetStatsClick(View view) {
        updateStats();
    }

    public void onGetReportClick(View view) {
        String report = SoHook.getLeakReport();
        Log.i(TAG, "=== 内存泄漏报告 ===");
        for (String line : report.split("\n")) {
            Log.i(TAG, line);
        }
        showToast("报告已输出到logcat");
    }

    public void onDumpReportClick(View view) {
        String filePath = getApplicationContext().getFilesDir() + "/sohook_leak_report.txt";
        int ret = SoHook.dumpLeakReport(filePath);
        
        if (ret == 0) {
            Log.i(TAG, "报告已导出到: " + filePath);
            showToast("报告已导出到: " + filePath);
        } else {
            Log.e(TAG, "导出报告失败: " + ret);
            showToast("导出报告失败: " + ret);
        }
    }

    public void onResetStatsClick(View view) {
        SoHook.resetStats();
        updateStats();
        Log.i(TAG, "统计信息已重置");
        showToast("统计信息已重置");
    }

    public void onAllocMemoryClick(View view) {
        if (!isHooked) {
            showToast("请先开始监控");
            return;
        }
        
        // 分配10个内存块
        NativeHacker.allocMemory(10);
        showToast("已分配10个内存块");
        
        Log.i(TAG, "分配了10个内存块");
    }

    public void onFreeMemoryClick(View view) {
        // 释放5个内存块
        NativeHacker.freeMemory(5);
        showToast("已释放5个内存块");
        
        Log.i(TAG, "释放了5个内存块");
    }

    public void onRunPerfTestsClick(View view) {
        if (!isHooked) {
            showToast("请先开始监控");
            return;
        }
        
        showToast("开始性能测试，请查看logcat");
        Log.i(TAG, "=== 开始运行性能测试套件 ===");
        
        // 在后台线程运行，避免阻塞UI
        new Thread(() -> {
            // 调用 libsample.so 中的性能测试
            NativeHacker.runPerfTests();
            runOnUiThread(() -> {
                showToast("性能测试完成，查看logcat和统计");
                updateStats();
            });
        }).start();
    }

    public void onQuickBenchmarkClick(View view) {
        if (!isHooked) {
            showToast("请先开始监控");
            return;
        }
        
        showToast("开始快速基准测试，请查看logcat");
        Log.i(TAG, "=== 开始快速基准测试 ===");
        
        // 在后台线程运行，避免阻塞UI
        new Thread(() -> {
            // 调用 libsample.so 中的快速基准测试
            NativeHacker.quickBenchmark(5000);
            runOnUiThread(() -> {
                showToast("基准测试完成，查看logcat和统计");
                updateStats();
            });
        }).start();
    }

    private void updateStats() {
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== 内存统计信息 ===\n\n");
        sb.append("总分配次数: ").append(stats.totalAllocCount).append("\n");
        sb.append("总分配大小: ").append(formatBytes(stats.totalAllocSize)).append("\n\n");
        sb.append("总释放次数: ").append(stats.totalFreeCount).append("\n");
        sb.append("总释放大小: ").append(formatBytes(stats.totalFreeSize)).append("\n\n");
        sb.append("当前泄漏次数: ").append(stats.currentAllocCount).append("\n");
        sb.append("当前泄漏大小: ").append(formatBytes(stats.currentAllocSize)).append("\n");
        
        tvStats.setText(sb.toString());
        
        // 只在数据变化时打印日志
        if (lastStats == null || !statsEquals(lastStats, stats)) {
            Log.d(TAG, "内存统计变化: " + stats.toString());
            lastStats = new SoHook.MemoryStats(
                stats.totalAllocCount, stats.totalAllocSize,
                stats.totalFreeCount, stats.totalFreeSize,
                stats.currentAllocCount, stats.currentAllocSize
            );
        }
    }
    
    private boolean statsEquals(SoHook.MemoryStats s1, SoHook.MemoryStats s2) {
        return s1.totalAllocCount == s2.totalAllocCount &&
               s1.totalAllocSize == s2.totalAllocSize &&
               s1.totalFreeCount == s2.totalFreeCount &&
               s1.totalFreeSize == s2.totalFreeSize &&
               s1.currentAllocCount == s2.currentAllocCount &&
               s1.currentAllocSize == s2.currentAllocSize;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
