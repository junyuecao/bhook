# 栈回溯功能说明

## 🎯 功能概述

SoHook 支持可选的栈回溯功能，可以记录每次内存分配时的调用栈，帮助定位内存泄漏的源头。

## 🔧 设计方案

### 核心思路：可选的实时回溯

考虑到性能影响，栈回溯采用**可选启用**的设计：

- **默认模式**：不回溯（高性能，~0.5 μs/op）
- **调试模式**：启用回溯（性能下降 10-20x，~5-10 μs/op）

### 为什么不采用延迟回溯？

**延迟回溯**（在需要时才回溯）在技术上**不可行**，因为：
- 调用栈在 malloc 返回后就消失了
- 无法在事后重建分配时的调用栈
- 必须在 malloc 时立即捕获

## 📝 使用方法

### 方法 1: 初始化时启用

```java
// 初始化时启用栈回溯
SoHook.init(true, true);  // debug=true, enableBacktrace=true

// 开始监控
SoHook.hook(Arrays.asList("libsample.so"));

// 获取泄漏报告（自动包含栈回溯信息）
String report = SoHook.getLeakReport();
Log.i(TAG, report);
```

### 方法 2: 动态启用/禁用

```java
// 初始化时不启用
SoHook.init(true, false);
SoHook.hook(Arrays.asList("libsample.so"));

// 运行一段时间...

// 需要调试时启用栈回溯
SoHook.setBacktraceEnabled(true);

// 继续运行，新的分配会记录栈回溯

// 调试完成后禁用
SoHook.setBacktraceEnabled(false);

// 检查状态
boolean enabled = SoHook.isBacktraceEnabled();
```

## 📊 泄漏报告格式

### 不启用栈回溯

```
=== Memory Leak Report ===
Total Allocations: 1000 (102400 bytes)
Total Frees: 950 (97280 bytes)
Current Leaks: 50 (5120 bytes)

Leak #1: ptr=0x7f8a4c0010, size=64, so=libsample.so
Leak #2: ptr=0x7f8a4c0050, size=128, so=libsample.so
...
```

### 启用栈回溯

```
=== Memory Leak Report ===
Total Allocations: 1000 (102400 bytes)
Total Frees: 950 (97280 bytes)
Current Leaks: 50 (5120 bytes)

Leak #1: ptr=0x7f8a4c0010, size=64, so=libsample.so
  Backtrace (8 frames):
    #0: 0x7f8a5c1234
    #1: 0x7f8a5c1456
    #2: 0x7f8a5c1678
    #3: 0x7f8a5c189a
    #4: 0x7f8a5c1abc
    #5: 0x7f8a5c1cde
    #6: 0x7f8a5c1ef0
    #7: 0x7f8a5c2012

Leak #2: ptr=0x7f8a4c0050, size=128, so=libsample.so
  Backtrace (6 frames):
    #0: 0x7f8a5c3456
    #1: 0x7f8a5c3678
    ...
```

## 🎓 技术实现

### 栈回溯机制

使用 Android NDK 的 `<unwind.h>` 库：

```c
#include <unwind.h>

// 回溯回调
static _Unwind_Reason_Code unwind_callback(struct _Unwind_Context *context, void *arg) {
  struct BacktraceState *state = (struct BacktraceState *)arg;
  uintptr_t pc = _Unwind_GetIP(context);
  if (pc && state->current < state->end) {
    *state->current++ = (void *)pc;
  }
  return state->current < state->end ? _URC_NO_REASON : _URC_END_OF_STACK;
}

// 捕获调用栈
static int capture_backtrace(void **buffer, int max_frames) {
  struct BacktraceState state = {buffer, buffer + max_frames};
  _Unwind_Backtrace(unwind_callback, &state);
  return state.current - buffer;
}
```

### 在 malloc 时捕获

```c
static void add_memory_record(void *ptr, size_t size, const char *so_name) {
  memory_record_t *record = pool_alloc_record();
  
  record->ptr = ptr;
  record->size = size;
  record->so_name = so_name;
  
  // 可选的栈回溯
  if (g_backtrace_enabled) {
    record->backtrace_size = capture_backtrace(record->backtrace, 16);
  } else {
    record->backtrace_size = 0;
  }
  
  hash_table_add(record);
}
```

## ⚠️ 性能影响

### 性能对比

| 模式 | 单次操作 | 吞吐量 | 性能等级 |
|------|---------|--------|---------|
| **不启用回溯** | 0.5 μs | 2M ops/s | ⭐⭐⭐⭐⭐ 生产级 |
| **启用回溯** | 5-10 μs | 100-200K ops/s | ⭐⭐⭐ 调试级 |

### 性能下降原因

1. **栈回溯本身开销**：~4-8 μs
   - 遍历调用栈
   - 获取每一帧的 PC 值

2. **缓存影响**：
   - backtrace 数组占用 128 bytes
   - 增加内存访问

### 建议使用场景

✅ **适合启用回溯**：
- 调试内存泄漏
- 定位泄漏源头
- 开发/测试阶段
- 低频分配场景（< 1K/s）

❌ **不建议启用回溯**：
- 生产环境
- 性能敏感场景
- 高频分配场景（> 10K/s）
- 只需要统计信息

## 🔍 符号解析

### 当前状态

栈回溯返回的是**原始地址**（如 `0x7f8a5c1234`），需要手动解析为函数名。

