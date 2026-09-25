package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.VectorDrawable;
import android.graphics.drawable.Drawable;
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
import java.util.ArrayList;
import java.util.List;

public class EditorActivity extends Activity {

    private Bitmap srcBmp;
    private ImageView preview;
    private List<View> bottomBtns = new ArrayList<>();

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
        pp.bottomMargin = dp(200);
        root.addView(preview, pp);

        // 顶部工具栏（删除/撤销/重做/分享/完成）
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(20), dp(16), dp(8));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, dp(56));
        tp.gravity = Gravity.TOP;
        root.addView(topBar, tp);

        // 顶部图标按钮
        addTopIcon(topBar, android.R.drawable.ic_menu_delete, v -> finish());
        addTopIcon(topBar, android.R.drawable.ic_menu_revert, v ->
                Toast.makeText(this, "撤销", Toast.LENGTH_SHORT).show());
        addTopIcon(topBar, android.R.drawable.ic_menu_rotate, v ->
                Toast.makeText(this, "重做", Toast.LENGTH_SHORT).show());
        // 分享占中间
        View spacer = new View(this);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, 1, 1);
        topBar.addView(spacer, sp);
        addTopIcon(topBar, android.R.drawable.ic_menu_share, v ->
                Toast.makeText(this, "分享", Toast.LENGTH_SHORT).show());
        addTopIcon(topBar, android.R.drawable.ic_menu_save, v -> save());

        // 底部工具栏（圆形按钮+文字）
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(8), dp(16), dp(8), dp(24));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(140));
        bp.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, bp);

        addBottomItem(bottomBar, "标记", android.R.drawable.ic_menu_edit, true);
        addBottomItem(bottomBar, "文字", android.R.drawable.ic_menu_edit, false);
        addBottomItem(bottomBar, "马赛克", android.R.drawable.ic_menu_gallery, false);
        addBottomItem(bottomBar, "识文", android.R.drawable.ic_menu_search, false);
        addBottomItem(bottomBar, "裁剪", android.R.drawable.ic_menu_crop, false);
        addBottomItem(bottomBar, "高级", android.R.drawable.ic_menu_manage, false);

        setContentView(root);
    }

    private void addTopIcon(LinearLayout bar, int iconRes, View.OnClickListener l) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(0xFFFFFFFF);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        iv.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        bar.addView(iv, lp);
    }

    private void addBottomItem(LinearLayout bar, String label, int iconRes, boolean selected) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);

        // 圆形背景
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(selected ? 0xFFFFFFFF : 0x33FFFFFF);
        item.setBackground(circle);

        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(selected ? 0xFF000000 : 0xFFFFFFFF);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(28), dp(28));
        ip.gravity = Gravity.CENTER;
        item.addView(iv, ip);

        LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        circleLp.gravity = Gravity.CENTER;
        item.setLayoutParams(circleLp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(6), 0, 0);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.gravity = Gravity.CENTER;
        bar.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(tv, tp);
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
