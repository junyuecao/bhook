# SoHook 使用示例

## 完整示例

### 1. 在Application中初始化

```java
package com.example.myapp;

import android.app.Application;
import android.util.Log;
import com.sohook.SoHook;

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 初始化SoHook
        int ret = SoHook.init(BuildConfig.DEBUG);
        if (ret == 0) {
            Log.i(TAG, "SoHook初始化成功");
        } else {
            Log.e(TAG, "SoHook初始化失败: " + ret);
        }
    }
}
```

### 2. 在Activity中使用

```java
package com.example.myapp;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.sohook.SoHook;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private boolean isHooked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 开始监控按钮
        Button btnStartHook = findViewById(R.id.btn_start_hook);
        btnStartHook.setOnClickListener(v -> startMemoryHook());

        // 停止监控按钮
        Button btnStopHook = findViewById(R.id.btn_stop_hook);
        btnStopHook.setOnClickListener(v -> stopMemoryHook());

        // 获取统计按钮
        Button btnGetStats = findViewById(R.id.btn_get_stats);
        btnGetStats.setOnClickListener(v -> getMemoryStats());

        // 生成报告按钮
        Button btnGenerateReport = findViewById(R.id.btn_generate_report);
        btnGenerateReport.setOnClickListener(v -> generateLeakReport());

        // 导出报告按钮
        Button btnExportReport = findViewById(R.id.btn_export_report);
        btnExportReport.setOnClickListener(v -> exportLeakReport());

        // 重置统计按钮
        Button btnReset = findViewById(R.id.btn_reset);
        btnReset.setOnClickListener(v -> resetStats());
    }

    private void startMemoryHook() {
        if (isHooked) {
            Log.w(TAG, "已经在监控中");
            return;
        }

        // 指定要监控的so库
        List<String> soNames = Arrays.asList(
            "libnative-lib.so",
            "libtest.so"
        );

        int ret = SoHook.hook(soNames);
        if (ret == 0) {
            isHooked = true;
            Log.i(TAG, "开始监控内存分配");
        } else {
            Log.e(TAG, "启动监控失败: " + ret);
        }
    }

    private void stopMemoryHook() {
        if (!isHooked) {
            Log.w(TAG, "未在监控中");
            return;
        }

        List<String> soNames = Arrays.asList(
            "libnative-lib.so",
            "libtest.so"
        );

        int ret = SoHook.unhook(soNames);
        if (ret == 0) {
            isHooked = false;
            Log.i(TAG, "停止监控内存分配");
        } else {
            Log.e(TAG, "停止监控失败: " + ret);
        }
    }

    private void getMemoryStats() {
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        Log.i(TAG, "=== 内存统计 ===");
        Log.i(TAG, "总分配次数: " + stats.totalAllocCount);
        Log.i(TAG, "总分配大小: " + formatBytes(stats.totalAllocSize));
        Log.i(TAG, "总释放次数: " + stats.totalFreeCount);
        Log.i(TAG, "总释放大小: " + formatBytes(stats.totalFreeSize));
        Log.i(TAG, "当前泄漏次数: " + stats.currentAllocCount);
        Log.i(TAG, "当前泄漏大小: " + formatBytes(stats.currentAllocSize));
    }

    private void generateLeakReport() {
        String report = SoHook.getLeakReport();
        Log.i(TAG, "=== 内存泄漏报告 ===");
        Log.i(TAG, report);
    }

    private void exportLeakReport() {
        // 注意：需要申请存储权限
        String filePath = getExternalFilesDir(null) + "/leak_report.txt";
        int ret = SoHook.dumpLeakReport(filePath);
        if (ret == 0) {
            Log.i(TAG, "报告已导出到: " + filePath);
        } else {
            Log.e(TAG, "导出报告失败: " + ret);
        }
    }

    private void resetStats() {
        SoHook.resetStats();
        Log.i(TAG, "统计信息已重置");
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
}
```

### 3. 定时检查内存泄漏

```java
package com.example.myapp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.sohook.SoHook;

public class MemoryLeakChecker {
    private static final String TAG = "MemoryLeakChecker";
    private static final long CHECK_INTERVAL = 30000; // 30秒检查一次

    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            checkMemoryLeak();
            handler.postDelayed(this, CHECK_INTERVAL);
        }
    };

    public void startChecking() {
        handler.post(checkRunnable);
        Log.i(TAG, "开始定时检查内存泄漏");
    }

    public void stopChecking() {
        handler.removeCallbacks(checkRunnable);
        Log.i(TAG, "停止定时检查内存泄漏");
    }

    private void checkMemoryLeak() {
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        // 如果有未释放的内存，输出警告
        if (stats.currentAllocCount > 0) {
            Log.w(TAG, String.format(
                "检测到可能的内存泄漏: %d次分配未释放，共 %d 字节",
                stats.currentAllocCount,
                stats.currentAllocSize
            ));
            
            // 如果泄漏超过阈值，生成详细报告
            if (stats.currentAllocSize > 10 * 1024 * 1024) { // 10MB
                String report = SoHook.getLeakReport();
                Log.e(TAG, "严重内存泄漏！\n" + report);
            }
        }
    }
}
```

### 4. 在测试中使用

```java
package com.example.myapp;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.sohook.SoHook;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MemoryLeakTest {

    @Before
    public void setUp() {
        // 初始化SoHook
        SoHook.init(true);
        
        // 重置统计
        SoHook.resetStats();
        
        // 开始监控
        List<String> soNames = Arrays.asList("libnative-lib.so");
        SoHook.hook(soNames);
    }

    @After
    public void tearDown() {
        // 停止监控
        List<String> soNames = Arrays.asList("libnative-lib.so");
        SoHook.unhook(soNames);
    }

    @Test
    public void testNoMemoryLeak() {
        // 执行一些操作
        performSomeOperations();
        
        // 检查是否有内存泄漏
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        assertEquals("不应该有内存泄漏", 0, stats.currentAllocCount);
        assertEquals("不应该有内存泄漏", 0, stats.currentAllocSize);
    }

    @Test
    public void testMemoryAllocation() {
        // 获取初始统计
        SoHook.MemoryStats before = SoHook.getMemoryStats();
        
        // 执行内存分配操作
        allocateMemory();
        
        // 获取分配后的统计
        SoHook.MemoryStats after = SoHook.getMemoryStats();
        
        // 验证分配次数增加
        assertTrue("分配次数应该增加", 
            after.totalAllocCount > before.totalAllocCount);
        assertTrue("分配大小应该增加", 
            after.totalAllocSize > before.totalAllocSize);
    }

    private void performSomeOperations() {
        // 执行一些操作的代码
    }

    private void allocateMemory() {
        // 执行内存分配的代码
    }
}
```

## 输出示例

### 内存统计输出

```
=== 内存统计 ===
总分配次数: 1523
总分配大小: 2.45 MB
总释放次数: 1498
总释放大小: 2.38 MB
当前泄漏次数: 25
当前泄漏大小: 72.50 KB
```

### 泄漏报告输出

```
=== Memory Leak Report ===
Total Allocations: 1523 (2568192 bytes)
Total Frees: 1498 (2494464 bytes)
Current Leaks: 25 (73728 bytes)

Leak #1: ptr=0x7a8c001000, size=4096, so=libnative-lib.so
Leak #2: ptr=0x7a8c002000, size=2048, so=libnative-lib.so
Leak #3: ptr=0x7a8c003000, size=8192, so=libtest.so
...
```
