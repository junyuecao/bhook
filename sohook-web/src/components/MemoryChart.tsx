import { useEffect, useState } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { TrendingUp } from 'lucide-react';

interface MemoryChartProps {
  currentAllocSize: number;
  currentAllocCount: number;
}

interface DataPoint {
  time: string;
  size: number;
  count: number;
}

// 格式化字节大小
function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
}

export function MemoryChart({ currentAllocSize, currentAllocCount }: MemoryChartProps) {
  const [data, setData] = useState<DataPoint[]>([]);
  const maxDataPoints = 30; // 保留最近 30 个数据点

  useEffect(() => {
    const now = new Date();
    const timeStr = now.toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });

    setData((prevData) => {
      const newData = [
        ...prevData,
        {
          time: timeStr,
          size: currentAllocSize,
          count: currentAllocCount,
        },
      ];

      // 只保留最近的数据点
      if (newData.length > maxDataPoints) {
        return newData.slice(newData.length - maxDataPoints);
      }

      return newData;
    });
  }, [currentAllocSize, currentAllocCount]);

  // 自定义 Tooltip
  const CustomTooltip = ({ active, payload }: any) => {
    if (active && payload && payload.length) {
      return (
        <div className="bg-background border rounded-lg p-3 shadow-lg">
          <p className="text-sm font-medium mb-2">{payload[0].payload.time}</p>
          <p className="text-sm text-blue-500">
            泄漏大小: {formatBytes(payload[0].value)}
          </p>
          <p className="text-sm text-green-500">
            泄漏次数: {payload[1].value.toLocaleString()}
          </p>
        </div>
      );
    }
    return null;
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <TrendingUp className="h-5 w-5" />
          内存趋势图
        </CardTitle>
      </CardHeader>
      <CardContent>
        {data.length === 0 ? (
          <div className="h-[300px] flex items-center justify-center text-muted-foreground">
            等待数据中...
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={data}>
              <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
              <XAxis
                dataKey="time"
                tick={{ fontSize: 12 }}
                className="text-muted-foreground"
              />
              <YAxis
                yAxisId="left"
                tick={{ fontSize: 12 }}
                className="text-muted-foreground"
                tickFormatter={(value) => formatBytes(value)}
              />
              <YAxis
                yAxisId="right"
                orientation="right"
                tick={{ fontSize: 12 }}
                className="text-muted-foreground"
              />
              <Tooltip content={<CustomTooltip />} />
              <Legend />
              <Line
                yAxisId="left"
                type="monotone"
                dataKey="size"
                stroke="#3b82f6"
                strokeWidth={2}
                name="泄漏大小"
                dot={false}
              />
              <Line
                yAxisId="right"
                type="monotone"
                dataKey="count"
                stroke="#10b981"
                strokeWidth={2}
                name="泄漏次数"
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </CardContent>
    </Card>
  );
}
