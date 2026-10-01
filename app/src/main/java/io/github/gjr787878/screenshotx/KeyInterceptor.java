package io.github.gjr787878.screenshotx;

import android.view.KeyEvent;

/**
 * 按键拦截器：
 * - 电源键 + 音量上键 = 触发录屏（多层拦截，屏蔽系统默认功能：震动切换、电源菜单等）
 * - 录屏期间，电源键短按（录屏稳定 1 秒后）= 结束录屏（不锁屏）
 * - 组合键触发时的电源键抬起不能被误判为"结束录屏"
 */
public class KeyInterceptor {

    private static volatile boolean recording = false;
    private static volatile boolean volUpDown = false;
    private static volatile boolean powerDown = false;
    private static long powerDownTime = 0;
    private static long volUpDownTime = 0;
    private static volatile boolean comboTriggered = false;
    private static long comboTime = 0;
    private static volatile boolean pendingPowerUpFromCombo = false;
    private static volatile long recordingStartTime = 0;

    private static final long COMBO_TIMEOUT = 1500;
    private static final long POWER_SINGLE_TIMEOUT = 400;
    private static final long RECORD_STABLE_DELAY = 1000;
    // 组合键触发后，短时间内继续拦截电源/音量上事件（防系统默认功能）
    private static final long COMBO_INTERCEPT_WINDOW = 2000;

    /**
     * 处理按键事件（interceptKeyBeforeQueueing 阶段）。
     * 返回 true 表示应该拦截这个事件（不让系统继续处理）。
     */
    public static boolean onKeyEvent(int keyCode, int action) {
        boolean down = action == KeyEvent.ACTION_DOWN;
        boolean up = action == KeyEvent.ACTION_UP;
        boolean intercept = false;

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (down) {
                    volUpDown = true;
                    volUpDownTime = System.currentTimeMillis();
                    // 仅当电源键【当前仍按住】（组合键操作中）才拦截音量上，
                    // 防止 crDroid 把 电源+音量上 当成"切换震动模式"默认功能。
                    // 电源键松开后的正常音量调节不受影响。
                    if (powerDown) {
                        intercept = true;
                    }
                } else if (up) {
                    volUpDown = false;
                    if (comboTriggered) {
                        intercept = true;
                        // 不立即重置 comboTriggered：保留 2 秒拦截窗口，
                        // 期间持续拦截电源/音量上事件（防 crDroid 震动切换等）
                    }
                }
                break;

            case KeyEvent.KEYCODE_POWER:
                if (down) {
                    powerDown = true;
                    powerDownTime = System.currentTimeMillis();
                    lastPowerDownTime = powerDownTime;
                    comboTriggered = false;
                    // 录屏期间，电源键按下：拦截，不锁屏
                    if (recording) {
                        intercept = true;
                    }
                    // 音量上已按下（1s 内）：立即触发录屏，并拦截电源 down
                    // （防止系统把电源键当普通唤醒/长按处理；
                    //   长按音量连续调节超 1s 后按电源不算组合键，避免误触发）
                    if (volUpDown && !recording && !comboTriggered
                            && System.currentTimeMillis() - volUpDownTime < 1000) {
                        HookLogic.log("volup+power combo detected (reverse), start recording");
                        HookLogic.startRecording();
                        recording = true;
                        comboTriggered = true;
                        comboTime = System.currentTimeMillis();
                        recordingStartTime = System.currentTimeMillis();
                        pendingPowerUpFromCombo = true;
                        intercept = true;
                    }
                } else if (up) {
                    powerDown = false;
                    long dur = System.currentTimeMillis() - powerDownTime;
                    if (pendingPowerUpFromCombo) {
                        pendingPowerUpFromCombo = false;
                        intercept = true;
                        break;
                    }
                    // 录屏期间，电源键短按抬起 = 结束录屏（录屏稳定 1 秒以上）
                    if (recording && dur < 500) {
                        long sinceRecordStart = System.currentTimeMillis() - recordingStartTime;
                        if (sinceRecordStart > RECORD_STABLE_DELAY) {
                            HookLogic.log("power single press during recording, stop");
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
                HookLogic.log("power+volup combo detected, start recording");
                HookLogic.startRecording();
                recording = true;
                comboTriggered = true;
                comboTime = now;
                recordingStartTime = System.currentTimeMillis();
                pendingPowerUpFromCombo = true;
                intercept = true;
            }
        }

        return intercept;
    }

    /** interceptKeyBeforeDispatching 阶段的拦截判断。返回 true 则返回 -1 拦截分发。 */
    public static boolean shouldInterceptDispatching(int keyCode) {
        long now = System.currentTimeMillis();
        // 录屏期间，电源键所有事件都拦截
        if (recording && keyCode == KeyEvent.KEYCODE_POWER) return true;
        // 组合键触发后的 2 秒窗口内，电源/音量上事件都拦截（防系统默认功能）
        if (comboTriggered && (keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            if (now - comboTime < COMBO_INTERCEPT_WINDOW) return true;
        }
        return false;
    }

    /** 组合键是否处于触发后的拦截窗口内（供电源菜单/长按拦截用）。 */
    public static boolean comboActive() {
        long now = System.currentTimeMillis();
        if (comboTime > 0 && now - comboTime < COMBO_INTERCEPT_WINDOW) return true;
        return comboTriggered
                && (now - comboTime < COMBO_INTERCEPT_WINDOW);
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
