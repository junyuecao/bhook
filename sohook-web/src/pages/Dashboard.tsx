import { useEffect } from 'react';
import { useMemoryStore } from '../store/useMemoryStore';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { MemoryStatsCard } from '../components/MemoryStatsCard';
import { LeaksList } from '../components/LeaksList';
import { MemoryChart } from '../components/MemoryChart';
import { Button } from '../components/ui/button';
import { RefreshCw, Trash2 } from 'lucide-react';

export function Dashboard() {
  const {
    stats,
    leaks,
    isLoading,
    isConnected,
    autoRefresh,
    refreshInterval,
    fetchStats,
    fetchLeaks,
    resetStats,
    checkConnection,
  } = useMemoryStore();

  // 初始化时检查连接
  useEffect(() => {
    checkConnection();
  }, [checkConnection]);

  // 自动刷新
  useEffect(() => {
    if (!autoRefresh || !isConnected) return;

    const fetchData = async () => {
      await Promise.all([fetchStats(), fetchLeaks()]);
    };

    // 立即获取一次
    fetchData();

    // 设置定时器
    const timer = setInterval(fetchData, refreshInterval);

    return () => clearInterval(timer);
  }, [autoRefresh, isConnected, refreshInterval, fetchStats, fetchLeaks]);

  const handleRefresh = async () => {
    await Promise.all([fetchStats(), fetchLeaks()]);
  };

  const handleReset = async () => {
    if (confirm('确定要重置所有统计数据吗？')) {
      await resetStats();
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 dark:from-gray-900 dark:to-gray-800">
      <div className="container mx-auto px-4 py-8">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-4xl font-bold text-gray-900 dark:text-white mb-2">
            SoHook 内存监控
          </h1>
          <p className="text-lg text-gray-600 dark:text-gray-300">
            实时监控 Android 应用内存泄漏
          </p>
        </div>

        {/* Actions */}
        <div className="flex gap-3 mb-6">
          <Button
            onClick={handleRefresh}
            disabled={!isConnected || isLoading}
            className="gap-2"
          >
            <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
            刷新数据
          </Button>
          <Button
            onClick={handleReset}
            disabled={!isConnected || isLoading}
            variant="destructive"
            className="gap-2"
          >
            <Trash2 className="h-4 w-4" />
            重置统计
          </Button>
        </div>

        {/* Content */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left Column - Connection Status */}
          <div className="lg:col-span-1">
            <ConnectionStatus />
          </div>

          {/* Right Column - Stats, Chart and Leaks */}
          <div className="lg:col-span-2 space-y-6">
            <MemoryStatsCard stats={stats} isLoading={isLoading} />
            
            {/* Memory Trend Chart */}
            {stats && (
              <MemoryChart
                currentAllocSize={stats.currentAllocSize}
                currentAllocCount={stats.currentAllocCount}
              />
            )}
            
            <LeaksList leaks={leaks} isLoading={isLoading} />
          </div>
        </div>

        {/* Footer */}
        <div className="mt-8 text-center text-sm text-gray-600 dark:text-gray-400">
          <p>
            💡 提示：数据每 {refreshInterval / 1000} 秒自动刷新
          </p>
        </div>
      </div>
    </div>
  );
}
