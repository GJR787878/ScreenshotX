package io.github.gjr787878.screenshotx;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/** 现代 LSPosed 入口（libxposed-api v100+），由 META-INF/xposed/java_init.list 声明。 */
public class ModernEntry extends XposedModule {

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        HookLogic.log("onModuleLoaded process=" + param.getProcessName()
                + " isSystemServer=" + param.isSystemServer());
    }

    /**
     * §6 现代 API：system_server 启动专用回调，提供系统服务器 ClassLoader，
     * 这是 hook PhoneWindowManager / ScreenshotHelper 的正确且可靠时机。
     */
    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        HookLogic.install(param.getClassLoader());
    }

    /**
     * 兜底 / 分进程加载：
     * - "android"：个别 LSPosed 版本通过 onPackageLoaded 上报系统框架，install 内部幂等
     * - "com.android.systemui"：安装授权对话框自动批准 hook（录屏零交互）
     */
    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if ("android".equals(pkg)) {
            HookLogic.install(param.getDefaultClassLoader());
        } else if ("com.android.systemui".equals(pkg)) {
            HookLogic.installSystemUiHooks(param.getDefaultClassLoader());
        }
    }
}
