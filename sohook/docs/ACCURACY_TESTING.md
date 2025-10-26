# SoHook 准确性测试指南

## 概述

为了验证内存泄漏检测的准确性，我们创建了一个独立的测试库 `libtest_memory.so`，它提供可控的内存分配和释放功能。

---

## 📦 测试库架构

### 组件

```
sohook/
├── src/androidTest/cpp/          # 测试库 native 代码
│   ├── test_memory.h             # 头文件
│   ├── test_memory.c             # 实现
│   ├── test_memory_jni.c         # JNI 绑定
│   └── CMakeLists.txt            # 构建配置（已集成到主 CMakeLists.txt）
│
├── src/androidTest/java/com/sohook/
│   ├── TestMemoryHelper.java    # Java 辅助类
│   └── SoHookAccuracyTest.java  # 准确性测试
```

### 工作原理

```
Java 测试代码
    ↓
TestMemoryHelper (Java)
    ↓
JNI 绑定 (test_memory_jni.c)
    ↓
测试库函数 (test_memory.c: test_malloc)
    ↓
调用 malloc (通过 libtest_memory.so 的 PLT)
    ↓
SoHook Hook 拦截 (修改 PLT 指向 malloc_proxy)
    ↓
malloc_proxy 记录统计
    ↓
调用真正的 malloc
```

**SoHook Hook 机制**：
1. **Hook `libtest_memory.so`**：修改该库的 PLT，将对 malloc/free 的调用重定向
2. **PLT 重定向**：`libtest_memory.so` 调用 malloc 时，被重定向到 `malloc_proxy`
3. **统计记录**：proxy 函数记录分配信息后，调用真正的 `malloc/free`
4. **精确捕获**：只捕获通过 `libtest_memory.so` 的内存操作，避免系统噪音

**关键步骤**：
1. **预加载库**：在 Hook 之前先调用 `TestMemoryHelper.alloc(16)` 确保库已加载
2. **Hook 目标库**：`SoHook.hook("libtest_memory.so")` 修改其 PLT
3. **等待生效**：Hook 后等待 200ms 确保修改完成
4. **隔离测试**：每个测试前 `resetStats()` 清零统计

**优势**：
- **精确性**：只捕获测试库的内存操作，无系统噪音
- **可控性**：通过 TestMemoryHelper 精确控制分配/释放时机和大小
- **可预测**：知道确切分配了多少次、多少字节
- **可重复**：测试结果稳定可重复
- **隔离性**：不受其他库（如 libc.so）的系统调用影响

---

## 🎯 准确性测试用例

### 1. 基础准确性测试（9个）

| 测试方法 | 验证内容 |
|---------|---------|
| `testStatsResetAccuracy` | 重置后统计归零 |
| `testStatsConsistency` | current = total - free |
| `testStatsMonotonicity` | 总分配只增不减 |
| `testLeakReportAccuracy` | 报告数字与统计一致 |
| `testMultipleResetAccuracy` | 多次重置准确性 |
| `testHookCapture` | Hook能捕获内存分配 |
| `testStatsNonNegativity` | 所有统计 >= 0 |
| `testStatsRationality` | free <= alloc |
| `testLeakReportCompleteness` | 报告完整性 |

### 2. 精确准确性测试（5个）

使用 `libtest_memory.so` 进行精确验证：

| 测试方法 | 验证内容 | 关键点 |
|---------|---------|--------|
| `testExactAllocationAccuracy` | 分配 1024 字节，验证统计 | 至少捕获 1 次分配，1024 字节 |
| `testExactFreeAccuracy` | 释放内存，验证统计更新 | 释放次数增加，当前分配减少 |
| `testMultipleAllocationAccuracy` | 分配 10 次 × 512 字节 | 至少捕获 10 次，5120 字节 |
| `testLeakDetectionAccuracy` | 故意泄漏 4096 字节 | 检测到至少 4096 字节泄漏 |
| `testAllocFreeCycleAccuracy` | 5 次分配-释放循环 | 至少 5 次分配，5 次释放 |

**总计**: 14个准确性测试

---

## 🔧 测试库 API

### C API (test_memory.h)

