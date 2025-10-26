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
    private boolean isHooked = false;
    private boolean isServerRunning = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateStatsRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sohook_web_test);

        tvServerStatus = findViewById(R.id.tv_server_status);
        tvServerUrl = findViewById(R.id.tv_server_url);
        tvStats = findViewById(R.id.tv_stats);

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
     * 重置统计
     */
    public void onResetStatsClick(View view) {
        SoHook.resetStats();
        Log.i(TAG, "统计已重置");
        showToast("统计已重置");
        updateStats();
    }

    /**
     * 更新统计信息显示
     */
    private void updateStats() {
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        StringBuilder sb = new StringBuilder();
        sb.append("📊 内存统计\n\n");
        sb.append("总分配次数: ").append(stats.totalAllocCount).append("\n");
        sb.append("总分配大小: ").append(formatBytes(stats.totalAllocSize)).append("\n");
        sb.append("总释放次数: ").append(stats.totalFreeCount).append("\n");
        sb.append("总释放大小: ").append(formatBytes(stats.totalFreeSize)).append("\n");
        sb.append("\n");
        sb.append("⚠️ 当前泄漏次数: ").append(stats.currentAllocCount).append("\n");
        sb.append("⚠️ 当前泄漏大小: ").append(formatBytes(stats.currentAllocSize)).append("\n");
        
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
