package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.MotionEvent;

import java.util.Arrays;

/**
 * 三指下滑手势识别（纯状态机）。
 *
 * 事件来源：HookLogic hook PhoneWindowManager.interceptMotionBeforeQueueing ——
 * 该方法在 system_server 内对“每个全局 MotionEvent 入队前”调用，稳定可靠，
 * 不再依赖 monitorGestureInput / 反射创建 InputEventReceiver（旧路径开机即
 * InvocationTargetException，导致三指完全失效）。
 *
 * 判定参照 AOSP 三指截屏：三根手指自各自落下点起，向下位移超过阈值即触发；
 * 记录每指“历史最大下滑量”，容忍手指中途停顿或轻微回弹。
 */
public class GestureWatcher {

    private static final int MAX_PTR = 10;
    private static final int NONE = Integer.MIN_VALUE;
    private static final long COOLDOWN = 1500L;

    private static GestureWatcher INSTANCE;

    /** 由 HookLogic 在拿到系统 Context 后调用，仅初始化识别器（不再注册输入通道）。 */
    public static synchronized void install(Context c) {
        if (INSTANCE == null) INSTANCE = new GestureWatcher(c);
    }

    /** 由 PhoneWindowManager.interceptMotionBeforeQueueing hook 对每个事件调用。 */
    public static void dispatch(MotionEvent ev) {
        GestureWatcher w = INSTANCE;
        if (w != null) w.onMotion(ev);
    }

    private final Context ctx;
    private final float threshold;
    private final int[] downId = new int[MAX_PTR];
    private final float[] downX = new float[MAX_PTR];
    private final float[] downY = new float[MAX_PTR];
    private final float[] maxDy = new float[MAX_PTR];
    private long lastFire = 0L;
    private boolean threeEnabled = false;

    private GestureWatcher(Context c) {
        ctx = c;
        DisplayMetrics dm = new DisplayMetrics();
        c.getDisplay().getRealMetrics(dm);
        threshold = dm.heightPixels * 0.06f; // 6% 屏高，提高灵敏度
        Arrays.fill(downId, NONE);
    }

    private synchronized void onMotion(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // DOWN 时刷新开关，避免每个 MOVE 都做一次 Settings binder（单指 MOVE 量极大）
                threeEnabled = Prefs.threeFinger(ctx);
                Arrays.fill(downId, NONE);
                if (threeEnabled) capture(ev, 0);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (threeEnabled) capture(ev, ev.getActionIndex());
                break;
            case MotionEvent.ACTION_MOVE:
                if (threeEnabled && ev.getPointerCount() >= 3) check(ev);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (threeEnabled) release(ev.getPointerId(ev.getActionIndex()));
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
                maxDy[i] = 0f;
                return;
            }
        }
    }

    private void release(int id) {
        for (int i = 0; i < MAX_PTR; i++) {
            if (downId[i] == id) { downId[i] = NONE; maxDy[i] = 0f; return; }
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
            if (dy > maxDy[s]) maxDy[s] = dy; // 历史最大下滑，容忍停顿/回弹
            float dx = Math.abs(ev.getX(p) - downX[s]);
            // 向下为主（纵向位移不小于横向），且超过阈值
            if (maxDy[s] > threshold && maxDy[s] >= dx) matched++;
        }
        if (matched >= 3) {
            long now = System.currentTimeMillis();
            if (now - lastFire > COOLDOWN) {
                lastFire = now;
                HookLogic.vibrate(ctx); // 立刻震动，与截屏请求成对出现
                HookLogic.log("three-finger swipe detected, request shot");
                HookLogic.requestShot();
            }
        }
    }
}
