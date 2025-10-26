# 代码重构计划 - memory_tracker.c

## 📊 当前状态

**文件大小**: ~662 行  
**问题**: 文件过长，职责混杂，不易维护

## 🎯 重构目标

将 `memory_tracker.c` 拆分为多个职责单一的模块：

### 1. **backtrace.h/c** - 栈回溯模块 ✨

**职责**: 捕获和格式化调用栈

**包含内容**:
```c
// 数据结构
struct BacktraceState {
  void **current;
  void **end;
};

// 函数
static _Unwind_Reason_Code unwind_callback(...);
static int capture_backtrace(void **buffer, int max_frames);
```

**行数**: ~20 行

**优势**:
- 独立的栈回溯逻辑
- 可复用
- 易于测试

---

### 2. **leak_report.h/c** - 泄漏报告生成模块 ✨

**职责**: 生成和格式化内存泄漏报告

**包含内容**:
```c
// 数据结构
typedef struct {
  char **report;
  size_t *offset;
  size_t *buffer_size;
  int *leak_count;
  int max_leaks;
} leak_report_context_t;

// 函数
static bool leak_report_callback(memory_record_t *record, void *user_data);
static void format_backtrace_frame(char *buffer, size_t size, void *addr, int index);
char *generate_leak_report(void);
int dump_leak_report(const char *file_path);
```

**行数**: ~200 行

**优势**:
- 报告生成逻辑独立
- 易于添加新的报告格式（JSON、XML等）
- 符号解析逻辑集中

---

### 3. **memory_stats.h/c** - 统计信息模块 ✨

**职责**: 管理和计算内存统计信息

**包含内容**:
```c
// 全局统计变量
static _Atomic uint64_t g_total_alloc_count;
static _Atomic uint64_t g_total_alloc_size;
static _Atomic uint64_t g_total_free_count;
static _Atomic uint64_t g_total_free_size;

// 函数
void stats_update_alloc(size_t size);
void stats_update_free(size_t size);
void stats_get(memory_stats_t *stats);
void stats_reset(void);
static void calculate_current_stats(uint64_t *count, uint64_t *size);
```

**行数**: ~80 行

**优势**:
- 统计逻辑独立
- 易于添加新的统计维度
- 原子操作集中管理

---

### 4. **memory_tracker.h/c** - 核心追踪模块 ⭐

**职责**: 核心的内存追踪和 hook 管理

**保留内容**:
```c
// 全局状态
static bool g_initialized;
static bool g_debug;
static bool g_backtrace_enabled;

// Hook 管理
static bytehook_stub_t g_malloc_stubs[MAX_HOOKS];
static int g_hook_count;
static void *(*original_malloc)(size_t);
static void (*original_free)(void *);

// 核心函数
int memory_tracker_init(bool debug, bool enable_backtrace);
int memory_tracker_hook(const char **so_names, int count);
int memory_tracker_unhook(const char **so_names, int count);

// Hook 代理函数
void *malloc_proxy(size_t size);
void *calloc_proxy(size_t nmemb, size_t size);
void *realloc_proxy(void *ptr, size_t size);
void free_proxy(void *ptr);

// 记录管理
static void add_memory_record(void *ptr, size_t size);
static void remove_memory_record(void *ptr);
```

**行数**: ~250 行

**优势**:
- 核心逻辑清晰
- 专注于 hook 和记录管理
- 调用其他模块的功能

---

## 📁 重构后的文件结构

```
sohook/src/main/cpp/
├── memory_tracker.h            # 主头文件（公共 API）
├── memory_tracker.c            # 核心追踪逻辑（~250 行）
├── memory_hash_table.h/c       # 哈希表模块（已存在）
├── memory_pool.h/c             # 内存池模块（已存在）
├── backtrace.h/c               # 栈回溯模块（新建）✨
├── leak_report.h/c             # 泄漏报告模块（新建）✨
├── memory_stats.h/c            # 统计信息模块（新建）✨
└── sohook_jni.c                # JNI 绑定
```

---

## 🔄 模块依赖关系

```
memory_tracker.c (核心)
    ├── memory_hash_table.c (哈希表)
    ├── memory_pool.c (内存池)
    ├── backtrace.c (栈回溯)
    ├── leak_report.c (报告生成)
    │   └── backtrace.c (符号解析)
    └── memory_stats.c (统计)
```

---

## 📝 详细拆分方案

### 1. backtrace.h

```c
#ifndef BACKTRACE_H
#define BACKTRACE_H

#include <stddef.h>

/**
 * 捕获当前调用栈
 * @param buffer 存储栈帧的缓冲区
 * @param max_frames 最大帧数
 * @return 实际捕获的帧数
 */
int backtrace_capture(void **buffer, int max_frames);

/**
 * 格式化单个栈帧为字符串
 * @param buffer 输出缓冲区
 * @param size 缓冲区大小
 * @param addr 栈帧地址
 * @param index 帧索引
 * @return 写入的字符数
 */
int backtrace_format_frame(char *buffer, size_t size, void *addr, int index);

#endif // BACKTRACE_H
```

### 2. leak_report.h

