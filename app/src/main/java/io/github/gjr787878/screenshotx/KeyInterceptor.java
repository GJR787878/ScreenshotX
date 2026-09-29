package io.github.gjr787878.screenshotx;

import android.view.KeyEvent;

/**
 * 按键拦截器：
 * - 电源键 + 音量上键 = 触发录屏（同时拦截这两个键，屏蔽系统默认功能）
 * - 录屏期间，电源键短按（且录屏已稳定 1 秒以上）= 结束录屏（不锁屏）
 * - 关键：组合键触发时的电源键抬起不能被误判为"单击结束录屏"
 */
public class KeyInterceptor {

    private static volatile boolean recording = false;
    private static volatile boolean volUpDown = false;
    private static volatile boolean powerDown = false;
    private static long powerDownTime = 0;
    private static volatile boolean comboTriggered = false;
    // 组合键触发时，电源键抬起需要被忽略的标志（避免误判为结束录屏）
    private static volatile boolean pendingPowerUpFromCombo = false;
    // 录屏开始时间，用来判断电源键结束是否是"稳定后的单独短按"
    private static volatile long recordingStartTime = 0;

    private static final long COMBO_TIMEOUT = 800;
    private static final long POWER_SINGLE_TIMEOUT = 400;
    // 录屏开始后 1 秒内，电源键抬起不判定为结束录屏（避免组合键抬起误触发）
    private static final long RECORD_STABLE_DELAY = 1000;

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
                    if (powerDown && !comboTriggered) {
                        intercept = true;
                    }
                } else if (up) {
                    volUpDown = false;
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
                    // 如果是组合键触发时的电源键抬起，直接忽略，不判定为结束录屏
                    if (pendingPowerUpFromCombo) {
                        pendingPowerUpFromCombo = false;
                        intercept = true;
                        break;
                    }
                    // 录屏期间，电源键短按抬起 = 结束录屏
                    // 但必须满足：录屏已稳定 1 秒以上（避免组合键抬起误触发）
                    if (recording && dur < 500) {
                        long sinceRecordStart = System.currentTimeMillis() - recordingStartTime;
                        if (sinceRecordStart > RECORD_STABLE_DELAY) {
                            android.util.Log.d("ScreenshotX", "power single press during recording, stop");
                            HookLogic.stopRecording();
                            recording = false;
                        }
                        intercept = true;
                    }
                }
                break;
        }

        // 检测组合键：电源键按下 + 音量上按下 = 触发录屏
        if (powerDown && volUpDown && !recording && !comboTriggered) {
            long now = System.currentTimeMillis();
            if (now - powerDownTime < COMBO_TIMEOUT) {
                android.util.Log.d("ScreenshotX", "power+volup combo detected, start recording");
                HookLogic.startRecording();
                recording = true;
                comboTriggered = true;
                recordingStartTime = System.currentTimeMillis();
                // 标记：接下来的电源键抬起是组合键的一部分，要忽略
                pendingPowerUpFromCombo = true;
                intercept = true;
            }
        }

        return intercept;
    }

    public static boolean isRecording() {
        return recording;
    }

    public static void setRecording(boolean r) {
        recording = r;
        if (r) {
            recordingStartTime = System.currentTimeMillis();
        }
    }
}
