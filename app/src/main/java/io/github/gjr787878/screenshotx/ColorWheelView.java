package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.view.View;

public class ColorWheelView extends View {
    private Paint ring, inner;
    private int center = Color.RED;
    public ColorWheelView(Context c) {
        super(c);
        ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(4));
        int[] colors = {Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED};
        ring.setShader(new SweepGradient(0,0,colors,null));
        inner = new Paint(Paint.ANTI_ALIAS_FLAG);
    }
    public void setCenterColor(int c){center=c;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        float cx=getWidth()/2f, cy=getHeight()/2f, r=Math.min(cx,cy)-dp(3);
        canvas.translate(cx,cy);
        inner.setColor(center);
        canvas.drawCircle(0,0,r-dp(4),inner);
        canvas.drawCircle(0,0,r-dp(2),ring);
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density);}
}
