# SoHook 内存监控 Web 可视化 - 实现总结

## 项目概述

成功实现了 SoHook 内存泄漏监控的 Web 可视化功能，包括 Android 端 HTTP 服务器和 Web 前端 Dashboard。

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      Web Dashboard                          │
│  (React + TypeScript + TailwindCSS + Recharts)             │
│                                                             │
│  - 实时数据展示                                              │
│  - 内存趋势图表                                              │
│  - 泄漏详情查看                                              │
└──────────────────┬──────────────────────────────────────────┘
                   │ HTTP/JSON
                   │
┌──────────────────▼──────────────────────────────────────────┐
│              Android HTTP Server                            │
│            (NanoHTTPD + Gson)                               │
│                                                             │
│  RESTful API:                                               │
│  - GET /api/health                                          │
│  - GET /api/stats                                           │
│  - GET /api/leaks                                           │
│  - GET /api/leak-report                                     │
│  - POST /api/reset                                          │
└──────────────────┬──────────────────────────────────────────┘
                   │ JNI
                   │
┌──────────────────▼──────────────────────────────────────────┐
│              Native Memory Tracker                          │
│                  (C/C++)                                    │
│                                                             │
│  - 内存分配追踪                                              │
│  - 哈希表存储                                                │
│  - 统计信息收集                                              │
│  - JSON 序列化                                               │
└─────────────────────────────────────────────────────────────┘
```

## 已实现功能

### Android 端

#### 1. HTTP 服务器 (`SoHookWebServer.java`)
- ✅ 基于 NanoHTTPD 的轻量级 HTTP 服务器
- ✅ RESTful API 接口
- ✅ CORS 支持
- ✅ JSON 响应格式
- ✅ 错误处理

#### 2. SoHook API 扩展 (`SoHook.java`)
- ✅ `startWebServer(int port)` - 启动 Web 服务器
- ✅ `startWebServer()` - 使用默认端口启动
- ✅ `stopWebServer()` - 停止 Web 服务器
- ✅ `isWebServerRunning()` - 检查服务器状态

#### 3. Native 层实现 (`memory_tracker.c`)
- ✅ `memory_tracker_get_leaks_json()` - 生成 JSON 格式泄漏数据
- ✅ 动态缓冲区管理
- ✅ 符号解析（dladdr）
- ✅ 调用栈序列化

#### 4. JNI 绑定 (`sohook_jni.c`)
- ✅ `nativeGetLeaksJson()` - JNI 方法实现
- ✅ 内存管理（自动释放 native 分配的内存）

### Web 端

#### 1. 核心组件

**API 客户端** (`src/services/api.ts`)
- ✅ Axios 封装
- ✅ 类型安全的 API 调用
- ✅ 错误处理
- ✅ 可配置的服务器地址

**状态管理** (`src/store/useMemoryStore.ts`)
- ✅ Zustand 状态管理
- ✅ 自动刷新机制
- ✅ 连接状态管理
- ✅ 错误处理

#### 2. UI 组件

**连接状态** (`ConnectionStatus.tsx`)
- ✅ 服务器地址配置
- ✅ 连接状态显示
- ✅ 连接测试功能
- ✅ 使用提示

**内存统计卡片** (`MemoryStatsCard.tsx`)
- ✅ 6 项关键指标展示
- ✅ 图标和颜色区分
- ✅ 数据格式化（字节、数字）
- ✅ 加载状态

**泄漏列表** (`LeaksList.tsx`)
- ✅ 泄漏记录展示
- ✅ 可展开的调用栈
- ✅ 复制功能
- ✅ 时间戳显示
- ✅ 空状态提示

**内存趋势图** (`MemoryChart.tsx`)
- ✅ 实时折线图
- ✅ 双 Y 轴（大小 + 次数）
- ✅ 自定义 Tooltip
- ✅ 数据点限制（最近 30 个）
- ✅ 响应式设计

**Dashboard** (`Dashboard.tsx`)
- ✅ 整体布局
- ✅ 自动刷新
- ✅ 手动刷新按钮
- ✅ 重置统计功能
- ✅ 响应式网格布局

#### 3. UI 基础组件 (shadcn/ui)
- ✅ Card
- ✅ Button
- ✅ Badge

## 技术栈

### Android
- **NanoHTTPD 2.3.1** - HTTP 服务器
- **Gson 2.10.1** - JSON 序列化
- **ByteHook** - 内存 Hook
- **JNI** - Java/Native 桥接

### Web
- **React 19** - UI 框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **TailwindCSS 4** - 样式框架
- **Zustand** - 状态管理
- **Axios** - HTTP 客户端
- **Recharts** - 图表库
- **Lucide React** - 图标库

## API 接口规范

### 响应格式

所有 API 返回统一格式：

```typescript
{
  code: number;      // 0 表示成功，其他表示错误
  message: string;   // 响应消息
  data: T;          // 响应数据
}
```

### 端点详情

| 方法 | 路径 | 说明 | 响应数据类型 |
|------|------|------|-------------|
| GET | /api/health | 健康检查 | `{ status: string, timestamp: number }` |
| GET | /api/stats | 获取统计 | `MemoryStats` |
| GET | /api/leaks | 获取泄漏列表 | `MemoryRecord[]` |
| GET | /api/leak-report | 获取文本报告 | `string` |
| POST | /api/reset | 重置统计 | `{ message: string }` |

## 使用流程

### 1. Android 端

```java
// 初始化
SoHook.init(true);

