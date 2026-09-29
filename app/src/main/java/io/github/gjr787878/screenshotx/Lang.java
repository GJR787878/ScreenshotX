package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** 语言包装：按全局设置把 Context 的 locale 强制为 中/英/俄。 */
public class Lang {

    public static Locale localeOf(String code) {
        if ("en".equals(code)) return Locale.ENGLISH;
        if ("ru".equals(code)) return new Locale("ru");
        return Locale.SIMPLIFIED_CHINESE;
    }

    public static Context wrap(Context base) {
        Locale loc = localeOf(Prefs.lang(base));
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(loc);
        return base.createConfigurationContext(cfg);
    }
}
