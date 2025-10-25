# SoHook 测试索引

快速查找所有测试方法

---

## 📑 目录

- [SoHookBasicTest (15个)](#sohookbasictest)
- [SoHookMemoryLeakTest (10个)](#sohookumemoryleaktest)
- [SoHookStressTest (12个)](#sohookstresstest)

---

## SoHookBasicTest

基础功能测试 - 15个测试方法

| # | 测试方法 | 描述 | 验证内容 |
|---|---------|------|---------|
| 1 | `testLibraryLoaded` | 库加载测试 | native 库正确加载 |
| 2 | `testInit` | 初始化测试 | 基本初始化功能 |
| 3 | `testInitWithBacktrace` | 栈回溯初始化 | 带栈回溯的初始化 |
| 4 | `testDoubleInit` | 重复初始化 | 重复初始化不崩溃 |
| 5 | `testHook` | Hook功能 | Hook系统库 |
| 6 | `testHookMultiple` | Hook多个库 | 同时Hook多个库 |
| 7 | `testHookEmptyList` | Hook空列表 | 空列表返回错误 |
| 8 | `testHookNull` | Hook null | null参数返回错误 |
| 9 | `testUnhook` | Unhook功能 | 取消Hook |
| 10 | `testGetMemoryStats` | 获取统计 | 统计信息结构正确 |
| 11 | `testResetStats` | 重置统计 | 重置后统计归零 |
| 12 | `testGetLeakReport` | 获取报告 | 报告格式正确 |
| 13 | `testBacktraceToggle` | 栈回溯开关 | 动态开关栈回溯 |
| 14 | `testMemoryStatsToString` | toString方法 | MemoryStats.toString |
| 15 | `testUninitializedAccess` | 未初始化访问 | API不崩溃 |

### 运行命令

```bash
# 运行所有基础测试
.\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookBasicTest"

# 运行单个测试
.\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookBasicTest.testInit"
```

---

## SoHookMemoryLeakTest

内存泄漏检测测试 - 10个测试方法

| # | 测试方法 | 描述 | 验证内容 |
|---|---------|------|---------|
| 1 | `testDumpLeakReport` | 导出报告 | 文件正确创建和写入 |
| 2 | `testDumpLeakReportInvalidPath` | 无效路径 | 错误处理 |
| 3 | `testDumpLeakReportNullPath` | null路径 | 参数检查 |
| 4 | `testDumpLeakReportEmptyPath` | 空路径 | 参数检查 |
| 5 | `testLeakReportFormat` | 报告格式 | 包含必要信息 |
| 6 | `testStatsAfterHook` | Hook后统计 | Hook后统计正常 |
| 7 | `testMultipleGetLeakReport` | 多次获取报告 | 连续调用稳定 |
| 8 | `testLeakReportAfterReset` | 重置后报告 | 重置后报告正确 |
| 9 | `testDumpMultipleReports` | 导出多个报告 | 导出多个文件 |
| 10 | `testOverwriteExistingReport` | 覆盖文件 | 文件覆盖功能 |

### 运行命令

```bash
# 运行所有泄漏检测测试
.\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookMemoryLeakTest"

# 运行单个测试
.\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookMemoryLeakTest.testDumpLeakReport"
```

---

## SoHookStressTest

压力测试 - 12个测试方法

| # | 测试方法 | 描述 | 验证内容 |
|---|---------|------|---------|
| 1 | `testRepeatedHookUnhook` | 重复Hook/Unhook | 10次重复操作稳定 |
| 2 | `testRepeatedGetStats` | 重复获取统计 | 100次调用稳定 |
| 3 | `testRepeatedGetLeakReport` | 重复获取报告 | 50次调用稳定 |
| 4 | `testRepeatedReset` | 重复重置 | 50次重置稳定 |
| 5 | `testConcurrentGetStats` | 并发获取统计 | 10线程并发安全 |
| 6 | `testConcurrentGetLeakReport` | 并发获取报告 | 5线程并发安全 |
| 7 | `testConcurrentHook` | 并发Hook | 5线程并发Hook |
| 8 | `testConcurrentBacktraceToggle` | 并发栈回溯开关 | 10线程并发开关 |
| 9 | `testHookManyLibraries` | Hook大量库 | Hook多个库 |
| 10 | `testGetStatsPerformance` | 统计性能 | 1000次调用性能 |
| 11 | `testGetLeakReportPerformance` | 报告性能 | 100次调用性能 |
| 12 | `testMemoryStability` | 内存稳定性 | 100次操作无泄漏 |

### 运行命令

```bash
# 运行所有压力测试
.\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookStressTest"

# 运行单个测试
.\gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookStressTest.testConcurrentGetStats"
```

---

## 快速查找

### 按功能分类

#### 初始化相关
- `SoHookBasicTest.testLibraryLoaded`
- `SoHookBasicTest.testInit`
- `SoHookBasicTest.testInitWithBacktrace`
- `SoHookBasicTest.testDoubleInit`
- `SoHookBasicTest.testUninitializedAccess`

#### Hook相关
- `SoHookBasicTest.testHook`
- `SoHookBasicTest.testHookMultiple`
- `SoHookBasicTest.testHookEmptyList`
- `SoHookBasicTest.testHookNull`
- `SoHookBasicTest.testUnhook`
- `SoHookMemoryLeakTest.testStatsAfterHook`
- `SoHookStressTest.testRepeatedHookUnhook`
- `SoHookStressTest.testConcurrentHook`
- `SoHookStressTest.testHookManyLibraries`

#### 统计信息相关
- `SoHookBasicTest.testGetMemoryStats`
- `SoHookBasicTest.testResetStats`
- `SoHookBasicTest.testMemoryStatsToString`
- `SoHookStressTest.testRepeatedGetStats`
- `SoHookStressTest.testRepeatedReset`
- `SoHookStressTest.testConcurrentGetStats`
- `SoHookStressTest.testGetStatsPerformance`

#### 泄漏报告相关
- `SoHookBasicTest.testGetLeakReport`
- `SoHookMemoryLeakTest.testDumpLeakReport`
- `SoHookMemoryLeakTest.testDumpLeakReportInvalidPath`
- `SoHookMemoryLeakTest.testDumpLeakReportNullPath`
- `SoHookMemoryLeakTest.testDumpLeakReportEmptyPath`
- `SoHookMemoryLeakTest.testLeakReportFormat`
- `SoHookMemoryLeakTest.testMultipleGetLeakReport`
- `SoHookMemoryLeakTest.testLeakReportAfterReset`
- `SoHookMemoryLeakTest.testDumpMultipleReports`
- `SoHookMemoryLeakTest.testOverwriteExistingReport`
- `SoHookStressTest.testRepeatedGetLeakReport`
- `SoHookStressTest.testConcurrentGetLeakReport`
- `SoHookStressTest.testGetLeakReportPerformance`

#### 栈回溯相关
- `SoHookBasicTest.testInitWithBacktrace`
- `SoHookBasicTest.testBacktraceToggle`
- `SoHookStressTest.testConcurrentBacktraceToggle`

#### 性能测试
- `SoHookStressTest.testGetStatsPerformance`
- `SoHookStressTest.testGetLeakReportPerformance`

#### 并发测试
- `SoHookStressTest.testConcurrentGetStats`
- `SoHookStressTest.testConcurrentGetLeakReport`
- `SoHookStressTest.testConcurrentHook`
- `SoHookStressTest.testConcurrentBacktraceToggle`

#### 稳定性测试
- `SoHookStressTest.testRepeatedHookUnhook`
- `SoHookStressTest.testRepeatedGetStats`
- `SoHookStressTest.testRepeatedGetLeakReport`
- `SoHookStressTest.testRepeatedReset`
- `SoHookStressTest.testMemoryStability`

---

## 测试统计

- **总测试数**: 37个
- **基础测试**: 15个 (40.5%)
- **泄漏检测**: 10个 (27.0%)
- **压力测试**: 12个 (32.5%)

---

## 相关文档

- [测试总结](../../../TEST_SUMMARY.md)
- [详细文档](../../../TEST_README.md)
- [快速开始](../../../TESTING_QUICK_START.md)

---

**最后更新**: 2025-10-25
