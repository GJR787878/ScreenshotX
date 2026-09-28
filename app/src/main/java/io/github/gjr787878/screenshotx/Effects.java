package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

/** 马赛克/遮挡三种常用效果：像素化、高斯模糊、纯色遮挡。 */
public class Effects {

    /** 经典像素马赛克：先缩小再最近邻放大，形成方块。区域尺寸应为 block 整数倍以对齐全局网格。 */
    public static Bitmap pixelate(Bitmap src, int block) {
        int w = src.getWidth(), h = src.getHeight();
        int sw = Math.max(1, Math.round(w / (float) block));
        int sh = Math.max(1, Math.round(h / (float) block));
        Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);
        return Bitmap.createScaledBitmap(small, w, h, false);
    }

    /** 高斯模糊：自然柔化。radius 范围 1..25。 */
    public static Bitmap gaussian(Context c, Bitmap src, float radius) {
        radius = Math.max(1f, Math.min(25f, radius));
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(),
                Bitmap.Config.ARGB_8888);
        RenderScript rs = RenderScript.create(c);
        try {
            Allocation in = Allocation.createFromBitmap(rs, src);
            Allocation gout = Allocation.createFromBitmap(rs, out);
            ScriptIntrinsicBlur blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            blur.setRadius(radius);
            blur.setInput(in);
            gout.copyTo(out);
            in.destroy();
            gout.destroy();
            blur.destroy();
        } finally {
            rs.destroy();
        }
        return out;
    }

    /** 纯色遮挡（默认黑色）：彻底抹除原信息，不可恢复。 */
    public static Bitmap solid(Bitmap src, int color) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(color == 0 ? Color.BLACK : color);
        return out;
    }
}