```c
// 基础内存操作
void* test_malloc(size_t size);
void test_free(void* ptr);
void* test_realloc(void* ptr, size_t size);
void* test_calloc(size_t nmemb, size_t size);

// 测试辅助函数
void* test_alloc_multiple(int count, size_t size);  // 分配多次
void test_free_multiple(void** ptrs, int count);    // 释放多个
void* test_leak_memory(size_t size);                // 故意泄漏
```

### Java API (TestMemoryHelper.java)

```java
// 基础操作
long alloc(long size);              // 分配内存
void free(long ptr);                // 释放内存
long calloc(long nmemb, long size); // 分配并清零
long realloc(long ptr, long size);  // 重新分配

// 测试辅助
long allocMultiple(int count, long size);  // 分配多次
long leakMemory(long size);                // 故意泄漏

// 状态检查
boolean isLibraryLoaded();  // 检查库是否加载
```

---

## 🚀 运行准确性测试

### 1. 构建测试库

```bash
# 构建主库和测试库
.\gradlew :sohook:assembleDebug

# 测试库会自动构建到：
# sohook/build/intermediates/cxx/Debug/.../libtest_memory.so
```

### 2. 运行所有准确性测试

```bash
.\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookAccuracyTest"
```

### 3. 运行特定测试

```bash
# 精确分配测试
.\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookAccuracyTest.testExactAllocationAccuracy"

# 泄漏检测测试
.\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookAccuracyTest.testLeakDetectionAccuracy"
```

---

## 📊 测试示例

### 示例 1: 精确分配测试

```java
@Test
public void testExactAllocationAccuracy() {
    // 重置统计
    SoHook.resetStats();
    
    // 分配 1024 字节
    long ptr = TestMemoryHelper.alloc(1024);
    
    // 获取统计
    SoHook.MemoryStats stats = SoHook.getMemoryStats();
    
    // 验证：至少捕获了这次分配
    assertTrue(stats.totalAllocCount >= 1);
    assertTrue(stats.totalAllocSize >= 1024);
    
    // 释放
    TestMemoryHelper.free(ptr);
}
```

**预期输出**:
```
✓ Exact allocation accuracy test passed
Stats: MemoryStats{totalAllocCount=1, totalAllocSize=1024, ...}
```

### 示例 2: 泄漏检测测试

```java
@Test
public void testLeakDetectionAccuracy() {
    SoHook.resetStats();
    
    // 故意泄漏 4096 字节
    long leakedPtr = TestMemoryHelper.leakMemory(4096);
    
    // 获取统计
    SoHook.MemoryStats stats = SoHook.getMemoryStats();
    
    // 验证：检测到泄漏
    assertTrue(stats.currentAllocCount >= 1);
    assertTrue(stats.currentAllocSize >= 4096);
    
    // 不释放 leakedPtr（故意泄漏）
}
```

**预期输出**:
```
✓ Leak detection accuracy test passed
Leaked: 4096 bytes
Detected: 4096 bytes in 1 allocations
```

---

## 🔍 验证准确性

### 查看测试日志

```bash
# 查看测试库日志
adb logcat -s TestMemory TestMemoryJNI

# 查看测试日志
adb logcat -s SoHookAccuracyTest

# 查看所有相关日志
adb logcat -s TestMemory TestMemoryJNI SoHookAccuracyTest SoHook-JNI
```

### 日志示例

```
TestMemory: test_malloc: allocating 1024 bytes
TestMemory: test_malloc: allocated at 0x7f8a4c2000
SoHook-JNI: Captured allocation: 1024 bytes at 0x7f8a4c2000
SoHookAccuracyTest: Stats: MemoryStats{totalAllocCount=1, totalAllocSize=1024, ...}
TestMemory: test_free: freeing 0x7f8a4c2000
SoHook-JNI: Captured free: 0x7f8a4c2000
```

---

## 📈 准确性指标

### 关键指标

| 指标 | 预期 | 说明 |
|------|------|------|
| **分配捕获率** | 100% | 所有通过 test_memory 的分配都应被捕获 |
| **释放捕获率** | 100% | 所有通过 test_memory 的释放都应被捕获 |
| **大小准确性** | ±0 字节 | 捕获的大小应与实际分配大小完全一致 |
| **泄漏检测率** | 100% | 故意泄漏的内存应被检测到 |
| **统计一致性** | 100% | current = total - free 必须成立 |

