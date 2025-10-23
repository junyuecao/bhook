# SoHook 测试指南

## 在bytehook_sample中测试SoHook

bytehook_sample应用已经集成了SoHook测试页面，可以直接运行测试。

### 运行测试

1. **编译并安装应用**

```bash
# 在项目根目录执行
./gradlew :bytehook_sample:installDebug
```

2. **启动应用**

在设备上打开"bytehook"应用

3. **进入SoHook测试页面**

点击主页面底部的红色按钮："SoHook 内存泄漏检测测试"

### 测试步骤

#### 基础测试流程

1. **开始监控**
   - 点击"开始监控 (libc.so, libsample.so)"按钮
   - 应该看到Toast提示"开始监控"
   - 统计信息会自动每秒更新一次

2. **触发内存分配**
   - 点击"分配内存 (dlopen + run)"按钮
   - 这会加载libsample.so并执行一些操作
   - 观察统计信息中的分配次数和大小增加

3. **查看统计信息**
   - 统计信息会自动更新
   - 也可以点击"刷新统计信息"手动更新
   - 观察以下指标：
     - 总分配次数/大小
     - 总释放次数/大小
     - 当前泄漏次数/大小

4. **生成泄漏报告**
   - 点击"生成泄漏报告 (输出到logcat)"
   - 使用以下命令查看报告：
     ```bash
     adb logcat -s SoHookTest
     ```

5. **导出报告到文件**
   - 点击"导出报告到文件"
   - 报告会保存到应用私有目录
   - 可以使用以下命令查看：
     ```bash
     adb shell cat /data/data/com.bytedance.android.bytehook.sample/files/sohook_leak_report.txt
     ```

6. **释放内存**
   - 点击"释放内存 (dlclose)"
   - 观察统计信息中的释放次数增加
   - 泄漏数量应该减少（如果之前的分配被正确释放）

7. **重置统计**
   - 点击"重置统计信息"
   - 所有统计数据归零

8. **停止监控**
   - 点击"停止监控"
   - 监控停止，统计信息不再更新

### 查看日志

#### 查看SoHook日志

```bash
adb logcat -s SoHookTest
```

#### 查看所有相关日志

```bash
adb logcat | grep -E "SoHook|MemoryTracker|ByteHook"
```

#### 查看详细的内存追踪日志

```bash
adb logcat -s MemoryTracker
```

### 预期结果

#### 正常情况

1. **初始化成功**
   ```
   I/SoHookTest: SoHook初始化成功
   ```

2. **开始监控成功**
   ```
   I/MemoryTracker: Hooking memory functions in: libc.so
   I/MemoryTracker: Hooking memory functions in: libsample.so
   I/MemoryTracker: Hooked 2 libraries
   ```

3. **统计信息更新**
   ```
   统计信息显示区域会显示：
   === 内存统计信息 ===
   
   总分配次数: 1234
   总分配大小: 45.67 KB
   
   总释放次数: 1200
   总释放大小: 43.21 KB
   
   当前泄漏次数: 34
   当前泄漏大小: 2.46 KB
   ```

4. **泄漏报告**
   ```
   === Memory Leak Report ===
   Total Allocations: 1234 (46768 bytes)
   Total Frees: 1200 (44248 bytes)
   Current Leaks: 34 (2520 bytes)
   
   Leak #1: ptr=0x7a8c001000, size=64, so=tracked
   Leak #2: ptr=0x7a8c002000, size=128, so=tracked
   ...
   ```

### 常见问题排查

#### Q1: 点击"开始监控"后没有反应

**排查步骤：**
1. 查看logcat是否有错误信息
2. 确认SoHook初始化是否成功
3. 检查ByteHook是否正常工作

```bash
adb logcat | grep -E "SoHook|MemoryTracker|ByteHook"
```

#### Q2: 统计信息一直是0

**可能原因：**
1. 监控的so库还未加载
2. Hook未生效
3. 目标so库没有调用malloc/free

**解决方法：**
- 先点击"分配内存"按钮触发一些操作
- 监控libc.so会看到大量的内存分配

#### Q3: 显示大量的内存泄漏

**说明：**
- 监控libc.so时，系统和应用的所有内存分配都会被记录
- 很多内存是正常的长期持有，不一定是泄漏
- 建议只监控特定的业务so库

#### Q4: 应用崩溃或卡顿

**可能原因：**
- 监控libc.so会产生大量的hook调用，影响性能
- 内存记录过多导致内存不足

**解决方法：**
- 只监控必要的so库
- 定期重置统计信息
- 在调试模式下使用，不要在生产环境开启

### 性能测试

#### 测试Hook对性能的影响

1. 在主页面选择"without hook"，点击"benchmark strlen()"
2. 记录QPS值
3. 选择"bytehook"，再次点击"benchmark strlen()"
4. 对比两次的QPS值

#### 测试SoHook的性能影响

1. 进入SoHook测试页面
2. 不开启监控，返回主页面执行benchmark测试
3. 开启监控后，再次执行benchmark测试
4. 对比性能差异

### 高级测试

#### 测试内存泄漏检测准确性

1. 创建一个已知会泄漏的native代码
2. 使用SoHook监控
3. 验证是否能正确检测到泄漏

#### 测试多线程场景

1. 在benchmark测试中开启SoHook监控
2. 执行多线程的benchmark测试
3. 验证统计信息的准确性

#### 压力测试

1. 开启监控
2. 长时间运行应用
3. 观察内存占用和性能
4. 检查是否有内存泄漏或崩溃

### 测试报告模板

```
测试日期: 2024-XX-XX
测试设备: XXX
Android版本: XX
测试场景: XXX

测试结果:
1. 初始化: [成功/失败]
2. Hook功能: [成功/失败]
3. 统计准确性: [准确/不准确]
4. 报告生成: [成功/失败]
5. 性能影响: [轻微/中等/严重]

发现的问题:
1. XXX
2. XXX

建议:
1. XXX
2. XXX
```

### 自动化测试

可以使用以下命令进行自动化测试：

```bash
# 启动应用并直接进入测试
adb shell am start -n com.bytedance.android.bytehook.sample/.SoHookTestActivity

# 模拟点击（需要先获取按钮坐标）
adb shell input tap X Y

# 查看日志
adb logcat -s SoHookTest MemoryTracker
```

### 注意事项

1. **⚠️ 重要**: 不要监控libc.so等系统库，会导致死锁和崩溃
2. **测试环境**: 建议在Debug版本中测试
3. **设备要求**: Android 4.1 (API 16) 及以上
4. **权限**: 确保应用有存储权限（导出报告时需要）
5. **性能**: 内存监控会影响性能，仅用于调试
6. **内存**: 长时间监控会占用较多内存，注意及时重置统计
