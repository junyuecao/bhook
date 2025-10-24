# 哈希表优化实施方案

## 📊 当前性能瓶颈

根据实测数据：
```
Malloc:  10.961 μs/op  ✅ 可接受
Free:    43.349 μs/op  🔴 主要瓶颈（4倍慢）
多线程: 45K ops/sec   🔴 锁竞争严重
```

**根本原因**: `remove_memory_record()` 使用 O(n) 链表查找

## 🎯 优化目标

| 指标 | 当前 | 目标 | 预期提升 |
|------|------|------|---------|
| Free | 43 μs | < 5 μs | **8-10x** |
| 批量 Free | 43 μs | < 5 μs | 8-10x |
| 多线程 | 45K ops/s | 150K+ ops/s | 3-4x |

## 🔧 实施方案

### 方案 1: 简单哈希表（推荐）

**优点**:
- 实现简单，2-3 小时完成
- Free 从 O(n) 降至 O(1)
- 预期提升 8-10x

**实现**:
```c
#define HASH_TABLE_SIZE 10007  // 质数，减少冲突

typedef struct hash_bucket {
    memory_record_t *head;
    pthread_mutex_t lock;  // 分段锁
} hash_bucket_t;

static hash_bucket_t g_hash_table[HASH_TABLE_SIZE];

// 哈希函数
static inline size_t hash_ptr(void *ptr) {
    uintptr_t addr = (uintptr_t)ptr;
    return (addr >> 3) % HASH_TABLE_SIZE;  // 右移3位忽略对齐
}
```

### 方案 2: 哈希表 + 内存池

**优点**:
- Free 提升 8-10x
- Malloc 额外提升 2-3x
- 总体提升 10-30x

**缺点**:
- 实现复杂，4-6 小时
- 需要内存池管理

## 📝 实施步骤（方案 1）

### 步骤 1: 修改数据结构（30分钟）

```c
// memory_tracker.c

#define HASH_TABLE_SIZE 10007

typedef struct hash_bucket {
    memory_record_t *head;
    pthread_mutex_t lock;
} hash_bucket_t;

static hash_bucket_t g_hash_table[HASH_TABLE_SIZE];

// 初始化哈希表
static void init_hash_table(void) {
    for (int i = 0; i < HASH_TABLE_SIZE; i++) {
        g_hash_table[i].head = NULL;
        pthread_mutex_init(&g_hash_table[i].lock, NULL);
    }
}
```

### 步骤 2: 实现哈希函数（15分钟）

```c
static inline size_t hash_ptr(void *ptr) {
    uintptr_t addr = (uintptr_t)ptr;
    // 右移3位忽略8字节对齐，减少冲突
    return (addr >> 3) % HASH_TABLE_SIZE;
}
```

### 步骤 3: 重写 add_memory_record（45分钟）

```c
static void add_memory_record(void *ptr, size_t size, const char *so_name) {
    if (g_in_hook) return;
    g_in_hook = true;

    memory_record_t *record = original_malloc(sizeof(memory_record_t));
    if (record == NULL) {
        g_in_hook = false;
        return;
    }

    record->ptr = ptr;
    record->size = size;
    record->so_name = so_name;
    record->backtrace_size = 0;
    record->next = NULL;

    // 计算哈希值
    size_t bucket_idx = hash_ptr(ptr);
    hash_bucket_t *bucket = &g_hash_table[bucket_idx];

    // 使用分段锁
    if (pthread_mutex_trylock(&bucket->lock) != 0) {
        original_free(record);
        g_in_hook = false;
        return;
    }

    // 插入链表头部 O(1)
    record->next = bucket->head;
    bucket->head = record;

    pthread_mutex_unlock(&bucket->lock);

    // 更新全局统计（需要全局锁）
    pthread_mutex_lock(&g_mutex);
    g_stats.total_alloc_count++;
    g_stats.total_alloc_size += size;
    g_stats.current_alloc_count++;
    g_stats.current_alloc_size += size;
    pthread_mutex_unlock(&g_mutex);

    g_in_hook = false;
}
```

### 步骤 4: 重写 remove_memory_record（45分钟）

