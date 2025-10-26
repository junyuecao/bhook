import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { FileText, FolderOpen, FolderClosed, AlertTriangle } from 'lucide-react';

interface FdStats {
  totalOpenCount: number;
  totalCloseCount: number;
  currentOpenCount: number;
}

interface FdStatsCardProps {
  stats: FdStats | null;
  isLoading: boolean;
}

export function FdStatsCard({ stats, isLoading }: FdStatsCardProps) {
  if (isLoading) {
    return (
      <Card className="shadow-lg border-blue-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-blue-700">
            <FileText className="h-5 w-5" />
            文件描述符统计
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-center py-8 text-gray-500">
            加载中...
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!stats) {
    return (
      <Card className="shadow-lg border-blue-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-blue-700">
            <FileText className="h-5 w-5" />
            文件描述符统计
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-center py-8 text-gray-500">
            暂无数据
          </div>
        </CardContent>
      </Card>
    );
  }

  const hasLeaks = stats.currentOpenCount > 0;

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
            {stats.totalOpenCount.toLocaleString()}
          </span>
        </div>

        {/* 总关闭次数 */}
        <div className="flex items-center justify-between p-3 bg-blue-50 rounded-lg">
          <div className="flex items-center gap-2">
            <FolderClosed className="h-5 w-5 text-blue-600" />
            <span className="text-sm font-medium text-gray-700">总关闭次数</span>
          </div>
          <span className="text-lg font-bold text-blue-700">
            {stats.totalCloseCount.toLocaleString()}
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
            {stats.currentOpenCount.toLocaleString()}
          </span>
        </div>

        {/* 泄漏警告 */}
        {hasLeaks && (
          <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg">
            <p className="text-sm text-red-700 font-medium">
              ⚠️ 检测到 {stats.currentOpenCount} 个文件描述符泄漏
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
