package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class EditorActivity extends Activity {

    private Bitmap srcBmp;
    private ImageView preview;
    private LinearLayout topBar, bottomBar;
    private String currentTool = "pen";
    private Paint drawPaint;
    private List<DrawOp> undoStack = new ArrayList<>();

    static class DrawOp {
        String tool; Path path; int color; float size;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String path = getIntent().getStringExtra("path");
        if (path == null) { finish(); return; }
        srcBmp = BitmapFactory.decodeFile(path);
        if (srcBmp == null) { finish(); return; }

        drawPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        drawPaint.setColor(0xFFFFFFFF);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeWidth(8f);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // 中间预览
        preview = new ImageView(this);
        preview.setImageBitmap(srcBmp);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-1, -1);
        pp.topMargin = dp(60); pp.bottomMargin = dp(180);
        root.addView(preview, pp);

        // 顶部工具栏
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(16), dp(16), dp(16), dp(8));
        addTopBtn(topBar, "删除", v -> finish());
        addTopBtn(topBar, "撤销", v -> undo());
        addTopBtn(topBar, "重做", v -> {});
        addTopBtn(topBar, "分享", v -> share());
        addTopBtn(topBar, "完成", v -> save());
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, dp(56));
        tp.gravity = Gravity.TOP;
        root.addView(topBar, tp);

        // 底部工具栏（毛玻璃）
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER);
        bottomBar.setPadding(dp(12), dp(12), dp(12), dp(20));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE61C1C1E, 0xCC2C2C2E});
        bottomBar.setBackground(bg);

        addBottomBtn(bottomBar, "标记", v -> setTool("pen"));
        addBottomBtn(bottomBar, "文字", v -> setTool("text"));
        addBottomBtn(bottomBar, "马赛克", v -> setTool("mosaic"));
        addBottomBtn(bottomBar, "识文", v -> Toast.makeText(this, "OCR 开发中", Toast.LENGTH_SHORT).show());
        addBottomBtn(bottomBar, "裁剪", v -> setTool("crop"));
        addBottomBtn(bottomBar, "高级", v -> Toast.makeText(this, "高级编辑开发中", Toast.LENGTH_SHORT).show());

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, dp(120));
        bp.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, bp);

        setContentView(root);
    }

    private void addTopBtn(LinearLayout bar, String label, View.OnClickListener l) {
        GlassCapsuleButton btn = new GlassCapsuleButton(this);
        btn.setLabel(label);
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        btn.setLayoutParams(lp);
        bar.addView(btn);
    }

    private void addBottomBtn(LinearLayout bar, String label, View.OnClickListener l) {
        GlassCapsuleButton btn = new GlassCapsuleButton(this);
        btn.setLabel(label);
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(60), 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        btn.setLayoutParams(lp);
        bar.addView(btn);
    }

    private void setTool(String t) {
        currentTool = t;
        Toast.makeText(this, "工具: " + t, Toast.LENGTH_SHORT).show();
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            undoStack.remove(undoStack.size()-1);
            redraw();
        }
    }

    private void redraw() {
        Bitmap bmp = srcBmp.copy(Bitmap.Config.ARGB_8888, true);
        Canvas c = new Canvas(bmp);
        for (DrawOp op : undoStack) {
            Paint p = new Paint(drawPaint);
            p.setColor(op.color); p.setStrokeWidth(op.size);
            if (op.tool.equals("mosaic")) p.setAlpha(128);
            c.drawPath(op.path, p);
        }
        preview.setImageBitmap(bmp);
    }

    private void save() {
        try {
            File f = new File(getExternalCacheDir(), "GlassShot_" + System.currentTimeMillis() + ".png");
            FileOutputStream fos = new FileOutputStream(f);
            preview.getDrawingCache().compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Toast.makeText(this, "已保存: " + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        Toast.makeText(this, "分享开发中", Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
