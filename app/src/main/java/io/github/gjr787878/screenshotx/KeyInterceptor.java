package io.github.gjr787878.screenshotx;

import android.view.KeyEvent;

/**
 * 按键拦截器：
 * - 电源键 + 音量上键 = 触发录屏（同时拦截这两个键，屏蔽系统默认功能）
 * - 录屏期间，电源键单击 = 结束录屏（且拦截电源键，不触发锁屏）
 * - 电源键 + 音量下键 = 截图（已有，走 ScreenshotHelper hook）
 */
public class KeyInterceptor {

    private static volatile boolean recording = false;
    private static volatile boolean volUpDown = false;
    private static volatile boolean powerDown = false;
    private static long powerDownTime = 0;
    private static volatile boolean comboTriggered = false; // 本次组合键已触发录屏

    private static final long COMBO_TIMEOUT = 800;
    private static final long POWER_SINGLE_TIMEOUT = 300;

    /**
     * 处理按键事件。返回 true 表示应该拦截这个事件（不让系统继续处理）。
     */
    public static boolean onKeyEvent(int keyCode, int action) {
        boolean down = action == KeyEvent.ACTION_DOWN;
        boolean up = action == KeyEvent.ACTION_UP;
        boolean intercept = false;

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (down) {
                    volUpDown = true;
                    // 如果电源键已经按下，说明是组合键，拦截音量上键
                    if (powerDown && !comboTriggered) {
                        intercept = true;
                    }
                } else if (up) {
                    volUpDown = false;
                    // 组合键抬起时，也拦截音量上键抬起事件
                    if (comboTriggered) {
                        intercept = true;
                        comboTriggered = false;
                    }
                }
                break;

            case KeyEvent.KEYCODE_POWER:
                if (down) {
                    powerDown = true;
                    powerDownTime = System.currentTimeMillis();
                    comboTriggered = false;
                    // 录屏期间，电源键按下：拦截，不锁屏
                    if (recording) {
                        intercept = true;
                    }
                } else if (up) {
                    powerDown = false;
                    long dur = System.currentTimeMillis() - powerDownTime;
                    // 录屏期间，电源键短按抬起 = 结束录屏，且拦截抬起事件
                    if (recording && dur < 500) {
                        HookLogic.log("power single press during recording, stop");
                        HookLogic.stopRecording();
                        recording = false;
                        intercept = true;
                    }
                }
                break;
        }

        // 检测组合键：电源键按下 + 音量上按下 = 触发录屏
        if (powerDown && volUpDown && !recording && !comboTriggered) {
            long now = System.currentTimeMillis();
            if (now - powerDownTime < COMBO_TIMEOUT) {
                HookLogic.log("power+volup combo detected, start recording");
                HookLogic.startRecording();
                recording = true;
                comboTriggered = true;
                // 拦截电源键按下事件，不让锁屏
                intercept = true;
            }
        }

        return intercept;
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
