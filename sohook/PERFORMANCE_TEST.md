# 内存追踪器性能测试文档

## 概述

本文档介绍如何使用内存追踪器的性能测试功能，用于评估和优化内存追踪的性能。

## 测试文件

- **C 实现**: `memory_tracker_perf_test.c` / `memory_tracker_perf_test.h`
- **Java 接口**: `SoHook.java` 中的 `runPerfTests()` 和 `quickBenchmark()`
- **UI 界面**: `SoHookTestActivity` 中的性能测试按钮

## 测试套件

### 1. 完整性能测试 (`runPerfTests()`)

运行 5 个不同场景的性能测试:

#### Test 1: 顺序分配和释放
- **目的**: 测试基本的 malloc/free 开销
- **操作**: 1000 次顺序分配 64 字节，然后顺序释放
- **指标**: 平均每次操作的时间

#### Test 2: 批量分配后释放
- **目的**: 测试最坏情况下的链表查找性能
- **操作**: 5000 次分配（变化大小），然后全部释放
- **指标**: 分配阶段和释放阶段的分别耗时
- **关键**: 释放时链表最长，能暴露 O(n) 查找问题

#### Test 3: 随机分配和释放
- **目的**: 模拟真实应用场景
- **操作**: 10000 次随机的分配或释放操作
- **指标**: 平均操作时间和吞吐量

#### Test 4: 多线程并发测试
- **目的**: 测试锁竞争和并发性能
- **操作**: 4 个线程同时进行内存操作
- **指标**: 总时间和平均线程时间

#### Test 5: 压力测试
- **目的**: 测试大规模内存块追踪
- **操作**: 分配 10000 个内存块，从中间开始释放
- **指标**: 大规模场景下的性能表现

### 2. 快速基准测试 (`quickBenchmark(iterations)`)

- **目的**: 快速对比优化前后的性能
- **操作**: 
  1. 连续分配 N 次 64 字节内存
  2. 连续释放 N 次
- **输出**: malloc 和 free 的平均耗时

## 使用方法

### 方法 1: 通过 UI 界面

1. 打开 `SoHookTestActivity`
2. 点击 "开始监控 (libsample.so)"
3. 点击 "运行完整性能测试" 或 "快速基准测试"
4. 查看 logcat 输出结果

```bash
adb logcat -s MemTrackerPerf
```

### 方法 2: 通过 Java 代码

```java
// 初始化
SoHook.init(true);
SoHook.hook(Arrays.asList("libsample.so"));

// 运行完整测试
SoHook.runPerfTests();

// 或运行快速基准测试
SoHook.quickBenchmark(5000);
```

### 方法 3: 通过 C 代码

```c
#include "memory_tracker_perf_test.h"

// 运行完整测试
memory_tracker_run_perf_tests();

// 或运行快速基准测试
memory_tracker_quick_benchmark(5000);
```

## 性能指标解读

### 关键指标

1. **Avg Time (μs/op)**: 平均每次操作的时间（微秒）
   - < 10 μs: 优秀
   - 10-50 μs: 良好
   - 50-100 μs: 可接受
   - > 100 μs: 需要优化

2. **Throughput (ops/sec)**: 吞吐量（每秒操作数）
   - > 100,000: 优秀
   - 50,000-100,000: 良好
   - 10,000-50,000: 可接受
   - < 10,000: 需要优化

3. **Free Phase Time**: 释放阶段耗时
   - 如果远大于分配阶段，说明链表查找是瓶颈

### 性能基准（优化前）

基于当前链表实现的预期性能:

| 测试场景 | 内存块数 | 预期 malloc | 预期 free | 总体评价 |
|---------|---------|------------|-----------|---------|
| 顺序 (1K) | 1,000 | ~5 μs | ~10 μs | 可接受 |
| 批量 (5K) | 5,000 | ~10 μs | ~50 μs | 明显 |
| 随机 (10K) | ~1,000 | ~20 μs | ~30 μs | 可接受 |
| 多线程 (4x500) | ~100 | ~30 μs | ~40 μs | 可接受 |
| 压力 (10K) | 10,000 | ~15 μs | ~500 μs | **严重** |

### 性能基准（优化后 - 哈希表）

预期优化后的性能提升:

