# SoHook Web Dashboard 性能分析

## 概述
分析在大数据量场景下的性能瓶颈和优化建议。

## 潜在性能问题

### 1. **前端性能问题**

#### 1.1 自动刷新频率过高
**问题**：
```typescript
// Dashboard.tsx
const timer = setInterval(fetchData, refreshInterval); // 默认 2000ms
```
- 默认每2秒刷新一次
- 每次刷新发起4个并发请求（stats, leaks, fdStats, fdLeaks）
- 大数据量时会导致频繁的网络请求和DOM更新

**影响**：
- 网络带宽占用高
- CPU占用高（JSON解析、React渲染）
- 可能导致UI卡顿

**建议**：
```typescript
// 根据数据量动态调整刷新间隔
const refreshInterval = leaks.length > 1000 ? 5000 : 2000;
```

#### 1.2 列表渲染性能
**问题**：
```typescript
// LeaksList.tsx & FdLeaksList.tsx
{displayLeaks.map((leak, idx) => (
  <LeakItem key={`leak-${idx}`} leak={leak} />
))}
```
- 没有虚拟滚动
- 大量DOM节点（1000+泄漏 = 1000+ DOM元素）
- 每个泄漏项包含展开/折叠状态

**影响**：
- 初始渲染慢（1000+节点）
- 滚动性能差
- 内存占用高

**建议**：
```typescript
// 使用虚拟滚动库
import { FixedSizeList } from 'react-window';

<FixedSizeList
  height={600}
  itemCount={displayLeaks.length}
  itemSize={100}
  width="100%"
>
  {({ index, style }) => (
    <div style={style}>
      <LeakItem leak={displayLeaks[index]} />
    </div>
  )}
</FixedSizeList>
```

#### 1.3 图表数据累积
**问题**：
```typescript
// MemoryChart.tsx
const maxDataPoints = 30; // 只保留30个点
```
- ✅ 已经限制了数据点数量
- ✅ 性能影响较小

#### 1.4 深度比较开销
**问题**：
```typescript
// LeaksList.tsx memo比较
for (let i = 0; i < prevProps.leaks.length; i++) {
  if (prevProps.leaks[i].count !== nextProps.leaks[i].count ||
      prevProps.leaks[i].totalSize !== nextProps.leaks[i].totalSize) {
    return false;
  }
}
```
- 大数据量时循环比较开销大
- 1000个泄漏 = 1000次比较

**建议**：
```typescript
// 使用浅比较或哈希值
const getLeaksHash = (leaks) => {
  return leaks.reduce((hash, leak) => 
    hash + leak.count + leak.totalSize, 0
  );
};

// memo比较
return getLeaksHash(prevProps.leaks) === getLeaksHash(nextProps.leaks);
```

---

### 2. **后端性能问题**

#### 2.1 内存泄漏聚合逻辑
**问题**：
```java
// SoHookWebServer.java - getLeaksFromNative()
for (Map<String, Object> leak : rawLeaks) {
    String stackKey = getStackKey(backtrace);
    // 每个泄漏都要计算stackKey
}
```
- 每次请求都重新聚合
- 大量泄漏时CPU开销大
- 没有缓存机制

**影响**：
- 10000个泄漏 = 10000次字符串拼接和HashMap操作
- 响应时间随泄漏数量线性增长

**建议**：
```java
// 在native层直接返回聚合后的数据
// 或者添加缓存机制
private Map<String, List<Map<String, Object>>> leaksCache;
private long lastCacheTime;

private List<Map<String, Object>> getLeaksFromNative() {
    long now = System.currentTimeMillis();
    if (leaksCache != null && now - lastCacheTime < 1000) {
        return leaksCache; // 1秒内使用缓存
    }
    // ... 聚合逻辑
    lastCacheTime = now;
}
```

#### 2.2 JSON序列化开销
**问题**：
```c
// fd_tracker.c - fd_tracker_get_leaks_json()
while (curr != NULL) {
    offset += snprintf(json + offset, buffer_size - offset,
        "{\"fd\":%d,\"path\":\"%s\",\"flags\":%d}", ...);
}
```
- 手动拼接JSON字符串
- 大量字符串操作
- 可能需要多次realloc

**影响**：
- 10000个FD泄漏 = 大量字符串拼接
- 内存分配/释放开销

**建议**：
```c
// 预分配足够大的缓冲区
size_t buffer_size = g_stats.current_open_count * 256 + 1024;

// 或者使用更高效的JSON库（如cJSON）
```

#### 2.3 FD泄漏列表无聚合
**问题**：
```java
// handleGetFdLeaks() - 直接返回原始列表
List<Map<String, Object>> leaks = gson.fromJson(leaksJson, List.class);
return createSuccessResponse(leaks);
```
- FD泄漏没有聚合（不像内存泄漏按调用栈聚合）
- 可能返回大量重复路径的FD

