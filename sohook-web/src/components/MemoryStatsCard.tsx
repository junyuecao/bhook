import { MemoryStats } from '../types';
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

export function MemoryStatsCard({ stats, isLoading }: MemoryStatsCardProps) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Activity className="h-5 w-5" />
            内存统计
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-center py-8">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!stats) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Activity className="h-5 w-5" />
            内存统计
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-center py-8 text-muted-foreground">
            暂无数据
          </div>
        </CardContent>
      </Card>
    );
  }

  const statItems = [
    {
      label: '总分配次数',
      value: formatNumber(stats.totalAllocCount),
      icon: TrendingUp,
      color: 'text-blue-500',
    },
    {
      label: '总分配大小',
      value: formatBytes(stats.totalAllocSize),
      icon: Database,
      color: 'text-blue-500',
    },
    {
      label: '总释放次数',
      value: formatNumber(stats.totalFreeCount),
      icon: TrendingDown,
      color: 'text-green-500',
    },
    {
      label: '总释放大小',
      value: formatBytes(stats.totalFreeSize),
      icon: Database,
      color: 'text-green-500',
    },
    {
      label: '当前泄漏次数',
      value: formatNumber(stats.currentAllocCount),
      icon: Activity,
      color: stats.currentAllocCount > 0 ? 'text-red-500' : 'text-gray-500',
    },
    {
      label: '当前泄漏大小',
      value: formatBytes(stats.currentAllocSize),
      icon: Database,
      color: stats.currentAllocSize > 0 ? 'text-red-500' : 'text-gray-500',
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
              className="flex items-start gap-3 p-4 rounded-lg bg-muted/50"
            >
              <item.icon className={`h-5 w-5 mt-0.5 ${item.color}`} />
              <div className="flex-1 min-w-0">
                <p className="text-sm text-muted-foreground">{item.label}</p>
                <p className="text-xl font-semibold truncate">{item.value}</p>
              </div>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
