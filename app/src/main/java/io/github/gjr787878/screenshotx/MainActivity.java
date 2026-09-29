package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private LinearLayout shotPanel, recPanel;
    private GlassCapsuleButton shotNavBtn, recNavBtn;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // 整体垂直布局
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(16));
        root.setBackgroundColor(0xFF000000);

        // 标题
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        // 语言按钮
        final GlassCapsuleButton langBtn = new GlassCapsuleButton(this);
        updateLangBtn(langBtn);
        langBtn.setOnClickListener(v -> cycleLang());
        root.addView(langBtn, marginLp(0, dp(8), 0, dp(4)));

        // Root 状态
        rootStatus = new TextView(this);
        rootStatus.setText(R.string.root_checking);
        rootStatus.setTextColor(0xFFCCCCCC);
        rootStatus.setTextSize(15);
        rootStatus.setPadding(0, dp(12), 0, dp(12));
        root.addView(rootStatus);
        requestRoot();
        try { startForegroundService(new Intent(this, ScreenshotService.class)); } catch (Throwable ignored) {}

        // ===== 内容区域：FrameLayout，切换截屏/录屏面板 =====
        FrameLayout contentFrame = new FrameLayout(this);
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        frameLp.setMargins(0, dp(8), 0, dp(8));

        // --- 截屏设置面板 ---
        shotPanel = buildShotPanel();
        contentFrame.addView(shotPanel);

        // --- 录屏设置面板 ---
        recPanel = buildRecPanel();
        recPanel.setVisibility(android.view.View.GONE);
        contentFrame.addView(recPanel);

        root.addView(contentFrame, frameLp);

        // ===== 底部导航栏：两个按钮切换 =====
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setPadding(0, dp(8), 0, 0);

        shotNavBtn = new GlassCapsuleButton(this);
        shotNavBtn.setText("截屏设置");
        shotNavBtn.setGlassSelected(true);
        shotNavBtn.setOnClickListener(v -> switchPanel(true));
        navBar.addView(shotNavBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        recNavBtn = new GlassCapsuleButton(this);
        recNavBtn.setText("录屏设置");
        recNavBtn.setGlassSelected(false);
        recNavBtn.setOnClickListener(v -> switchPanel(false));
        navBar.addView(recNavBtn, new LinearLayout.LayoutParams(0, -2, 1f));

        root.addView(navBar, marginLp(0, dp(8), 0, 0));

        setContentView(root);
    }

    private void switchPanel(boolean showShot) {
        shotPanel.setVisibility(showShot ? android.view.View.VISIBLE : android.view.View.GONE);
        recPanel.setVisibility(showShot ? android.view.View.GONE : android.view.View.VISIBLE);
        shotNavBtn.setGlassSelected(showShot);
        recNavBtn.setGlassSelected(!showShot);
    }

    // 截屏设置面板（原有的所有内容）
    private LinearLayout buildShotPanel() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF000000);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(2), dp(8), dp(2), dp(8));

        // 触发方式标题
        TextView trigHeader = new TextView(this);
        trigHeader.setText(R.string.trigger_header);
        trigHeader.setTextColor(0xFFFFFFFF);
        trigHeader.setTextSize(17);
        trigHeader.setPadding(0, dp(8), 0, dp(8));
        inner.addView(trigHeader);

        // 按键截屏开关
        final GlassCapsuleButton keySw = new GlassCapsuleButton(this);
        keySw.setText(R.string.trigger_keys);
        keySw.setGlassSelected(Prefs.keys(this));
        keySw.setOnClickListener(v -> {
            boolean sel = !keySw.isGlassSelected();
            keySw.setGlassSelected(sel);
            final String val = sel ? "1" : "0";
            new Thread(() -> Prefs.putGlobal(Prefs.K_KEYS, val)).start();
        });
        inner.addView(keySw, marginLp(0, dp(4), 0, dp(4)));

        // 三指下滑开关
        final GlassCapsuleButton threeSw = new GlassCapsuleButton(this);
        threeSw.setText(R.string.trigger_three);
        threeSw.setGlassSelected(Prefs.threeFinger(this));
        threeSw.setOnClickListener(v -> {
            boolean sel = !threeSw.isGlassSelected();
            threeSw.setGlassSelected(sel);
            final String val = sel ? "1" : "0";
            new Thread(() -> Prefs.putGlobal(Prefs.K_THREE, val)).start();
        });
        inner.addView(threeSw, marginLp(0, dp(4), 0, dp(4)));

        // DRM 开关
        final GlassCapsuleButton drmSw = new GlassCapsuleButton(this);
        drmSw.setText(R.string.trigger_drm);
        drmSw.setGlassSelected(Prefs.drmCapture(this));
        drmSw.setOnClickListener(v -> {
            boolean sel = !drmSw.isGlassSelected();
            drmSw.setGlassSelected(sel);
            final String val = sel ? "1" : "0";
            new Thread(() -> Prefs.putGlobal(Prefs.K_DRM, val)).start();
        });
        inner.addView(drmSw, marginLp(0, dp(4), 0, dp(2)));

        // DRM 说明
        TextView drmNote = new TextView(this);
        drmNote.setText(R.string.drm_note);
        drmNote.setTextColor(0xFF999999);
        drmNote.setTextSize(12);
        drmNote.setLineSpacing(dp(2), 1f);
        drmNote.setPadding(dp(2), 0, dp(2), dp(8));
        inner.addView(drmNote);

        // 使用说明
        TextView steps = new TextView(this);
        steps.setText(R.string.usage);
        steps.setTextColor(0xFFCCCCCC);
        steps.setTextSize(15);
        steps.setLineSpacing(dp(4), 1f);
        steps.setPadding(0, dp(20), 0, dp(20));
        inner.addView(steps);

        // 测试按钮
        final GlassCapsuleButton test = new GlassCapsuleButton(this);
        test.setText(R.string.test_btn);
        test.setOnClickListener(v -> {
            Intent svc = new Intent(this, ScreenshotService.class);
            svc.setAction(ScreenshotService.ACTION_SHOOT);
            startService(svc);
            Toast.makeText(this, R.string.test_hint, Toast.LENGTH_LONG).show();
        });
        inner.addView(test, marginLp(0, dp(4), 0, dp(4)));

        // 导出日志按钮
        final GlassCapsuleButton export = new GlassCapsuleButton(this);
        export.setText(R.string.export_btn);
        export.setOnClickListener(v -> exportDiag());
        inner.addView(export, marginLp(0, dp(4), 0, 0));

        sv.addView(inner);
        ll.addView(sv);
        return ll;
    }

    // 录屏设置面板
    private LinearLayout buildRecPanel() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF000000);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(2), dp(8), dp(2), dp(8));

        // 标题
        TextView header = new TextView(this);
        header.setText("录屏设置");
        header.setTextColor(0xFFFFFFFF);
        header.setTextSize(17);
        header.setPadding(0, dp(8), 0, dp(8));
        inner.addView(header);

        // 操作说明
        TextView info = new TextView(this);
        info.setText("电源键 + 音量上键 = 开始录屏\n录屏中单击电源键 = 结束录屏\n小胶囊可拖动，单击结束录屏");
        info.setTextColor(0xFFCCCCCC);
        info.setTextSize(14);
        info.setLineSpacing(dp(4), 1f);
        info.setPadding(0, dp(8), 0, dp(16));
        inner.addView(info);

        // 码率选择标题
        TextView bitrateTitle = new TextView(this);
        bitrateTitle.setText("录屏码率");
        bitrateTitle.setTextColor(0xFFFFFFFF);
        bitrateTitle.setTextSize(15);
        bitrateTitle.setPadding(0, dp(8), 0, dp(4));
        inner.addView(bitrateTitle);

        int curBitrate = Prefs.recBitrate(this);

        // 低码率 1Mbps
        final GlassCapsuleButton lowBtn = new GlassCapsuleButton(this);
        lowBtn.setText("低（1Mbps，省电不发热）");
        lowBtn.setGlassSelected(curBitrate == 1000000);
        lowBtn.setOnClickListener(v -> setBitrate(1000000, lowBtn));
        inner.addView(lowBtn, marginLp(0, dp(4), 0, dp(4)));

        // 中码率 2Mbps
        final GlassCapsuleButton midBtn = new GlassCapsuleButton(this);
        midBtn.setText("中（2Mbps，平衡）");
        midBtn.setGlassSelected(curBitrate == 2000000);
        midBtn.setOnClickListener(v -> setBitrate(2000000, midBtn));
        inner.addView(midBtn, marginLp(0, dp(4), 0, dp(4)));

        // 高码率 4Mbps
        final GlassCapsuleButton highBtn = new GlassCapsuleButton(this);
        highBtn.setText("高（4Mbps，清晰但发热）");
        highBtn.setGlassSelected(curBitrate == 4000000);
        highBtn.setOnClickListener(v -> setBitrate(4000000, highBtn));
        inner.addView(highBtn, marginLp(0, dp(4), 0, dp(4)));

        // 说明
        TextView note = new TextView(this);
        note.setText("码率越高视频越清晰，但手机发热和耗电也越明显。建议日常用中码率。");
        note.setTextColor(0xFF999999);
        note.setTextSize(12);
        note.setLineSpacing(dp(2), 1f);
        note.setPadding(dp(2), dp(12), dp(2), dp(8));
        inner.addView(note);

        sv.addView(inner);
        ll.addView(sv);
        return ll;
    }

    private void setBitrate(int bitrate, GlassCapsuleButton selectedBtn) {
        // 更新所有按钮状态
        recPanel.findViewWithTag("bitrate");
        // 简单方式：遍历子 View 更新
        ((LinearLayout)((ScrollView)recPanel.getChildAt(0)).getChildAt(0)).setTag("bitrate");
        // 直接更新三个按钮
        selectedBtn.setGlassSelected(true);
        // 其他按钮取消选中（通过遍历）
        LinearLayout inner = (LinearLayout)((ScrollView)recPanel.getChildAt(0)).getChildAt(0);
        for (int i = 0; i < inner.getChildCount(); i++) {
            android.view.View child = inner.getChildAt(i);
            if (child instanceof GlassCapsuleButton && child != selectedBtn) {
                ((GlassCapsuleButton) child).setGlassSelected(false);
            }
        }
        final String val = String.valueOf(bitrate);
        new Thread(() -> Prefs.putGlobal(Prefs.K_REC_BITRATE, val)).start();
        Toast.makeText(this, "录屏码率已设置", Toast.LENGTH_SHORT).show();
    }

    private LinearLayout.LayoutParams marginLp(int l, int t, int r, int btm) {
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
