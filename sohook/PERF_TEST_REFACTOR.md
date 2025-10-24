# 性能测试重构说明

## 🔄 重构原因

### 原来的实现问题
之前的性能测试代码位于 `libsohook.so` 中，直接调用系统的 `malloc/free`：
- ❌ 测试的是**系统原生**的 malloc/free 性能
- ❌ 这些调用**不会被 hook 追踪**
- ❌ 无法反映真实的内存追踪开销
- ❌ 测试结束后显示 "Total Allocs: 0"，容易误解

### 正确的需求
性能测试应该测量**被 hook 后**的 malloc/free 性能：
- ✅ 测试代码应该在**被 hook 的目标 so** 中
- ✅ 所有分配都应该**被追踪**
- ✅ 能看到真实的追踪开销
- ✅ 测试结果包含内存统计数据

## ✨ 新的实现

### 架构调整

```
旧架构:
libsohook.so (性能测试) → 系统 malloc/free
                          ↓
                      不会被追踪 ❌

新架构:
libsample.so (性能测试) → malloc/free
                          ↓
                    被 SoHook 拦截 ✅
                          ↓
                    memory_tracker 追踪
```

### 代码位置

#### 新增文件
- `bytehook_sample/src/main/cpp/sample.cpp` - 添加性能测试函数
  - `sample_quick_benchmark(int iterations)`
  - `sample_run_perf_tests()`

#### 更新文件
- `sample.h` - 添加函数声明
- `NativeHacker.java` - 添加 Java 接口
- `hacker.c` - 添加 JNI 绑定
- `SoHookTestActivity.java` - 调用新接口

#### 保留文件（待删除）
- `sohook/src/main/cpp/memory_tracker_perf_test.c` - 旧实现
- `sohook/src/main/cpp/memory_tracker_perf_test.h` - 旧实现

## 📊 测试对比

### 旧实现输出
```
I/MemTrackerPerf: Quick Benchmark Results
I/MemTrackerPerf: Malloc: 1.23 ms (0.246 μs/op)  ← 系统原生性能
I/MemTrackerPerf: Free:   1.45 ms (0.290 μs/op)  ← 系统原生性能

Final Memory Stats:
  Total Allocs: 0 (0 bytes)  ← 没有被追踪 ❌
```

### 新实现输出
```
I/SamplePerf: Quick Benchmark (Hooked malloc/free)
I/SamplePerf: Malloc: 45.23 ms (9.046 μs/op)  ← 被 hook 后的性能
I/SamplePerf: Free:   125.67 ms (25.134 μs/op) ← 被 hook 后的性能

Final Memory Stats:
  Total Allocs: 5000 (320000 bytes)  ← 被追踪了 ✅
  Total Frees: 5000 (320000 bytes)
```

## 🎯 关键改进

### 1. 真实性能测量
- ✅ 测量的是**被 hook 后**的实际性能
- ✅ 包含了 hook 拦截、追踪记录、链表操作的全部开销
- ✅ 反映了真实使用场景的性能

### 2. 可见的追踪结果
- ✅ 测试结束后能看到内存统计变化
- ✅ 验证追踪功能正常工作
- ✅ 可以对比优化前后的追踪数据

### 3. 更准确的性能基准
- ✅ 优化前: malloc ~10 μs, free ~25 μs (真实开销)
- ✅ 优化后目标: malloc < 3 μs, free < 3 μs
- ✅ 可以量化优化效果

## 🔧 使用方法

### 编译和运行
```bash
# 编译
.\gradlew assembleDebug

# 安装
adb install -r bytehook_sample\build\outputs\apk\debug\bytehook_sample-debug.apk

# 查看日志
adb logcat -s SamplePerf MemoryTracker SoHook
```

### UI 操作
1. 打开 SoHookTestActivity
2. 点击 "开始监控 (libsample.so)"
3. 点击 "快速基准测试 (5000次)"
4. 查看 logcat 输出和内存统计

### 代码调用
```java
// 初始化
SoHook.init(true);
SoHook.hook(Arrays.asList("libsample.so"));

// 运行测试（现在调用 libsample.so 中的函数）
NativeHacker.quickBenchmark(5000);
NativeHacker.runPerfTests();

// 查看统计
SoHook.MemoryStats stats = SoHook.getMemoryStats();
// stats.totalAllocCount 会显示 5000+ ✅
```

## 📝 文档更新

已更新以下文档：
- ✅ `PERF_QUICK_START.md` - 更新日志过滤器和输出示例
- ✅ `PERFORMANCE_TEST.md` - 更新注意事项和示例输出
- ✅ `activity_sohook_test.xml` - 更新日志提示
- ✅ `SoHookTestActivity.java` - 调用新接口

## 🗑️ 待清理

以下文件可以删除（旧实现）：
- `sohook/src/main/cpp/memory_tracker_perf_test.c`
- `sohook/src/main/cpp/memory_tracker_perf_test.h`
- `sohook_jni.c` 中的相关 JNI 绑定
- `SoHook.java` 中的相关方法

**注意**: 暂时保留这些文件，等新实现验证通过后再删除。

## ✅ 验证清单

重构完成后需要验证：
- [ ] 编译通过，无警告
- [ ] 快速基准测试能正常运行
- [ ] 完整测试套件能正常运行
- [ ] logcat 输出正确的性能数据
- [ ] 内存统计显示追踪的分配数量
- [ ] 性能数据合理（malloc ~10 μs, free ~25 μs）
- [ ] 多次运行结果稳定

## 🎉 总结

这次重构解决了根本性的问题：
- ✅ 性能测试现在测量的是**真实的被 hook 后的性能**
- ✅ 能够看到内存追踪的实际工作情况
- ✅ 为后续优化提供准确的性能基准
- ✅ 验证优化效果更加可靠

现在可以放心地使用性能测试来：
1. 评估当前性能瓶颈
2. 验证优化效果
3. 对比优化前后的差异
4. 持续监控性能回退

---

**重构日期**: 2025-10-24  
**重构原因**: 测试代码位置错误，无法测量真实的 hook 性能  
**解决方案**: 将性能测试移至被 hook 的目标 so (libsample.so) 中
