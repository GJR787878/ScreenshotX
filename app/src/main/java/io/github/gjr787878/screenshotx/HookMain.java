package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
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
        // Hook SystemUI 的 PhoneWindowManager 拦截按键
        if (lp.packageName.equals("com.android.systemui")) {
            hookPhoneWindowManager(lp);
        }
    }

    private void hookPhoneWindowManager(XC_LoadPackage.LoadPackageParam lp) {
        try {
            Class<?> pwmClass = XposedHelpers.findClass(
                    "com.android.systemui.keyguard.KeyguardViewMediator", lp.classLoader);
            Log.d(TAG, "Found KeyguardViewMediator");
        } catch (Throwable t) {
            Log.e(TAG, "hookPhoneWindowManager error", t);
        }

        // 直接 hook PhoneWindowManager.interceptKeyBeforeQueueing
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

                            // 电源键按下
                            if (keyCode == KeyEvent.KEYCODE_POWER) {
                                if (action == KeyEvent.ACTION_DOWN) {
                                    powerPressed = true;
                                    powerTime = System.currentTimeMillis();
                                } else if (action == KeyEvent.ACTION_UP) {
                                    powerPressed = false;
                                }
                            }

                            // 音量下键按下，且电源键在 500ms 内按下
                            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                                    && action == KeyEvent.ACTION_DOWN
                                    && powerPressed
                                    && System.currentTimeMillis() - powerTime < 500) {
                                Log.d(TAG, "Power+VolDown detected!");
                                // 触发截屏
                                triggerScreenshot();
                                // 取消系统截屏
                                param.setResult(0);
                            }
                        }
                    });
            Log.d(TAG, "PhoneWindowManager hooked");
        } catch (Throwable t) {
            Log.e(TAG, "PhoneWindowManager hook error", t);
        }
    }

    private void triggerScreenshot() {
        // 通过 broadcast 或直接启动 service 触发截屏
        // 因为是在 SystemUI 进程里，需要通过 am 命令启动
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{
                        "su", "-c",
                        "am startservice -n io.github.gjr787878.screenshotx/.ScreenshotService -a io.github.gjr787878.screenshotx.SHOOT"
                });
            } catch (Exception e) {
                Log.e(TAG, "triggerScreenshot error", e);
            }
        });
    }
}
