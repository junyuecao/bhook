# SoHook - 基于ByteHook的内存泄漏检测库

## 简介

SoHook是一个基于ByteHook的Android内存泄漏检测库，可以监控指定so库的内存分配和释放，帮助开发者发现内存泄漏问题。

## 功能特性

- ✅ 监控指定so库的内存分配（malloc/calloc/realloc）
- ✅ 监控指定so库的内存释放（free）
- ✅ 实时统计内存分配和释放情况
- ✅ 生成内存泄漏报告
- ✅ 支持导出报告到文件
- ✅ 支持调试模式
- ✅ **性能测试套件** - 评估和优化追踪性能

## 快速开始

### 1. 初始化

在应用启动时初始化SoHook：

```java
import com.sohook.SoHook;

// 初始化，开启调试模式
int ret = SoHook.init(true);
if (ret == 0) {
    Log.i(TAG, "SoHook初始化成功");
} else {
    Log.e(TAG, "SoHook初始化失败: " + ret);
}
```

### 2. 开始监控

指定需要监控的so库：

```java
import java.util.Arrays;
import java.util.List;

// 监控指定的so库
List<String> soNames = Arrays.asList(
    "libnative-lib.so",
    "libtest.so"
);

int ret = SoHook.hook(soNames);
if (ret == 0) {
    Log.i(TAG, "开始监控内存");
}
```

### 3. 获取内存统计

实时获取内存分配统计信息：

```java
SoHook.MemoryStats stats = SoHook.getMemoryStats();
Log.i(TAG, "内存统计: " + stats.toString());
// 输出示例：
// MemoryStats{
//   totalAllocCount=1000,
//   totalAllocSize=102400,
//   totalFreeCount=950,
//   totalFreeSize=97280,
//   currentAllocCount=50,
//   currentAllocSize=5120
// }
```

### 4. 生成泄漏报告

获取内存泄漏报告：

```java
// 获取报告字符串
String report = SoHook.getLeakReport();
Log.i(TAG, report);

// 或导出到文件
String filePath = "/sdcard/leak_report.txt";
int ret = SoHook.dumpLeakReport(filePath);
if (ret == 0) {
    Log.i(TAG, "报告已导出到: " + filePath);
}
```

### 5. 停止监控

停止对指定so库的监控：

```java
int ret = SoHook.unhook(soNames);
if (ret == 0) {
    Log.i(TAG, "停止监控");
}
```

### 6. 重置统计

重置所有统计信息：

```java
SoHook.resetStats();
```

### 7. 性能测试 🆕

评估内存追踪的性能开销（测试运行在被 hook 的 libsample.so 中）：

```java
// 初始化并开始监控
SoHook.init(true);
SoHook.hook(Arrays.asList("libsample.so"));

// 快速基准测试（推荐）
NativeHacker.quickBenchmark(5000);  // 5000次分配/释放
// 查看 logcat: adb logcat -s SamplePerf

// 或运行完整测试套件
NativeHacker.runPerfTests();
// 包含3个测试场景：批量、随机、多线程

// 查看追踪统计
SoHook.MemoryStats stats = SoHook.getMemoryStats();
// stats 会显示 5000+ 次分配和释放
```

**性能测试文档**：
- 📖 [快速开始](./PERF_QUICK_START.md) - 5分钟快速测试
- 📊 [详细文档](./PERFORMANCE_TEST.md) - 完整测试说明
- 🔧 [优化计划](./OPTIMIZATION_PLAN.md) - 性能优化指南
- 🔄 [重构说明](./PERF_TEST_REFACTOR.md) - 重构详情

## API文档

### SoHook类

#### 静态方法

| 方法 | 说明 | 参数 | 返回值 |
|------|------|------|--------|
| `init(boolean debug)` | 初始化内存泄漏检测库 | debug: 是否开启调试模式 | 0表示成功，其他值表示失败 |
| `hook(List<String> soNames)` | 开始监控指定so库 | soNames: so文件名列表 | 0表示成功，其他值表示失败 |
| `unhook(List<String> soNames)` | 停止监控指定so库 | soNames: so文件名列表 | 0表示成功，其他值表示失败 |
| `getLeakReport()` | 获取内存泄漏报告 | 无 | 报告字符串 |
| `dumpLeakReport(String filePath)` | 导出报告到文件 | filePath: 文件路径 | 0表示成功，其他值表示失败 |
| `getMemoryStats()` | 获取内存统计信息 | 无 | MemoryStats对象 |
| `resetStats()` | 重置统计信息 | 无 | 无 |

