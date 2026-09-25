package io.github.gjr787878.screenshotx;

import android.view.KeyEvent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookMain implements IXposedHookLoadPackage {

    private static final String TAG = "ScreenshotX";
    private static boolean powerPressed = false;
    private static long powerTime = 0;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        // PhoneWindowManager 在 system_server 进程，包名是 "android"
        if (lp.packageName.equals("android")) {
            hookPhoneWindowManager(lp);
        }
    }

    private void hookPhoneWindowManager(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> pwmClass = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager", lp.classLoader);

            XposedHelpers.findAndHookMethod(pwmClass, "interceptKeyBeforeQueueing",
                    KeyEvent.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            KeyEvent event = (KeyEvent) param.args[0];
                            if (event == null) return;

                            int keyCode = event.getKeyCode();
                            int action = event.getAction();

                            if (keyCode == KeyEvent.KEYCODE_POWER) {
                                if (action == KeyEvent.ACTION_DOWN) {
                                    powerPressed = true;
                                    powerTime = System.currentTimeMillis();
                                } else if (action == KeyEvent.ACTION_UP) {
                                    powerPressed = false;
                                }
                            }

                            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                                    && action == KeyEvent.ACTION_DOWN
                                    && powerPressed
                                    && System.currentTimeMillis() - powerTime < 500) {
                                triggerScreenshot();
                                // 取消系统截屏
                                param.setResult(0);
                            }
                        }
                    });
            android.util.Log.d(TAG, "PhoneWindowManager hooked");
        } catch (Throwable t) {
            android.util.Log.e(TAG, "hook error", t);
        }
    }

    private void triggerScreenshot() {
        try {
            Runtime.getRuntime().exec(new String[]{
                    "su", "-c",
                    "am startservice -n io.github.gjr787878.screenshotx/.ScreenshotService -a io.github.gjr787878.screenshotx.SHOOT"
            });
        } catch (Exception e) {
            android.util.Log.e(TAG, "trigger error", e);
        }
    }
}
