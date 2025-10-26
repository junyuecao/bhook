package com.sohook;

import android.util.Log;

/**
 * SoHook Web 服务器管理器
 * 负责管理 Web 可视化监控服务器的生命周期
 */
public class SoHookWebManager {
    private static final String TAG = "SoHook-WebManager";
    private static SoHookWebServer sWebServer = null;

    /**
     * 启动 Web 服务器
     * @param port 端口号
     * @return true 成功，false 失败
     */
    public static boolean startServer(int port) {
        if (sWebServer != null && sWebServer.isRunning()) {
            Log.w(TAG, "Web server is already running");
            return true;
        }
        
        sWebServer = new SoHookWebServer(port);
        boolean success = sWebServer.startServer();
        
        if (success) {
            Log.i(TAG, "Web server started on port " + port);
        } else {
            Log.e(TAG, "Failed to start web server");
            sWebServer = null;
        }
        
        return success;
    }

    /**
     * 启动 Web 服务器（使用默认端口 8080）
     * @return true 成功，false 失败
     */
    public static boolean startServer() {
        return startServer(8080);
    }

    /**
     * 停止 Web 服务器
     */
    public static void stopServer() {
        if (sWebServer != null) {
            sWebServer.stopServer();
            sWebServer = null;
            Log.i(TAG, "Web server stopped");
        }
    }

    /**
     * 检查 Web 服务器是否运行
     * @return true 运行中，false 未运行
     */
    public static boolean isRunning() {
        return sWebServer != null && sWebServer.isRunning();
    }

    /**
     * 获取服务器端口
     * @return 端口号，如果未运行返回 -1
     */
    public static int getPort() {
        if (sWebServer != null && sWebServer.isRunning()) {
            return sWebServer.getPort();
        }
        return -1;
    }
}
