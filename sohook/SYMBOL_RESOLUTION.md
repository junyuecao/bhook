# 符号解析说明

## 🎯 功能概述

栈回溯现在支持符号解析，可以将内存地址转换为可读的函数名。

## 📊 报告格式

### 基本格式（带符号解析）

```
Leak #1: ptr=0x7cc8247e80, size=128, so=tracked
  Backtrace (8 frames):
    #0: 0x7f8a5c1234 malloc+52 (libc.so)
    #1: 0x7f8a5c1456 my_alloc_function+124 (libsample.so)
    #2: 0x7f8a5c1678 test_function+88 (libsample.so)
    #3: 0x7f8a5c189a Java_com_example_NativeHacker_testMalloc+42 (libsample.so)
    #4: 0x7f8a5c1abc art_quick_generic_jni_trampoline+28 (libart.so)
    ...
```

### 格式说明

每一帧显示：
- `#N`: 帧编号
- `0x7f8a5c1234`: 内存地址
- `malloc`: 函数名
- `+52`: 函数内偏移量（字节）
- `(libc.so)`: 所属库

## 🔧 实现原理

使用 `dladdr()` 函数进行符号解析：

```c
Dl_info info;
if (dladdr(address, &info) && info.dli_sname) {
  const char *symbol = info.dli_sname;      // 函数名
  const char *lib = info.dli_fname;         // 库文件路径
  void *base = info.dli_saddr;              // 函数基地址
  ptrdiff_t offset = address - base;        // 偏移量
}
```

## 📝 符号信息说明

### 1. C/C++ 函数

```
#1: 0x7f8a5c1456 my_alloc_function+124 (libsample.so)
```

- **函数名**: `my_alloc_function`
- **偏移**: `+124` 字节（从函数入口开始）
- **库**: `libsample.so`

### 2. JNI 函数

```
#3: 0x7f8a5c189a Java_com_example_NativeHacker_testMalloc+42 (libsample.so)
```

- **函数名**: `Java_com_example_NativeHacker_testMalloc`
- **对应 Java 方法**: `NativeHacker.testMalloc()`

### 3. 系统函数

```
#0: 0x7f8a5c1234 malloc+52 (libc.so)
#4: 0x7f8a5c1abc art_quick_generic_jni_trampoline+28 (libart.so)
```

- 系统库函数也会被解析
- 可以看到 ART 虚拟机的内部调用

### 4. 无法解析的地址

```
#5: 0x7f8a5c2000
```

- 只显示地址
- 可能是：
  - 匿名内存区域
  - 已卸载的库
  - JIT 代码

## 🎓 高级用法

### 1. C++ 名称修饰（Name Mangling）

C++ 函数名会被修饰：

```
#2: 0x7f8a5c1678 _ZN7MyClass11allocMemoryEi+88 (libsample.so)
```

可以使用 `c++filt` 解码：

```bash
$ c++filt _ZN7MyClass11allocMemoryEi
MyClass::allocMemory(int)
```

### 2. 获取源文件和行号

`dladdr` 不提供源文件信息，需要使用 `addr2line`：

```bash
# 获取地址
# 假设报告显示: #1: 0x7f8a5c1456 my_alloc_function+124 (libsample.so)

# 使用 addr2line 解析
$ adb pull /data/app/.../lib/arm64/libsample.so
$ addr2line -e libsample.so -f -C 0x1456
my_alloc_function
/path/to/source.cpp:42
```

参数说明：
- `-e`: 指定可执行文件/库
- `-f`: 显示函数名
- `-C`: 解码 C++ 名称
- `0x1456`: 地址（需要减去库的加载基址）

### 3. 批量解析

创建脚本自动解析所有地址：

```bash
#!/bin/bash
# resolve_backtrace.sh

# 从报告中提取地址
grep "#" leak_report.txt | while read line; do
  addr=$(echo $line | awk '{print $2}')
  lib=$(echo $line | grep -o '([^)]*)')
  
  if [ ! -z "$lib" ]; then
    lib=${lib:1:-1}  # 去掉括号
    echo "=== $line ==="
    addr2line -e $lib -f -C $addr
  fi
done
```

