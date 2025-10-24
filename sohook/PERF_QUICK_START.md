# 性能测试快速开始指南

## 5 分钟快速测试

### 步骤 1: 编译项目

```bash
cd D:\Work\bhook
.\gradlew assembleDebug
```

### 步骤 2: 安装并运行应用

```bash
adb install -r bytehook_sample\build\outputs\apk\debug\bytehook_sample-debug.apk
adb shell am start -n com.bytedance.android.bytehook.sample/.SoHookTestActivity
```

### 步骤 3: 开启日志监控

在新的终端窗口运行:

```bash
adb logcat -s SamplePerf:I MemoryTracker:I SoHook:I SoHookTest:I
```

### 步骤 4: 运行测试

在应用界面中:
1. 点击 **"开始监控 (libsample.so)"**
2. 点击 **"快速基准测试 (5000次)"**
3. 等待 5-10 秒
4. 查看终端输出的性能数据

### 步骤 5: 查看结果

你会看到类似这样的输出:

```
I/SamplePerf: ╔════════════════════════════════════════╗
I/SamplePerf: ║  Quick Benchmark (Hooked malloc/free) ║
I/SamplePerf: ╚════════════════════════════════════════╝
I/SamplePerf: Running benchmark with 5000 iterations...
I/SamplePerf: ╔════════════════════════════════════════╗
I/SamplePerf: ║  Benchmark Results                     ║
I/SamplePerf: ╠════════════════════════════════════════╣
I/SamplePerf: ║  Iterations: 5000
I/SamplePerf: ║  Malloc: 45.23 ms (9.046 μs/op)
I/SamplePerf: ║  Free:   125.67 ms (25.134 μs/op)
I/SamplePerf: ║  Total:  170.90 ms
I/SamplePerf: ╚════════════════════════════════════════╝
I/SamplePerf: 
I/SamplePerf: This measures malloc/free performance
I/SamplePerf: WITH memory tracking hook active.
```

**同时查看内存统计**，你会看到追踪的内存分配数量（5000次分配+释放）。

## 性能评估

根据输出判断性能状态:

### ✅ 优秀 (无需优化)
- Malloc < 5 μs/op
- Free < 5 μs/op

### 🟢 良好 (可选优化)
- Malloc 5-10 μs/op
- Free 5-20 μs/op

### 🟡 可接受 (建议优化)
- Malloc 10-20 μs/op
- Free 20-50 μs/op

### 🔴 需要优化
- Malloc > 20 μs/op
- Free > 50 μs/op

## 运行完整测试套件

如果需要更详细的性能分析:

1. 点击 **"运行完整性能测试"**
2. 等待 30-60 秒
3. 查看 logcat 输出的 5 个测试场景结果

## 对比优化效果

### 优化前记录基准

```bash
# 记录当前性能
adb logcat -s SamplePerf > perf_before.log
# 在应用中运行快速基准测试
```

### 实施优化

修改 `memory_tracker.c` 实现你的优化方案

### 优化后测试

```bash
# 重新编译
.\gradlew assembleDebug
adb install -r bytehook_sample\build\outputs\apk\debug\bytehook_sample-debug.apk

# 记录优化后性能
adb logcat -s SamplePerf > perf_after.log
# 在应用中运行相同的测试
```

### 对比结果

```bash
# 对比两个日志文件
fc perf_before.log perf_after.log
```

## 常见问题

### Q: 测试完成后看不到内存统计变化
**A**: 现在性能测试运行在 `libsample.so` 中，所有分配都会被追踪！
- 快速基准测试 5000 次会显示 5000 次分配和释放
- 完整测试会显示更多的内存操作
- 测试完成后点击"刷新统计信息"查看结果

### Q: 测试按钮点击无反应
**A**: 确保先点击 "开始监控" 按钮

### Q: 日志没有输出
**A**: 检查 logcat 过滤器是否正确:
```bash
adb logcat -c  # 清空日志
adb logcat -s SamplePerf  # 只看性能测试
```

### Q: 测试时间过长
**A**: 使用快速基准测试，或减少迭代次数:
```java
SoHook.quickBenchmark(1000);  // 减少到 1000 次
```

### Q: 性能数据波动大
**A**: 
- 关闭其他应用
- 多次运行取平均值
- 等待设备冷却

## 下一步

- 📖 阅读 [PERFORMANCE_TEST.md](./PERFORMANCE_TEST.md) 了解详细测试说明
- 🔧 开始实施优化（哈希表、内存池、分段锁）
- 📊 使用性能测试验证优化效果
- 🎯 目标: Free 操作从 25 μs 降至 < 5 μs

## 性能优化路线图

```
当前状态 (链表实现)
    ↓
[1] 实现哈希表 → 预期提升 10-100x
    ↓
[2] 添加内存池 → 预期提升 2-3x
    ↓
[3] 实现分段锁 → 预期提升 4-8x (多线程)
    ↓
最终目标: < 5 μs/op
```

开始你的优化之旅吧! 🚀
