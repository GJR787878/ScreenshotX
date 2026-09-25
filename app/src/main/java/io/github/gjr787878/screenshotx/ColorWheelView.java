package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.view.View;

public class ColorWheelView extends View {
    private Paint ring;
    public ColorWheelView(Context c) {
        super(c);
        ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(5));
        int[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
        ring.setShader(new SweepGradient(0, 0, colors, null));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth()/2f, cy = getHeight()/2f, r = Math.min(cx,cy)-dp(3);
        canvas.translate(cx, cy);
        canvas.drawCircle(0, 0, r, ring);
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(Color.RED);
        canvas.drawCircle(0, 0, r-dp(5), inner);
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density);}
}