## 🔍 调试技巧

### 1. 确保符号未被剥离

检查库是否包含符号：

```bash
$ adb pull /data/app/.../lib/arm64/libsample.so
$ nm -D libsample.so | grep my_function
0000000000001456 T my_alloc_function
```

如果没有输出，说明符号被剥离了。

### 2. 使用未剥离的调试版本

在 `build.gradle` 中：

```gradle
android {
    buildTypes {
        debug {
            ndk {
                debugSymbolLevel 'FULL'  // 保留完整符号
            }
        }
    }
}
```

### 3. 保存符号文件

```gradle
android {
    buildTypes {
        release {
            ndk {
                debugSymbolLevel 'SYMBOL_TABLE'  // 生成符号表
            }
        }
    }
}
```

这会在 `build/intermediates/cmake/release/obj/` 生成未剥离的 `.so` 文件。

## 📊 性能影响

符号解析的性能影响：

| 操作 | 耗时 | 说明 |
|------|------|------|
| 栈回溯 | ~5 μs | 捕获调用栈 |
| 符号解析 | ~1 μs/帧 | `dladdr` 查询 |
| 总计 | ~13 μs | 8 帧的情况 |

**建议**：
- 符号解析在生成报告时进行（不在 malloc 时）
- 对性能影响很小（只在查看报告时）

## 🎯 最佳实践

### 1. 开发阶段

```java
// 使用 Debug 构建，保留符号
SoHook.init(true, true);  // 启用栈回溯
```

报告会显示完整的函数名。

### 2. 发布阶段

```java
// Release 构建，可以剥离符号
SoHook.init(false, false);  // 不启用栈回溯
```

如果需要调试：
1. 启用栈回溯
2. 获取报告（只有地址）
3. 使用保存的符号文件离线解析

### 3. 混合方案

```java
// 只在需要时启用
if (BuildConfig.DEBUG || isDebugging) {
  SoHook.setBacktraceEnabled(true);
}
```

## 🛠️ 故障排查

### 问题 1: 只显示地址，没有函数名

**原因**：
- 符号被剥离
- 地址不在任何已加载的库中

**解决**：
```bash
# 检查符号
nm -D libsample.so | grep -i malloc

# 使用 Debug 构建
./gradlew assembleDebug
```

### 问题 2: 函数名是乱码

**原因**：C++ 名称修饰

**解决**：
```bash
# 使用 c++filt 解码
c++filt _ZN7MyClass11allocMemoryEi
```

### 问题 3: 无法获取源文件行号

**原因**：`dladdr` 不提供行号信息

**解决**：
```bash
# 使用 addr2line
addr2line -e libsample.so -f -C 0x1456
```

## 📚 参考资料

### dladdr 文档

```c
#include <dlfcn.h>

typedef struct {
  const char *dli_fname;  // 库文件路径
  void       *dli_fbase;  // 库加载基址
  const char *dli_sname;  // 符号名称
  void       *dli_saddr;  // 符号地址
} Dl_info;

int dladdr(const void *addr, Dl_info *info);
```

### 相关工具

- `nm`: 列出符号表
- `addr2line`: 地址转源文件行号
- `c++filt`: C++ 名称解码
- `objdump`: 反汇编
- `readelf`: 读取 ELF 信息

## 🎉 总结

### 功能特点

- ✅ **自动符号解析** - 运行时自动解析
- ✅ **函数名显示** - 清晰的函数调用链
- ✅ **库信息** - 显示所属库
- ✅ **偏移量** - 精确定位代码位置
- ✅ **零配置** - 无需额外设置

### 使用建议

1. **开发阶段**：启用栈回溯 + 保留符号
2. **测试阶段**：按需启用栈回溯
3. **生产环境**：禁用栈回溯或使用离线解析

---

**功能状态**: ✅ 已实现  
**性能影响**: ~1 μs/帧（报告生成时）  
**文档日期**: 2025-10-25
