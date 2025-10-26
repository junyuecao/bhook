import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { MemoryStats, LeakGroup, FdStats, FdLeak } from '../types/index';
import { apiClient } from '../services/api';

interface MemoryStore {
  // 状态
  stats: MemoryStats | null;
  leaks: LeakGroup[];  // 改为泄漏分组
  fdStats: FdStats | null;
  fdLeaks: FdLeak[];
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
  fetchFdStats: () => Promise<void>;
  fetchFdLeaks: () => Promise<void>;
  resetStats: () => Promise<void>;
  checkConnection: () => Promise<void>;
  setAutoRefresh: (enabled: boolean) => void;
  setRefreshInterval: (interval: number) => void;
}

// 从 localStorage 读取保存的服务器地址
const getSavedServerUrl = () => {
  try {
    return localStorage.getItem('sohook-server-url') || 'http://localhost:8080';
  } catch {
    return 'http://localhost:8080';
  }
};

export const useMemoryStore = create<MemoryStore>()(
  persist(
    (set, get) => {
      // 初始化时设置 apiClient 的 baseUrl
      const savedUrl = getSavedServerUrl();
      apiClient.setBaseUrl(savedUrl);
      
      return {
        // 初始状态
        stats: null,
        leaks: [],
        fdStats: null,
        fdLeaks: [],
        isConnected: false,
        isLoading: false,
        error: null,
        serverUrl: savedUrl,
        autoRefresh: true,
        refreshInterval: 2000, // 默认 2 秒刷新一次

      // 设置服务器地址
      setServerUrl: (url: string) => {
        apiClient.setBaseUrl(url);
        // 保存到 localStorage
        try {
          localStorage.setItem('sohook-server-url', url);
        } catch (e) {
          console.error('Failed to save server URL:', e);
        }
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
  
  // 获取FD统计
  fetchFdStats: async () => {
    set({ isLoading: true, error: null });
    try {
      const fdStats = await apiClient.getFdStats();
      set({ fdStats, isConnected: true, isLoading: false });
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '获取FD统计失败',
        isLoading: false,
        isConnected: false,
      });
    }
  },
  
  // 获取FD泄漏列表
  fetchFdLeaks: async () => {
    set({ isLoading: true, error: null });
    try {
      const fdLeaks = await apiClient.getFdLeaks();
      set({ fdLeaks, isConnected: true, isLoading: false });
    } catch (error) {
      set({
        error: error instanceof Error ? error.message : '获取FD泄漏列表失败',
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
      await get().fetchFdStats();
      await get().fetchFdLeaks();
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
      };
    },
    {
      name: 'sohook-storage', // localStorage key
      partialize: (state) => ({
        serverUrl: state.serverUrl,
        autoRefresh: state.autoRefresh,
        refreshInterval: state.refreshInterval,
      }),
    }
  )
);
