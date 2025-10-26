function App() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 to-indigo-100 dark:from-gray-900 dark:to-gray-800">
      <div className="container mx-auto px-4 py-16">
        <div className="max-w-4xl mx-auto">
          {/* Header */}
          <div className="text-center mb-12">
            <h1 className="text-5xl font-bold text-gray-900 dark:text-white mb-4">
              🚀 SoHook Web Dashboard
            </h1>
            <p className="text-xl text-gray-600 dark:text-gray-300">
              Android 内存泄漏检测可视化平台
            </p>
          </div>

          {/* Status Card */}
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl p-8 mb-8">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-2xl font-semibold text-gray-800 dark:text-white">
                ✅ 项目初始化成功
              </h2>
              <span className="px-4 py-2 bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200 rounded-full text-sm font-medium">
                就绪
              </span>
            </div>

            <div className="space-y-4">
              <div className="flex items-start">
                <span className="text-2xl mr-3">📦</span>
                <div>
                  <h3 className="font-semibold text-gray-800 dark:text-white">技术栈</h3>
                  <p className="text-gray-600 dark:text-gray-300">
                    React 19 + TypeScript + Vite + TailwindCSS
                  </p>
                </div>
              </div>

              <div className="flex items-start">
                <span className="text-2xl mr-3">🎨</span>
                <div>
                  <h3 className="font-semibold text-gray-800 dark:text-white">UI 组件</h3>
                  <p className="text-gray-600 dark:text-gray-300">
                    shadcn/ui + Lucide Icons + Recharts
                  </p>
                </div>
              </div>

              <div className="flex items-start">
                <span className="text-2xl mr-3">⚡</span>
                <div>
                  <h3 className="font-semibold text-gray-800 dark:text-white">开发体验</h3>
                  <p className="text-gray-600 dark:text-gray-300">
                    极速 HMR + TypeScript 智能提示 + Prettier 格式化
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Next Steps */}
          <div className="bg-white dark:bg-gray-800 rounded-lg shadow-xl p-8">
            <h2 className="text-2xl font-semibold text-gray-800 dark:text-white mb-6">
              📝 下一步操作
            </h2>

            <div className="space-y-4">
              <div className="flex items-start">
                <span className="flex-shrink-0 w-8 h-8 bg-blue-500 text-white rounded-full flex items-center justify-center font-bold mr-4">
                  1
                </span>
                <div>
                  <h3 className="font-semibold text-gray-800 dark:text-white">安装依赖</h3>
                  <code className="block mt-2 p-3 bg-gray-100 dark:bg-gray-900 rounded text-sm">
                    .\install-deps.bat
                  </code>
                  <p className="text-sm text-gray-600 dark:text-gray-400 mt-2">
                    或手动运行: npm install -D tailwindcss postcss autoprefixer ...
                  </p>
                </div>
              </div>

              <div className="flex items-start">
                <span className="flex-shrink-0 w-8 h-8 bg-blue-500 text-white rounded-full flex items-center justify-center font-bold mr-4">
                  2
                </span>
                <div>
                  <h3 className="font-semibold text-gray-800 dark:text-white">
                    查看配置文档
                  </h3>
                  <p className="text-gray-600 dark:text-gray-300 mt-2">
                    阅读 <code className="px-2 py-1 bg-gray-100 dark:bg-gray-900 rounded">SETUP.md</code> 了解详细配置
                  </p>
                </div>
              </div>

              <div className="flex items-start">
                <span className="flex-shrink-0 w-8 h-8 bg-blue-500 text-white rounded-full flex items-center justify-center font-bold mr-4">
                  3
                </span>
                <div>
                  <h3 className="font-semibold text-gray-800 dark:text-white">开始开发</h3>
                  <p className="text-gray-600 dark:text-gray-300 mt-2">
                    依赖安装完成后，即可开始开发仪表盘页面
                  </p>
                </div>
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="text-center mt-8 text-gray-600 dark:text-gray-400">
            <p>💡 提示：查看 FRAMEWORK_SELECTION.md 了解技术选型详情</p>
          </div>
        </div>
      </div>
    </div>
  );
}

export default App;
