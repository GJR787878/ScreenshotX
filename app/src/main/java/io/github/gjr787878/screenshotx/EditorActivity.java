package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
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

    private Bitmap srcBmp;
    private ImageView preview;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        srcBmp = BitmapFactory.decodeFile(path);
        if (srcBmp == null) { finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // 中间截图预览
        preview = new ImageView(this);
        preview.setImageBitmap(srcBmp);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, -1);
        pp.topMargin = dp(70);
        pp.bottomMargin = dp(180);
        root.addView(preview, pp);

        // 顶部工具栏（删除/撤销/重做/分享/完成）
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(20), dp(16), dp(8));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, dp(56));
        tp.gravity = Gravity.TOP;
        root.addView(topBar, tp);

        addTopIcon(topBar, android.R.drawable.ic_menu_delete, v -> finish());
        addTopIcon(topBar, android.R.drawable.ic_menu_revert, v ->
                Toast.makeText(this, "撤销", Toast.LENGTH_SHORT).show());
        addTopIcon(topBar, android.R.drawable.ic_menu_rotate, v ->
                Toast.makeText(this, "重做", Toast.LENGTH_SHORT).show());
        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        addTopIcon(topBar, android.R.drawable.ic_menu_share, v ->
                Toast.makeText(this, "分享", Toast.LENGTH_SHORT).show());
        addTopIcon(topBar, android.R.drawable.ic_menu_save, v -> save());

        // 底部工具栏（毛玻璃胶囊）
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(12), dp(16), dp(12), dp(24));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(160));
        bp.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, bp);

        // 五个按钮：标记/文字/马赛克/识文/裁剪（去掉高级）
        addBottomItem(bottomBar, "标记", true);
        addBottomItem(bottomBar, "文字", false);
        addBottomItem(bottomBar, "马赛克", false);
        addBottomItem(bottomBar, "识文", false);
        addBottomItem(bottomBar, "裁剪", false);

        setContentView(root);
    }

    private void addTopIcon(LinearLayout bar, int iconRes, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(0xFFFFFFFF);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        iv.setOnClickListener(l);
        bar.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
    }

    private void addBottomItem(LinearLayout bar, String label, boolean selected) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(12), dp(10), dp(12), dp(10));

        // 毛玻璃胶囊背景
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(24));
        if (selected) {
            bg.setColor(0x55FFFFFF);
            bg.setStroke(dp(1), 0xFFFFFFFF);
        } else {
            bg.setColor(0x22FFFFFF);
            bg.setStroke(dp(1), 0x40FFFFFF);
        }
        item.setBackground(bg);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(selected ? 0xFFFFFFFF : 0xCCFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setGravity(Gravity.CENTER);
        item.addView(tv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(item, lp);
    }

    private void save() {
        try {
            File f = new File(getExternalCacheDir(), "ScreenshotX_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(f);
            srcBmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Toast.makeText(this, "已保存: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
