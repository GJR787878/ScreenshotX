package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

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
        root.setPadding(dp(24), dp(60), dp(24), dp(32));
        root.setBackgroundColor(0xFF000000);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        // 三语切换按钮：中 → En → Ru 循环
        Button langBtn = new Button(this);
        updateLangBtn(langBtn);
        langBtn.setOnClickListener(v -> cycleLang());
        root.addView(langBtn);

        rootStatus = new TextView(this);
        rootStatus.setText(R.string.root_checking);
        rootStatus.setTextColor(0xFFCCCCCC);
        rootStatus.setTextSize(15);
        rootStatus.setPadding(0, dp(12), 0, dp(16));
        root.addView(rootStatus);
        requestRoot();
        try { startService(new Intent(this, ScreenshotService.class)); } catch (Throwable ignored) {}

        // 触发方式分组
        TextView trigHeader = new TextView(this);
        trigHeader.setText(R.string.trigger_header);
        trigHeader.setTextColor(0xFFFFFFFF);
        trigHeader.setTextSize(17);
        trigHeader.setPadding(0, dp(8), 0, dp(8));
        root.addView(trigHeader);

        Switch keySw = new Switch(this);
        keySw.setText(R.string.trigger_keys);
        keySw.setTextColor(0xFFEEEEEE);
        keySw.setPadding(0, dp(4), 0, dp(4));
        keySw.setChecked(Prefs.keys(this));
        keySw.setOnCheckedChangeListener((v, checked) ->
                new Thread(() -> Prefs.putGlobal(Prefs.K_KEYS, checked ? "1" : "0")).start());
        root.addView(keySw);

        Switch threeSw = new Switch(this);
        threeSw.setText(R.string.trigger_three);
        threeSw.setTextColor(0xFFEEEEEE);
        threeSw.setPadding(0, dp(4), 0, dp(4));
        threeSw.setChecked(Prefs.threeFinger(this));
        threeSw.setOnCheckedChangeListener((v, checked) ->
                new Thread(() -> Prefs.putGlobal(Prefs.K_THREE, checked ? "1" : "0")).start());
        root.addView(threeSw);

        TextView steps = new TextView(this);
        steps.setText(R.string.usage);
        steps.setTextColor(0xFFCCCCCC);
        steps.setTextSize(15);
        steps.setLineSpacing(dp(4), 1f);
        steps.setPadding(0, dp(20), 0, dp(20));
        root.addView(steps);

        Button test = new Button(this);
        test.setText(R.string.test_btn);
        test.setOnClickListener(v -> {
            Intent svc = new Intent(this, ScreenshotService.class);
            svc.setAction(ScreenshotService.ACTION_SHOOT);
            startService(svc);
            Toast.makeText(this, R.string.test_hint, Toast.LENGTH_LONG).show();
        });
        root.addView(test);

        Button export = new Button(this);
        export.setText(R.string.export_btn);
        export.setOnClickListener(v -> exportDiag());
        root.addView(export);

        setContentView(root);
    }

    private void updateLangBtn(Button btn) {
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
