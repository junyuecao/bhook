import { create } from 'zustand';
import type { MemoryStats, MemoryRecord } from '../types/index';
import { apiClient } from '../services/api';

interface MemoryStore {
  // 状态
  stats: MemoryStats | null;
  leaks: MemoryRecord[];
  isConnected: boolean;
  isLoading: boolean;
  error: string | null;
  serverUrl: string;
  autoRefresh: boolean;
  refreshInterval: number; // 毫秒

  // 操作
  setServerUrl: (url: string) => void;
  fetchStats: () => Promise<void>;
  fetchLeaks: () => Promise<void>;
  resetStats: () => Promise<void>;
  checkConnection: () => Promise<void>;
  setAutoRefresh: (enabled: boolean) => void;
  setRefreshInterval: (interval: number) => void;
}

export const useMemoryStore = create<MemoryStore>((set, get) => ({
  // 初始状态
  stats: null,
  leaks: [],
  isConnected: false,
  isLoading: false,
  error: null,
  serverUrl: 'http://localhost:8080',
  autoRefresh: true,
  refreshInterval: 2000, // 默认 2 秒刷新一次

  // 设置服务器地址
  setServerUrl: (url: string) => {
    apiClient.setBaseUrl(url);
    set({ serverUrl: url });
  },

  // 获取内存统计
  fetchStats: async () => {
    set({ isLoading: true, error: null });
    try {
      const stats = await apiClient.getMemoryStats();
      set({ stats, isConnected: true, isLoading: false });
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '获取统计信息失败',
        isLoading: false,
        isConnected: false,
      });
    }
  },

  // 获取泄漏列表
  fetchLeaks: async () => {
    set({ isLoading: true, error: null });
    try {
      const leaks = await apiClient.getLeaks();
      set({ leaks, isConnected: true, isLoading: false });
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '获取泄漏列表失败',
        isLoading: false,
        isConnected: false,
      });
    }
  },

  // 重置统计
  resetStats: async () => {
    set({ isLoading: true, error: null });
    try {
      await apiClient.resetStats();
      // 重置后重新获取数据
      await get().fetchStats();
      await get().fetchLeaks();
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '重置统计失败',
        isLoading: false,
      });
    }
  },

  // 检查连接状态
  checkConnection: async () => {
    try {
      const isConnected = await apiClient.healthCheck();
      set({ isConnected });
    } catch {
      set({ isConnected: false });
    }
  },

  // 设置自动刷新
  setAutoRefresh: (enabled: boolean) => {
    set({ autoRefresh: enabled });
  },

  // 设置刷新间隔
  setRefreshInterval: (interval: number) => {
    set({ refreshInterval: interval });
  },
}));
