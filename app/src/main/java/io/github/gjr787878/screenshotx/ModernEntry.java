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
     * 兜底：个别 LSPosed 版本仍可能通过 onPackageLoaded 上报 "android"。
     * install 内部幂等，重复进入不会二次 hook。
     */
    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if ("android".equals(param.getPackageName())) {
            HookLogic.install(param.getDefaultClassLoader());
        }
    }
}
