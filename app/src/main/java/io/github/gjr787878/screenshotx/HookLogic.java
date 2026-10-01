package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SharedMemory;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.system.OsConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** 实际 hook 逻辑，由现代入口 ModernEntry 调用。hook 仍用 LSPosed 兼容的 XposedBridge API。 */
public class HookLogic {

    private static final String LOG_FILE = "/data/system/screenshotx_diag.log";
    private static Context sysContext;
    private static ClassLoader serverCl;
    private static long lastTrigger = 0;
    private static final Handler OWN = new Handler(Looper.getMainLooper());
    private static boolean logInited = false;
    private static volatile boolean installed = false;
    private static volatile boolean gestureInstalled = false;
    private static final Set<Class<?>> hookedHelperClasses =
            new java.util.concurrent.CopyOnWriteArraySet<>();

    // system_server 内常驻的 Root shell，省去每次触发冷启动 su
    private static Process rootShellProc;
    private static DataOutputStream rootShellOs;

    public static synchronized void log(String msg) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date())
                + " " + msg;
        XposedBridge.log("ScreenshotX: " + msg);
        if (!logInited) {
            logInited = true;
            try {
                FileWriter h = new FileWriter(new File(LOG_FILE), false);
                h.write("=== ScreenshotX diag "
                        + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())
                        + " ===\n");
                h.close();
            } catch (Throwable ignored) {}
        }
        try {
            FileWriter fw = new FileWriter(new File(LOG_FILE), true);
            fw.write(line + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    public static void install(ClassLoader cl) {
        if (installed) {
            log("install already done, skip");
            return;
        }
        serverCl = cl;
        log("install: start hooking in system_server");

        Class<?> pwm;
        try {
            pwm = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager", cl);
            log("PhoneWindowManager found");
        } catch (Throwable t) {
            log("PhoneWindowManager NOT found: " + t);
            return;
        }

        // 抓系统 Context，并在拿到后安装三指手势监视器
        try {
            Set<?> r = XposedBridge.hookAllMethods(pwm, "init", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0 && p.args[0] instanceof Context) {
                        Context c = (Context) p.args[0];
                        sysContext = c;
                        log("init context captured");
                        installGesture(c);
                    }
                }
            });
            log("init hooks=" + r.size());
        } catch (Throwable t) {
            log("init hook failed: " + t);
        }

        // 三指手势事件源：hook 全局 MotionEvent 入队前回调（每个 DOWN/MOVE/UP 都经过，
        // 在 system_server 内稳定运行），无需反射自建 InputChannel/InputEventReceiver。
        try {
            XposedBridge.hookAllMethods(pwm, "interceptMotionBeforeQueueing",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    for (Object a : p.args) {
                        if (a instanceof MotionEvent) {
                            GestureWatcher.dispatch((MotionEvent) a);
                            break;
                        }
                    }
                }
            });
            log("interceptMotionBeforeQueueing hooked");
        } catch (Throwable t) {
            log("motion hook failed: " + t);
        }

        // 按键事件：hook interceptKeyBeforeQueueing，监听电源键+音量上组合触发录屏，
        // 以及录屏期间电源键单击结束录屏（不锁屏）。
        try {
            XposedBridge.hookAllMethods(pwm, "interceptKeyBeforeQueueing",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object keyEv = p.args.length > 0 ? p.args[0] : null;
                        if (keyEv == null) return;
                        // 反射获取 keyCode 和 action
                        Method getKeyCode = keyEv.getClass().getMethod("getKeyCode");
                        Method getAction = keyEv.getClass().getMethod("getAction");
                        int keyCode = (int) getKeyCode.invoke(keyEv);
                        int action = (int) getAction.invoke(keyEv);
                        // 返回 true 表示拦截该事件，不让系统继续处理
                        boolean intercept = KeyInterceptor.onKeyEvent(keyCode, action);
                        if (intercept) {
                            // interceptKeyBeforeQueueing 返回 0 = 不向用户分发该事件
                            p.setResult(0);
                            log("intercepted key: code=" + keyCode + " action=" + action);
                        }
                    } catch (Throwable t) {
                        log("key event hook failed: " + t);
                    }
                }
            });
            log("interceptKeyBeforeQueueing hooked");
        } catch (Throwable t) {
            log("key hook failed: " + t);
        }

        // 拦截分发阶段：interceptKeyBeforeDispatching 返回 -1 表示拦截，不分发到应用。
        // 用于在组合键触发后的 2 秒窗口内，阻止系统把电源/音量上事件分发给
        // 上层（crDroid 的震动切换、长按电源菜单等默认功能在这一层或更早处理）。
        try {
            XposedBridge.hookAllMethods(pwm, "interceptKeyBeforeDispatching",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    try {
                        Object keyEv = p.args.length > 1 ? p.args[1] : null;
                        if (keyEv == null) return;
                        Method getKeyCode = keyEv.getClass().getMethod("getKeyCode");
                        int keyCode = (int) getKeyCode.invoke(keyEv);
                        if (KeyInterceptor.shouldInterceptDispatching(keyCode)) {
                            p.setResult(-1);
                            log("dispatch intercepted: code=" + keyCode);
                        }
                    } catch (Throwable t) {
                        log("key dispatch hook failed: " + t);
                    }
                }
            });
            log("interceptKeyBeforeDispatching hooked");
        } catch (Throwable t) {
            log("key dispatch hook install failed: " + t);
        }

        // 拦截电源菜单（长按电源键弹出的关机/重启面板）
        try {
            XposedBridge.hookAllMethods(pwm, "showGlobalActionsInternal",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (KeyInterceptor.isRecording() || KeyInterceptor.comboActive()) {
                        p.setResult(null);
                        log("global actions intercepted");
                    }
                }
            });
            log("showGlobalActionsInternal hooked");
        } catch (Throwable t) {
            log("global actions hook failed: " + t);
        }

        // 拦截电源键按下处理（长按检测入口），录屏/组合键窗口内直接吞掉
        try {
            XposedBridge.hookAllMethods(pwm, "interceptPowerKeyDown",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (KeyInterceptor.isRecording() || KeyInterceptor.comboActive()) {
                        p.setResult(null);
                        log("power key down intercepted");
                    }
                }
            });
            log("interceptPowerKeyDown hooked");
        } catch (Throwable t) {
            log("power key down hook failed: " + t);
        }

        // 拦截电源长按处理本身（crDroid 长按定时器到点后走 powerLongPress）
        try {
            XposedBridge.hookAllMethods(pwm, "powerLongPress",
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (KeyInterceptor.isRecording() || KeyInterceptor.comboActive()) {
                        p.setResult(null);
                        log("power long press intercepted");
                    }
                }
            });
            log("powerLongPress hooked");
        } catch (Throwable t) {
            log("power long press hook failed: " + t);
        }

        // 按键截屏：hook ScreenshotHelper.takeScreenshot，受“按键截屏”开关控制
        try {
            Class<?> sh = XposedHelpers.findClass(
                    "com.android.internal.util.ScreenshotHelper", cl);
            hookScreenshotHelper(sh);

            XposedBridge.hookAllConstructors(sh, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Class<?> runtime = p.thisObject.getClass();
                    if (runtime != sh) hookScreenshotHelper(runtime);
                }
            });
            log("ScreenshotHelper ctor hooked");
        } catch (Throwable t) {
            log("ScreenshotHelper hook failed: " + t);
        }

        // 截取受保护 / DRM 内容：剥离窗口 FLAG_SECURE（受对应开关控制，回调内动态读取）
        try {
            installSecureBypass();
        } catch (Throwable t) {
            log("secure bypass install failed: " + t);
        }

        // 开机后尽力预建常驻 Root shell（兜底路径用）
        new Thread(HookLogic::ensureDirectShell).start();

        installed = true;
        log("install: hooking done");
    }

    /** FLAG_SECURE 标志位（窗口级）。 */
    private static final int FLAG_SECURE = 0x00002000;
    /** WindowManager.LayoutParams.privateFlags 中的 PRIVATE_FLAG_SECURE。 */
    private static final int PRIVATE_FLAG_SECURE = 0x00000002;
    private static volatile boolean secureInstalled = false;

    /**
     * 绕过安全窗口黑屏：hook 窗口 secure 判定返回 false，并在窗口 add/relayout 时
     * 剥离 FLAG_SECURE / PRIVATE_FLAG_SECURE，使 SurfaceFlinger 不再把该 layer 当
     * 受保护内容（受保护 layer 在截图时会被涂黑）。
     * 注意：仅对 FLAG_SECURE 有效；硬件级 Widevine L1 / secure decoder 的帧在
     * TrustZone 受保护缓冲中，不进普通内存，软件无法截取。
     */
    private static void installSecureBypass() {
        if (secureInstalled) return;

        // 1) WindowState 安全判定：isSecureLocked()/isSecure() 返回 false；构造后清 mAttrs
        try {
            Class<?> ws = XposedHelpers.findClass(
                    "com.android.server.wm.WindowState", serverCl);
            for (Method m : ws.getDeclaredMethods()) {
                Class<?>[] pts = m.getParameterTypes();
                String n = m.getName();
                if (pts.length == 0 && m.getReturnType() == boolean.class
                        && (n.equals("isSecureLocked") || n.equals("isSecure"))) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (secureOn()) p.setResult(Boolean.FALSE);
                        }
                    });
                }
            }
            XposedBridge.hookAllConstructors(ws, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!secureOn()) return;
                    try {
                        Object attrs = XposedHelpers.getObjectField(p.thisObject, "mAttrs");
                        if (attrs instanceof WindowManager.LayoutParams) {
                            stripSecure((WindowManager.LayoutParams) attrs);
                        }
                    } catch (Throwable ignored) {}
                }
            });
            log("WindowState secure hooks installed");
        } catch (Throwable t) {
            log("secure: WindowState hook failed: " + t);
        }

        // 2) WindowManagerService addWindow / relayoutWindow：传入的 LayoutParams 剥离 secure
        try {
            Class<?> wms = XposedHelpers.findClass(
                    "com.android.server.wm.WindowManagerService", serverCl);
            XC_MethodHook strip = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!secureOn()) return;
                    for (Object a : p.args) {
                        if (a instanceof WindowManager.LayoutParams) {
                            stripSecure((WindowManager.LayoutParams) a);
                        }
                    }
                }
            };
            Set<?> a = XposedBridge.hookAllMethods(wms, "addWindow", strip);
            Set<?> b = XposedBridge.hookAllMethods(wms, "relayoutWindow", strip);
            log("WMS secure strip hooks: addWindow=" + a.size()
                    + " relayoutWindow=" + b.size());
        } catch (Throwable t) {
            log("secure: WMS hook failed: " + t);
        }

        secureInstalled = true;
    }

    /** “截取受保护内容”开关是否开启（system_server 内直接读 Settings.Global）。 */
    private static boolean secureOn() {
        Context c = sysContext;
        if (c == null) c = resolveSystemContext();
        return c != null && Prefs.drmCapture(c);
    }

    /** 剥离 LayoutParams 上的窗口级与私有 secure 标志。 */
    private static void stripSecure(WindowManager.LayoutParams lp) {
        try {
            lp.flags &= ~FLAG_SECURE;
        } catch (Throwable ignored) {}
        try {
            Field pf = WindowManager.LayoutParams.class.getDeclaredField("privateFlags");
            pf.setAccessible(true);
            pf.setInt(lp, pf.getInt(lp) & ~PRIVATE_FLAG_SECURE);
        } catch (Throwable ignored) {}
    }

    private static synchronized void installGesture(Context c) {
        if (gestureInstalled) return;
        gestureInstalled = true;
        GestureWatcher.install(c);
    }

    /** 在指定类（基类或 ROM 子类）上 hook 截屏入口：按键开关开启时取消系统截屏并自有抓拍。 */
    private static void hookScreenshotHelper(Class<?> cls) {
        if (!hookedHelperClasses.add(cls)) return;
        XC_MethodHook replace = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Context c = sysContext;
                boolean keysOn = c == null ? true : Prefs.keys(c);
                long now = System.currentTimeMillis();
                boolean fire = (now - lastTrigger) > 300;
                lastTrigger = now;
                if (!keysOn) {
                    // 按键截屏已关闭：放行系统默认截屏，不拦截
                    return;
                }
                p.setResult(null); // 取消系统截屏
                log("intercepted " + p.method.getDeclaringClass().getSimpleName()
                        + "." + p.method.getName() + " fire=" + fire);
                if (fire) {
                    if (c != null) vibrate(c); // 立刻震动反馈
                    OWN.postDelayed(HookLogic::fire, 0);
                }
            }
        };
        Set<?> a = XposedBridge.hookAllMethods(cls, "takeScreenshot", replace);
        Set<?> b = XposedBridge.hookAllMethods(cls, "takeScreenshotInternal", replace);
        log("hook ScreenshotHelper on " + cls.getName()
                + " takeScreenshot=" + a.size() + " takeScreenshotInternal=" + b.size());
    }

    /** 供三指手势调用。 */
    public static void requestShot() { fire(); }

    /** 触发截图后立刻短震动反馈（40ms）。system_server 为 system uid，自带 VIBRATE 权限。 */
    public static void vibrate(Context c) {
        vibrate(c, 40L, VibrationEffect.DEFAULT_AMPLITUDE);
    }

    /** 三指下滑专用震动：强度更高，弥补没有按键震动叠加的体感差异。 */
    public static void vibrateStrong(Context c) {
        vibrate(c, 80L, 220);
    }

    /** 通用震动入口，可自定义时长与振幅。 */
    public static void vibrate(Context c, long duration, int amplitude) {
        try {
            Context use = sysContext != null ? sysContext : c;
            if (use == null) return;
            Vibrator v = use.getSystemService(Vibrator.class);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createOneShot(duration, amplitude));
            }
        } catch (Throwable t) {
            log("vibrate failed: " + t);
        }
    }

    /** 兜底获取系统 Context。 */
    private static Context resolveSystemContext() {
        try {
            ClassLoader cl = serverCl != null ? serverCl : ClassLoader.getSystemClassLoader();
            Class<?> at = Class.forName("android.app.ActivityThread", false, cl);
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Object ctx = at.getMethod("getSystemContext").invoke(thread);
            if (ctx instanceof Context) {
                log("system context resolved via ActivityThread");
                return (Context) ctx;
            }
        } catch (Throwable t) {
            log("resolveSystemContext failed: " + t);
        }
        return null;
    }

    private static void fire() {
        // 后台线程执行，避免阻塞 system_server 主线程；按速度分层兜底
        new Thread(() -> {
            if (surfaceShot()) return;          // 最快：SurfaceControl 直拍 + 共享内存
            if (rootShotPersistent()) return;   // 其次：常驻 root shell + screencap
            serviceFallback();                  // 最后：拉起 App 服务
        }).start();
    }

    /** 最快路径：system_server 内直接调 SurfaceControl 截图（毫秒级、无需 root），再经共享内存传给 App。 */
    private static boolean surfaceShot() {
        Context c = sysContext != null ? sysContext : resolveSystemContext();
        if (c == null) return false;
        sysContext = c;
        try {
            Point size = new Point();
            c.getDisplay().getRealSize(size);
            int w = size.x, h = size.y;
            Class<?> sc = Class.forName("android.view.SurfaceControl");
            Bitmap bmp = tryScreenshot(sc, w, h);
            if (bmp == null) {
                log("surface shot: no matching SurfaceControl.screenshot signature");
                return false;
            }
            return deliverViaShm(c, bmp);
        } catch (Throwable t) {
            log("surface shot failed: " + t);
            return false;
        }
    }

    /**
     * 枚举 SurfaceControl 所有名为 screenshot 的静态方法，按参数类型启发式构造实参并依次尝试，
     * 任何一个返回非空 Bitmap 即采用。自适应不同 Android 版本 / ROM 的签名差异
     * （旧固定签名在 Android 14 crDroid 上已全部 NoSuchMethod）。
     */
    private static Bitmap tryScreenshot(Class<?> sc, int w, int h) {
        Method[] all;
        try { all = sc.getDeclaredMethods(); } catch (Throwable t) { return null; }
        java.util.List<String> seen = new java.util.ArrayList<>();
        for (Method m : all) {
            if (!"screenshot".equals(m.getName())) continue;
            Class<?> rt = m.getReturnType();
            boolean retBitmap = rt == Bitmap.class;
            boolean retShb = rt.getName().contains("ScreenshotHardwareBuffer");
            if (!retBitmap && !retShb) continue;
            seen.add(rt.getSimpleName() + Arrays.toString(m.getParameterTypes()));
            Object out;
            try {
                Object[] args = buildScreenshotArgs(m.getParameterTypes(), w, h);
                if (args == null) continue; // 含无法安全构造的参数（DisplayCaptureArgs/IBinder 等）
                out = m.invoke(null, args);
            } catch (Throwable t) {
                continue;
            }
            if (out == null) continue;
            Bitmap bmp;
            if (retBitmap) {
                bmp = (Bitmap) out;
            } else {
                try {
                    Method asBmp = out.getClass().getMethod("asBitmap");
                    bmp = (Bitmap) asBmp.invoke(out);
                } catch (Throwable t) { continue; }
            }
            bmp = toSoftwareBitmap(bmp);
            if (bmp != null) {
                log("surface shot via " + rt.getSimpleName()
                        + ".screenshot" + Arrays.toString(m.getParameterTypes()));
                return bmp;
            }
        }
        if (!seen.isEmpty()) log("screenshot signatures seen: " + seen);
        return null;
    }

    /** 按参数类型启发式构造实参；int 依次取 width,height,0,0…；无法构造则返回 null。 */
    private static Object[] buildScreenshotArgs(Class<?>[] pts, int w, int h) {
        Object[] args = new Object[pts.length];
        int[] intQueue = { w, h, 0, 0, 0, 0 };
        int intIdx = 0;
        for (int i = 0; i < pts.length; i++) {
            Class<?> t = pts[i];
            if (t == Rect.class) args[i] = new Rect(0, 0, w, h);
            else if (t == int.class) args[i] = intIdx < intQueue.length ? intQueue[intIdx++] : 0;
            else if (t == boolean.class) args[i] = Boolean.FALSE;
            else if (t == long.class) args[i] = 0L;
            else if (t == float.class) args[i] = 0f;
            else return null;
        }
        return args;
    }

    /** SurfaceControl 可能返回 HARDWARE bitmap（无法 copyPixelsToBuffer），统一转成 ARGB_8888。 */
    private static Bitmap toSoftwareBitmap(Bitmap bmp) {
        if (bmp == null) return null;
        try {
            // 不用 isHardware()/isSoftware()（会被 compileOnly 的旧 framework stub 遮蔽），
            // 用最老的 getConfig() 判定 HARDWARE。
            if (bmp.getConfig() == Bitmap.Config.HARDWARE) {
                Bitmap sw = bmp.copy(Bitmap.Config.ARGB_8888, false);
                if (sw != null) return sw;
            }
        } catch (Throwable t) {
            try { return bmp.copy(Bitmap.Config.ARGB_8888, false); } catch (Throwable ignored) {}
        }
        return bmp;
    }

    /** 把 Bitmap 放入共享内存（ashmem，无 Binder 大小限制），启动 App 悬浮预览。 */
    private static boolean deliverViaShm(Context c, Bitmap bmp) {
        SharedMemory shm = null;
        try {
            int w = bmp.getWidth(), h = bmp.getHeight();
            int bytes = bmp.getByteCount();
            int total = bytes + 12;
            shm = SharedMemory.create("screenshotx-shot", total);
            ByteBuffer bb = shm.map(OsConstants.PROT_READ | OsConstants.PROT_WRITE, 0, total);
            bb.putInt(w); bb.putInt(h); bb.putInt(bytes);
            bmp.copyPixelsToBuffer(bb);
            SharedMemory.unmap(bb);
            shm.setProtect(OsConstants.PROT_READ);
            Intent i = new Intent();
            i.setClassName("io.github.gjr787878.screenshotx",
                    "io.github.gjr787878.screenshotx.FloatingPreviewService");
            i.putExtra("shm", shm); // SharedMemory 为 Parcelable，Binder 自动传 ashmem fd
            c.startService(i);
            log("surface shot delivered via shm " + w + "x" + h);
            return true;
        } catch (Throwable t) {
            log("shm deliver failed: " + t);
            return false;
        } finally {
            if (shm != null) { try { shm.close(); } catch (Throwable ignored) {} }
        }
    }

    /** 最后兜底：拉起 App 的 ScreenshotService。 */
    private static void serviceFallback() {
        Context c = sysContext;
        if (c == null) c = resolveSystemContext();
        if (c == null) {
            log("fire skipped, no context");
            return;
        }
        sysContext = c;
        try {
            Intent svc = new Intent();
            svc.setClassName("io.github.gjr787878.screenshotx",
                    "io.github.gjr787878.screenshotx.ScreenshotService");
            svc.setAction(ScreenshotService.ACTION_SHOOT);
            c.startService(svc);
            log("fire sent (service fallback)");
        } catch (Throwable t) {
            log("fire failed: " + t);
        }
    }

    private static void drainStatic(InputStream is) {
        new Thread(() -> {
            try {
                byte[] b = new byte[1024];
                while (is.read(b) > 0) { }
            } catch (Throwable ignored) { }
        }).start();
    }

    /** 建立（或复用）system_server 内常驻的 Root shell。 */
    private static synchronized boolean ensureDirectShell() {
        if (rootShellProc != null && rootShellOs != null) return true;
        try {
            rootShellProc = Runtime.getRuntime().exec("su");
            rootShellOs = new DataOutputStream(rootShellProc.getOutputStream());
            drainStatic(rootShellProc.getInputStream());
            drainStatic(rootShellProc.getErrorStream());
            rootShellOs.writeBytes("appops set io.github.gjr787878.screenshotx"
                    + " SYSTEM_ALERT_WINDOW allow\n");
            rootShellOs.flush();
            return true;
        } catch (Throwable t) {
            rootShellProc = null;
            rootShellOs = null;
            return false;
        }
    }

    /** 通过常驻 Root shell 抓拍。 */
    private static boolean rootShotPersistent() {
        final String shot = "/data/local/tmp/screenshotx_shot.png";
        if (!ensureDirectShell()) {
            log("direct shell unavailable");
            return false;
        }
        try {
            DataOutputStream os;
            synchronized (HookLogic.class) {
                os = rootShellOs;
            }
            if (os == null) return false;
            os.writeBytes("am startservice -n io.github.gjr787878.screenshotx/.ScreenshotService &\n");
            os.writeBytes("rm -f " + shot + "\n");
            os.writeBytes("screencap -p " + shot + "\n");
            os.writeBytes("chmod 666 " + shot + "\n");
            os.writeBytes("am startservice -n io.github.gjr787878.screenshotx/.FloatingPreviewService"
                    + " --es path " + shot + "\n");
            os.flush();
            log("direct shell shot dispatched");
            return true;
        } catch (Throwable t) {
            log("direct shell shot failed: " + t);
            synchronized (HookLogic.class) {
                rootShellProc = null;
                rootShellOs = null;
            }
            return false;
        }
    }

    /** 触发录屏（由 KeyInterceptor 调用）。 */
    public static void startRecording() {
        Context c = sysContext;
        if (c == null) c = resolveSystemContext();
        if (c == null) {
            log("startRecording skipped, no context");
            return;
        }
        sysContext = c;
        try {
            // 录制系统声音（REMOTE_SUBMIX 需要 system 权限，只能在 system_server 录）
            if (Prefs.recAudio(c)) {
                SystemAudioRecorder.start(c);
            }
            Intent svc = new Intent();
            svc.setClassName("io.github.gjr787878.screenshotx",
                    "io.github.gjr787878.screenshotx.RecordService");
            svc.setAction(RecordService.ACTION_START);
            c.startForegroundService(svc);
            log("recording start sent");
            vibrate(c, 60L, 200);
        } catch (Throwable t) {
            log("startRecording failed: " + t);
        }
    }

    /** 结束录屏（由 KeyInterceptor 调用）。 */
    public static void stopRecording() {
        Context c = sysContext;
        if (c == null) c = resolveSystemContext();
        if (c == null) return;
        try {
            // 电源键停止路径：先停音频录制（悬浮窗停止路径由 RecordService 发广播触发）
            SystemAudioRecorder.stop();
            Intent svc = new Intent();
            svc.setClassName("io.github.gjr787878.screenshotx",
                    "io.github.gjr787878.screenshotx.RecordService");
            svc.setAction(RecordService.ACTION_STOP);
            c.startService(svc);
            log("recording stop sent");
        } catch (Throwable t) {
            log("stopRecording failed: " + t);
        }
    }

    /** system_server 内录制系统声音（REMOTE_SUBMIX）。app 进程无 CAPTURE_AUDIO_OUTPUT
     *  权限，只能在 system_server 录。PCM 写入 /data/local/tmp/sx_audio.pcm，
     *  由 RecordService 编码 AAC 后与 screenrecord 视频合并。 */
    private static class SystemAudioRecorder {
        private static volatile AudioRecord record;
        private static volatile boolean running = false;
        private static volatile Thread writeThread;
        private static final String PCM_PATH = "/data/local/tmp/sx_audio.pcm";

        static synchronized void start(Context ctx) {
            if (running) return;
            try {
                int sampleRate = 44100;
                int minBuf = AudioRecord.getMinBufferSize(sampleRate,
                        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                int bufSize = Math.max(minBuf * 2, 8192);
                AudioRecord ar = new AudioRecord(MediaRecorder.AudioSource.REMOTE_SUBMIX,
                        sampleRate, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, bufSize);
                if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
                    try { ar.release(); } catch (Throwable ignored) {}
                    log("audio: REMOTE_SUBMIX init failed");
                    return;
                }
                record = ar;
                ar.startRecording();
                running = true;
                final int bs = bufSize;
                writeThread = new Thread(() -> {
                    byte[] buf = new byte[bs];
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(PCM_PATH)) {
                        while (running) {
                            int n = record.read(buf, 0, buf.length);
                            if (n > 0) fos.write(buf, 0, n);
                            else if (n < 0) break;
                        }
                        fos.flush();
                    } catch (Throwable t) {
                        log("audio: write failed " + t);
                    }
                }, "sx-audio-rec");
                writeThread.start();
                log("audio: system audio recording started (REMOTE_SUBMIX)");
            } catch (Throwable t) {
                log("audio: start failed " + t);
                try { if (record != null) record.release(); } catch (Throwable ignored) {}
                record = null;
                running = false;
            }
        }

        static synchronized void stop() {
            if (!running && record == null) return;
            running = false;
            try { if (record != null) record.stop(); } catch (Throwable ignored) {}
            try { if (writeThread != null) writeThread.join(2000); } catch (Throwable ignored) {}
            try { if (record != null) record.release(); } catch (Throwable ignored) {}
            record = null;
            writeThread = null;
            // 让 app 进程可读音频文件
            runSu("chmod 666 " + PCM_PATH);
            log("audio: system audio recording stopped");
        }

        private static void runSu(String cmd) {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes(cmd + "\n");
                os.writeBytes("exit\n");
                os.flush();
                p.waitFor();
                p.destroy();
            } catch (Throwable ignored) {}
        }
    }
}