```c
#ifndef LEAK_REPORT_H
#define LEAK_REPORT_H

#include "memory_tracker.h"

/**
 * 生成内存泄漏报告
 * @return 报告字符串，调用者需要 free
 */
char *leak_report_generate(void);

/**
 * 将泄漏报告导出到文件
 * @param file_path 文件路径
 * @return 0 成功，-1 失败
 */
int leak_report_dump(const char *file_path);

#endif // LEAK_REPORT_H
```

### 3. memory_stats.h

```c
#ifndef MEMORY_STATS_H
#define MEMORY_STATS_H

#include "memory_tracker.h"

/**
 * 更新分配统计
 */
void memory_stats_update_alloc(size_t size);

/**
 * 更新释放统计
 */
void memory_stats_update_free(size_t size);

/**
 * 获取统计信息
 */
void memory_stats_get(memory_stats_t *stats);

/**
 * 重置统计信息
 */
void memory_stats_reset(void);

#endif // MEMORY_STATS_H
```

---

## 🚀 重构步骤

### 阶段 1: 创建新模块（不破坏现有代码）

1. ✅ 创建 `backtrace.h/c`
2. ✅ 创建 `leak_report.h/c`
3. ✅ 创建 `memory_stats.h/c`
4. ✅ 实现新模块的函数

### 阶段 2: 迁移代码

1. ✅ 将栈回溯代码移到 `backtrace.c`
2. ✅ 将报告生成代码移到 `leak_report.c`
3. ✅ 将统计代码移到 `memory_stats.c`
4. ✅ 更新 `memory_tracker.c` 调用新模块

### 阶段 3: 清理和测试

1. ✅ 删除 `memory_tracker.c` 中的旧代码
2. ✅ 更新 `CMakeLists.txt`
3. ✅ 编译测试
4. ✅ 功能测试

---

## 📊 预期效果

### 代码行数对比

| 文件 | 重构前 | 重构后 | 变化 |
|------|--------|--------|------|
| memory_tracker.c | 662 行 | 250 行 | -412 行 ✅ |
| backtrace.c | - | 20 行 | +20 行 |
| leak_report.c | - | 200 行 | +200 行 |
| memory_stats.c | - | 80 行 | +80 行 |
| **总计** | **662 行** | **550 行** | **-112 行** |

### 优势

1. **职责单一** ✅
   - 每个模块只做一件事
   - 易于理解和维护

2. **可复用** ✅
   - `backtrace` 可用于其他项目
   - `leak_report` 可支持多种格式

3. **易于测试** ✅
   - 每个模块可独立测试
   - 减少测试复杂度

4. **易于扩展** ✅
   - 添加新功能不影响核心代码
   - 例如：添加 JSON 报告格式

5. **编译速度** ✅
   - 修改单个模块只需重新编译该模块
   - 减少编译时间

---

## 🎯 优先级

### 高优先级 🔴

1. **leak_report.c** - 报告生成逻辑最复杂（~200 行）
2. **memory_stats.c** - 统计逻辑独立性强（~80 行）

### 中优先级 🟡

3. **backtrace.c** - 代码简单但独立性强（~20 行）

### 低优先级 🟢

4. 进一步优化 `memory_tracker.c` 的结构

---

## 💡 额外优化建议

### 1. 配置模块

创建 `memory_tracker_config.h`：

```c
// 配置参数
#define MAX_HOOKS 64
#define MAX_BACKTRACE_FRAMES 16
#define INITIAL_REPORT_BUFFER_SIZE 4096
#define MAX_LEAKS_IN_REPORT 100
```

### 2. 错误处理模块

创建 `memory_tracker_error.h`：

```c
typedef enum {
  MT_OK = 0,
  MT_ERROR_NOT_INITIALIZED = -1,
  MT_ERROR_ALREADY_INITIALIZED = -2,
  MT_ERROR_HOOK_FAILED = -3,
  // ...
} mt_error_t;

const char *mt_error_string(mt_error_t error);
```

### 3. 日志模块

创建 `memory_tracker_log.h`：

```c
void mt_log_debug(const char *fmt, ...);
void mt_log_info(const char *fmt, ...);
void mt_log_warn(const char *fmt, ...);
void mt_log_error(const char *fmt, ...);
```

---

## 📅 实施计划

### 第 1 天：创建基础模块

- [ ] 创建 `backtrace.h/c`
- [ ] 创建 `memory_stats.h/c`
- [ ] 实现基本功能

### 第 2 天：创建报告模块

- [ ] 创建 `leak_report.h/c`
- [ ] 迁移报告生成逻辑
- [ ] 实现符号解析

### 第 3 天：集成和测试

- [ ] 更新 `memory_tracker.c`
- [ ] 更新 `CMakeLists.txt`
- [ ] 编译测试
- [ ] 功能测试

---

## ✅ 验收标准

1. ✅ 所有功能正常工作
2. ✅ 性能无明显下降
3. ✅ 代码行数减少
4. ✅ 模块职责清晰
5. ✅ 易于维护和扩展

---

**创建日期**: 2025-10-25  
**状态**: 待实施  
**预计工作量**: 3 天
