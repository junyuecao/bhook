import { useState } from 'react';
import { useMemoryStore } from '../store/useMemoryStore';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { Wifi, WifiOff, Settings } from 'lucide-react';

export function ConnectionStatus() {
  const { isConnected, serverUrl, setServerUrl, checkConnection } =
    useMemoryStore();
  const [isEditing, setIsEditing] = useState(false);
  const [inputUrl, setInputUrl] = useState(serverUrl);

  const handleSave = () => {
    setServerUrl(inputUrl);
    setIsEditing(false);
    checkConnection();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          <span className="flex items-center gap-2">
            {isConnected ? (
              <Wifi className="h-5 w-5 text-green-500" />
            ) : (
              <WifiOff className="h-5 w-5 text-red-500" />
            )}
            连接状态
          </span>
          <Badge variant={isConnected ? 'success' : 'destructive'}>
            {isConnected ? '已连接' : '未连接'}
          </Badge>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-4">
          <div>
            <label className="text-sm font-medium text-muted-foreground">
              服务器地址
            </label>
            {isEditing ? (
              <div className="flex gap-2 mt-2">
                <input
                  type="text"
                  value={inputUrl}
                  onChange={(e) => setInputUrl(e.target.value)}
                  className="flex-1 px-3 py-2 text-sm border rounded-md focus:outline-none focus:ring-2 focus:ring-ring"
                  placeholder="http://localhost:8080"
                />
                <Button size="sm" onClick={handleSave}>
                  保存
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setIsEditing(false);
                    setInputUrl(serverUrl);
                  }}
                >
                  取消
                </Button>
              </div>
            ) : (
              <div className="flex items-center gap-2 mt-2">
                <code className="flex-1 px-3 py-2 text-sm bg-muted rounded-md">
                  {serverUrl}
                </code>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setIsEditing(true)}
                >
                  <Settings className="h-4 w-4" />
                </Button>
              </div>
            )}
          </div>

          <div className="flex gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={checkConnection}
              className="flex-1"
            >
              测试连接
            </Button>
          </div>

          <div className="text-xs text-muted-foreground">
            <p>💡 提示：</p>
            <ul className="list-disc list-inside space-y-1 mt-1">
              <li>确保 Android 设备已启动 HTTP 服务</li>
              <li>使用设备的 IP 地址（如 192.168.1.100:8080）</li>
              <li>确保设备和电脑在同一网络</li>
            </ul>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