### MemoryStats类

内存统计信息类，包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `totalAllocCount` | long | 总分配次数 |
| `totalAllocSize` | long | 总分配大小（字节） |
| `totalFreeCount` | long | 总释放次数 |
| `totalFreeSize` | long | 总释放大小（字节） |
| `currentAllocCount` | long | 当前未释放的分配次数 |
| `currentAllocSize` | long | 当前未释放的内存大小（字节） |

## 架构设计

### 代码结构

```
sohook/
├── src/main/
│   ├── java/com/sohook/
│   │   └── SoHook.java                    # Java API接口
│   └── cpp/
│       ├── sohook_jni.c                   # JNI实现
│       ├── memory_tracker.h               # 内存追踪器头文件
│       ├── memory_tracker.c               # 内存追踪器实现
│       ├── memory_tracker_perf_test.h 🆕  # 性能测试头文件
│       ├── memory_tracker_perf_test.c 🆕  # 性能测试实现
│       └── CMakeLists.txt                 # CMake构建配置
├── PERFORMANCE_TEST.md 🆕                  # 性能测试详细文档
├── PERF_QUICK_START.md 🆕                  # 性能测试快速开始
├── OPTIMIZATION_PLAN.md 🆕                 # 性能优化实施计划
└── build.gradle                           # Gradle构建配置
```

### 工作原理

1. **初始化阶段**：调用`bytehook_init()`初始化ByteHook库
2. **Hook阶段**：使用`bytehook_hook_all()`对指定so库的malloc/calloc/realloc/free函数进行hook
3. **监控阶段**：
   - 当目标so库调用malloc/calloc/realloc时，记录分配信息
   - 当目标so库调用free时，移除对应的分配记录
   - 实时更新统计信息
4. **报告阶段**：遍历未释放的内存记录，生成泄漏报告

### 技术要点

- 使用ByteHook进行PLT/GOT hook
- 使用线程本地存储（TLS）防止递归调用
- 使用互斥锁保护共享数据结构
- 使用链表存储内存分配记录

## 注意事项

1. **⚠️ 不要监控libc.so**：监控系统库会导致递归调用和死锁，建议只监控应用自己的so库
2. **性能影响**：内存监控会带来一定的性能开销，建议仅在调试阶段使用
   - 使用 `quickBenchmark()` 评估性能开销
   - 当前实现: malloc ~10 μs/op, free ~25 μs/op
   - 优化后目标: malloc < 3 μs/op, free < 3 μs/op
3. **线程安全**：所有API都是线程安全的
4. **初始化顺序**：必须先调用`init()`再调用其他方法
5. **文件权限**：导出报告时需要确保有文件写入权限
6. **内存开销**：每个未释放的内存块都会占用额外的记录空间（~80字节/块）

## 性能优化路线图

基于性能测试结果，按优先级排序：

### 🔴 高优先级（预期提升 10-100x）
- [ ] **哈希表替代链表** - 解决 O(n) 查找问题
  - 当前: free 操作 O(n) 复杂度
  - 优化后: O(1) 查找
  - 预期提升: 10-100x（取决于内存块数量）

### 🟡 中优先级（预期提升 2-3x）
- [ ] **内存池** - 减少系统 malloc 调用
  - 预分配 record 对象池
  - 减少 50-70% 的系统调用

### 🟢 低优先级
- [ ] **分段锁** - 降低多线程锁竞争（4-8x 多线程场景）
- [ ] **实现 backtrace** - 提升调试能力
- [ ] **采样模式** - 降低整体开销
- [ ] **内存分配热点分析**
- [ ] **实时监控和告警**

详见 [优化实施计划](./OPTIMIZATION_PLAN.md)

## 前置条件

- Android Studio 4.0+
- Android NDK
- CMake 3.18.1+
- Android SDK API 16+ (与ByteHook保持一致)用于编译native代码

## 许可证

与ByteHook保持一致
