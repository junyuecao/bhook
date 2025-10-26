import { memo } from 'react';
import type { MemoryStats } from '../types/index';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Activity, TrendingUp, TrendingDown, Database } from 'lucide-react';

interface MemoryStatsCardProps {
  stats: MemoryStats | null;
  isLoading?: boolean;
}

// 格式化字节大小
function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
}

// 格式化数字
function formatNumber(num: number): string {
  return num.toLocaleString();
}

const MemoryStatsCardComponent = ({ stats, isLoading }: MemoryStatsCardProps) => {
  // 如果没有数据，显示初始状态（全为 0）
  const displayStats = stats || {
    totalAllocCount: 0,
    totalAllocSize: 0,
    totalFreeCount: 0,
    totalFreeSize: 0,
    currentAllocCount: 0,
    currentAllocSize: 0,
  };

  const statItems = [
    {
      label: '总分配次数',
      value: formatNumber(displayStats.totalAllocCount),
      icon: TrendingUp,
      color: 'text-blue-500',
    },
    {
      label: '总分配大小',
      value: formatBytes(displayStats.totalAllocSize),
      icon: Database,
      color: 'text-blue-500',
    },
    {
      label: '总释放次数',
      value: formatNumber(displayStats.totalFreeCount),
      icon: TrendingDown,
      color: 'text-green-500',
    },
    {
      label: '总释放大小',
      value: formatBytes(displayStats.totalFreeSize),
      icon: Database,
      color: 'text-green-500',
    },
    {
      label: '当前泄漏次数',
      value: formatNumber(displayStats.currentAllocCount),
      icon: Activity,
      color: displayStats.currentAllocCount > 0 ? 'text-red-500' : 'text-gray-500',
    },
    {
      label: '当前泄漏大小',
      value: formatBytes(displayStats.currentAllocSize),
      icon: Database,
      color: displayStats.currentAllocSize > 0 ? 'text-red-500' : 'text-gray-500',
    },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Activity className="h-5 w-5" />
          内存统计
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {statItems.map((item) => (
            <div
              key={item.label}
              className="flex items-start gap-3 p-4 rounded-lg bg-gray-50 border border-gray-200"
            >
              <item.icon className={`h-5 w-5 mt-0.5 ${item.color}`} />
              <div className="flex-1 min-w-0">
                <p className="text-sm text-gray-600">{item.label}</p>
                <p className="text-xl font-semibold text-gray-900 truncate">{item.value}</p>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
};

// 使用 memo 优化，只在 stats 实际变化时才重新渲染
export const MemoryStatsCard = memo(MemoryStatsCardComponent, (prevProps, nextProps) => {
  // 如果 loading 状态变化，需要重新渲染
  if (prevProps.isLoading !== nextProps.isLoading) {
    return false;
  }
  
  // 如果都没有 stats，不需要重新渲染
  if (!prevProps.stats && !nextProps.stats) {
    return true;
  }
  
  // 如果一个有 stats 一个没有，需要重新渲染
  if (!prevProps.stats || !nextProps.stats) {
    return false;
  }
  
  // 比较 stats 的各个字段，只有真正变化时才重新渲染
  return (
    prevProps.stats.totalAllocCount === nextProps.stats.totalAllocCount &&
    prevProps.stats.totalAllocSize === nextProps.stats.totalAllocSize &&
    prevProps.stats.totalFreeCount === nextProps.stats.totalFreeCount &&
    prevProps.stats.totalFreeSize === nextProps.stats.totalFreeSize &&
    prevProps.stats.currentAllocCount === nextProps.stats.currentAllocCount &&
    prevProps.stats.currentAllocSize === nextProps.stats.currentAllocSize
  );
});
