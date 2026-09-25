package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class EditorActivity extends Activity {

    private DrawView drawView;
    private int selected = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        Bitmap src = BitmapFactory.decodeFile(path);
        if (src == null) { finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // 绘图区
        drawView = new DrawView(this);
        drawView.setBitmap(src);
        FrameLayout.LayoutParams dvp = new FrameLayout.LayoutParams(-1, -1);
        dvp.topMargin = dp(64);
        dvp.bottomMargin = dp(190);
        root.addView(drawView, dvp);

        // ===== 顶部栏 =====
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16), dp(18), dp(16), dp(8));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, dp(58));
        tp.gravity = Gravity.TOP;
        root.addView(top, tp);

        topIcon(top, R.drawable.ic_trash, v -> finish());
        View gap1 = new View(this); top.addView(gap1, new LinearLayout.LayoutParams(0,1,1));
        topIcon(top, R.drawable.ic_undo, v -> drawView.undo());
        topIcon(top, R.drawable.ic_redo, v -> drawView.redo());
        View gap2 = new View(this); top.addView(gap2, new LinearLayout.LayoutParams(0,1,1));
        topIcon(top, R.drawable.ic_share, v -> Toast.makeText(this,"分享",Toast.LENGTH_SHORT).show());
        topIcon(top, R.drawable.ic_check, v -> save());

        // ===== 底部容器 =====
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), dp(8), dp(16), dp(18));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(180));
        bp.gravity = Gravity.BOTTOM;
        root.addView(bottom, bp);

        // 画笔条
        LinearLayout pens = new LinearLayout(this);
        pens.setOrientation(LinearLayout.HORIZONTAL);
        pens.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        pens.setPadding(0, dp(6), 0, dp(10));
        LinearLayout.LayoutParams pensLp = new LinearLayout.LayoutParams(-1, dp(70));
        bottom.addView(pens, pensLp);

        addPen(pens, dp(7),  dp(46), 0xFFD04030);
        addPen(pens, dp(13), dp(52), 0xFF4A4A50);
        addPen(pens, dp(8),  dp(48), 0xFFE8C88A);
        addPen(pens, dp(9),  dp(50), 0xFF3A6EA8);
        addPen(pens, dp(14), dp(44), 0xFFB8B8BC);
        View pgap = new View(this); pens.addView(pgap, new LinearLayout.LayoutParams(0,1,1));
        ColorWheelView wheel = new ColorWheelView(this);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(dp(40), dp(40));
        pens.addView(wheel, wlp);

        // 功能行
        LinearLayout funcs = new LinearLayout(this);
        funcs.setOrientation(LinearLayout.HORIZONTAL);
        funcs.setGravity(Gravity.CENTER);
        bottom.addView(funcs, new LinearLayout.LayoutParams(-1, dp(90)));

        addFunc(funcs, R.drawable.ic_pen,    "标记",   0);
        addFunc(funcs, R.drawable.ic_text,   "文字",   1);
        addFunc(funcs, R.drawable.ic_mosaic, "马赛克", 2);
        addFunc(funcs, R.drawable.ic_scan,   "识文",   3);
        addFunc(funcs, R.drawable.ic_crop,   "形状裁剪", 4);
        addFunc(funcs, R.drawable.ic_pen,    "高级编辑", 5);

        setContentView(root);
    }

    private void topIcon(LinearLayout bar, int icon, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setPadding(dp(11), dp(11), dp(11), dp(11));
        iv.setOnClickListener(l);
        bar.addView(iv, new LinearLayout.LayoutParams(dp(46), dp(46)));
    }

    private void addPen(LinearLayout bar, int w, int h, int color) {
        View pen = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(w/2f);
        d.setColor(color);
        pen.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.setMargins(dp(7), 0, dp(7), 0);
        bar.addView(pen, lp);
    }

    private void addFunc(LinearLayout bar, int icon, String label, int index) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);

        ImageView circle = new ImageView(this);
        circle.setImageResource(icon);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        boolean sel = index == selected;
        if (sel) {
            bg.setColor(0xFFFFFFFF);
            circle.setColorFilter(0xFF000000);
        } else {
            bg.setColor(0x24FFFFFF);
            circle.setColorFilter(0xFFFFFFFF);
        }
        circle.setBackground(bg);
        circle.setPadding(dp(12), dp(12), dp(12), dp(12));
        item.addView(circle, new LinearLayout.LayoutParams(dp(50), dp(50)));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(sel ? 0xFFFFFFFF : 0xCCFFFFFF);
        tv.setTextSize(12);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(5), 0, 0);
        item.addView(tv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        bar.addView(item, lp);

        item.setOnClickListener(v -> {
            if (index == 0) return; // 标记默认
            Toast.makeText(this, label + " 开发中", Toast.LENGTH_SHORT).show();
        });
    }

    private void save() {
        try {
            Bitmap result = drawView.getResultBitmap();
            File dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_PICTURES + "/Screenshots");
            dir.mkdirs();
            File f = new File(dir, "ScreenshotX_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(f);
            result.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Toast.makeText(this, "已保存到 Pictures/Screenshots", Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
