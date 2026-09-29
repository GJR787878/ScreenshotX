package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gjr.glassbutton.GlassCapsuleButton;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {

    private TextView rootStatus;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // §3.6 根布局 padding：顶48、左右24、底32
        root.setPadding(dp(24), dp(48), dp(24), dp(32));
        root.setBackgroundColor(0xFF000000);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        // 三语切换按钮
        final GlassCapsuleButton langBtn = new GlassCapsuleButton(this);
        updateLangBtn(langBtn);
        langBtn.setOnClickListener(v -> cycleLang());
        root.addView(langBtn, marginLp(0, dp(8), 0, dp(4)));

        rootStatus = new TextView(this);
        rootStatus.setText(R.string.root_checking);
        rootStatus.setTextColor(0xFFCCCCCC);
        rootStatus.setTextSize(15);
        rootStatus.setPadding(0, dp(12), 0, dp(12));
        root.addView(rootStatus);
        requestRoot();
        try { startForegroundService(new Intent(this, ScreenshotService.class)); } catch (Throwable ignored) {}

        // 触发方式
        TextView trigHeader = new TextView(this);
        trigHeader.setText(R.string.trigger_header);
        trigHeader.setTextColor(0xFFFFFFFF);
        trigHeader.setTextSize(17);
        trigHeader.setPadding(0, dp(8), 0, dp(8));
        root.addView(trigHeader);

        // §3.6 所有开关必须用 GlassCapsuleButton，禁止原生 Switch
        final GlassCapsuleButton keySw = new GlassCapsuleButton(this);
        keySw.setText(R.string.trigger_keys);
        keySw.setGlassSelected(Prefs.keys(this));
        keySw.setOnClickListener(v -> {
            boolean sel = !keySw.isGlassSelected();
            keySw.setGlassSelected(sel);
            final String val = sel ? "1" : "0";
            new Thread(() -> Prefs.putGlobal(Prefs.K_KEYS, val)).start();
        });
        root.addView(keySw, marginLp(0, dp(4), 0, dp(4)));

        final GlassCapsuleButton threeSw = new GlassCapsuleButton(this);
        threeSw.setText(R.string.trigger_three);
        threeSw.setGlassSelected(Prefs.threeFinger(this));
        threeSw.setOnClickListener(v -> {
            boolean sel = !threeSw.isGlassSelected();
            threeSw.setGlassSelected(sel);
            final String val = sel ? "1" : "0";
            new Thread(() -> Prefs.putGlobal(Prefs.K_THREE, val)).start();
        });
        root.addView(threeSw, marginLp(0, dp(4), 0, dp(4)));

        // 截取受保护 / DRM 内容开关（绕过 FLAG_SECURE 黑屏）
        final GlassCapsuleButton drmSw = new GlassCapsuleButton(this);
        drmSw.setText(R.string.trigger_drm);
        drmSw.setGlassSelected(Prefs.drmCapture(this));
        drmSw.setOnClickListener(v -> {
            boolean sel = !drmSw.isGlassSelected();
            drmSw.setGlassSelected(sel);
            final String val = sel ? "1" : "0";
            new Thread(() -> Prefs.putGlobal(Prefs.K_DRM, val)).start();
        });
        root.addView(drmSw, marginLp(0, dp(4), 0, dp(2)));

        // DRM 限制说明（小字）
        TextView drmNote = new TextView(this);
        drmNote.setText(R.string.drm_note);
        drmNote.setTextColor(0xFF999999);
        drmNote.setTextSize(12);
        drmNote.setLineSpacing(dp(2), 1f);
        drmNote.setPadding(dp(2), 0, dp(2), dp(8));
        root.addView(drmNote);

        TextView steps = new TextView(this);
        steps.setText(R.string.usage);
        steps.setTextColor(0xFFCCCCCC);
        steps.setTextSize(15);
        steps.setLineSpacing(dp(4), 1f);
        steps.setPadding(0, dp(20), 0, dp(20));
        root.addView(steps);

        final GlassCapsuleButton test = new GlassCapsuleButton(this);
        test.setText(R.string.test_btn);
        test.setOnClickListener(v -> {
            Intent svc = new Intent(this, ScreenshotService.class);
            svc.setAction(ScreenshotService.ACTION_SHOOT);
            startService(svc);
            Toast.makeText(this, R.string.test_hint, Toast.LENGTH_LONG).show();
        });
        root.addView(test, marginLp(0, dp(4), 0, dp(4)));

        final GlassCapsuleButton export = new GlassCapsuleButton(this);
        export.setText(R.string.export_btn);
        export.setOnClickListener(v -> exportDiag());
        root.addView(export, marginLp(0, dp(4), 0, 0));

        setContentView(root);
    }

    private LinearLayout.LayoutParams marginLp(int l, int t, int r, int btm) {
        // 宽度统一为 MATCH_PARENT，所有按钮等长；GlassCapsuleButton 内部文字居中
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(l, t, r, btm);
        return lp;
    }

    private void updateLangBtn(GlassCapsuleButton btn) {
        String code = Prefs.lang(this);
        int nameId = "en".equals(code) ? R.string.lang_en
                : "ru".equals(code) ? R.string.lang_ru : R.string.lang_zh;
        btn.setText(getString(R.string.language_btn) + ": " + getString(nameId));
    }

    private void cycleLang() {
        String cur = Prefs.lang(this);
        final String next = "zh".equals(cur) ? "en" : "en".equals(cur) ? "ru" : "zh";
        new Thread(() -> {
            Prefs.putGlobal(Prefs.K_LANG, next);
            runOnUiThread(this::recreate);
        }).start();
    }

    private void requestRoot() {
        new Thread(() -> {
            boolean ok = false;
            Process p = null;
            try {
                p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("id\n");
                os.writeBytes("exit\n");
                os.flush();
                ok = p.waitFor() == 0;
            } catch (Throwable t) {
                ok = false;
            } finally {
                if (p != null) p.destroy();
            }
            final boolean granted = ok;
            runOnUiThread(() -> rootStatus.setText(granted
                    ? R.string.root_granted : R.string.root_denied));
        }).start();
    }

    private void exportDiag() {
        new Thread(() -> {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("cat /data/system/screenshotx_diag.log\n");
                os.writeBytes("exit\n");
                os.flush();
                InputStream is = p.getInputStream();
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] tmp = new byte[8192];
                int n;
                while ((n = is.read(tmp)) > 0) buf.write(tmp, 0, n);
                p.waitFor();
                byte[] logData = buf.toByteArray();

                ByteArrayOutputStream zipBuf = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(zipBuf);
                zos.putNextEntry(new ZipEntry("screenshotx_diag.log"));
                zos.write(logData);
                zos.closeEntry();
                zos.close();

                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, "111.zip");
                values.put(MediaStore.Downloads.MIME_TYPE, "application/zip");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                OutputStream out = getContentResolver().openOutputStream(uri);
                out.write(zipBuf.toByteArray());
                out.close();

                runOnUiThread(() -> Toast.makeText(this,
                        R.string.export_ok, Toast.LENGTH_LONG).show());
            } catch (Throwable t) {
                runOnUiThread(() -> Toast.makeText(this,
                        R.string.export_fail, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
