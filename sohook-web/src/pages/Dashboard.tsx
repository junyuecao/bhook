import { useEffect } from 'react';
import { useMemoryStore } from '../store/useMemoryStore';
import { ConnectionStatus } from '../components/ConnectionStatus';
import { MemoryStatsCard } from '../components/MemoryStatsCard';
import { FdStatsCard } from '../components/FdStatsCard';
import { LeaksList } from '../components/LeaksList';
import { FdLeaksList } from '../components/FdLeaksList';
import { MemoryChart } from '../components/MemoryChart';
import { Button } from '../components/ui/button';
import { Checkbox } from '../components/ui/checkbox';
import { RefreshCw, Trash2 } from 'lucide-react';

export function Dashboard() {
  const {
    stats,
    leaks,
    fdStats,
    fdLeaks,
    isLoading,
    isConnected,
    autoRefresh,
    refreshInterval,
    fetchStats,
    fetchLeaks,
    fetchFdStats,
    fetchFdLeaks,
    resetStats,
    checkConnection,
    setAutoRefresh,
  } = useMemoryStore();

  // 初始化时检查连接
  useEffect(() => {
    checkConnection();
  }, [checkConnection]);

  // 自动刷新
  useEffect(() => {
    if (!autoRefresh || !isConnected) return;

    const fetchData = async () => {
      await Promise.all([
        fetchStats(), 
        fetchLeaks(), 
        fetchFdStats(), 
        fetchFdLeaks()
      ]);
    };

    // 立即获取一次
    fetchData();

    // 设置定时器
    const timer = setInterval(fetchData, refreshInterval);

    return () => clearInterval(timer);
  }, [autoRefresh, isConnected, refreshInterval, fetchStats, fetchLeaks, fetchFdStats, fetchFdLeaks]);

  const handleRefresh = async () => {
    await Promise.all([
      fetchStats(), 
      fetchLeaks(), 
      fetchFdStats(), 
      fetchFdLeaks()
    ]);
  };

  const handleReset = async () => {
    if (confirm('确定要重置所有统计数据吗？')) {
      await resetStats();
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-indigo-50">
      <div className="container mx-auto px-4 py-8">
        {/* Header */}
        <div className="mb-8 flex items-start justify-between">
          <div>
            <h1 className="text-4xl font-bold text-gray-900 mb-2">
              SoHook 内存监控
            </h1>
            <p className="text-lg text-gray-700">
              实时监控 Android 应用内存泄漏
            </p>
          </div>
          
          {/* Actions */}
          <div className="flex flex-col gap-3">
            <div className="flex gap-3">
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
            
            {/* 自动刷新控制 */}
            <div className="flex items-center gap-2 bg-white px-4 py-2 rounded-lg border shadow-sm">
              <Checkbox
                id="auto-refresh"
                checked={autoRefresh}
                onCheckedChange={(checked) => setAutoRefresh(checked as boolean)}
                disabled={!isConnected}
              />
              <label
                htmlFor="auto-refresh"
                className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 cursor-pointer select-none"
              >
                自动刷新 ({refreshInterval / 1000} 秒)
              </label>
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left Column - Connection Status & Stats */}
          <div className="lg:col-span-1 space-y-6">
            <ConnectionStatus />
            <MemoryStatsCard stats={stats} isLoading={isLoading} />
            <FdStatsCard stats={fdStats} isLoading={isLoading} />
          </div>

          {/* Right Column - Chart and Leaks */}
          <div className="lg:col-span-2 space-y-6">
            {/* Memory Trend Chart */}
            {stats && (
              <MemoryChart
                currentAllocSize={stats.currentAllocSize}
                currentAllocCount={stats.currentAllocCount}
              />
            )}
            
            <LeaksList leaks={leaks} isLoading={isLoading} />
            <FdLeaksList leaks={fdLeaks} isLoading={isLoading} />
          </div>
        </div>

        {/* Footer */}
        <div className="mt-8 text-center text-sm text-gray-600">
          <p>
            {autoRefresh ? (
              <>💡 提示：数据每 {refreshInterval / 1000} 秒自动刷新</>
            ) : (
              <>⏸️ 自动刷新已暂停，点击"刷新数据"按钮手动更新</>
            )}
          </p>
        </div>
      </div>
    </div>
  );
}
