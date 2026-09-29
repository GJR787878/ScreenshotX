package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.MotionEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * 三指下滑手势监听：在 system_server 内通过 InputManager.monitorGestureInput
 * 注册全局手势监视器（只接收事件副本，不拦截正常输入），三指同时下滑即截屏。
 * InputChannel / InputEventReceiver 为隐藏类，全程反射；
 * 事件回调通过 hook 自建 receiver 的 onInputEvent 取得（只处理自己的实例）。
 * 参照 AOSP SystemGesturesPointerEventListener。
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

    public static void install(final Context c) {
        try {
            InputManager im = (InputManager) c.getSystemService(Context.INPUT_SERVICE);
            Method mm = InputManager.class.getMethod(
                    "monitorGestureInput", String.class, int.class);
            Object monitor = mm.invoke(im, "screenshotx-gesture", 0);
            Object ch = monitor.getClass().getMethod("getInputChannel").invoke(monitor);

            final GestureWatcher w = new GestureWatcher(c);

            // 反射创建 InputEventReceiver(InputChannel, Looper)
            Class<?> icClass = Class.forName("android.view.InputChannel");
            Class<?> ierClass = Class.forName("android.view.InputEventReceiver");
            Class<?> ieClass = Class.forName("android.view.InputEvent");
            Constructor<?> ctor = ierClass.getDeclaredConstructor(icClass, Looper.class);
            final Object receiver = ctor.newInstance(ch, Looper.getMainLooper());

            // hook onInputEvent，仅处理自建实例；默认实现随后 finishInputEvent(false)
            Method onInputEvent = ierClass.getMethod("onInputEvent", ieClass);
            XposedBridge.hookMethod(onInputEvent, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.thisObject != receiver) return;
                    Object e = p.args[0];
                    if (e instanceof MotionEvent) w.onMotion((MotionEvent) e);
                }
            });
            HookLogic.log("gesture watcher installed");
        } catch (Throwable t) {
            HookLogic.log("gesture watcher failed: " + t);
        }
    }

    private GestureWatcher(Context c) {
        ctx = c;
        DisplayMetrics dm = new DisplayMetrics();
        c.getDisplay().getRealMetrics(dm);
        threshold = dm.heightPixels * 0.08f;
        Arrays.fill(downId, NONE);
    }

    private void onMotion(MotionEvent ev) {
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
                HookLogic.vibrate(ctx); // 立刻震动反馈
                HookLogic.log("three-finger swipe detected, request shot");
                HookLogic.requestShot();
            }
        }
    }
}