// 启动 Web 服务器
SoHook.startWebServer(8080);

// 开始监控
SoHook.hook(Arrays.asList("libnative-lib.so"));

// 停止服务器
SoHook.stopWebServer();
```

### 2. Web 端

```bash
cd sohook-web
npm install
npm run dev
```

访问 http://localhost:5173，输入设备 IP 地址连接。

## 文件清单

### Android 端新增文件
```
sohook/src/main/java/com/sohook/
├── SoHookWebServer.java          # HTTP 服务器实现
└── SoHook.java                   # 添加 Web 服务器管理方法

sohook/src/main/cpp/
├── memory_tracker.h              # 添加 get_leaks_json 声明
├── memory_tracker.c              # 实现 JSON 序列化
└── sohook_jni.c                  # 添加 JNI 绑定

sohook/
├── build.gradle                  # 添加依赖
└── WEB_SERVER_USAGE.md          # 使用文档
```

### Web 端新增文件
```
sohook-web/src/
├── services/
│   └── api.ts                    # API 客户端
├── store/
│   └── useMemoryStore.ts         # 状态管理
├── components/
│   ├── ui/
│   │   ├── card.tsx
│   │   ├── button.tsx
│   │   └── badge.tsx
│   ├── ConnectionStatus.tsx      # 连接状态组件
│   ├── MemoryStatsCard.tsx       # 统计卡片
│   ├── LeaksList.tsx             # 泄漏列表
│   └── MemoryChart.tsx           # 趋势图表
├── pages/
│   └── Dashboard.tsx             # 主页面
└── types/
    └── index.ts                  # 类型定义

sohook-web/
├── README_MONITOR.md             # 功能说明
└── IMPLEMENTATION_SUMMARY.md     # 实现总结
```

## 性能考虑

### Android 端
- ✅ 使用原始 malloc/free 避免递归
- ✅ 动态缓冲区扩展
- ✅ 哈希表 O(1) 查找
- ✅ 可选的栈回溯（性能影响 10-20x）

### Web 端
- ✅ 自动刷新间隔可配置（默认 2 秒）
- ✅ 图表数据点限制（最近 30 个）
- ✅ 虚拟滚动（泄漏列表）
- ✅ 按需加载

## 安全建议

⚠️ **重要提示：**

1. **仅开发/测试环境使用**
2. **无身份验证** - 任何人都可访问
3. **明文传输** - 未加密
4. **性能开销** - 不适合生产环境

## 后续优化方向

### 功能增强
- [ ] 历史数据记录
- [ ] 数据导出（CSV/JSON）
- [ ] 多设备管理
- [ ] 实时告警
- [ ] 过滤和搜索
- [ ] 内存快照对比

### 性能优化
- [ ] WebSocket 实时推送
- [ ] 数据压缩
- [ ] 增量更新
- [ ] 服务端分页

### 安全增强
- [ ] 基本认证
- [ ] HTTPS 支持
- [ ] Token 验证
- [ ] IP 白名单

## 测试建议

### 单元测试
- [ ] API 客户端测试
- [ ] 状态管理测试
- [ ] 组件测试

### 集成测试
- [ ] HTTP 服务器测试
- [ ] JNI 调用测试
- [ ] 端到端测试

### 性能测试
- [ ] 大量泄漏数据测试
- [ ] 并发请求测试
- [ ] 长时间运行测试

## 总结

✅ **核心功能已完成**
- Android 端 HTTP 服务器
- RESTful API 接口
- Web Dashboard 可视化
- 实时数据刷新
- 内存趋势图表
- 泄漏详情展示

🎯 **技术亮点**
- 模块化设计
- 类型安全
- 响应式 UI
- 实时更新
- 易于扩展

📚 **文档完善**
- 使用指南
- API 文档
- 实现总结
- 代码注释

该实现提供了一个完整的内存泄漏监控解决方案，可以帮助开发者快速定位和解决内存问题。
