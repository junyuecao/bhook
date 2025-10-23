# SoHook 快速开始指南

## 前置条件

- Android Studio 4.0+
- Android NDK
- CMake 3.18.1+
- Android SDK API 24+

## 构建步骤

### 1. 同步项目

在Android Studio中打开bhook项目根目录，等待Gradle同步完成。

### 2. 编译项目

```bash
# 在项目根目录执行
./gradlew :sohook:assembleDebug
```

或者在Android Studio中选择 `Build > Make Project`

### 3. 检查编译产物

编译成功后，可以在以下路径找到生成的库文件：

```
sohook/build/intermediates/cmake/debug/obj/
├── arm64-v8a/
│   └── libsohook.so
└── armeabi-v7a/
    └── libsohook.so
```

## 集成到你的项目

### 方式1: 作为模块依赖

如果你的项目在同一个工程中：

1. 在你的app的`build.gradle`中添加依赖：

```gradle
dependencies {
    implementation project(':sohook')
}
```

2. 在`settings.gradle`中确保包含sohook模块：

```gradle
include ':sohook'
```

### 方式2: 使用AAR文件

1. 生成AAR文件：

```bash
./gradlew :sohook:assembleRelease
```

2. AAR文件位置：

```
sohook/build/outputs/aar/sohook-release.aar
```

3. 在你的项目中导入AAR：

将AAR文件复制到你的app的`libs`目录，然后在`build.gradle`中添加：

```gradle
dependencies {
    implementation files('libs/sohook-release.aar')
    implementation project(':bytehook')  // 还需要依赖bytehook
}
```

## 最简使用示例

### 1. 在Application中初始化

```java
public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SoHook.init(true);  // 开启调试模式
    }
}
```

### 2. 在需要的地方开始监控

```java
import com.sohook.SoHook;
import java.util.Arrays;

// 监控指定的so库
SoHook.hook(Arrays.asList("libnative-lib.so"));

// 执行你的业务逻辑...

// 获取内存统计
SoHook.MemoryStats stats = SoHook.getMemoryStats();
Log.i("SoHook", "当前泄漏: " + stats.currentAllocCount + " 次, " 
    + stats.currentAllocSize + " 字节");

// 生成泄漏报告
String report = SoHook.getLeakReport();
Log.i("SoHook", report);
```

## 常见问题

### Q1: 编译时找不到bytehook

**A**: 确保bytehook模块在同一个项目中，并且在`settings.gradle`中已包含：

```gradle
include ':bytehook'
include ':sohook'
```

### Q2: 运行时找不到libsohook.so

**A**: 检查以下几点：
1. 确保NDK已正确安装
2. 检查`build.gradle`中的ndk配置是否正确
3. 确保CMakeLists.txt路径正确
4. 清理并重新编译：`./gradlew clean :sohook:assembleDebug`

### Q3: Hook不生效

**A**: 可能的原因：
1. 没有先调用`SoHook.init()`
2. so库名称不正确（需要包含"lib"前缀和".so"后缀）
3. 目标so库还未加载到内存中
4. ByteHook初始化失败

调试方法：
```java
// 开启调试模式
SoHook.init(true);

// 查看logcat输出
adb logcat | grep -E "SoHook|MemoryTracker|ByteHook"
```

### Q4: 性能影响有多大？

**A**: 内存监控会带来一定的性能开销：
- 每次内存分配/释放都会增加约10-50微秒的延迟
- 内存开销：每个未释放的内存块约占用100字节的记录空间
- 建议仅在调试阶段使用，不要在生产环境开启

### Q5: 如何监控系统库（如libc.so）？

**A**: 可以直接传入系统库名称：

```java
SoHook.hook(Arrays.asList("libc.so"));
```

但要注意：
1. 系统库的内存分配非常频繁，会产生大量记录
2. 可能会影响性能
3. 建议只在必要时监控系统库

## 验证安装

运行以下测试代码验证SoHook是否正常工作：

```java
import com.sohook.SoHook;
import android.util.Log;

public class SoHookTest {
    private static final String TAG = "SoHookTest";
    
    public static void test() {
        // 1. 初始化
        int ret = SoHook.init(true);
        Log.i(TAG, "Init result: " + ret);
        
        // 2. 获取初始统计
        SoHook.MemoryStats before = SoHook.getMemoryStats();
        Log.i(TAG, "Before: " + before);
        
        // 3. 开始监控（这里监控libc.so作为示例）
        ret = SoHook.hook(Arrays.asList("libc.so"));
        Log.i(TAG, "Hook result: " + ret);
        
        // 4. 分配一些内存（通过JNI或其他方式）
        // ... 你的测试代码 ...
        
        // 5. 获取统计
        SoHook.MemoryStats after = SoHook.getMemoryStats();
        Log.i(TAG, "After: " + after);
        
        // 6. 生成报告
        String report = SoHook.getLeakReport();
        Log.i(TAG, "Report:\n" + report);
        
        // 7. 停止监控
        ret = SoHook.unhook(Arrays.asList("libc.so"));
        Log.i(TAG, "Unhook result: " + ret);
    }
}
```

如果看到类似以下输出，说明安装成功：

```
I/SoHookTest: Init result: 0
I/SoHookTest: Before: MemoryStats{totalAllocCount=0, ...}
I/SoHookTest: Hook result: 0
I/SoHookTest: After: MemoryStats{totalAllocCount=123, ...}
I/SoHookTest: Report:
              === Memory Leak Report ===
              Total Allocations: 123 (45678 bytes)
              ...
I/SoHookTest: Unhook result: 0
```

## 下一步

- 查看 [README.md](README.md) 了解详细功能
- 查看 [USAGE_EXAMPLE.md](USAGE_EXAMPLE.md) 了解更多使用示例
- 查看 [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) 了解项目结构

## 获取帮助

如果遇到问题：
1. 查看logcat日志：`adb logcat | grep -E "SoHook|MemoryTracker"`
2. 检查ByteHook是否正常工作
3. 确认NDK和CMake版本是否符合要求