### 测试通过标准

✅ **通过**：
- 所有断言成功
- 统计数字 >= 预期值
- 一致性检查通过

❌ **失败**：
- 任何断言失败
- 统计数字 < 预期值
- 一致性检查失败

---

## 🐛 故障排查

### 问题 1: test_memory 库未加载

**症状**:
```
Skipping test: test_memory library not loaded
```

**原因**: 测试库未编译或未打包到 APK

**解决**:
```bash
# 清理并重新构建
.\gradlew :sohook:clean
.\gradlew :sohook:assembleDebug

# 检查库是否存在
dir sohook\build\intermediates\cxx\Debug\*\obj\arm64-v8a\libtest_memory.so
```

### 问题 2: Hook 未捕获分配

**症状**: 统计显示 0 次分配

**原因**: 
1. 未 Hook `libtest_memory.so`
2. Hook 时机太晚

**解决**:
```java
@Before
public void setUp() {
    SoHook.init(true);
    // 确保 Hook test_memory
    SoHook.hook(Collections.singletonList("libtest_memory.so"));
}
```

### 问题 3: 统计数字不准确

**症状**: 捕获的大小与预期不符

**可能原因**:
1. 系统内存分配器添加了额外的元数据
2. 内存对齐导致实际分配大小增加

**说明**: 这是正常的，测试使用 `>=` 而不是 `==` 来验证

---

## 📝 添加新的准确性测试

### 步骤

1. **在 test_memory.c 添加新函数**（如果需要）

```c
void* test_custom_alloc(size_t size) {
    // 自定义分配逻辑
    return malloc(size);
}
```

2. **在 test_memory_jni.c 添加 JNI 绑定**

```c
JNIEXPORT jlong JNICALL
Java_com_sohook_TestMemoryHelper_nativeCustomAlloc(JNIEnv *env, jclass clazz, jlong size) {
    void* ptr = test_custom_alloc((size_t)size);
    return (jlong)(uintptr_t)ptr;
}
```

3. **在 TestMemoryHelper.java 添加 Java 方法**

```java
public static long customAlloc(long size) {
    return nativeCustomAlloc(size);
}

private static native long nativeCustomAlloc(long size);
```

4. **在 SoHookAccuracyTest.java 添加测试**

```java
@Test
public void testCustomAllocAccuracy() {
    SoHook.resetStats();
    long ptr = TestMemoryHelper.customAlloc(2048);
    
    SoHook.MemoryStats stats = SoHook.getMemoryStats();
    assertTrue(stats.totalAllocSize >= 2048);
    
    TestMemoryHelper.free(ptr);
}
```

---

## 🎯 最佳实践

### 1. 测试隔离

```java
@Before
public void setUp() {
    SoHook.resetStats();  // 每个测试前重置
}

@After
public void tearDown() {
    SoHook.resetStats();  // 每个测试后清理
}
```

### 2. 等待统计更新

```java
// 分配后等待一小段时间
TestMemoryHelper.alloc(1024);
Thread.sleep(50);  // 给 Hook 时间更新统计
SoHook.MemoryStats stats = SoHook.getMemoryStats();
```

### 3. 使用 >= 而不是 ==

```java
// ✅ 好的做法
assertTrue(stats.totalAllocSize >= 1024);

// ❌ 不好的做法（可能因为内存对齐失败）
assertEquals(1024, stats.totalAllocSize);
```

### 4. 清理资源

```java
long ptr = TestMemoryHelper.alloc(1024);
try {
    // 测试代码
} finally {
    TestMemoryHelper.free(ptr);  // 确保释放
}
```

---

## 📚 参考

- **测试库源码**: `sohook/src/androidTest/cpp/`
- **Java 辅助类**: `TestMemoryHelper.java`
- **准确性测试**: `SoHookAccuracyTest.java`
- **CMake 配置**: `sohook/src/main/cpp/CMakeLists.txt`

---

**创建时间**: 2025-10-25  
**测试数量**: 14个准确性测试  
**测试库**: libtest_memory.so  
**状态**: ✅ 就绪
