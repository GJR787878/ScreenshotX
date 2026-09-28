package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawView extends View {

    public static final int BALL=0, MARKER=1, PENCIL=2, FOUNTAIN=3, ERASER=4;

    /** 荧光笔统一不透明度 40% = 0.4*255 ≈ 102 */
    private static final int MARKER_ALPHA = 102;

    private Bitmap base, overlay;
    private Canvas overlayCanvas;
    // 荧光笔独立图层：笔画以不透明绘制，落笔时整体按 MARKER_ALPHA 合成，保证统一透明无渐变
    private Bitmap markerLayer;
    private Canvas markerCanvas;
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
    private final Paint markerCommitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
    private final Path path = new Path();
    private float curX, curY;
    private int tool = BALL, color = Color.RED;
    private float widthScale = 1f; // 全局粗细倍率

    public void setWidthScale(float s) {
        widthScale = Math.max(0.2f, Math.min(8f, s));
        applyStyle();
    }
    public float getWidthScale(){return widthScale;}

    // FIT_CENTER 显示参数
    private float scale=1, drawLeft=0, drawTop=0;

    private final List<Bitmap> undoStack = new ArrayList<>();
    private final List<Bitmap> redoStack = new ArrayList<>();

    public DrawView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeJoin(Paint.Join.ROUND);
        markerPaint.setStrokeCap(Paint.Cap.ROUND);
        markerCommitPaint.setAlpha(MARKER_ALPHA);
    }

    public void setBitmap(Bitmap b) {
        base = b.copy(Bitmap.Config.ARGB_8888, true);
        overlay = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
        overlayCanvas = new Canvas(overlay);
        markerLayer = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
        markerCanvas = new Canvas(markerLayer);
        requestLayout();
        invalidate();
    }

    public void setTool(int t){tool=t;applyStyle();}
    public void setColor(int c){color=c;if(tool!=ERASER)applyStyle();}
    public int getColor(){return color;}

    private float widthFor(int t){
        if(base==null) return 6;
        float bw=base.getWidth();
        switch(t){
            case MARKER: return bw*0.012f;
            case ERASER: return bw*0.025f;
            case PENCIL: return bw*0.0028f;
            case BALL:   return bw*0.0038f;
            case FOUNTAIN:return bw*0.005f;
        }
        return bw*0.004f;
    }

    private void applyStyle(){
        paint.setStrokeWidth(widthFor(tool)*widthScale);
        markerPaint.setStrokeWidth(widthFor(MARKER)*widthScale);
        if(tool==ERASER){
            paint.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            paint.setAlpha(255);
        }else{
            paint.setXfermode(null);
            paint.setColor(color);
            paint.setAlpha(255);
            // 荧光笔在独立图层以不透明绘制，颜色统一，合成时再统一压到 40%
            markerPaint.setXfermode(null);
            markerPaint.setColor(color);
            markerPaint.setAlpha(255);
        }
    }

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        if(base==null) return;
        scale = Math.min(w/(float)base.getWidth(), h/(float)base.getHeight());
        drawLeft = (w - base.getWidth()*scale)/2f;
        drawTop  = (h - base.getHeight()*scale)/2f;
    }

    @Override protected void onDraw(Canvas canvas){
        if(base==null) return;
        canvas.save();
        canvas.translate(drawLeft, drawTop);
        canvas.scale(scale, scale);
        canvas.drawBitmap(base, 0, 0, null);
        canvas.drawBitmap(overlay, 0, 0, null);
        // 正在绘制的荧光笔实时预览，统一 40% 透明
        if(tool==MARKER && markerLayer!=null)
            canvas.drawBitmap(markerLayer, 0, 0, markerCommitPaint);
        canvas.restore();
    }

    // 触摸坐标 → 图片坐标
    private float[] toImg(float x,float y){
        return new float[]{(x-drawLeft)/scale, (y-drawTop)/scale};
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(base==null) return false;
        float[] p=toImg(e.getX(),e.getY());
        float x=p[0], y=p[1];
        // 忽略图片外的触摸
        if(x<0||y<0||x>base.getWidth()||y>base.getHeight()){
            if(e.getAction()==MotionEvent.ACTION_UP) path.reset();
            return true;
        }
        switch(e.getAction()){
            case MotionEvent.ACTION_DOWN:
                pushUndo(); applyStyle();
                path.reset(); path.moveTo(x,y);
                curX=x; curY=y;
                if(tool==MARKER){
                    markerLayer.eraseColor(Color.TRANSPARENT);
                    markerCanvas.drawPoint(x,y,markerPaint);
                }else{
                    overlayCanvas.drawPoint(x,y,paint);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                path.quadTo(curX,curY,(x+curX)/2,(y+curY)/2);
                if(tool==MARKER) markerCanvas.drawPath(path,markerPaint);
                else overlayCanvas.drawPath(path,paint);
                curX=x; curY=y;
                break;
            case MotionEvent.ACTION_UP:
                if(tool==MARKER){
                    markerCanvas.drawPath(path,markerPaint);
                    // 整笔统一按 40% 合成到 overlay，随后清空临时图层
                    overlayCanvas.drawBitmap(markerLayer,0,0,markerCommitPaint);
                    markerLayer.eraseColor(Color.TRANSPARENT);
                }else{
                    overlayCanvas.drawPath(path,paint);
                }
                path.reset();
                break;
            default: return false;
        }
        invalidate();
        return true;
    }

    private void pushUndo(){
        redoStack.clear();
        undoStack.add(overlay.copy(Bitmap.Config.ARGB_8888,false));
        if(undoStack.size()>25) undoStack.remove(0);
    }

    public void undo(){
        if(undoStack.isEmpty()||overlay==null) return;
        redoStack.add(overlay.copy(Bitmap.Config.ARGB_8888,false));
        Bitmap prev=undoStack.remove(undoStack.size()-1);
        overlay.eraseColor(Color.TRANSPARENT);
        new Canvas(overlay).drawBitmap(prev,0,0,null);
        invalidate();
    }

    public void redo(){
        if(redoStack.isEmpty()||overlay==null) return;
        undoStack.add(overlay.copy(Bitmap.Config.ARGB_8888,false));
        Bitmap next=redoStack.remove(redoStack.size()-1);
        overlay.eraseColor(Color.TRANSPARENT);
        new Canvas(overlay).drawBitmap(next,0,0,null);
        invalidate();
    }

    public Bitmap getResultBitmap(){
        Bitmap out=base.copy(Bitmap.Config.ARGB_8888,true);
        Canvas c=new Canvas(out);
        c.drawBitmap(overlay,0,0,null);
        // 兜底：保存时若有未落笔的荧光笔，也按统一 40% 合成
        if(markerLayer!=null) c.drawBitmap(markerLayer,0,0,markerCommitPaint);
        return out;
    }
}
