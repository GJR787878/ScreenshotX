package io.github.gjr787878.screenshotx;

import android.view.KeyEvent;

/**
 * 按键拦截器：
 * - 电源键 + 音量上键 = 触发录屏（电源键+音量下 = 截图，已有）
 * - 录屏期间，电源键单击 = 结束录屏（且不触发锁屏）
 */
public class KeyInterceptor {

    private static volatile boolean recording = false;
    private static volatile boolean volUpDown = false;
    private static volatile boolean powerDown = false;
    private static long powerDownTime = 0;

    private static final long COMBO_TIMEOUT = 800; // 组合键有效窗口 800ms
    private static final long POWER_SINGLE_TIMEOUT = 300; // 电源键单击判定窗口

    public static void onKeyEvent(int keyCode, int action) {
        boolean down = action == KeyEvent.ACTION_DOWN;
        boolean up = action == KeyEvent.ACTION_UP;

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (down) {
                    volUpDown = true;
                } else if (up) {
                    volUpDown = false;
                }
                break;

            case KeyEvent.KEYCODE_POWER:
                if (down) {
                    powerDown = true;
                    powerDownTime = System.currentTimeMillis();
                    // 录屏期间，电源键按下：拦截，不锁屏
                    if (recording) {
                        // 标记为已处理，不传给系统
                        // 注意：实际拦截需要在 beforeHookedMethod 中 setResult，
                        // 这里只是状态记录，真正拦截在 HookLogic 中
                    }
                } else if (up) {
                    powerDown = false;
                    long dur = System.currentTimeMillis() - powerDownTime;
                    // 录屏期间，电源键短按抬起 = 结束录屏
                    if (recording && dur < 500) {
                        HookLogic.log("power single press during recording, stop");
                        HookLogic.stopRecording();
                        recording = false;
                        // TODO: 拦截这次电源键事件，不触发锁屏
                    }
                }
                break;
        }

        // 检测组合键：电源键按下 + 音量上按下 = 触发录屏
        if (powerDown && volUpDown) {
            long now = System.currentTimeMillis();
            if (now - powerDownTime < COMBO_TIMEOUT) {
                if (!recording) {
                    HookLogic.log("power+volup combo detected, start recording");
                    HookLogic.startRecording();
                    recording = true;
                    // 重置状态，避免重复触发
                    volUpDown = false;
                }
            }
        }
    }

    /** 录屏状态（供悬浮窗等查询）。 */
    public static boolean isRecording() {
        return recording;
    }

    /** 录屏状态变更（由 RecordService 回调）。 */
    public static void setRecording(boolean r) {
        recording = r;
    }
}
