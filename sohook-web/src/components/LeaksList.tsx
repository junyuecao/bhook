import { useState } from 'react';
import { MemoryRecord } from '../types';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Badge } from './ui/badge';
import { AlertTriangle, ChevronDown, ChevronUp, Copy } from 'lucide-react';
import { Button } from './ui/button';

interface LeaksListProps {
  leaks: MemoryRecord[];
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

function LeakItem({ leak }: { leak: MemoryRecord }) {
  const [isExpanded, setIsExpanded] = useState(false);

  return (
    <div className="border rounded-lg p-4 hover:bg-muted/50 transition-colors">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-2">
            <code className="text-sm font-mono bg-muted px-2 py-1 rounded">
              {leak.ptr}
            </code>
            <Badge variant="destructive">{formatBytes(leak.size)}</Badge>
          </div>
          {leak.timestamp > 0 && (
            <p className="text-xs text-muted-foreground">
              分配时间: {formatTimestamp(leak.timestamp)}
            </p>
          )}
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
        <div className="mt-4 pt-4 border-t">
          <div className="flex items-center justify-between mb-2">
            <p className="text-sm font-medium">调用栈:</p>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => copyToClipboard(leak.backtrace.join('\n'))}
            >
              <Copy className="h-3 w-3 mr-1" />
              复制
            </Button>
          </div>
          <div className="bg-muted rounded-md p-3 max-h-60 overflow-y-auto">
            <pre className="text-xs font-mono whitespace-pre-wrap">
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

export function LeaksList({ leaks, isLoading }: LeaksListProps) {
  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5" />
            内存泄漏列表
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

  const totalSize = leaks.reduce((sum, leak) => sum + leak.size, 0);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <span className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5" />
            内存泄漏列表
          </span>
          <div className="flex items-center gap-2">
            <Badge variant={leaks.length > 0 ? 'destructive' : 'secondary'}>
              {leaks.length} 个泄漏
            </Badge>
            {totalSize > 0 && (
              <Badge variant="outline">{formatBytes(totalSize)}</Badge>
            )}
          </div>
        </CardTitle>
      </CardHeader>
      <CardContent>
        {leaks.length === 0 ? (
          <div className="text-center py-8 text-muted-foreground">
            <p className="text-lg mb-2">🎉 太棒了！</p>
            <p>未检测到内存泄漏</p>
          </div>
        ) : (
          <div className="space-y-3 max-h-[600px] overflow-y-auto">
            {leaks.map((leak, idx) => (
              <LeakItem key={`${leak.ptr}-${idx}`} leak={leak} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