**建议**：
```java
// 按路径聚合FD泄漏
Map<String, FdLeakGroup> groupByPath = new HashMap<>();
for (FdLeak leak : leaks) {
    FdLeakGroup group = groupByPath.computeIfAbsent(
        leak.path, k -> new FdLeakGroup(leak.path)
    );
    group.fds.add(leak.fd);
    group.count++;
}
```

---

### 3. **网络性能问题**

#### 3.1 并发请求过多
**问题**：
```typescript
await Promise.all([
  fetchStats(), 
  fetchLeaks(),      // 可能很大
  fetchFdStats(), 
  fetchFdLeaks()     // 可能很大
]);
```
- 4个并发请求
- 大数据量时传输时间长

**建议**：
```typescript
// 合并为单个请求
GET /api/dashboard-data
{
  "stats": {...},
  "leaks": [...],
  "fdStats": {...},
  "fdLeaks": [...]
}
```

#### 3.2 无数据压缩
**问题**：
- HTTP响应没有启用gzip压缩
- JSON数据可压缩性高

**建议**：
```java
// NanoHTTPD支持gzip
response.addHeader("Content-Encoding", "gzip");
```

#### 3.3 无分页机制
**问题**：
- 一次性返回所有泄漏数据
- 10000个泄漏 = 可能数MB的JSON

**建议**：
```java
// 添加分页参数
GET /api/leaks?page=1&pageSize=100&sortBy=totalSize

// 返回分页数据
{
  "data": [...],
  "total": 10000,
  "page": 1,
  "pageSize": 100
}
```

---

## 性能基准测试

### 场景1：小数据量（< 100个泄漏）
- ✅ 当前实现完全可用
- 刷新间隔：2秒
- 响应时间：< 100ms
- 内存占用：< 50MB

### 场景2：中等数据量（100-1000个泄漏）
- ⚠️ 可能出现轻微卡顿
- 建议刷新间隔：3-5秒
- 响应时间：100-500ms
- 内存占用：50-200MB

### 场景3：大数据量（1000-10000个泄漏）
- ❌ 严重性能问题
- 建议刷新间隔：10秒+
- 响应时间：500ms-2s
- 内存占用：200MB-1GB
- **需要虚拟滚动和分页**

### 场景4：超大数据量（> 10000个泄漏）
- ❌ 不可用
- **必须实现分页、虚拟滚动、后端聚合**

---

## 优化优先级

### 🔴 高优先级（立即实施）
1. **添加分页机制**
   - 前端：分页组件
   - 后端：分页API

2. **实现虚拟滚动**
   - 使用 `react-window` 或 `react-virtualized`
   - 只渲染可见区域的DOM

3. **优化刷新间隔**
   - 根据数据量动态调整
   - 添加手动刷新按钮

### 🟡 中优先级（近期实施）
4. **后端缓存**
   - 聚合结果缓存1秒
   - 减少重复计算

5. **合并API请求**
   - 单个dashboard数据接口
   - 减少网络往返

6. **启用gzip压缩**
   - 减少传输数据量

### 🟢 低优先级（长期优化）
7. **WebSocket实时推送**
   - 替代轮询机制
   - 只推送变化的数据

8. **数据采样**
   - 超过阈值时只显示Top N
   - 提供"查看全部"选项

9. **Web Worker**
   - 在worker中处理JSON解析
   - 避免阻塞主线程

---

## 推荐配置

### 开发/测试环境
```typescript
{
  refreshInterval: 2000,      // 2秒
  maxLeaksDisplay: 1000,      // 最多显示1000个
  enableVirtualScroll: false, // 数据量小时不需要
  pageSize: 100               // 分页大小
}
```

### 生产环境
```typescript
{
  refreshInterval: 5000,      // 5秒
  maxLeaksDisplay: 500,       // 最多显示500个
  enableVirtualScroll: true,  // 启用虚拟滚动
  pageSize: 50,               // 分页大小
  enableCache: true,          // 启用缓存
  cacheTimeout: 1000          // 缓存1秒
}
```

---

## 总结

当前实现在**小数据量**（< 100个泄漏）场景下表现良好，但在**大数据量**场景下存在明显性能问题。

**关键瓶颈**：
1. 前端：无虚拟滚动，大量DOM渲染
2. 后端：每次请求重新聚合，无缓存
3. 网络：无分页，一次性传输所有数据

**建议实施顺序**：
1. 添加分页（前后端）
2. 实现虚拟滚动（前端）
3. 添加缓存机制（后端）
4. 优化刷新策略（前端）
5. 合并API请求（后端）

实施这些优化后，系统应该能够流畅处理**10000+**个泄漏记录。
