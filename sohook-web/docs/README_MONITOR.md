# SoHook 内存监控 Web Dashboard

## 功能特性

- 🔄 实时内存统计监控
- 📊 内存泄漏可视化展示
- 🔍 详细的泄漏信息和调用栈
- 🌐 通过 HTTP 连接 Android 设备
- ⚡ 自动刷新数据

## 快速开始

### 1. 启动 Web 服务

```bash
cd sohook-web
npm install
npm run dev
```

访问 http://localhost:5173

### 2. Android 端配置

Android 端需要集成 HTTP 服务器来提供 API 接口（待实现）。

预期的 API 接口：

```
GET /api/health        - 健康检查
GET /api/stats         - 获取内存统计
GET /api/leaks         - 获取泄漏列表
GET /api/leak-report   - 获取完整报告
POST /api/reset        - 重置统计
```

### 3. 连接设备

1. 在 Web 页面的"连接状态"卡片中
2. 输入 Android 设备的 IP 地址和端口（如 `http://192.168.1.100:8080`）
3. 点击"测试连接"
4. 连接成功后，数据会自动刷新

## 数据格式

### MemoryStats

```typescript
{
  totalAllocCount: number;    // 总分配次数
  totalAllocSize: number;     // 总分配大小（字节）
  totalFreeCount: number;     // 总释放次数
  totalFreeSize: number;      // 总释放大小（字节）
  currentAllocCount: number;  // 当前泄漏次数
  currentAllocSize: number;   // 当前泄漏大小（字节）
}
```

### MemoryRecord

```typescript
{
  ptr: string;           // 内存地址
  size: number;          // 分配大小
  backtrace: string[];   // 调用栈（可选）
  timestamp: number;     // 分配时间戳
}
```

## 技术栈

- **React 19** - UI 框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **TailwindCSS** - 样式框架
- **Zustand** - 状态管理
- **Axios** - HTTP 客户端
- **Lucide React** - 图标库

## 下一步开发

- [ ] Android 端 HTTP 服务器集成
- [ ] 内存趋势图表
- [ ] 导出报告功能
- [ ] 多设备管理
- [ ] 历史记录查看
