package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 三指下滑手势监听：在 system_server 内通过 InputManager.monitorGestureInput
 * 注册一个全局手势监视器（只接收事件副本，不拦截正常输入），
 * 检测到三根手指同时向下滑动即触发截屏。参照 AOSP SystemGesturesPointerEventListener。
 */
public class GestureWatcher {

    private static final int MAX_PTR = 10;
    private static final int NONE = Integer.MIN_VALUE;
    private static final long COOLDOWN = 1500L;

    private final Context ctx;
    private final float threshold;
    private final int[] downId = new int[MAX_PTR];
    private final float[] downX = new float[MAX_PTR];
    private final float[] downY = new float[MAX_PTR];
    private long lastFire = 0L;

    /** 在 system_server 内安装全局手势监视器。 */
    public static void install(final Context c) {
        try {
            InputManager im = (InputManager) c.getSystemService(Context.INPUT_SERVICE);
            Method mm = InputManager.class.getMethod(
                    "monitorGestureInput", String.class, int.class);
            Object monitor = mm.invoke(im, "screenshotx-gesture", 0);
            Method gic = monitor.getClass().getMethod("getInputChannel");
            InputChannel ch = (InputChannel) gic.invoke(monitor);

            final GestureWatcher w = new GestureWatcher(c);
            new InputEventReceiver(ch, Looper.getMainLooper()) {
                @Override public void onInputEvent(InputEvent event) {
                    try {
                        if (event instanceof MotionEvent) w.onMotion((MotionEvent) event);
                    } finally {
                        finishInputEvent(event, false);
                    }
                }
            };
            HookLogic.log("gesture watcher installed");
        } catch (Throwable t) {
            HookLogic.log("gesture watcher failed: " + t);
        }
    }

    private GestureWatcher(Context c) {
        ctx = c;
        DisplayMetrics dm = new DisplayMetrics();
        c.getDisplay().getRealMetrics(dm);
        threshold = dm.heightPixels * 0.08f; // 下滑超过屏高 8%
        Arrays.fill(downId, NONE);
    }

    private void onMotion(MotionEvent ev) {
        // 开关关闭：不检测，同时清空落点避免脏数据
        if (!Prefs.threeFinger(ctx)) { Arrays.fill(downId, NONE); return; }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                Arrays.fill(downId, NONE);
                capture(ev, 0);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                capture(ev, ev.getActionIndex());
                break;
            case MotionEvent.ACTION_MOVE:
                if (ev.getPointerCount() >= 3) check(ev);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                Arrays.fill(downId, NONE);
                break;
            default:
                break;
        }
    }

    private void capture(MotionEvent ev, int idx) {
        int id = ev.getPointerId(idx);
        for (int i = 0; i < MAX_PTR; i++) {
            if (downId[i] == NONE) {
                downId[i] = id;
                downX[i] = ev.getX(idx);
                downY[i] = ev.getY(idx);
                return;
            }
        }
    }

    private void check(MotionEvent ev) {
        int matched = 0;
        int n = Math.min(ev.getPointerCount(), 5);
        for (int p = 0; p < n; p++) {
            int id = ev.getPointerId(p);
            int s = -1;
            for (int i = 0; i < MAX_PTR; i++) {
                if (downId[i] == id) { s = i; break; }
            }
            if (s < 0) continue;
            float dy = ev.getY(p) - downY[s];
            float dx = Math.abs(ev.getX(p) - downX[s]);
            if (dy > threshold && dy > dx) matched++;
        }
        if (matched >= 3) {
            long now = System.currentTimeMillis();
            if (now - lastFire > COOLDOWN) {
                lastFire = now;
                HookLogic.log("three-finger swipe detected, request shot");
                HookLogic.requestShot();
            }
        }
    }
}
