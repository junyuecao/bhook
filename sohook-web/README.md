# SoHook Web Dashboard

实时监控 Android 应用内存泄漏的 Web 可视化平台。

## 功能特性

- 📊 **实时内存统计** - 6 项关键指标实时展示
- 📈 **内存趋势图** - 可视化内存变化趋势
- 🔍 **泄漏详情** - 详细的泄漏列表和调用栈
- 🔄 **自动刷新** - 每 2 秒自动更新数据
- 💻 **现代化 UI** - 基于 React 19 + TailwindCSS

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 启动开发服务器

```bash
npm run dev
```

访问 http://localhost:5173

### 3. 连接 Android 设备

1. 确保 Android 应用已启动 SoHook Web 服务器
2. 在 Web Dashboard 中输入设备 IP 地址
3. 点击"测试连接"

## 技术栈

- **React 19** - UI 框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **TailwindCSS** - 样式框架
- **Zustand** - 状态管理
- **Recharts** - 图表库
- **Axios** - HTTP 客户端

## 文档

- 📖 [功能说明](./README_MONITOR.md)
- 🚀 [快速开始](docs/QUICK_START_WEB_MONITOR.md)
- 📊 [实现总结](./IMPLEMENTATION_SUMMARY.md)
- 🎯 [项目总览](./PROJECT_OVERVIEW.md)
- 🎬 [演示指南](./DEMO_GUIDE.md)

## 构建

```bash
npm run build
```

构建产物在 `dist/` 目录。
