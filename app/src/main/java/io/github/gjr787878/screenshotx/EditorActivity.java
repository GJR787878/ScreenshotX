package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    private Bitmap srcBmp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        srcBmp = BitmapFactory.decodeFile(path);
        if (srcBmp == null) { finish(); return; }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // 绘图区域
        drawView = new DrawView(this);
        drawView.setBitmap(srcBmp);
        FrameLayout.LayoutParams dp2 = new FrameLayout.LayoutParams(-1, -1);
        dp2.topMargin = dp(60);
        dp2.bottomMargin = dp(120);
        root.addView(drawView, dp2);

        // 顶部栏
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(16), dp(12), dp(8));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, dp(56));
        tp.gravity = Gravity.TOP;
        root.addView(topBar, tp);

        addIcon(topBar, android.R.drawable.ic_menu_delete, v -> finish());
        addIcon(topBar, android.R.drawable.ic_menu_revert, v -> drawView.undo());
        addIcon(topBar, android.R.drawable.ic_menu_rotate, v -> drawView.redo());
        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        addIcon(topBar, android.R.drawable.ic_menu_share, v ->
                Toast.makeText(this, "分享", Toast.LENGTH_SHORT).show());
        addIcon(topBar, android.R.drawable.ic_menu_save, v -> save());

        // 底部栏
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(8), dp(12), dp(8), dp(20));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(100));
        bp.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, bp);

        addTab(bottomBar, "标记", true);
        addTab(bottomBar, "文字", false);
        addTab(bottomBar, "马赛克", false);
        addTab(bottomBar, "识文", false);
        addTab(bottomBar, "裁剪", false);

        setContentView(root);
    }

    private void addIcon(LinearLayout bar, int icon, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(0xFFFFFFFF);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        iv.setOnClickListener(l);
        bar.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
    }

    private void addTab(LinearLayout bar, String text, boolean selected) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(selected ? 0xFFFFFFFF : 0x99FFFFFF);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        bar.addView(tv, lp);
    }

    private void save() {
        try {
            Bitmap result = drawView.getResultBitmap();
            File f = new File(getExternalCacheDir(), "ScreenshotX_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(f);
            result.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
