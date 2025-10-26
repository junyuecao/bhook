import axios, { AxiosInstance } from 'axios';
import { MemoryStats, MemoryRecord, ApiResponse } from '../types';

// API 客户端配置
const DEFAULT_BASE_URL = 'http://localhost:8080';

class SoHookApiClient {
  private client: AxiosInstance;
  private baseUrl: string;

  constructor(baseUrl: string = DEFAULT_BASE_URL) {
    this.baseUrl = baseUrl;
    this.client = axios.create({
      baseURL: baseUrl,
      timeout: 5000,
      headers: {
        'Content-Type': 'application/json',
      },
    });
  }

  // 更新服务器地址
  setBaseUrl(url: string) {
    this.baseUrl = url;
    this.client.defaults.baseURL = url;
  }

  getBaseUrl(): string {
    return this.baseUrl;
  }

  // 获取内存统计信息
  async getMemoryStats(): Promise<MemoryStats> {
    const response = await this.client.get<ApiResponse<MemoryStats>>('/api/stats');
    return response.data.data;
  }

  // 获取泄漏列表
  async getLeaks(): Promise<MemoryRecord[]> {
    const response = await this.client.get<ApiResponse<MemoryRecord[]>>('/api/leaks');
    return response.data.data;
  }

  // 获取完整泄漏报告（文本格式）
  async getLeakReport(): Promise<string> {
    const response = await this.client.get<ApiResponse<string>>('/api/leak-report');
    return response.data.data;
  }

  // 重置统计
  async resetStats(): Promise<void> {
    await this.client.post<ApiResponse<void>>('/api/reset');
  }

  // 健康检查
  async healthCheck(): Promise<boolean> {
    try {
      const response = await this.client.get<ApiResponse<{ status: string }>>('/api/health');
      return response.data.data.status === 'ok';
    } catch {
      return false;
    }
  }
}

// 导出单例
export const apiClient = new SoHookApiClient();
export default SoHookApiClient;
