// 内存统计信息
export interface MemoryStats {
  totalAllocCount: number;
  totalAllocSize: number;
  totalFreeCount: number;
  totalFreeSize: number;
  currentAllocCount: number;
  currentAllocSize: number;
}

// 内存记录
export interface MemoryRecord {
  ptr: string;
  size: number;
  backtrace: string[];
  timestamp: number;
}

// 内存泄漏报告
export interface MemoryReport {
  id: string;
  deviceId: string;
  deviceName: string;
  appName: string;
  packageName: string;
  timestamp: number;
  stats: MemoryStats;
  leaks: MemoryRecord[];
  tags?: string[];
}

// 报告列表项
export interface ReportListItem {
  id: string;
  deviceName: string;
  appName: string;
  timestamp: number;
  leakCount: number;
  leakSize: number;
  tags?: string[];
}

// 分页响应
export interface PaginatedResponse<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
}

// API 响应
export interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
}
