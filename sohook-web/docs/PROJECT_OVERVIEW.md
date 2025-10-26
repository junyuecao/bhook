# SoHook Web 监控项目总览

## 项目结构

```
bhook/
├── sohook/                          # Android 内存监控库
│   ├── src/main/
│   │   ├── java/com/sohook/
│   │   │   ├── SoHook.java          # 主 API 类
│   │   │   └── SoHookWebServer.java # HTTP 服务器
│   │   └── cpp/
│   │       ├── memory_tracker.c     # 内存追踪核心
│   │       ├── memory_tracker.h
│   │       ├── sohook_jni.c         # JNI 绑定
│   │       └── ...
│   ├── build.gradle                 # 依赖配置
│   ├── README.md                    # 主文档
│   └── WEB_SERVER_USAGE.md         # Web 服务器使用指南
│
├── sohook-web/                      # Web Dashboard
│   ├── src/
│   │   ├── components/              # React 组件
│   │   │   ├── ui/                  # 基础 UI 组件
│   │   │   │   ├── card.tsx
│   │   │   │   ├── button.tsx
│   │   │   │   └── badge.tsx
│   │   │   ├── ConnectionStatus.tsx # 连接状态
│   │   │   ├── MemoryStatsCard.tsx  # 统计卡片
│   │   │   ├── MemoryChart.tsx      # 趋势图表
│   │   │   └── LeaksList.tsx        # 泄漏列表
│   │   ├── pages/
│   │   │   └── Dashboard.tsx        # 主页面
│   │   ├── services/
│   │   │   └── api.ts               # API 客户端
│   │   ├── store/
│   │   │   └── useMemoryStore.ts    # 状态管理
│   │   ├── types/
│   │   │   └── index.ts             # TypeScript 类型
│   │   └── lib/
│   │       └── utils.ts             # 工具函数
│   ├── package.json                 # 依赖配置
│   ├── README_MONITOR.md           # 功能说明
│   ├── IMPLEMENTATION_SUMMARY.md   # 实现总结
│   └── PROJECT_OVERVIEW.md         # 本文档
│
└── QUICK_START_WEB_MONITOR.md      # 快速开始指南
```

## 技术栈

### Android 端
| 技术 | 版本 | 用途 |
|------|------|------|
| NanoHTTPD | 2.3.1 | 轻量级 HTTP 服务器 |
| Gson | 2.10.1 | JSON 序列化/反序列化 |
| ByteHook | - | 内存 Hook 框架 |
| JNI | - | Java/Native 桥接 |

### Web 端
| 技术 | 版本 | 用途 |
|------|------|------|
| React | 19.1.1 | UI 框架 |
| TypeScript | 5.9.3 | 类型安全 |
| Vite | 7.1.7 | 构建工具 |
| TailwindCSS | 4.1.16 | 样式框架 |
| Zustand | 5.0.8 | 状态管理 |
| Axios | 1.12.2 | HTTP 客户端 |
| Recharts | 3.3.0 | 图表库 |
| Lucide React | 0.548.0 | 图标库 |

## 核心功能

### 1. 实时监控
- 每 2 秒自动刷新数据
- 支持手动刷新
- 连接状态实时显示

### 2. 数据展示
- **内存统计卡片**
  - 总分配次数/大小
  - 总释放次数/大小
  - 当前泄漏次数/大小
  
- **内存趋势图**
  - 实时折线图
  - 双 Y 轴（大小 + 次数）
  - 最近 30 个数据点
  
- **泄漏列表**
  - 内存地址和大小
  - 可展开的调用栈
  - 复制功能

### 3. 操作功能
- 配置服务器地址
- 测试连接
- 刷新数据
- 重置统计

## 数据流

```
┌─────────────┐
│   Native    │
│   Memory    │
│   Tracker   │
└──────┬──────┘
       │ 追踪内存分配/释放
       ▼
┌─────────────┐
│  Hash Table │
│   + Stats   │
└──────┬──────┘
       │ JNI 调用
       ▼
┌─────────────┐
│ SoHookWeb   │
│   Server    │
└──────┬──────┘
       │ HTTP/JSON
       ▼
┌─────────────┐
│  API Client │
│   (Axios)   │
└──────┬──────┘
       │ 状态更新
       ▼
┌─────────────┐
│   Zustand   │
│    Store    │
└──────┬──────┘
       │ React 渲染
       ▼
┌─────────────┐
│  Dashboard  │
│  Components │
└─────────────┘
```

