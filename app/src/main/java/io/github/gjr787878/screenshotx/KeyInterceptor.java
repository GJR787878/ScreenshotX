package io.github.gjr787878.screenshotx;

import android.view.KeyEvent;

/**
 * 按键拦截器（重写版）：
 * - 电源键 + 音量上键 = 触发录屏（两种按键顺序统一走 tryCombo，窗口 1.5s）
 * - 录屏期间：电源键短按（开始 1s 后）= 结束录屏（不锁屏）；
 *   音量键一律放行，可正常调节音量（旧版录屏中音量上键被全部吞掉）
 * - 组合触发后的拦截窗口仅保留 0.6s 即复位所有标志
 *   （旧版 comboTriggered 永久残留，会导致系统静音切换永久失效）
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
    private static long recordingStartTime = 0;

    /** 先按下的键在该窗口内，另一键按下才算组合。 */
    private static final long COMBO_WINDOW = 1500;
    /** 组合触发后继续拦截残余事件的短窗口。 */
    private static final long COMBO_TAIL = 600;
    /** 录屏开始后多久才允许电源键停止（防组合释放被误判）。 */
    private static final long RECORD_STABLE_DELAY = 1000;

    /**
     * 处理按键事件（interceptKeyBeforeQueueing 阶段）。
     * 返回 true 表示拦截该事件，不让系统继续处理。
     */
    public static boolean onKeyEvent(int keyCode, int action) {
        boolean down = action == KeyEvent.ACTION_DOWN;
        boolean up = action == KeyEvent.ACTION_UP;
        boolean intercept = false;
        long now = System.currentTimeMillis();

        // 尾部窗口过期，立即复位组合标志（修复旧版标志永久残留）
        if (comboTriggered && now - comboTime > COMBO_TAIL) {
            comboTriggered = false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (down) {
                    volUpDown = true;
                    volUpDownTime = now;
                    // 电源键当前按住且未在录屏：尝试组合；录屏中音量键放行调音量
                    if (powerDown && !recording) {
                        intercept = tryCombo(now);
                    }
                } else if (up) {
                    volUpDown = false;
                    // 组合释放当刻的音量上抬起吞掉；录屏中的音量抬起一律放行
                    if (comboTriggered) intercept = true;
                }
                break;

            case KeyEvent.KEYCODE_POWER:
                if (down) {
                    powerDown = true;
                    powerDownTime = now;
                    if (recording) {
                        // 录屏中电源按下：拦截，不锁屏/不唤醒
                        intercept = true;
                    } else if (volUpDown) {
                        // 反向顺序：音量上已按住，电源后按
                        intercept = tryCombo(now);
                    }
                } else if (up) {
                    powerDown = false;
                    long dur = now - powerDownTime;
                    if (pendingPowerUpFromCombo) {
                        // 组合触发当次的电源抬起吞掉，不判为"结束录屏"
                        pendingPowerUpFromCombo = false;
                        intercept = true;
                        break;
                    }
                    if (recording) {
                        if (dur < 500 && now - recordingStartTime > RECORD_STABLE_DELAY) {
                            HookLogic.log("power single press during recording, stop");
                            HookLogic.stopRecording();
                            recording = false;
                        }
                        // 录屏中电源抬起一律拦截，不触发睡眠
                        intercept = true;
                    }
                }
                break;
        }

        return intercept;
    }

    /** 统一组合判定：两键在 COMBO_WINDOW 内同按即触发。返回是否应拦截当前事件。 */
    private static boolean tryCombo(long now) {
        if (recording || comboTriggered) return true;
        // 录屏总开关关闭：不触发录屏，也不拦截组合（系统静音切换等默认行为照常）
        if (!HookLogic.recordingEnabled()) return false;
        long firstTime = Math.min(powerDownTime, volUpDownTime);
        if (firstTime <= 0 || now - firstTime > COMBO_WINDOW) return false;
        HookLogic.log("power+volup combo detected, start recording");
        // 重活（授权/录制）全部异步进行，不阻塞输入事件线程
        HookLogic.startRecording();
        recording = true;
        comboTriggered = true;
        comboTime = now;
        recordingStartTime = now;
        pendingPowerUpFromCombo = true;
        return true;
    }

    /** interceptKeyBeforeDispatching 阶段的拦截判断。返回 true 则拦截分发。 */
    public static boolean shouldInterceptDispatching(int keyCode) {
        long now = System.currentTimeMillis();
        // 录屏期间，电源键所有事件都拦截
        if (recording && keyCode == KeyEvent.KEYCODE_POWER) return true;
        // 组合短窗口内，电源/音量上残余事件拦截
        if (comboTriggered && now - comboTime < COMBO_TAIL
                && (keyCode == KeyEvent.KEYCODE_POWER
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            return true;
        }
        return false;
    }

    /**
     * 实时组合意图：两键同按，或触发后的短尾窗口。
     * 供系统组合键管理器（静音切换等）消费拦截用。
     */
    public static boolean isComboIntent() {
        long now = System.currentTimeMillis();
        // 录屏总开关关闭时，不消费系统组合键（静音切换等照常）
        if (!HookLogic.recordingEnabled()) return false;
        if (powerDown && volUpDown) return true;
        return comboTriggered && now - comboTime < COMBO_TAIL;
    }

    /** 组合/录屏活动状态：录屏中（电源菜单/长按要拦）或组合短窗口。 */
    public static boolean comboActive() {
        long now = System.currentTimeMillis();
        if (recording) return true;
        return comboTriggered && now - comboTime < COMBO_TAIL;
    }

    public static boolean isRecording() {
        return recording;
    }

    /** 由 HookLogic 在授权取消 / 录制结束广播时复位。 */
    public static void setRecording(boolean r) {
        recording = r;
        comboTriggered = false;
        if (r) {
            recordingStartTime = System.currentTimeMillis();
        } else {
            // 彻底复位，防止服务异常死亡后按键标志残留导致电源长按/组合失灵
            pendingPowerUpFromCombo = false;
            powerDown = false;
            volUpDown = false;
        }
    }
}
