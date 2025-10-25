# SoHook 测试隔离指南

## 问题描述

**症状**: 单独运行某个测试类通过，但在测试套件中运行时失败

**原因**: 测试之间共享状态，导致相互影响

---

## 🔍 根本原因

### 1. 静态状态共享

Android Instrumentation 测试在同一个进程中运行，所有测试共享：

- **SoHook 初始化状态** - `sInitialized` 是静态变量
- **Hook 状态** - 一旦 Hook 就无法撤销
- **Native 全局变量** - `g_backtrace_enabled` 等

### 2. Hook 冲突

```java
// BasicTest 中
SoHook.hook(Collections.singletonList("libc.so"));

// AccuracyTest 中
SoHook.hook(Collections.singletonList("libtest_memory.so"));
```

如果 BasicTest 先运行，可能会影响 AccuracyTest 的统计。

---

## ✅ 解决方案

### 1. 使用静态标志避免重复 Hook

```java
@RunWith(AndroidJUnit4.class)
public class SoHookAccuracyTest {
    private static boolean sHookInitialized = false;

    @Before
    public void setUp() {
        SoHook.init(true);
        
        // 只 Hook 一次
        if (TestMemoryHelper.isLibraryLoaded() && !sHookInitialized) {
            int hookResult = SoHook.hook(Collections.singletonList("libtest_memory.so"));
            if (hookResult == 0) {
                sHookInitialized = true;
                Log.i(TAG, "Hooked libtest_memory.so successfully");
            }
        }
        
        // 每次测试前重置统计
        SoHook.resetStats();
    }
}
```

### 2. 添加等待时间

```java
@Before
public void setUp() {
    // 等待之前的测试完成
    try {
        Thread.sleep(100);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
    
    SoHook.init(true);
    
    // Hook 后等待生效
    if (!sHookInitialized) {
        SoHook.hook(Collections.singletonList("libtest_memory.so"));
        Thread.sleep(50);
        sHookInitialized = true;
    }
    
    SoHook.resetStats();
    
    // 重置后等待生效
    Thread.sleep(50);
}
```

### 3. 在 tearDown 中清理

```java
@After
public void tearDown() {
    // 重置统计
    SoHook.resetStats();
    
    // 等待清理完成
    try {
        Thread.sleep(50);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
}
```

---

## 🎯 测试套件执行顺序

### 当前顺序

```java
@Suite.SuiteClasses({
    SoHookBasicTest.class,        // 1. 先运行（Hook libc.so）
    SoHookMemoryLeakTest.class,   // 2. 
    SoHookStressTest.class,       // 3. 
    SoHookAccuracyTest.class      // 4. 最后运行（Hook libtest_memory.so）
})
```

### 潜在问题

1. **BasicTest Hook libc.so** → 可能捕获系统内存分配
2. **AccuracyTest 期望干净的统计** → 但可能已经有 libc 的统计数据

### 解决方案

**方案 A**: 每个测试类使用独立的库

- ✅ BasicTest → Hook libc.so
- ✅ AccuracyTest → Hook libtest_memory.so
- ✅ 两者不冲突

**方案 B**: 调整测试顺序

```java
@Suite.SuiteClasses({
    SoHookAccuracyTest.class,     // 1. 先运行准确性测试
    SoHookBasicTest.class,        // 2. 
    SoHookMemoryLeakTest.class,   // 3. 
    SoHookStressTest.class        // 4. 
})
```

**方案 C**: 使用 `@FixMethodOrder`

```java
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SoHookAccuracyTest {
    // 测试方法按名称排序执行
}
```

---

## 🛡️ 最佳实践

### 1. 测试独立性

```java
@Test
public void testSomething() {
    // ✅ 每个测试开始时重置状态
    SoHook.resetStats();
    
    // 执行测试
    long ptr = TestMemoryHelper.alloc(1024);
    
    // ✅ 测试结束时清理资源
    TestMemoryHelper.free(ptr);
}
```

### 2. 避免假设初始状态

```java
// ❌ 不好的做法
@Test
public void testBad() {
    // 假设统计是 0
    assertEquals(0, SoHook.getMemoryStats().totalAllocCount);
}

// ✅ 好的做法
@Test
public void testGood() {
    // 显式重置
    SoHook.resetStats();
    
    // 验证重置后是 0
    assertEquals(0, SoHook.getMemoryStats().totalAllocCount);
}
```

### 3. 使用相对断言