### 解析方法

#### 方法 1: 使用 addr2line

```bash
# 获取泄漏报告
adb pull /sdcard/leak_report.txt

# 解析地址
addr2line -e libsample.so -f 0x7f8a5c1234
# 输出: my_function
#       /path/to/source.cpp:42
```

#### 方法 2: 使用 ndk-stack

```bash
# 保存报告到文件
adb logcat -s SoHook > leak.log

# 使用 ndk-stack 解析
ndk-stack -sym /path/to/symbols -dump leak.log
```

#### 方法 3: 使用 llvm-symbolizer

```bash
llvm-symbolizer --obj=libsample.so 0x7f8a5c1234
# 输出: my_function
#       /path/to/source.cpp:42
```

### 未来改进

可以在运行时解析符号（需要额外依赖）：

```c
// 使用 dladdr 获取符号信息
Dl_info info;
if (dladdr(record->backtrace[i], &info)) {
  const char *symbol = info.dli_sname;
  const char *file = info.dli_fname;
  // 输出符号名
}
```

## 📋 API 参考

### Java API

```java
// 初始化（启用回溯）
public static int init(boolean debug, boolean enableBacktrace)

// 动态启用/禁用
public static void setBacktraceEnabled(boolean enable)

// 检查状态
public static boolean isBacktraceEnabled()

// 获取报告（自动包含回溯信息）
public static String getLeakReport()
```

### C API

```c
// 初始化
int memory_tracker_init(bool debug, bool enable_backtrace);

// 动态控制
void memory_tracker_set_backtrace_enabled(bool enable);
bool memory_tracker_is_backtrace_enabled(void);

// 获取报告（自动包含回溯信息）
char *memory_tracker_get_leak_report(void);
```

## 🎯 最佳实践

### 1. 分阶段调试

```java
// 阶段 1: 高性能模式，收集统计
SoHook.init(false, false);
SoHook.hook(Arrays.asList("libsample.so"));
// 运行应用，观察统计数据

// 阶段 2: 发现泄漏，启用回溯
SoHook.setBacktraceEnabled(true);
// 重现泄漏场景

// 阶段 3: 获取详细报告
String report = SoHook.getLeakReport();
// 分析栈回溯，定位问题

// 阶段 4: 修复后验证
SoHook.setBacktraceEnabled(false);
SoHook.resetStats();
// 验证修复效果
```

### 2. 条件启用

```java
// 只在 Debug 构建启用回溯
boolean enableBacktrace = BuildConfig.DEBUG;
SoHook.init(true, enableBacktrace);
```

### 3. 动态切换

```java
// 根据泄漏数量动态启用
MemoryStats stats = SoHook.getMemoryStats();
if (stats.currentAllocCount > 1000) {
  // 发现大量泄漏，启用回溯
  SoHook.setBacktraceEnabled(true);
  Log.w(TAG, "Detected memory leak, enabling backtrace");
}
```

## 🔧 故障排查

### 问题 1: 回溯信息为空

**原因**：
- 栈回溯未启用
- 在启用前分配的内存

**解决**：
```java
// 确保在分配前启用
SoHook.setBacktraceEnabled(true);
// 重置旧数据
SoHook.resetStats();
// 重新触发泄漏
```

### 问题 2: 性能严重下降

**原因**：
- 启用了栈回溯
- 分配频率过高

**解决**：
```java
// 禁用回溯
SoHook.setBacktraceEnabled(false);

// 或者只在特定场景启用
if (isDebugging) {
  SoHook.setBacktraceEnabled(true);
}
```

### 问题 3: 栈深度不够

**当前限制**：最多 16 帧

**解决**：
- 修改 `memory_record_t` 中的 `backtrace[16]` 大小
- 重新编译

## 📊 性能测试

### 测试代码

```java
// 测试不启用回溯
SoHook.init(true, false);
SoHook.hook(Arrays.asList("libsample.so"));
long start = System.nanoTime();
for (int i = 0; i < 10000; i++) {
  NativeHacker.testMalloc(64);
}
long elapsed = System.nanoTime() - start;
Log.i(TAG, "Without backtrace: " + (elapsed / 10000) + " ns/op");

// 测试启用回溯
SoHook.resetStats();
SoHook.setBacktraceEnabled(true);
start = System.nanoTime();
for (int i = 0; i < 10000; i++) {
  NativeHacker.testMalloc(64);
}
elapsed = System.nanoTime() - start;
Log.i(TAG, "With backtrace: " + (elapsed / 10000) + " ns/op");
```

### 预期结果

```
Without backtrace: 500 ns/op
With backtrace: 5000-10000 ns/op
Performance impact: 10-20x
```

## 🎉 总结

### 功能特点

- ✅ **可选启用** - 默认高性能，需要时启用
- ✅ **动态切换** - 运行时启用/禁用
- ✅ **自动集成** - 报告自动包含回溯信息
- ✅ **性能可控** - 明确的性能影响

### 使用建议

1. **生产环境**：不启用回溯
2. **开发环境**：按需启用
3. **调试泄漏**：临时启用
4. **性能测试**：禁用回溯

---

**功能状态**: ✅ 已实现  
**性能影响**: 10-20x（启用时）  
**推荐场景**: 调试阶段  
**文档日期**: 2025-10-25