## API 接口

### 端点列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/health | 健康检查 |
| GET | /api/stats | 获取内存统计 |
| GET | /api/leaks | 获取泄漏列表（JSON） |
| GET | /api/leak-report | 获取泄漏报告（文本） |
| POST | /api/reset | 重置统计 |

### 响应格式

```typescript
{
  code: number;      // 0 = 成功
  message: string;   // 响应消息
  data: T;          // 响应数据
}
```

## 开发指南

### 启动 Web 开发服务器

```bash
cd sohook-web
npm install
npm run dev
```

访问: http://localhost:5173

### 构建生产版本

```bash
npm run build
npm run preview
```

### 代码规范

- **TypeScript**: 严格模式
- **ESLint**: 代码检查
- **Prettier**: 代码格式化
- **组件**: 函数式组件 + Hooks
- **样式**: TailwindCSS 实用类

### 添加新组件

1. 在 `src/components/` 创建组件文件
2. 使用 TypeScript 定义 Props
3. 导出组件
4. 在需要的地方导入使用

示例:
```typescript
interface MyComponentProps {
  title: string;
  count: number;
}

export function MyComponent({ title, count }: MyComponentProps) {
  return (
    <div>
      <h2>{title}</h2>
      <p>{count}</p>
    </div>
  );
}
```

### 添加新 API

1. 在 `src/services/api.ts` 添加方法
2. 在 `src/types/index.ts` 定义类型
3. 在 `src/store/useMemoryStore.ts` 添加状态和操作
4. 在组件中使用

## 部署

### Android 端

集成到你的应用：

```gradle
dependencies {
    implementation project(':sohook')
}
```

### Web 端

#### 选项 1: 本地运行
```bash
npm run dev
```

#### 选项 2: 构建静态文件
```bash
npm run build
# 输出到 dist/ 目录
```

#### 选项 3: Docker 部署
```dockerfile
FROM node:18-alpine
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build
EXPOSE 5173
CMD ["npm", "run", "preview"]
```

## 性能优化

### Android 端
- 使用哈希表 O(1) 查找
- 内存池减少分配开销
- 可选的栈回溯（按需启用）

### Web 端
- 自动刷新间隔可配置
- 图表数据点限制
- 组件懒加载
- 响应式设计

## 安全考虑

⚠️ **重要提示**

1. **仅开发/测试环境使用**
2. **无身份验证** - 任何人都可以访问
3. **明文传输** - HTTP 未加密
4. **端口暴露** - 注意防火墙设置

### 建议的安全措施

- 使用 ADB 端口转发而非网络连接
- 限制服务器监听地址（localhost）
- 添加基本认证（未来版本）
- 使用 HTTPS（未来版本）

## 故障排除

### 常见问题

**1. 无法连接到设备**
- 检查设备和电脑是否在同一网络
- 使用 `adb forward tcp:8080 tcp:8080`
- 检查防火墙设置

**2. 数据不更新**
- 确认已调用 `SoHook.hook()`
- 检查连接状态
- 查看 Logcat 日志

**3. 性能问题**
- 禁用栈回溯
- 增加刷新间隔
- 只监控必要的 so 库

## 贡献指南

### 报告问题
在 GitHub Issues 中提交，包含：
- 问题描述
- 复现步骤
- 环境信息
- 日志输出

### 提交代码
1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 路线图

### 短期计划
- [ ] 添加单元测试
- [ ] 改进错误处理
- [ ] 添加更多图表类型
- [ ] 支持数据导出

### 长期计划
- [ ] WebSocket 实时推送
- [ ] 多设备管理
- [ ] 历史数据记录
- [ ] 告警功能
- [ ] 性能分析工具

## 相关文档

- [快速开始](QUICK_START_WEB_MONITOR.md)
- [Web 服务器使用指南](../../sohook/WEB_SERVER_USAGE.md)
- [实现总结](IMPLEMENTATION_SUMMARY.md)
- [SoHook 主文档](../../sohook/README.md)

## 许可证

与 ByteHook 保持一致
