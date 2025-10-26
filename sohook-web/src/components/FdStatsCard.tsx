import { memo } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { FileText, FolderOpen, FolderClosed, AlertTriangle } from 'lucide-react';

interface FdStats {
  totalOpenCount: number;
  totalCloseCount: number;
  currentOpenCount: number;
}

interface FdStatsCardProps {
  stats: FdStats | null;
  isLoading?: boolean;
}

const FdStatsCardComponent = ({ stats, isLoading }: FdStatsCardProps) => {
  // 如果没有数据，显示初始状态（全为 0）
  const displayStats = stats || {
    totalOpenCount: 0,
    totalCloseCount: 0,
    currentOpenCount: 0,
  };

  const hasLeaks = displayStats.currentOpenCount > 0;

  return (
    <Card className="shadow-lg border-blue-200">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-blue-700">
          <FileText className="h-5 w-5" />
          文件描述符统计
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 总打开次数 */}
        <div className="flex items-center justify-between p-3 bg-green-50 rounded-lg">
          <div className="flex items-center gap-2">
            <FolderOpen className="h-5 w-5 text-green-600" />
            <span className="text-sm font-medium text-gray-700">总打开次数</span>
          </div>
          <span className="text-lg font-bold text-green-700">
            {displayStats.totalOpenCount.toLocaleString()}
          </span>
        </div>

        {/* 总关闭次数 */}
        <div className="flex items-center justify-between p-3 bg-blue-50 rounded-lg">
          <div className="flex items-center gap-2">
            <FolderClosed className="h-5 w-5 text-blue-600" />
            <span className="text-sm font-medium text-gray-700">总关闭次数</span>
          </div>
          <span className="text-lg font-bold text-blue-700">
            {displayStats.totalCloseCount.toLocaleString()}
          </span>
        </div>

        {/* 当前未关闭 */}
        <div
          className={`flex items-center justify-between p-3 rounded-lg ${
            hasLeaks ? 'bg-red-50' : 'bg-gray-50'
          }`}
        >
          <div className="flex items-center gap-2">
            <AlertTriangle
              className={`h-5 w-5 ${hasLeaks ? 'text-red-600' : 'text-gray-400'}`}
            />
            <span className="text-sm font-medium text-gray-700">当前未关闭</span>
          </div>
          <span
            className={`text-lg font-bold ${
              hasLeaks ? 'text-red-700' : 'text-gray-500'
            }`}
          >
            {displayStats.currentOpenCount.toLocaleString()}
          </span>
        </div>

        {/* 泄漏警告 */}
        {hasLeaks && (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg">
            <p className="text-sm text-red-700 font-medium">
              ⚠️ 检测到 {displayStats.currentOpenCount} 个文件描述符泄漏
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
};

// 使用 memo 优化，只在 stats 实际变化时才重新渲染
export const FdStatsCard = memo(FdStatsCardComponent, (prevProps, nextProps) => {
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
    prevProps.stats.totalOpenCount === nextProps.stats.totalOpenCount &&
    prevProps.stats.totalCloseCount === nextProps.stats.totalCloseCount &&
    prevProps.stats.currentOpenCount === nextProps.stats.currentOpenCount
  );
});
