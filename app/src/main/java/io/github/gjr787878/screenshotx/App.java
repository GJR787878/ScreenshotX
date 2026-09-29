package io.github.gjr787878.screenshotx;

import android.app.Application;
import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;

/**
 * 应用入口：安装进程级未捕获异常处理器，把崩溃堆栈落到
 * 应用私有外部目录 files/crash/crash.log（无需任何存储权限），
 * 便于事后导出真实堆栈，避免仅凭现象猜测。
 */
public class App extends Application {

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override public void onCreate() {
        super.onCreate();

        final File dir = getExternalFilesDir("crash");
        if (dir == null) return;
        final Thread.UncaughtExceptionHandler de = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, "crash.log");
                FileWriter fw = new FileWriter(f, true);
                fw.write("==== " + new Date() + " thread=" + thread.getName() + " ====\n");
                PrintWriter pw = new PrintWriter(fw);
                ex.printStackTrace(pw);
                pw.flush();
                pw.close();
            } catch (Throwable ignored) { }
            // 交给系统默认处理（仍会正常结束进程/记录到 dropbox）
            if (de != null) de.uncaughtException(thread, ex);
        });
    }
}
