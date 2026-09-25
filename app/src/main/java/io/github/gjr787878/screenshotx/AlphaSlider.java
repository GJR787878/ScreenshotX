package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class AlphaSlider extends View {
    public interface OnAlpha { void alpha(int a); }
    private int color=Color.RED, alpha=255;
    private OnAlpha listener;
    private Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF bar=new RectF();
    private Bitmap checker;

    public AlphaSlider(Context c){
        super(c);
        dot.setColor(Color.WHITE);
    }
    public void setListener(OnAlpha l){listener=l;}
    public void setColor(int c){color=c; invalidate();}
    public void setAlpha(int a){alpha=a; invalidate();}

    private Bitmap checker(int w,int h){
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas cv=new Canvas(b);
        int cs=dp(8);
        for(int y=0;y<h;y+=cs)for(int x=0;x<w;x+=cs){
            p.setColor(((x/cs+y/cs)%2==0)?0xFFCCCCCC:0xFFEEEEEE);
            cv.drawRect(x,y,x+cs,y+cs,p);
        }
        return b;
    }

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        bar.set(dp(4),h/2f-dp(10),w-dp(4),h/2f+dp(10));
        checker=checker(w,h);
    }

    @Override protected void onDraw(Canvas canvas){
        // 圆角裁剪棋盘
        canvas.save();
        android.graphics.Path clip=new android.graphics.Path();
        clip.addRoundRect(bar,dp(10),dp(10),android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.drawBitmap(checker,bar.left,bar.top,null);
        int solid=color|0xFF000000;
        Paint grad=new Paint(Paint.ANTI_ALIAS_FLAG);
        grad.setShader(new LinearGradient(bar.left,0,bar.right,0,color&0x00FFFFFF,solid,Shader.TileMode.CLAMP));
        canvas.drawRect(bar,grad);
        canvas.restore();

        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.5f)); p.setColor(0x55FFFFFF);
        canvas.drawRoundRect(bar,dp(10),dp(10),p);

        float x=bar.left+(alpha/255f)*bar.width();
        canvas.drawCircle(x,bar.centerY(),dp(9),dot);
        p.setStyle(Paint.Style.STROKE); p.setColor(0x66000000); p.setStrokeWidth(dp(1));
        canvas.drawCircle(x,bar.centerY(),dp(9),p);
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN||e.getAction()==MotionEvent.ACTION_MOVE){
            float x=Math.max(bar.left,Math.min(bar.right,e.getX()));
            alpha=Math.round((x-bar.left)/bar.width()*255);
            invalidate();
            if(listener!=null)listener.alpha(alpha);
            return true;
        }
        return false;
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density);}
}