```c
static void remove_memory_record(void *ptr) {
    if (g_in_hook || ptr == NULL) return;
    g_in_hook = true;

    // 计算哈希值 O(1)
    size_t bucket_idx = hash_ptr(ptr);
    hash_bucket_t *bucket = &g_hash_table[bucket_idx];

    if (pthread_mutex_trylock(&bucket->lock) != 0) {
        g_in_hook = false;
        return;
    }

    memory_record_t *prev = NULL;
    memory_record_t *curr = bucket->head;

    // 在桶内查找 O(1) 平均
    while (curr != NULL) {
        if (curr->ptr == ptr) {
            if (prev == NULL) {
                bucket->head = curr->next;
            } else {
                prev->next = curr->next;
            }

            size_t freed_size = curr->size;
            original_free(curr);

            pthread_mutex_unlock(&bucket->lock);

            // 更新全局统计
            pthread_mutex_lock(&g_mutex);
            g_stats.total_free_count++;
            g_stats.total_free_size += freed_size;
            g_stats.current_alloc_count--;
            g_stats.current_alloc_size -= freed_size;
            pthread_mutex_unlock(&g_mutex);

            g_in_hook = false;
            return;
        }
        prev = curr;
        curr = curr->next;
    }

    pthread_mutex_unlock(&bucket->lock);
    g_in_hook = false;
}
```

### 步骤 5: 更新初始化和清理（30分钟）

```c
int memory_tracker_init(bool debug) {
    // ... 现有代码 ...
    
    // 初始化哈希表
    init_hash_table();
    
    // ... 现有代码 ...
}

// 清理函数
static void cleanup_hash_table(void) {
    for (int i = 0; i < HASH_TABLE_SIZE; i++) {
        hash_bucket_t *bucket = &g_hash_table[i];
        pthread_mutex_lock(&bucket->lock);
        
        memory_record_t *curr = bucket->head;
        while (curr != NULL) {
            memory_record_t *next = curr->next;
            original_free(curr);
            curr = next;
        }
        
        bucket->head = NULL;
        pthread_mutex_unlock(&bucket->lock);
        pthread_mutex_destroy(&bucket->lock);
    }
}
```

## 📊 预期性能提升

### 理论分析

**当前实现**:
- Add: O(1) - 链表头插入
- Remove: O(n) - 遍历整个链表
- 5000 块时，平均查找 2500 次比较

**哈希表实现**:
- Add: O(1) - 哈希 + 链表头插入
- Remove: O(1) 平均 - 哈希 + 桶内查找
- 10007 个桶，每桶平均 0.5 个元素

**提升计算**:
```
当前 Free: 43 μs (包含 2500 次链表遍历)
优化后:    5 μs (包含 1 次哈希 + 0.5 次链表遍历)

提升: 43 / 5 = 8.6x
```

### 实测预期

| 测试场景 | 当前 | 预期 | 提升 |
|---------|------|------|------|
| Malloc | 11 μs | 8 μs | 1.4x |
| Free | 43 μs | **5 μs** | **8.6x** |
| 批量 Free | 43 μs | 5 μs | 8.6x |
| 随机操作 | 7.6 μs | 6 μs | 1.3x |
| 多线程 | 45K ops/s | 150K ops/s | 3.3x |

## ✅ 验证步骤

1. **编译测试**
   ```bash
   .\gradlew assembleDebug
   ```

2. **运行基准测试**
   ```bash
   adb install -r bytehook_sample\build\outputs\apk\debug\bytehook_sample-debug.apk
   adb logcat -s SamplePerf
   # 在应用中点击 "快速基准测试"
   ```

3. **对比结果**
   ```
   优化前 Free: 43.349 μs/op
   优化后 Free: < 5 μs/op  ← 目标
   提升:       8-10x
   ```

## 🎯 下一步

是否立即开始实施哈希表优化？

**预计时间**: 2-3 小时  
**预期提升**: Free 性能 8-10x  
**风险**: 低（可以先在分支测试）

---

**文档日期**: 2025-10-24  
**当前性能**: Free 43 μs/op  
**优化目标**: Free < 5 μs/op  
**预期提升**: 8-10x
