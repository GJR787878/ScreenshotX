package io.github.gjr787878.screenshotx;

import android.util.Log;
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
        // PhoneWindowManager 在 system_server 进程，包名是 android
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
                                    Log.d(TAG, "Power DOWN");
                                } else if (action == KeyEvent.ACTION_UP) {
                                    powerPressed = false;
                                }
                            }

                            // 音量下 + 电源键在 500ms 内
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                                    && action == KeyEvent.ACTION_DOWN
                                    && powerPressed
                                    && System.currentTimeMillis() - powerTime < 500) {
                                Log.d(TAG, "Power+VolDown detected!");
                                triggerScreenshot();
                                // 取消后续处理，阻止系统截屏
                                param.setResult(0);
                            }
                        }
                    });
            Log.d(TAG, "PhoneWindowManager hooked OK");
        } catch (Throwable t) {
            Log.e(TAG, "PhoneWindowManager hook error", t);
        }
    }

    private void triggerScreenshot() {
        try {
            Runtime.getRuntime().exec(new String[]{
                    "su", "-c",
                    "am startservice -n io.github.gjr787878.screenshotx/.ScreenshotService -a io.github.gjr787878.screenshotx.SHOOT"
            });
        } catch (Exception e) {
            Log.e(TAG, "triggerScreenshot error", e);
        }
    }
}