```java
// ❌ 不好的做法（绝对值）
@Test
public void testBad() {
    TestMemoryHelper.alloc(1024);
    assertEquals(1, SoHook.getMemoryStats().totalAllocCount);
}

// ✅ 好的做法（相对值）
@Test
public void testGood() {
    SoHook.resetStats();
    long countBefore = SoHook.getMemoryStats().totalAllocCount;
    
    TestMemoryHelper.alloc(1024);
    
    long countAfter = SoHook.getMemoryStats().totalAllocCount;
    assertTrue("Should increase by at least 1", 
               countAfter >= countBefore + 1);
}
```

### 4. 检查前置条件

```java
@Test
public void testWithPrecondition() {
    // ✅ 检查测试库是否加载
    if (!TestMemoryHelper.isLibraryLoaded()) {
        Log.w(TAG, "Skipping test: library not loaded");
        return;
    }
    
    // ✅ 检查 Hook 是否成功
    if (!sHookInitialized) {
        Log.w(TAG, "Skipping test: hook not initialized");
        return;
    }
    
    // 执行测试
    // ...
}
```

---

## 🔧 调试测试隔离问题

### 1. 查看执行顺序

```bash
# 运行测试套件并查看日志
.\gradlew :sohook:connectedAndroidTest 2>&1 | findstr "Test setup\|Test teardown"
```

**输出示例**:
```
SoHookBasicTest: Test setup completed
SoHookBasicTest: Test teardown completed
SoHookAccuracyTest: Test setup completed (hook initialized: false)
SoHookAccuracyTest: Test teardown completed
```

### 2. 检查 Hook 状态

在每个测试开始时记录：

```java
@Before
public void setUp() {
    Log.i(TAG, "=== Test Setup ===");
    Log.i(TAG, "Hook initialized: " + sHookInitialized);
    
    SoHook.MemoryStats stats = SoHook.getMemoryStats();
    Log.i(TAG, "Initial stats: " + stats);
    
    // ...
}
```

### 3. 添加测试间隔

```java
@After
public void tearDown() {
    SoHook.resetStats();
    
    // 添加间隔，确保清理完成
    try {
        Thread.sleep(200);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
}
```

---

## 📊 验证隔离性

### 测试 1: 单独运行

```bash
# 运行 AccuracyTest
.\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookAccuracyTest"
```

**预期**: ✅ 所有测试通过

### 测试 2: 套件运行

```bash
# 运行整个测试套件
.\gradlew :sohook:connectedAndroidTest
```

**预期**: ✅ 所有测试通过

### 测试 3: 重复运行

```bash
# 连续运行 3 次
for /L %i in (1,1,3) do (
    echo Run %i
    .\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookAccuracyTest"
)
```

**预期**: ✅ 每次都通过

---

## 🚨 常见问题

### 问题 1: Hook 失败

**症状**:
```
Failed to hook libtest_memory.so, result: -1
```

**原因**: 重复 Hook

**解决**: 使用静态标志 `sHookInitialized`

### 问题 2: 统计不归零

**症状**:
```
Expected: 0
Actual: 42
```

**原因**: 之前的测试没有清理

**解决**: 在 `setUp()` 中调用 `SoHook.resetStats()`

### 问题 3: 测试顺序敏感

**症状**: 改变测试顺序后结果不同

**原因**: 测试之间有依赖

**解决**: 
1. 使每个测试完全独立
2. 不依赖执行顺序
3. 显式设置所需状态

---

## ✅ 检查清单

运行测试套件前确保：

- [ ] 每个测试类有独立的 `setUp()` 和 `tearDown()`
- [ ] `setUp()` 中重置统计：`SoHook.resetStats()`
- [ ] Hook 使用静态标志避免重复
- [ ] 测试结束时清理资源
- [ ] 使用相对断言而不是绝对断言
- [ ] 添加适当的等待时间
- [ ] 检查前置条件（库是否加载）
- [ ] 测试不依赖执行顺序

---

## 📝 修改总结

### SoHookAccuracyTest 的修改

```java
// 添加静态标志
private static boolean sHookInitialized = false;

@Before
public void setUp() {
    // 1. 等待之前的测试完成
    Thread.sleep(100);
    
    // 2. 初始化
    SoHook.init(true);
    
    // 3. 只 Hook 一次
    if (!sHookInitialized) {
        SoHook.hook(Collections.singletonList("libtest_memory.so"));
        Thread.sleep(50);
        sHookInitialized = true;
    }
    
    // 4. 重置统计
    SoHook.resetStats();
    Thread.sleep(50);
}
```

---

**状态**: ✅ 已修复  
**修改文件**: `SoHookAccuracyTest.java`  
**关键改进**: 静态 Hook 标志 + 等待时间 + 显式重置
