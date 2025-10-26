import { memo } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { FileWarning, File } from 'lucide-react';
import { Badge } from './ui/badge';

interface FdLeak {
  fd: number;
  path: string;
  flags: number;
}

interface FdLeaksListProps {
  leaks: FdLeak[];
  isLoading?: boolean;
}

const FdLeaksListComponent = ({ leaks, isLoading }: FdLeaksListProps) => {
  // 始终显示列表，不管是否在加载
  const displayLeaks = leaks || [];

  const getFlagsString = (flags: number) => {
    const flagNames = [];
    if (flags & 0x0000) flagNames.push('O_RDONLY');
    if (flags & 0x0001) flagNames.push('O_WRONLY');
    if (flags & 0x0002) flagNames.push('O_RDWR');
    if (flags & 0x0100) flagNames.push('O_CREAT');
    if (flags & 0x0200) flagNames.push('O_EXCL');
    if (flags & 0x0400) flagNames.push('O_TRUNC');
    if (flags & 0x0800) flagNames.push('O_APPEND');
    return flagNames.length > 0 ? flagNames.join(' | ') : `0x${flags.toString(16)}`;
  };

  return (
    <Card className={displayLeaks.length > 0 ? "shadow-lg border-orange-200" : "shadow-lg border-green-200"}>
      <CardHeader>
        <CardTitle className={`flex items-center justify-between ${displayLeaks.length > 0 ? 'text-orange-700' : 'text-green-700'}`}>
          <span className="flex items-center gap-2">
            <FileWarning className="h-5 w-5" />
            FD 泄漏详情
          </span>
          {displayLeaks.length > 0 && (
            <Badge variant="destructive">
              {displayLeaks.length} 个泄漏
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {displayLeaks.length === 0 ? (
          <div className="text-center py-8">
            <p className="text-lg mb-2 text-green-600 font-medium">✅ 太棒了！</p>
            <p className="text-gray-600">所有文件描述符都已正确关闭</p>
          </div>
        ) : (
          <>
            <div className="space-y-3 max-h-[600px] overflow-y-auto">
              {displayLeaks.map((leak, index) => (
                <div
                  key={`fd-${leak.fd}-${index}`}
                  className="p-4 bg-orange-50 border border-orange-200 rounded-lg hover:bg-orange-100 transition-colors"
                >
                  <div className="flex items-start gap-3">
                    <File className="h-5 w-5 text-orange-600 mt-0.5 flex-shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-2">
                        <span className="text-xs font-mono bg-orange-200 text-orange-800 px-2 py-1 rounded">
                          FD: {leak.fd}
                        </span>
                        <span className="text-xs font-mono bg-gray-200 text-gray-700 px-2 py-1 rounded">
                          {getFlagsString(leak.flags)}
                        </span>
                      </div>
                      <p className="text-sm text-gray-700 font-mono break-all">
                        {leak.path}
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>

            {displayLeaks.length > 5 && (
              <div className="mt-4 text-center text-sm text-gray-500">
                显示 {displayLeaks.length} 个泄漏的文件描述符
              </div>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
};

// 使用 memo 优化，只在 leaks 数组真正变化时才重新渲染
export const FdLeaksList = memo(FdLeaksListComponent, (prevProps, nextProps) => {
  // 如果数组长度不同，需要重新渲染
  if (prevProps.leaks.length !== nextProps.leaks.length) {
    return false;
  }
  
  // 如果长度相同但都为 0，不需要重新渲染
  if (prevProps.leaks.length === 0 && nextProps.leaks.length === 0) {
    return true;
  }
  
  // 比较数组内容（比较 fd 和 path）
  for (let i = 0; i < prevProps.leaks.length; i++) {
    if (prevProps.leaks[i].fd !== nextProps.leaks[i].fd ||
        prevProps.leaks[i].path !== nextProps.leaks[i].path ||
        prevProps.leaks[i].flags !== nextProps.leaks[i].flags) {
      return false;
    }
  }
  
  return true;
});
