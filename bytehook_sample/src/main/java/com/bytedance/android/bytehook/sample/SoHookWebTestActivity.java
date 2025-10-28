package com.bytedance.android.bytehook.sample;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.sohook.SoHook;
import com.sohook.SoHookWebManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * SoHook Web 监控测试页面
 * 演示如何使用 Web 界面实时监控内存泄漏
 */
public class SoHookWebTestActivity extends AppCompatActivity {

    private static final String TAG = "SoHook-WebTest";
    private static final int WEB_SERVER_PORT = 8080;
    
    private TextView tvServerStatus;
    private TextView tvServerUrl;
    private TextView tvStats;
    private android.widget.Button btnStressTest;
    private boolean isHooked = false;
    private boolean isServerRunning = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateStatsRunnable;
    
    // 压力测试相关
    private volatile boolean isStressTestRunning = false;
    private Thread[] stressTestThreads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sohook_web_test);

        tvServerStatus = findViewById(R.id.tv_server_status);
        tvServerUrl = findViewById(R.id.tv_server_url);
        tvStats = findViewById(R.id.tv_stats);
        btnStressTest = findViewById(R.id.btn_stress_test);

        // 初始化 SoHook
        int ret = SoHook.init(true, true); // 启用调试和栈回溯
        if (ret == 0) {
            Log.i(TAG, "SoHook 初始化成功");
            showToast("SoHook 初始化成功");
        } else {
            Log.e(TAG, "SoHook 初始化失败: " + ret);
            showToast("SoHook 初始化失败: " + ret);
        }

        // 定时更新统计信息
        updateStatsRunnable = new Runnable() {
            @Override
            public void run() {
                if (isHooked) {
                    updateStats();
                    handler.postDelayed(this, 2000); // 每 2 秒更新一次
                }
            }
        };
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateStatsRunnable);
        
        // 停止压力测试
        stopStressTest();
        
        // 停止 Web 服务器
        if (isServerRunning) {
            stopWebServer();
        }
        
        // 停止监控
        if (isHooked) {
            stopHook();
        }
    }

    /**
     * 启动 Web 服务器
     */
    public void onStartWebServerClick(View view) {
        if (isServerRunning) {
            showToast("Web 服务器已在运行");
            return;
        }

        boolean success = SoHookWebManager.startServer(WEB_SERVER_PORT);
        if (success) {
            isServerRunning = true;
            String ipAddress = getLocalIpAddress();
            String url = "http://" + ipAddress + ":" + WEB_SERVER_PORT;
            
            tvServerStatus.setText("状态: 运行中 ✅");
            tvServerUrl.setText("访问地址: " + url);
            
            Log.i(TAG, "Web 服务器已启动: " + url);
            showToast("Web 服务器已启动\n" + url);
        } else {
            Log.e(TAG, "Web 服务器启动失败");
            showToast("Web 服务器启动失败");
        }
    }

    /**
     * 停止 Web 服务器
     */
    public void onStopWebServerClick(View view) {
        stopWebServer();
    }

    private void stopWebServer() {
        if (!isServerRunning) {
            showToast("Web 服务器未运行");
            return;
        }

        SoHookWebManager.stopServer();
        isServerRunning = false;
        
        tvServerStatus.setText("状态: 已停止 ⭕");
        tvServerUrl.setText("访问地址: -");
        
        Log.i(TAG, "Web 服务器已停止");
        showToast("Web 服务器已停止");
    }

    /**
     * 复制服务器地址到剪贴板
     */
    public void onCopyUrlClick(View view) {
        if (!isServerRunning) {
            showToast("请先启动 Web 服务器");
            return;
        }

        String ipAddress = getLocalIpAddress();
        String url = "http://" + ipAddress + ":" + WEB_SERVER_PORT;
        
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("SoHook Server URL", url);
        clipboard.setPrimaryClip(clip);
        
        showToast("已复制到剪贴板: " + url);
    }

    /**
     * 开始监控内存
     */
    public void onStartHookClick(View view) {
        if (isHooked) {
            showToast("已经在监控中");
            return;
        }

        // 先加载 libsample.so
        NativeHacker.doDlopen();
        Log.i(TAG, "已加载 libsample.so");
        
        // 等待一下确保 so 加载完成
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 开始监控 libsample.so
        List<String> soNames = Arrays.asList("libsample.so");
        int ret = SoHook.hook(soNames);
        
        if (ret == 0) {
            isHooked = true;
            Log.i(TAG, "开始监控: " + soNames);
            showToast("开始监控 libsample.so");
            
            // 开始定时更新统计
            handler.post(updateStatsRunnable);
        } else {
            Log.e(TAG, "Hook 失败: " + ret);
            showToast("Hook 失败: " + ret);
        }
    }

    /**
     * 停止监控
     */
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
            handler.removeCallbacks(updateStatsRunnable);
            Log.i(TAG, "停止监控");
            showToast("停止监控");
        } else {
            Log.e(TAG, "Unhook 失败: " + ret);
            showToast("Unhook 失败: " + ret);
        }
    }

    /**
     * 创建内存泄漏（用于测试）
     */
    public void onCreateLeakClick(View view) {
        if (!isHooked) {
            showToast("请先开始监控");
            return;
        }

        int count = 5;  // 每种类型 5 个
        
        // C malloc 泄漏
        NativeHacker.allocMemory(count);
        Log.i(TAG, "创建 " + count + " 个 malloc 泄漏");
        
        // C++ operator new 泄漏
        NativeHacker.allocWithNew(count);
        Log.i(TAG, "创建 " + count + " 个 operator new 泄漏");
        
        // C++ operator new[] 泄漏
        NativeHacker.allocWithNewArray(count);
        Log.i(TAG, "创建 " + count + " 个 operator new[] 泄漏");

        // C++ new 对象泄漏
        NativeHacker.allocObjects(count);
        Log.i(TAG, "创建 " + count + " 个 new 对象泄漏");

        // C++ new[] 对象数组泄漏
        NativeHacker.allocObjectArrays(count);
        Log.i(TAG, "创建 " + count + " 个 new[] 对象数组泄漏");
        
        int totalLeaks = count * 5;
        Log.i(TAG, "总共创建 " + totalLeaks + " 个内存泄漏 (C + C++)");
        showToast("已创建 " + totalLeaks + " 个内存泄漏\n(包含 C 和 C++ 分配)");
        
        // 立即更新统计
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateStats();
            }
        }, 500);
    }

    /**
     * 创建FD泄漏（用于测试）
     */
    public void onCreateFdLeakClick(View view) {
        if (!isHooked) {
            showToast("请先开始监控");
            return;
        }

        int count = 5;  // 每种类型 5 个
        String pathPrefix = getCacheDir().getAbsolutePath() + "/test_fd_leak";
        
        // open 泄漏
        NativeHacker.leakFileDescriptors(count, pathPrefix);
        Log.i(TAG, "创建 " + count + " 个 open FD 泄漏");
        
        // fopen 泄漏
        NativeHacker.leakFilePointers(count, pathPrefix);
        Log.i(TAG, "创建 " + count + " 个 fopen FD 泄漏");
        
        int totalLeaks = count * 2;
        Log.i(TAG, "总共创建 " + totalLeaks + " 个 FD 泄漏 (open + fopen)");
        showToast("已创建 " + totalLeaks + " 个 FD 泄漏\n(包含 open 和 fopen)");
        
        // 立即更新统计
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateStats();
            }
        }, 500);
    }

    /**
     * 压力测试 - 100个线程持续申请小内存
     */
    public void onStressTestClick(View view) {
        if (!isHooked) {
            showToast("请先开始监控");
            return;
        }

        // 如果正在运行，则停止
        if (isStressTestRunning) {
            stopStressTest();
            return;
        }

        // 显示确认对话框
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚠️ 压力测试警告")
            .setMessage("即将启动多线程压力测试！\n\n" +
                    "配置：\n" +
                    "• 线程数: 100 个\n" +
                    "• 每次分配: 1-10 KB\n" +
                    "• 分配间隔: 10-50 ms\n" +
                    "• 持续运行直到手动停止\n\n" +
                    "这将：\n" +
                    "• 持续占用内存\n" +
                    "• 测试 Web 页面实时性能\n" +
                    "• 模拟真实泄漏场景\n\n" +
                    "建议先在 Web 页面中打开 Dashboard。\n\n" +
                    "确定继续？")
            .setPositiveButton("开始测试", (dialog, which) -> {
                startStressTest();
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * 启动压力测试
     */
    private void startStressTest() {
        if (isStressTestRunning) {
            return;
        }

        isStressTestRunning = true;
        final int THREAD_COUNT = 100;
        stressTestThreads = new Thread[THREAD_COUNT];
        
        // 更新按钮文本
        handler.post(() -> {
            btnStressTest.setText("⏹️ 停止压力测试");
            btnStressTest.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF4CAF50) // 绿色
            );
        });
        
        Log.i(TAG, "启动压力测试: " + THREAD_COUNT + " 个线程");
        showToast("压力测试已启动\n" + THREAD_COUNT + " 个线程持续分配内存");
        
        // 启动监控线程，定期更新统计
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            while (isStressTestRunning) {
                try {
                    Thread.sleep(2000); // 每2秒更新一次
                    
                    long runningTime = (System.currentTimeMillis() - startTime) / 1000;
                    handler.post(() -> {
                        updateStats();
                        SoHook.MemoryStats stats = SoHook.getMemoryStats();
                        Log.i(TAG, String.format("压力测试运行中: %d秒, 当前泄漏: %,d 个, %s",
                                runningTime,
                                stats.currentAllocCount,
                                formatBytes(stats.currentAllocSize)));
                    });
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
        
        // 启动100个工作线程
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            stressTestThreads[i] = new Thread(() -> {
                Log.d(TAG, "线程 " + threadId + " 启动");
                java.util.Random random = new java.util.Random();
                
                while (isStressTestRunning) {
                    try {
                        // 每次分配 1-10 个小内存块
                        int allocCount = 1 + random.nextInt(10);
                        NativeHacker.allocMemory(allocCount);
                        
                        // 随机休息 10-50 毫秒
                        int sleepTime = 10 + random.nextInt(40);
                        Thread.sleep(sleepTime);
                        
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "线程 " + threadId + " 异常", e);
                        break;
                    }
                }
                
                Log.d(TAG, "线程 " + threadId + " 停止");
            }, "StressTest-" + i);
            
            stressTestThreads[i].start();
        }
        
        // 更新按钮文本
        handler.post(() -> {
            showToast("压力测试运行中...\n再次点击按钮停止");
        });
    }

    /**
     * 停止压力测试
     */
    private void stopStressTest() {
        if (!isStressTestRunning) {
            return;
        }

        Log.i(TAG, "停止压力测试");
        isStressTestRunning = false;
        
        // 等待所有线程结束
        if (stressTestThreads != null) {
            for (Thread thread : stressTestThreads) {
                if (thread != null && thread.isAlive()) {
                    thread.interrupt();
                }
            }
            
            // 等待线程结束
            new Thread(() -> {
                try {
                    for (Thread thread : stressTestThreads) {
                        if (thread != null) {
                            thread.join(1000); // 最多等待1秒
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                
                handler.post(() -> {
                    // 恢复按钮文本
                    btnStressTest.setText("💥 压力测试 (100线程持续分配)");
                    btnStressTest.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFFF5722) // 橙色
                    );
                    
                    updateStats();
                    SoHook.MemoryStats stats = SoHook.getMemoryStats();
                    String result = String.format("✅ 压力测试已停止\n\n" +
                            "最终统计：\n" +
                            "总泄漏数: %,d 个\n" +
                            "总泄漏大小: %s\n\n" +
                            "请在 Web Dashboard 中查看详细数据",
                            stats.currentAllocCount,
                            formatBytes(stats.currentAllocSize));
                    
                    showToast("压力测试已停止");
                    
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("压力测试已停止")
                        .setMessage(result)
                        .setPositiveButton("确定", null)
                        .show();
                    
                    Log.i(TAG, result);
                });
            }).start();
        }
    }

    /**
     * 重置统计
     */
    public void onResetStatsClick(View view) {
        SoHook.resetStats();
        SoHook.resetFdStats();
        Log.i(TAG, "统计已重置");
        showToast("统计已重置");
        updateStats();
    }

    /**
     * 更新统计信息显示
     */
    private void updateStats() {
        SoHook.MemoryStats memStats = SoHook.getMemoryStats();
        SoHook.FdStats fdStats = SoHook.getFdStats();
        
        StringBuilder sb = new StringBuilder();
        sb.append("📊 内存统计\n\n");
        sb.append("总分配次数: ").append(memStats.totalAllocCount).append("\n");
        sb.append("总分配大小: ").append(formatBytes(memStats.totalAllocSize)).append("\n");
        sb.append("总释放次数: ").append(memStats.totalFreeCount).append("\n");
        sb.append("总释放大小: ").append(formatBytes(memStats.totalFreeSize)).append("\n");
        sb.append("\n");
        sb.append("⚠️ 当前泄漏次数: ").append(memStats.currentAllocCount).append("\n");
        sb.append("⚠️ 当前泄漏大小: ").append(formatBytes(memStats.currentAllocSize)).append("\n");
        
        sb.append("\n━━━━━━━━━━━━━━━━\n\n");
        sb.append("📁 文件描述符统计\n\n");
        sb.append("总打开次数: ").append(fdStats.totalOpenCount).append("\n");
        sb.append("总关闭次数: ").append(fdStats.totalCloseCount).append("\n");
        sb.append("\n");
        sb.append("⚠️ 当前未关闭: ").append(fdStats.currentOpenCount).append("\n");
        
        tvStats.setText(sb.toString());
    }

    /**
     * 格式化字节大小
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 获取本地 IP 地址
     */
    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); 
                 en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); 
                     enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && 
                        inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取 IP 地址失败", e);
        }
        return "localhost";
    }

    /**
     * 显示 Toast
     */
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
