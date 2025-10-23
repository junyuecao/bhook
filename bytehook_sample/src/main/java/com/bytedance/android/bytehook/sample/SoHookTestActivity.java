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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sohook_test);

        tvStats = findViewById(R.id.tv_stats);

        // 初始化SoHook
        int ret = SoHook.init(true);
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

        // 监控libc.so和libsample.so
        List<String> soNames = Arrays.asList("libc.so", "libsample.so");
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

        List<String> soNames = Arrays.asList("libc.so", "libsample.so");
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
        // 触发一些内存分配操作
        NativeHacker.doDlopen();
        NativeHacker.doRun(false);
        showToast("已触发内存分配操作");
    }

    public void onFreeMemoryClick(View view) {
        // 触发内存释放操作
        NativeHacker.doDlclose();
        showToast("已触发内存释放操作");
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
        
        Log.d(TAG, "内存统计: " + stats.toString());
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
