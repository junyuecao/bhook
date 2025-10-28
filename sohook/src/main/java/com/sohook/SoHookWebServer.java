package com.sohook;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * SoHook Web 服务器
 * 提供 HTTP API 接口用于查看内存监控数据
 */
public class SoHookWebServer extends NanoHTTPD {
    private static final String TAG = "SoHookWebServer";
    private static final int DEFAULT_PORT = 8080;
    
    private final Gson gson;
    private boolean isRunning = false;

    public SoHookWebServer() {
        this(DEFAULT_PORT);
    }

    public SoHookWebServer(int port) {
        super(port);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * 启动服务器
     */
    public boolean startServer() {
        if (isRunning) {
            Log.w(TAG, "Server is already running");
            return true;
        }

        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            isRunning = true;
            Log.i(TAG, "SoHook Web Server started on port " + getListeningPort());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start server", e);
            return false;
        }
    }

    /**
     * 停止服务器
     */
    public void stopServer() {
        if (isRunning) {
            stop();
            isRunning = false;
            Log.i(TAG, "SoHook Web Server stopped");
        }
    }

    /**
     * 检查服务器是否运行
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 获取服务器端口
     */
    public int getPort() {
        return getListeningPort();
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, "Request: " + method + " " + uri);

        // 添加 CORS 头
        Response response;

        try {
            // 路由处理
            if (uri.equals("/api/health")) {
                response = handleHealth();
            } else if (uri.equals("/api/stats")) {
                response = handleGetStats();
            } else if (uri.equals("/api/leaks")) {
                response = handleGetLeaks();
            } else if (uri.equals("/api/leak-report")) {
                response = handleGetLeakReport();
            } else if (uri.equals("/api/fd-stats")) {
                response = handleGetFdStats();
            } else if (uri.equals("/api/fd-leaks")) {
                response = handleGetFdLeaks();
            } else if (uri.equals("/api/fd-leak-report")) {
                response = handleGetFdLeakReport();
            } else if (uri.equals("/api/reset") && method == Method.POST) {
                response = handleReset();
            } else {
                response = newFixedLengthResponse(Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT, "Not Found");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling request", e);
            response = createErrorResponse(500, "Internal Server Error: " + e.getMessage());
        }

        // 添加 CORS 头
        addCorsHeaders(response);
        return response;
    }

    /**
     * 健康检查
     */
    private Response handleHealth() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "ok");
        data.put("timestamp", System.currentTimeMillis());
        return createSuccessResponse(data);
    }

    /**
     * 获取内存统计
     */
    private Response handleGetStats() {
        SoHook.MemoryStats stats = SoHook.getMemoryStats();
        
        Map<String, Object> data = new HashMap<>();
        data.put("totalAllocCount", stats.totalAllocCount);
        data.put("totalAllocSize", stats.totalAllocSize);
        data.put("totalFreeCount", stats.totalFreeCount);
        data.put("totalFreeSize", stats.totalFreeSize);
        data.put("currentAllocCount", stats.currentAllocCount);
        data.put("currentAllocSize", stats.currentAllocSize);
        
        return createSuccessResponse(data);
    }

    /**
     * 获取泄漏列表
     */
    private Response handleGetLeaks() {
        // 从 native 层获取泄漏列表（已聚合）
        List<Map<String, Object>> leaks = getLeaksFromNative();
        return createSuccessResponse(leaks);
    }

    /**
     * 获取完整泄漏报告
     */
    private Response handleGetLeakReport() {
        String report = SoHook.getLeakReport();
        return createSuccessResponse(report);
    }

    /**
     * 重置统计
     */
    private Response handleReset() {
        SoHook.resetStats();
        SoHook.resetFdStats();
        Map<String, Object> data = new HashMap<>();
        data.put("message", "Statistics reset successfully");
        return createSuccessResponse(data);
    }

    /**
     * 获取FD统计
     */
    private Response handleGetFdStats() {
        SoHook.FdStats stats = SoHook.getFdStats();
        
        Map<String, Object> data = new HashMap<>();
        data.put("totalOpenCount", stats.totalOpenCount);
        data.put("totalCloseCount", stats.totalCloseCount);
        data.put("currentOpenCount", stats.currentOpenCount);
        
        return createSuccessResponse(data);
    }

    /**
     * 获取FD泄漏列表
     */
    private Response handleGetFdLeaks() {
        String leaksJson = SoHook.nativeGetFdLeaksJson();
        
        if (leaksJson == null || leaksJson.isEmpty()) {
            return createSuccessResponse(new ArrayList<>());
        }
        
        try {
            List<Map<String, Object>> leaks = gson.fromJson(leaksJson, List.class);
            return createSuccessResponse(leaks);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse FD leaks JSON", e);
            return createSuccessResponse(new ArrayList<>());
        }
    }

    /**
     * 获取FD泄漏报告
     */
    private Response handleGetFdLeakReport() {
        String report = SoHook.getFdLeakReport();
        return createSuccessResponse(report);
    }

    /**
     * 从 native 层获取泄漏列表（结构化数据）
     * C 层已完成聚合和排序，直接返回
     */
    private List<Map<String, Object>> getLeaksFromNative() {
        // 调用 SoHook 的 native 方法获取聚合后的 JSON 数据
        String leaksJson = SoHook.nativeGetLeaksAggregatedJson();
        
        if (leaksJson == null || leaksJson.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            // 解析 JSON（已经是聚合后的格式）
            List<Map<String, Object>> aggregatedLeaks = gson.fromJson(leaksJson, List.class);
            return aggregatedLeaks != null ? aggregatedLeaks : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse aggregated leaks JSON", e);
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * 创建成功响应
     */
    private Response createSuccessResponse(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 0);
        response.put("message", "success");
        response.put("data", data);
        
        String json = gson.toJson(response);
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    /**
     * 创建错误响应
     */
    private Response createErrorResponse(int code, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("message", message);
        response.put("data", null);
        
        String json = gson.toJson(response);
        Response.Status status = code >= 500 ? Response.Status.INTERNAL_ERROR : Response.Status.BAD_REQUEST;
        return newFixedLengthResponse(status, "application/json", json);
    }

    /**
     * 添加 CORS 头
     */
    private void addCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
    }
}
