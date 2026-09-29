package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.provider.Settings;

import java.io.DataOutputStream;

/**
 * 全局设置：统一存放在 Settings.Global（App 与 system_server 均可直接读）。
 * 普通 App 无 WRITE_SECURE_SETTINGS，写入通过 Root 执行 `settings put global`。
 */
public class Prefs {
    public static final String K_KEYS = "screenshotx_keys";        // 按键截屏
    public static final String K_THREE = "screenshotx_threefinger";// 三指下滑
    public static final String K_LANG = "screenshotx_lang";        // zh / en / ru

    public static boolean keys(Context c) {
        try { return Settings.Global.getInt(c.getContentResolver(), K_KEYS, 1) == 1; }
        catch (Throwable t) { return true; }
    }

    public static boolean threeFinger(Context c) {
        try { return Settings.Global.getInt(c.getContentResolver(), K_THREE, 0) == 1; }
        catch (Throwable t) { return false; }
    }

    /** 语言代码，默认简中。 */
    public static String lang(Context c) {
        try {
            String v = Settings.Global.getString(c.getContentResolver(), K_LANG);
            if (v == null || v.length() == 0) return "zh";
            return v;
        } catch (Throwable t) { return "zh"; }
    }

    /** App 侧：通过 Root 写 Settings.Global。同步执行，返回是否成功。 */
    public static boolean putGlobal(String key, String value) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("settings put global " + key + " " + value + "\n");
            os.writeBytes("exit\n");
            os.flush();
            return p.waitFor() == 0;
        } catch (Throwable t) {
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }
}
