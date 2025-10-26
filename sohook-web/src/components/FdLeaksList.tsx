import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { FileWarning, File } from 'lucide-react';

interface FdLeak {
  fd: number;
  path: string;
  flags: number;
}

interface FdLeaksListProps {
  leaks: FdLeak[];
  isLoading: boolean;
}

export function FdLeaksList({ leaks, isLoading }: FdLeaksListProps) {
  if (isLoading) {
    return (
      <Card className="shadow-lg border-orange-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-orange-700">
            <FileWarning className="h-5 w-5" />
            FD 泄漏详情
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

  if (!leaks || leaks.length === 0) {
    return (
      <Card className="shadow-lg border-green-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-green-700">
            <FileWarning className="h-5 w-5" />
            FD 泄漏详情
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-center py-8">
            <p className="text-green-600 font-medium">✅ 暂无 FD 泄漏</p>
            <p className="text-sm text-gray-500 mt-2">
              所有文件描述符都已正确关闭
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

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
    <Card className="shadow-lg border-orange-200">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-orange-700">
          <FileWarning className="h-5 w-5" />
          FD 泄漏详情
          <span className="ml-2 px-2 py-1 text-xs font-semibold bg-orange-100 text-orange-700 rounded-full">
            {leaks.length} 个泄漏
          </span>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-3 max-h-96 overflow-y-auto">
          {leaks.map((leak, index) => (
            <div
              key={index}
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

        {leaks.length > 5 && (
          <div className="mt-4 text-center text-sm text-gray-500">
            显示 {leaks.length} 个泄漏的文件描述符
          </div>
        )}
      </CardContent>
    </Card>
  );
}
