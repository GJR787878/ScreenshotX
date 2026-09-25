package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

public class HSVView extends View {

    public interface OnChange { void changed(int color); }

    private float h=0, s=1, v=1;
    private int alpha=255;
    private OnChange listener;

    private Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF square = new RectF();
    private float cx, cy, ringR, ringW;

    public HSVView(Context c) {
        super(c);
        marker.setStyle(Paint.Style.STROKE);
        marker.setStrokeWidth(dp(2.5f));
        marker.setColor(Color.WHITE);
    }

    public void setListener(OnChange l){listener=l;}
    public void setColor(int c) {
        alpha = Color.alpha(c);
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        h=hsv[0]; s=hsv[1]; v=hsv[2];
        invalidate(); emit();
    }

    private void emit(){ if(listener!=null) listener.changed(Color.HSVToColor(alpha,new float[]{h,s,v})); }

    @Override protected void onSizeChanged(int w,int hh,int ow,int oh){
        cx=w/2f; cy=hh/2f;
        ringR=Math.min(w,hh)/2f-dp(6);
        ringW=dp(11);
        float sq=ringR-ringW-dp(10);
        square.set(cx-sq/2, cy-sq/2, cx+sq/2, cy+sq/2);
    }

    @Override protected void onDraw(Canvas canvas){
        // 色环
        Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ringW);
        int[] cols={Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED};
        ring.setShader(new SweepGradient(cx,cy,cols,null));
        canvas.drawCircle(cx,cy,ringR-ringW/2,ring);

        // SV 方块：水平 白→色相
        int hueColor=Color.HSVToColor(new float[]{h,1f,1f});
        Paint sat=new Paint(Paint.ANTI_ALIAS_FLAG);
        sat.setShader(new LinearGradient(square.left,0,square.right,0,Color.WHITE,hueColor,Shader.TileMode.CLAMP));
        canvas.drawRect(square,sat);
        // 垂直 透→黑
        Paint val=new Paint(Paint.ANTI_ALIAS_FLAG);
        val.setShader(new LinearGradient(0,square.top,0,square.bottom,0x00000000,Color.BLACK,Shader.TileMode.CLAMP));
        canvas.drawRect(square,val);

        // 色相选择器(环上白圈)
        double rad=Math.toRadians(h);
        float hx=cx+(float)Math.cos(rad)*(ringR-ringW/2);
        float hy=cy+(float)Math.sin(rad)*(ringR-ringW/2);
        canvas.drawCircle(hx,hy,ringW/2+dp(1),marker);

        // SV 选择器
        float sx=square.left+s*(square.width());
        float sy=square.top+(1-v)*(square.height());
        Paint sm=new Paint(Paint.ANTI_ALIAS_FLAG);
        sm.setStyle(Paint.Style.STROKE); sm.setStrokeWidth(dp(2)); sm.setColor(Color.WHITE);
        canvas.drawCircle(sx,sy,dp(6),sm);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()!=MotionEvent.ACTION_DOWN && e.getAction()!=MotionEvent.ACTION_MOVE) return false;
        float x=e.getX(), y=e.getY();
        float dx=x-cx, dy=y-cy;
        float dist=(float)Math.sqrt(dx*dx+dy*dy);
        if(dist>=square.width()/2){
            // 在环上
            float ang=(float)Math.toDegrees(Math.atan2(dy,dx));
            h=(ang+360)%360;
        } else if(square.contains(x,y)){
            s=(x-square.left)/square.width(); s=Math.max(0,Math.min(1,s));
            v=1-(y-square.top)/square.height(); v=Math.max(0,Math.min(1,v));
        }
        invalidate(); emit();
        return true;
    }

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density);}
}
