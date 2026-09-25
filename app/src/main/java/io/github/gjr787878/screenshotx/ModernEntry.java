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

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!"android".equals(param.getPackageName())) return;
        HookLogic.install(param.getDefaultClassLoader());
    }
}