| 测试场景 | 优化前 free | 优化后 free | 提升倍数 |
|---------|------------|------------|---------|
| 顺序 (1K) | ~10 μs | ~3 μs | **3x** |
| 批量 (5K) | ~50 μs | ~3 μs | **16x** |
| 随机 (10K) | ~30 μs | ~5 μs | **6x** |
| 压力 (10K) | ~500 μs | ~3 μs | **166x** |

## 优化建议

根据测试结果，按优先级实施以下优化:

### 🔴 高优先级
1. **哈希表替代链表** - 解决 O(n) 查找问题
2. **内存池** - 减少系统 malloc 调用
3. **分段锁** - 降低多线程锁竞争

### 🟡 中优先级
4. **实现 backtrace** - 提升调试价值
5. **采样模式** - 降低整体开销
6. **批量统计更新** - 减少锁持有时间

## 对比测试流程

优化前后对比测试步骤:

1. **记录基准**
   ```bash
   # 优化前运行基准测试
   SoHook.quickBenchmark(5000);
   # 记录输出的 malloc 和 free 时间
   ```

2. **实施优化**
   - 修改 `memory_tracker.c` 实现优化

3. **重新测试**
   ```bash
   # 优化后运行相同的基准测试
   SoHook.quickBenchmark(5000);
   # 对比时间差异
   ```

4. **计算提升**
   ```
   提升百分比 = (优化前时间 - 优化后时间) / 优化前时间 × 100%
   ```

## 注意事项

1. **性能测试运行在被 hook 的 so 中**:
   - 性能测试代码位于 `libsample.so` 中
   - 所有内存分配都会被 SoHook 追踪
   - 测试结束后可以看到追踪的内存统计（如 5000 次分配+释放）
   - 这样测量的是**真实的被 hook 后的性能**

2. **测试环境**: 
   - 关闭其他应用，减少干扰
   - 多次运行取平均值
   - 注意设备温度影响

3. **内存状态**:
   - 测试前调用 `resetStats()` 清空状态
   - 确保没有其他内存泄漏影响结果

4. **日志过滤**:
   ```bash
   # 只看性能测试日志
   adb logcat -s SamplePerf
   
   # 看所有相关日志
   adb logcat -s SamplePerf:I MemoryTracker:I SoHook:I
   ```

5. **性能影响**:
   - 完整测试套件会运行较长时间（30-60秒）
   - 在后台线程运行，避免阻塞 UI

## 示例输出

```
I/SamplePerf: ╔════════════════════════════════════════╗
I/SamplePerf: ║  Memory Tracker Performance Test      ║
I/SamplePerf: ║  (Testing HOOKED malloc/free)          ║
I/SamplePerf: ╚════════════════════════════════════════╝
I/SamplePerf: 
I/SamplePerf: [Test 1] Bulk Alloc Then Free
I/SamplePerf: [Test] Bulk Alloc Then Free (5000 blocks)
I/SamplePerf:   Alloc phase: 45.23 ms (9.046 μs/op)
I/SamplePerf:   Free phase:  125.67 ms (25.134 μs/op)
I/SamplePerf:   Total:       170.90 ms (17.090 μs/op)
I/SamplePerf:   Throughput:  58480 ops/sec
...
I/SamplePerf: 
I/SamplePerf: All allocations were tracked by SoHook.
I/SamplePerf: Check memory stats to see the results.
```

## 故障排查

### 问题: 测试无输出
- 检查是否已调用 `SoHook.init()`
- 检查是否已调用 `SoHook.hook()`
- 查看 logcat 是否有错误信息

### 问题: 性能异常差
- 检查是否在 debug 模式（debug 模式会有额外日志开销）
- 检查设备是否过热导致降频
- 确认没有其他应用占用 CPU

### 问题: 测试崩溃
- 检查内存是否充足
- 降低测试规模（如 `quickBenchmark(1000)`）
- 查看崩溃日志定位问题

## 总结

性能测试是优化的基础。通过系统的性能测试，可以:
- ✅ 量化当前性能瓶颈
- ✅ 验证优化效果
- ✅ 防止性能回退
- ✅ 指导优化方向

建议在每次重大优化后都运行完整测试套件，确保性能持续改进。
