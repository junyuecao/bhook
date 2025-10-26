# SoHook Web 监控 - 5 分钟快速开始

## 概述

通过 Web 界面实时监控 Android 应用的内存泄漏情况。

## 前置条件

- Android Studio
- Node.js 16+
- Android 设备或模拟器

## 步骤 1: 启动 Web Dashboard (1 分钟)

```bash
cd sohook-web
npm install
npm run dev
```

浏览器访问: http://localhost:5173

## 步骤 2: 在 Android 应用中集成 (2 分钟)

### 2.1 添加依赖

在你的 app 模块的 `build.gradle` 中：

```gradle
dependencies {
    implementation project(':sohook')
}
```

### 2.2 初始化并启动服务器

在 `MainActivity.java` 或 `Application` 类中：

```java
import com.sohook.SoHook;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 1. 初始化 SoHook
        SoHook.init(true);
        
        // 2. 启动 Web 服务器
        boolean success = SoHook.startWebServer(8080);
        if (success) {
            String ip = getLocalIpAddress();
            Log.i("SoHook", "Web 服务器已启动: http://" + ip + ":8080");
            
            // 可选：显示 Toast 提示用户
            Toast.makeText(this, "监控地址: http://" + ip + ":8080", 
                          Toast.LENGTH_LONG).show();
        }
        
        // 3. 开始监控你的 native 库
        SoHook.hook(Arrays.asList("libnative-lib.so"));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        SoHook.stopWebServer();
    }
    
    // 获取设备 IP 地址
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
            e.printStackTrace();
        }
        return "localhost";
    }
}
```

### 2.3 添加权限

在 `AndroidManifest.xml` 中：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## 步骤 3: 连接设备 (1 分钟)

### 方法 1: 同一网络（推荐）

1. 确保电脑和 Android 设备在同一 WiFi
2. 运行 Android 应用，查看 Logcat 中的 IP 地址
3. 在 Web Dashboard 的"连接状态"卡片中输入该地址
   - 例如: `http://192.168.1.100:8080`
4. 点击"测试连接"

### 方法 2: ADB 端口转发

如果不在同一网络：

```bash
# 转发端口
adb forward tcp:8080 tcp:8080
```

然后在 Web Dashboard 中使用: `http://localhost:8080`

## 步骤 4: 开始监控 (1 分钟)

连接成功后，你将看到：

✅ **实时内存统计**
- 总分配次数/大小
- 总释放次数/大小
- 当前泄漏次数/大小

✅ **内存趋势图**
- 实时折线图
- 泄漏大小和次数双轴显示

✅ **泄漏详情列表**
- 每个泄漏的地址、大小
- 可展开查看调用栈（需启用 backtrace）

## 常用操作

### 刷新数据
- 自动刷新：每 2 秒自动更新
- 手动刷新：点击"刷新数据"按钮

### 重置统计
点击"重置统计"按钮清空所有数据

### 启用调用栈
```java
// 初始化时启用（会影响性能 10-20x）
SoHook.init(true, true);

// 或运行时启用
SoHook.setBacktraceEnabled(true);
```

## 故障排除

### 无法连接

**检查清单：**
- [ ] Android 应用是否已启动
- [ ] Web 服务器是否成功启动（查看 Logcat）
- [ ] IP 地址是否正确
- [ ] 设备和电脑是否在同一网络
- [ ] 防火墙是否阻止端口 8080

**解决方法：**
```bash
# 使用 ADB 端口转发
adb forward tcp:8080 tcp:8080

# 然后使用 localhost
http://localhost:8080
```

### 数据不更新

- 确保已调用 `SoHook.hook()` 开始监控
- 检查连接状态是否为"已连接"
- 确保应用有内存分配活动

### 性能问题

```java
// 禁用调用栈以提升性能
SoHook.setBacktraceEnabled(false);

// 减少 Web 刷新频率（在 Web 端设置）
```

## 示例截图

### Web Dashboard
```
┌─────────────────────────────────────────────────────────┐
│  SoHook 内存监控                                         │
│  实时监控 Android 应用内存泄漏                            │
├─────────────────────────────────────────────────────────┤
│  [刷新数据] [重置统计]                                   │
├──────────────┬──────────────────────────────────────────┤
│ 连接状态     │  内存统计                                 │
│ ✅ 已连接    │  总分配: 1000 次 / 100 KB                │
│              │  总释放: 950 次 / 95 KB                  │
│ 服务器地址   │  当前泄漏: 50 次 / 5 KB                  │
│ 192.168.1.100│                                          │
│              │  内存趋势图                               │
│ [测试连接]   │  [实时折线图]                            │
│              │                                          │
│              │  内存泄漏列表                             │
│              │  ⚠️ 50 个泄漏 | 5 KB                    │
│              │  - 0x7b8c001000 (1024 B)                │
│              │  - 0x7b8c002000 (512 B)                 │
└──────────────┴──────────────────────────────────────────┘
```

## 下一步

- 📖 查看 [详细使用文档](../../sohook/WEB_SERVER_USAGE.md)
- 📊 查看 [实现总结](sohook-web/IMPLEMENTATION_SUMMARY.md)
- 🔧 查看 [SoHook README](../../sohook/README.md)

## 完整示例

参考 `bytehook_sample` 模块中的完整示例代码。

---

**提示：** 此功能仅用于开发和测试环境，不要在生产环境中使用。
