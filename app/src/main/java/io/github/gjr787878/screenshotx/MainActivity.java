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

import android.view.Gravity;

import com.gjr.glassbutton.GlassCapsuleButton;
import com.gjr.glassbutton.GlassNavBar;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {

    private TextView rootStatus;
    private LinearLayout shotPanel, recPanel;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // §3.4 平板断点：smallestScreenWidthDp >= 600
        boolean tablet = getResources().getConfiguration().smallestScreenWidthDp >= 600;

        // 根布局：手机竖排（导航在底）/ 平板横排（导航在左）
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(tablet ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(16));
        root.setBackgroundColor(0xFF000000);

        // 主列：标题/语言/Root/内容
        LinearLayout mainCol = new LinearLayout(this);
        mainCol.setOrientation(LinearLayout.VERTICAL);

        // 标题
        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, dp(16));
        mainCol.addView(title);

        // 语言按钮
        final GlassCapsuleButton langBtn = new GlassCapsuleButton(this);
        updateLangBtn(langBtn);
        langBtn.setOnClickListener(v -> cycleLang());
        mainCol.addView(langBtn, marginLp(0, dp(8), 0, dp(4)));

        // Root 状态
        rootStatus = new TextView(this);
        rootStatus.setText(R.string.root_checking);
        rootStatus.setTextColor(0xFFCCCCCC);
        rootStatus.setTextSize(15);
        rootStatus.setPadding(0, dp(12), 0, dp(12));
        mainCol.addView(rootStatus);
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

        mainCol.addView(contentFrame, frameLp);

        // ===== §3.6 D 导航栏：统一用 GlassNavBar（组件内置 §3.6.1 磨砂背景） =====
        final GlassNavBar nav = new GlassNavBar(this);
        nav.addItem(getDrawable(R.drawable.ic_nav_screenshot), getString(R.string.nav_screenshot));
        nav.addItem(getDrawable(R.drawable.ic_nav_record), getString(R.string.nav_record));
        nav.setSelected(0);
        nav.setOnItemSelectedListener(index -> switchPanel(index == 0));

        if (tablet) {
            // §3.4/§3.6 D 平板：导航改左侧竖排悬浮胶囊，垂直居中、约半屏高
            nav.setOrientation(LinearLayout.VERTICAL);
            nav.setSideWidthDp(72f);
            FrameLayout navSlot = new FrameLayout(this);
            int navH = (int) (getResources().getDisplayMetrics().heightPixels * 0.5f);
            FrameLayout.LayoutParams navInSlot =
                    new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, navH);
            navInSlot.gravity = Gravity.CENTER_VERTICAL;
            navSlot.addView(nav, navInSlot);

            LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
            slotLp.rightMargin = dp(16);
            root.addView(navSlot, slotLp);
            root.addView(mainCol, new LinearLayout.LayoutParams(0, -1, 1f));
        } else {
            // 手机：主列占满，GlassNavBar 固定底部横排
            root.addView(mainCol, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout.LayoutParams navLp =
                    new LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT);
            navLp.topMargin = dp(8);
            root.addView(nav, navLp);
        }

        setContentView(root);
    }

    private void switchPanel(boolean showShot) {
        shotPanel.setVisibility(showShot ? android.view.View.VISIBLE : android.view.View.GONE);
        recPanel.setVisibility(showShot ? android.view.View.GONE : android.view.View.VISIBLE);
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
        header.setText(R.string.rec_settings);
        header.setTextColor(0xFFFFFFFF);
        header.setTextSize(17);
        header.setPadding(0, dp(8), 0, dp(8));
        inner.addView(header);

        // ===== 录屏功能总开关（§3.6 B 独立布尔开关） =====
        final GlassCapsuleButton enableBtn = new GlassCapsuleButton(this);
        boolean enabled = Prefs.recEnabled(this);
        enableBtn.setText(enabled ? R.string.rec_enable_on : R.string.rec_enable_off);
        enableBtn.setGlassSelected(enabled);
        enableBtn.setOnClickListener(v -> {
            boolean nv = !Prefs.recEnabled(this);
            final String val = nv ? "1" : "0";
            new Thread(() -> Prefs.putGlobal(Prefs.K_REC_ENABLED, val)).start();
            enableBtn.setText(nv ? R.string.rec_enable_on : R.string.rec_enable_off);
            enableBtn.setGlassSelected(nv);
        });
        inner.addView(enableBtn, marginLp(0, dp(4), 0, dp(4)));

        TextView enableNote = new TextView(this);
        enableNote.setText(R.string.rec_enable_note);
        enableNote.setTextColor(0xFF999999);
        enableNote.setTextSize(12);
        enableNote.setLineSpacing(dp(2), 1f);
        enableNote.setPadding(dp(2), dp(8), dp(2), dp(8));
        inner.addView(enableNote);

        // 操作说明
        TextView info = new TextView(this);
        info.setText(R.string.rec_info);
        info.setTextColor(0xFFCCCCCC);
        info.setTextSize(14);
        info.setLineSpacing(dp(4), 1f);
        info.setPadding(0, dp(8), 0, dp(16));
        inner.addView(info);

        // 码率选择标题
        TextView bitrateTitle = new TextView(this);
        bitrateTitle.setText(R.string.rec_bitrate_title);
        bitrateTitle.setTextColor(0xFFFFFFFF);
        bitrateTitle.setTextSize(15);
        bitrateTitle.setPadding(0, dp(8), 0, dp(4));
        inner.addView(bitrateTitle);

        int curBitrate = Prefs.recBitrate(this);

        // 低码率 1Mbps
        final GlassCapsuleButton lowBtn = new GlassCapsuleButton(this);
        lowBtn.setText(R.string.rec_bitrate_low);
        lowBtn.setGlassSelected(curBitrate == 1000000);
        lowBtn.setOnClickListener(v -> setBitrate(1000000, lowBtn));
        inner.addView(lowBtn, marginLp(0, dp(4), 0, dp(4)));

        // 中码率 2Mbps
        final GlassCapsuleButton midBtn = new GlassCapsuleButton(this);
        midBtn.setText(R.string.rec_bitrate_mid);
        midBtn.setGlassSelected(curBitrate == 2000000);
        midBtn.setOnClickListener(v -> setBitrate(2000000, midBtn));
        inner.addView(midBtn, marginLp(0, dp(4), 0, dp(4)));

        // 高码率 4Mbps
        final GlassCapsuleButton highBtn = new GlassCapsuleButton(this);
        highBtn.setText(R.string.rec_bitrate_high);
        highBtn.setGlassSelected(curBitrate == 4000000);
        highBtn.setOnClickListener(v -> setBitrate(4000000, highBtn));
        inner.addView(highBtn, marginLp(0, dp(4), 0, dp(4)));

        // 说明
        TextView note = new TextView(this);
        note.setText(R.string.rec_bitrate_note);
        note.setTextColor(0xFF999999);
        note.setTextSize(12);
        note.setLineSpacing(dp(2), 1f);
        note.setPadding(dp(2), dp(12), dp(2), dp(8));
        inner.addView(note);

        // 录制系统声音开关
        TextView audioTitle = new TextView(this);
        audioTitle.setText(R.string.rec_audio_title);
        audioTitle.setTextColor(0xFFFFFFFF);
        audioTitle.setTextSize(15);
        audioTitle.setPadding(0, dp(8), 0, dp(4));
        inner.addView(audioTitle);

        appendAudioOptions(inner);

        sv.addView(inner);
        ll.addView(sv);
        return ll;
    }

    /** 录屏面板：录制系统声音开关（独立方法）。 */
    private void appendAudioOptions(LinearLayout inner) {
        final GlassCapsuleButton audioBtn = new GlassCapsuleButton(this);
        boolean audioOn = Prefs.recAudio(this);
        audioBtn.setText(audioOn ? R.string.rec_audio_on : R.string.rec_audio_off);
        audioBtn.setGlassSelected(audioOn);
        audioBtn.setOnClickListener(v -> {
            boolean nv = !Prefs.recAudio(this);
            final String val = nv ? "1" : "0";
            new Thread(() -> Prefs.putGlobal(Prefs.K_REC_AUDIO, val)).start();
            audioBtn.setText(nv ? R.string.rec_audio_on : R.string.rec_audio_off);
            audioBtn.setGlassSelected(nv);
        });
        inner.addView(audioBtn, marginLp(0, dp(4), 0, dp(4)));

        TextView audioNote = new TextView(this);
        audioNote.setText(R.string.rec_audio_note);
        audioNote.setTextColor(0xFF999999);
        audioNote.setTextSize(12);
        audioNote.setLineSpacing(dp(2), 1f);
        audioNote.setPadding(dp(2), dp(12), dp(2), dp(8));
        inner.addView(audioNote);
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
        Toast.makeText(this, R.string.rec_bitrate_set, Toast.LENGTH_SHORT).show();
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
