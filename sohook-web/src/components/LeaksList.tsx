import { useState, memo } from 'react';
import type { LeakGroup } from '../types/index';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Badge } from './ui/badge';
import { AlertTriangle, ChevronDown, ChevronUp, Copy } from 'lucide-react';
import { Button } from './ui/button';

interface LeaksListProps {
  leaks: LeakGroup[];  // 改为泄漏分组
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

// 格式化时间戳
function formatTimestamp(timestamp: number): string {
  return new Date(timestamp).toLocaleString('zh-CN');
}

// 复制到剪贴板
function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text);
}

function LeakItem({ leak }: { leak: LeakGroup }) {
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <div className="border border-gray-200 rounded-lg p-4 hover:bg-gray-50 transition-colors bg-white">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-2">
            <Badge variant="destructive" className="text-sm">
              {formatBytes(leak.totalSize || 0)}
            </Badge>
            <Badge variant="outline" className="text-sm">
              {leak.count || 0} 次泄漏
            </Badge>
          </div>
          <p className="text-xs text-gray-600">
            该调用栈共泄漏 {leak.count || 0} 次，总计 {formatBytes(leak.totalSize || 0)}
          </p>
        </div>
        {leak.backtrace && leak.backtrace.length > 0 && (
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setIsExpanded(!isExpanded)}
          >
            {isExpanded ? (
              <ChevronUp className="h-4 w-4" />
            ) : (
              <ChevronDown className="h-4 w-4" />
            )}
          </Button>
        )}
      </div>

      {isExpanded && leak.backtrace && leak.backtrace.length > 0 && (
        <div className="mt-4 pt-4 border-t border-gray-200">
          <div className="flex items-center justify-between mb-2">
            <p className="text-sm font-medium text-gray-900">调用栈:</p>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => copyToClipboard(leak.backtrace.join('\n'))}
            >
              <Copy className="h-3 w-3 mr-1" />
              复制
            </Button>
          </div>
          <div className="bg-gray-50 border border-gray-200 rounded-md p-3 max-h-60 overflow-y-auto">
            <pre className="text-xs font-mono whitespace-pre-wrap text-gray-800">
              {leak.backtrace.map((frame, idx) => (
                <div key={idx} className="py-0.5">
                  #{idx} {frame}
                </div>
              ))}
            </pre>
          </div>
        </div>
      )}
    </div>
  );
}

const LeaksListComponent = ({ leaks, isLoading }: LeaksListProps) => {
  // 始终显示列表，不管是否在加载
  const displayLeaks = leaks || [];
  const totalSize = displayLeaks.reduce((sum, leak) => sum + leak.totalSize, 0);
  const totalCount = displayLeaks.reduce((sum, leak) => sum + leak.count, 0);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <span className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5" />
            内存泄漏列表
          </span>
          <div className="flex items-center gap-2">
            <Badge variant={displayLeaks.length > 0 ? 'destructive' : 'secondary'}>
              {displayLeaks.length} 个调用栈
            </Badge>
            {totalCount > 0 && (
              <Badge variant="outline">{totalCount} 次泄漏</Badge>
            )}
            {totalSize > 0 && (
              <Badge variant="outline">{formatBytes(totalSize)}</Badge>
            )}
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent>
        {displayLeaks.length === 0 ? (
          <div className="text-center py-8 text-gray-600">
            <p className="text-lg mb-2">🎉 太棒了！</p>
            <p>未检测到内存泄漏</p>
          </div>
        ) : (
          <div className="space-y-3 max-h-[600px] overflow-y-auto">
            {displayLeaks.map((leak, idx) => (
              <LeakItem key={`leak-${idx}-${leak.count}-${leak.totalSize}`} leak={leak} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
};

// 使用 memo 优化，只在 leaks 数组真正变化时才重新渲染
export const LeaksList = memo(LeaksListComponent, (prevProps, nextProps) => {
  // 如果 loading 状态变化，不需要重新渲染（因为我们不显示 loading 状态）
  
  // 如果数组长度不同，需要重新渲染
  if (prevProps.leaks.length !== nextProps.leaks.length) {
    return false;
  }
  
  // 如果长度相同但都为 0，不需要重新渲染
  if (prevProps.leaks.length === 0 && nextProps.leaks.length === 0) {
    return true;
  }
  
  // 比较数组内容（比较调用栈和总大小）
  for (let i = 0; i < prevProps.leaks.length; i++) {
    if (prevProps.leaks[i].count !== nextProps.leaks[i].count ||
        prevProps.leaks[i].totalSize !== nextProps.leaks[i].totalSize) {
      return false;
    }
  }
  
  return true;
});
