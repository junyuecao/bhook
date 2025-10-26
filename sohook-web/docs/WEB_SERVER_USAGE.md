# SoHook Web 服务器使用指南

## 概述

SoHook 提供了内置的 HTTP 服务器，可以通过 Web 界面实时查看内存泄漏监控数据。

## 快速开始

### 1. 在 Android 应用中启动 Web 服务器

```java
import com.sohook.SoHook;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. 初始化 SoHook
        SoHook.init(true);  // 开启调试模式
        
        // 2. 启动 Web 服务器（默认端口 8080）
        boolean success = SoHook.startWebServer();
        if (success) {
            Log.i("SoHook", "Web server started successfully");
            // 获取设备 IP 地址并显示
            String ipAddress = getLocalIpAddress();
            Log.i("SoHook", "访问地址: http://" + ipAddress + ":8080");
        }
        
        // 3. 开始监控内存
        SoHook.hook(Arrays.asList("libnative-lib.so"));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止 Web 服务器
        SoHook.stopWebServer();
    }
    
    // 获取本地 IP 地址
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

### 2. 添加网络权限

在 `AndroidManifest.xml` 中添加：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 3. 启动 Web Dashboard

```bash
cd sohook-web
npm install
npm run dev
```

访问 http://localhost:5173

### 4. 连接到 Android 设备

1. 确保电脑和 Android 设备在同一网络
2. 在 Web Dashboard 的"连接状态"卡片中输入设备 IP 和端口
   - 例如：`http://192.168.1.100:8080`
3. 点击"测试连接"
4. 连接成功后，数据会自动刷新

## API 接口

Web 服务器提供以下 RESTful API：

### GET /api/health

健康检查

**响应示例：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "ok",
    "timestamp": 1698765432000
  }
}
```

### GET /api/stats

获取内存统计信息

**响应示例：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalAllocCount": 1000,
    "totalAllocSize": 102400,
    "totalFreeCount": 950,
    "totalFreeSize": 97280,
    "currentAllocCount": 50,
    "currentAllocSize": 5120
  }
}
```

### GET /api/leaks

获取内存泄漏列表（结构化数据）

**响应示例：**
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "ptr": "0x7b8c001000",
      "size": 1024,
      "timestamp": 0,
      "backtrace": [
        "malloc+12",
        "my_function+45",
        "main+123"
      ]
    }
  ]
}
```

### GET /api/leak-report

获取完整的泄漏报告（文本格式）

**响应示例：**
```json
{
  "code": 0,
  "message": "success",
  "data": "=== Memory Leak Report ===\n..."
}
```

### POST /api/reset

重置所有统计数据

**响应示例：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "message": "Statistics reset successfully"
  }
}
```

## 高级配置

### 自定义端口

```java
// 使用自定义端口
SoHook.startWebServer(9090);
```

### 检查服务器状态

```java
if (SoHook.isWebServerRunning()) {
    Log.i("SoHook", "Web server is running");
}
```

### 启用栈回溯

```java
// 初始化时启用栈回溯（会影响性能）
SoHook.init(true, true);

// 或运行时动态启用
SoHook.setBacktraceEnabled(true);
```

## 网络配置

### 使用 ADB 端口转发（无需同一网络）

如果设备和电脑不在同一网络，可以使用 ADB 端口转发：

```bash
# 转发端口
adb forward tcp:8080 tcp:8080

# 然后在 Web Dashboard 中使用
# http://localhost:8080
```

### 防火墙设置

确保防火墙允许端口 8080（或自定义端口）的入站连接。

## 故障排除

### 无法连接到设备

1. 检查设备和电脑是否在同一网络
2. 检查 IP 地址是否正确
3. 检查端口是否被占用
4. 检查防火墙设置
5. 尝试使用 ADB 端口转发

### 数据不刷新

1. 检查连接状态是否为"已连接"
2. 检查是否已调用 `SoHook.hook()` 开始监控
3. 检查应用是否有内存分配活动

### 性能问题

1. 如果不需要调用栈，禁用栈回溯：`SoHook.setBacktraceEnabled(false)`
2. 增加 Web Dashboard 的刷新间隔
3. 只监控必要的 so 库

## 安全建议

⚠️ **重要提示：**

1. **仅在开发/测试环境使用** - 不要在生产环境启用 Web 服务器
2. **网络安全** - Web 服务器没有身份验证，任何能访问该端口的人都可以查看数据
3. **性能影响** - 内存监控会带来性能开销，不适合在生产环境长期运行

## 示例项目

参考 `bytehook_sample` 模块中的完整示例。
