package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawView extends View {

    public static final int BALL=0, MARKER=1, PENCIL=2, FOUNTAIN=3, ERASER=4;

    // 马赛克效果
    public static final int MOS_PIXEL=0, MOS_GAUSS=1, MOS_SOLID=2;

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

    // 双指缩放/平移（标记与马赛克通用）
    private final ZoomController zoomCtl;
    private boolean gestureActive=false;
    // 是否已有标注（画笔/马赛克落到 overlay）；用于进入裁剪时跳过整屏复制
    private boolean edited=false;

    // ===== 马赛克状态 =====
    private boolean mosaicMode = false;
    private int mosaicEffect = MOS_PIXEL;
    private boolean mosaicRect = false; // true=框选 false=涂抹
    private int mosBlock = 20;
    private float mosBrush = 48f;
    private static final float GAUSS_RADIUS = 20f;

    // 马赛克实时预览（指示层）
    private Bitmap mosPreview;
    private Canvas mosPreviewCanvas;
    private final Path mosPath = new Path();
    private final Paint mosIndPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mosRectFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mosRectBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mosHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mosDrawing = false;
    private float mosSX, mosSY, mosCX, mosCY; // 框选起点/当前
    private float minX, minY, maxX, maxY;      // 涂抹包围盒

    public void setWidthScale(float s) {
        widthScale = Math.max(0.2f, Math.min(8f, s));
        applyStyle();
    }
    public float getWidthScale(){return widthScale;}

    private final List<Bitmap> undoStack = new ArrayList<>();
    private final List<Bitmap> redoStack = new ArrayList<>();

    public DrawView(Context c) {
        super(c);
        zoomCtl=new ZoomController(c,1f,6f,true,()->invalidate());

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeJoin(Paint.Join.ROUND);
        markerPaint.setStrokeCap(Paint.Cap.ROUND);
        markerCommitPaint.setAlpha(MARKER_ALPHA);

        // 涂抹指示：半透明白
        mosIndPaint.setStyle(Paint.Style.STROKE);
        mosIndPaint.setStrokeJoin(Paint.Join.ROUND);
        mosIndPaint.setStrokeCap(Paint.Cap.ROUND);
        mosIndPaint.setColor(0x88FFFFFF);
        // 框选填充
        mosRectFillPaint.setStyle(Paint.Style.FILL);
        mosRectFillPaint.setColor(0x22FFFFFF);
        // 框选虚线边
        mosRectBorderPaint.setStyle(Paint.Style.STROKE);
        mosRectBorderPaint.setColor(0xFFFFFFFF);
        mosRectBorderPaint.setStrokeWidth(3f);
        mosRectBorderPaint.setPathEffect(new DashPathEffect(new float[]{16f,12f},0f));
        // 角手柄
        mosHandlePaint.setStyle(Paint.Style.FILL);
        mosHandlePaint.setColor(0xFFFFFFFF);
    }

    public void setBitmap(Bitmap b) {
        base = b.copy(Bitmap.Config.ARGB_8888, true);
        overlay = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
        overlayCanvas = new Canvas(overlay);
        markerLayer = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
        markerCanvas = new Canvas(markerLayer);
        mosPreview = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
        mosPreviewCanvas = new Canvas(mosPreview);
        float bw = base.getWidth();
        mosBlock = Math.max(6, Math.round(bw*0.02f));
        mosBrush = bw*0.045f;
        undoStack.clear();
        redoStack.clear();
        edited=false;
        zoomCtl.reset();
        if(getWidth()>0 && getHeight()>0)
            zoomCtl.setSize(getWidth(),getHeight(),base.getWidth(),base.getHeight());
        requestLayout();
        invalidate();
    }

    public void setTool(int t){tool=t;applyStyle();}
    public void setColor(int c){color=c;if(tool!=ERASER)applyStyle();}
    public int getColor(){return color;}

    // ===== 马赛克外部接口 =====
    public void setMosaicMode(boolean m){mosaicMode=m;applyStyle();}
    public void setMosaicEffect(int e){mosaicEffect=e;}
    public void setMosaicRect(boolean r){mosaicRect=r;}

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

    private float brushDiameter(){
        return mosBrush*widthScale;
    }

    private void applyStyle(){
        paint.setStrokeWidth(widthFor(tool)*widthScale);
        markerPaint.setStrokeWidth(widthFor(MARKER)*widthScale);
        if(tool==ERASER){
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
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
        zoomCtl.setSize(w,h,base.getWidth(),base.getHeight());
    }

    @Override protected void onDraw(Canvas canvas){
        if(base==null) return;
        canvas.save();
        canvas.translate(zoomCtl.left(),zoomCtl.top());
        canvas.scale(zoomCtl.dispScale(),zoomCtl.dispScale());
        canvas.drawBitmap(base, 0, 0, null);
        canvas.drawBitmap(overlay, 0, 0, null);
        // 正在绘制的荧光笔实时预览，统一 40% 透明
        if(tool==MARKER && markerLayer!=null && !mosaicMode)
            canvas.drawBitmap(markerLayer, 0, 0, markerCommitPaint);
        // 马赛克实时指示
        if(mosaicMode && mosPreview!=null)
            canvas.drawBitmap(mosPreview, 0, 0, null);
        canvas.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(base==null) return false;
        int am=e.getActionMasked();
        int pc=e.getPointerCount();

        zoomCtl.onTouch(e);

        // 第二指落下：进入双指缩放，取消当前未完成的单指手势
        if(am==MotionEvent.ACTION_POINTER_DOWN && pc==2){
            cancelGesture();
            invalidate();
            return true;
        }
        if(zoomCtl.isPinching() || pc>=2) return true;
        if(am==MotionEvent.ACTION_POINTER_UP) return true;

        float[] p=zoomCtl.toImg(e.getX(),e.getY());
        float x=p[0], y=p[1];

        if(mosaicMode){
            x=Math.max(0,Math.min(base.getWidth(),x));
            y=Math.max(0,Math.min(base.getHeight(),y));
            handleMosaic(am,x,y);
            invalidate();
            return true;
        }

        // 画笔：忽略图片外的触摸
        if(x<0||y<0||x>base.getWidth()||y>base.getHeight()){
            if(am==MotionEvent.ACTION_UP){ path.reset(); gestureActive=false; }
            return true;
        }
        switch(am){
            case MotionEvent.ACTION_DOWN:
                pushUndo(); applyStyle();
                edited=true;
                path.reset(); path.moveTo(x,y);
                curX=x; curY=y;
                gestureActive=true;
                if(tool==MARKER){
                    markerLayer.eraseColor(Color.TRANSPARENT);
                    markerCanvas.drawPoint(x,y,markerPaint);
                }else{
                    overlayCanvas.drawPoint(x,y,paint);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if(!gestureActive) break;
                path.quadTo(curX,curY,(x+curX)/2,(y+curY)/2);
                if(tool==MARKER) markerCanvas.drawPath(path,markerPaint);
                else overlayCanvas.drawPath(path,paint);
                curX=x; curY=y;
                break;
            case MotionEvent.ACTION_UP:
                if(!gestureActive) break;
                gestureActive=false;
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

    private void cancelGesture(){
        if(mosaicMode){
            mosDrawing=false;
            if(mosPreview!=null) mosPreview.eraseColor(Color.TRANSPARENT);
            mosPath.reset();
        }else if(tool==MARKER){
            if(markerLayer!=null) markerLayer.eraseColor(Color.TRANSPARENT);
            path.reset();
        }else{
            path.reset();
        }
        gestureActive=false;
    }

    // ================= 马赛克触摸 =================
    private void handleMosaic(int am, float x, float y){
        switch(am){
            case MotionEvent.ACTION_DOWN:
                pushUndo();
                mosDrawing=true;
                gestureActive=true;
                mosSX=mosCX=x; mosSY=mosCY=y;
                minX=maxX=x; minY=maxY=y;
                mosPreview.eraseColor(Color.TRANSPARENT);
                mosPath.reset(); mosPath.moveTo(x,y);
                if(mosaicRect) drawRectIndicator();
                else drawBrushDot(x,y);
                break;
            case MotionEvent.ACTION_MOVE:
                if(!mosDrawing) return;
                if(mosaicRect){
                    mosCX=x; mosCY=y; drawRectIndicator();
                }else{
                    float mx=(x+mosCX)/2f, my=(y+mosCY)/2f;
                    mosPath.quadTo(mosCX,mosCY,mx,my);
                    drawBrushSeg(mosCX,mosCY,x,y);
                    mosCX=x; mosCY=y;
                    if(x<minX)minX=x; if(x>maxX)maxX=x;
                    if(y<minY)minY=y; if(y>maxY)maxY=y;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(!mosDrawing) return;
                mosDrawing=false;
                gestureActive=false;
                if(mosaicRect){ finishRect(); }
                else finishBrush();
                mosPreview.eraseColor(Color.TRANSPARENT);
                mosPath.reset();
                break;
        }
    }

    private void drawBrushDot(float x,float y){
        mosIndPaint.setStrokeWidth(brushDiameter());
        mosPreviewCanvas.drawPoint(x,y,mosIndPaint);
    }
    private void drawBrushSeg(float x0,float y0,float x1,float y1){
        mosIndPaint.setStrokeWidth(brushDiameter());
        mosPreviewCanvas.drawLine(x0,y0,x1,y1,mosIndPaint);
    }

    private void drawRectIndicator(){
        mosPreview.eraseColor(Color.TRANSPARENT);
        float l=Math.min(mosSX,mosCX), t=Math.min(mosSY,mosCY);
        float r=Math.max(mosSX,mosCX), b=Math.max(mosSY,mosCY);
        RectF rf=new RectF(l,t,r,b);
        mosPreviewCanvas.drawRect(rf,mosRectFillPaint);
        mosPreviewCanvas.drawRect(rf,mosRectBorderPaint);
        float hs=10f;
        float[][] pts={{l,t},{r,t},{l,b},{r,b}};
        for(float[] pt:pts)
            mosPreviewCanvas.drawRect(pt[0]-hs,pt[1]-hs,pt[0]+hs,pt[1]+hs,mosHandlePaint);
    }

    // ================= 马赛克应用 =================
    private void finishBrush(){
        float half=brushDiameter()/2f;
        int pad=(mosaicEffect==MOS_GAUSS)?Math.round(GAUSS_RADIUS):0;
        RectF vb=new RectF(minX-half-pad,minY-half-pad,maxX+half+pad,maxY+half+pad);
        int[] region=resolveRegion(vb);
        int rx=region[0],ry=region[1],rw=region[2],rh=region[3];
        if(rw<=0||rh<=0) return;
        Bitmap src=Bitmap.createBitmap(base,rx,ry,rw,rh);
        Bitmap eff=runEffect(src);
        // 蒙版：涂抹路径按笔宽描边
        Bitmap mask=Bitmap.createBitmap(rw,rh,Bitmap.Config.ARGB_8888);
        Canvas mc=new Canvas(mask);
        Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG);
        mp.setColor(0xFFFFFFFF);
        mp.setStyle(Paint.Style.STROKE);
        mp.setStrokeJoin(Paint.Join.ROUND);
        mp.setStrokeCap(Paint.Cap.ROUND);
        mp.setStrokeWidth(brushDiameter());
        mc.translate(-rx,-ry);
        mc.drawPath(mosPath,mp);
        // 起始点补圆，避免 moveTo 单点留空
        mc.drawCircle(Math.max(0,Math.min(base.getWidth(),mosSX)),
                Math.max(0,Math.min(base.getHeight(),mosSY)),
                brushDiameter()/2f,mp);
        stamp(eff,mask,rx,ry);
    }

    private void finishRect(){
        float l=Math.min(mosSX,mosCX), t=Math.min(mosSY,mosCY);
        float r=Math.max(mosSX,mosCX), b=Math.max(mosSY,mosCY);
        if(r-l<6||b-t<6) return;
        int pad=(mosaicEffect==MOS_GAUSS)?Math.round(GAUSS_RADIUS):0;
        RectF vb=new RectF(l-pad,t-pad,r+pad,b+pad);
        int[] region=resolveRegion(vb);
        int rx=region[0],ry=region[1],rw=region[2],rh=region[3];
        if(rw<=0||rh<=0) return;
        Bitmap src=Bitmap.createBitmap(base,rx,ry,rw,rh);
        Bitmap eff=runEffect(src);
        // 蒙版：实心矩形
        Bitmap mask=Bitmap.createBitmap(rw,rh,Bitmap.Config.ARGB_8888);
        Canvas mc=new Canvas(mask);
        Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG);
        mp.setColor(0xFFFFFFFF);
        mp.setStyle(Paint.Style.FILL);
        mc.translate(-rx,-ry);
        mc.drawRect(l,t,r,b,mp);
        stamp(eff,mask,rx,ry);
    }

    /** 计算区域；像素化时按方块网格对齐，保证多次涂抹网格一致。 */
    private int[] resolveRegion(RectF vb){
        int W=base.getWidth(), H=base.getHeight();
        int x0,y0,x1,y1;
        if(mosaicEffect==MOS_PIXEL){
            int blk=mosBlock;
            x0=(int)Math.floor(vb.left/blk)*blk;
            y0=(int)Math.floor(vb.top/blk)*blk;
            x1=(int)Math.ceil(vb.right/blk)*blk;
            y1=(int)Math.ceil(vb.bottom/blk)*blk;
        }else{
            x0=(int)Math.floor(vb.left);
            y0=(int)Math.floor(vb.top);
            x1=(int)Math.ceil(vb.right);
            y1=(int)Math.ceil(vb.bottom);
        }
        x0=Math.max(0,x0); y0=Math.max(0,y0);
        x1=Math.min(W,x1); y1=Math.min(H,y1);
        return new int[]{x0,y0,x1-x0,y1-y0};
    }

    private Bitmap runEffect(Bitmap src){
        switch(mosaicEffect){
            case MOS_GAUSS: return Effects.gaussian(getContext(),src,GAUSS_RADIUS);
            case MOS_SOLID: return Effects.solid(src,Color.BLACK);
            default:        return Effects.pixelate(src,mosBlock);
        }
    }

    /** 用蒙版裁剪效果并合成到 overlay。 */
    private void stamp(Bitmap eff,Bitmap mask,int dx,int dy){
        Bitmap s=Bitmap.createBitmap(eff.getWidth(),eff.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(s);
        c.drawBitmap(eff,0,0,null);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        c.drawBitmap(mask,0,0,p);
        overlayCanvas.drawBitmap(s,dx,dy,null);
        edited=true;
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

    public Bitmap getBase(){return base;}
    public boolean isEdited(){return edited;}

    public Bitmap getResultBitmap(){
        Bitmap out=base.copy(Bitmap.Config.ARGB_8888,true);
        Canvas c=new Canvas(out);
        c.drawBitmap(overlay,0,0,null);
        // 兜底：保存时若有未落笔的荧光笔，也按统一 40% 合成
        if(markerLayer!=null) c.drawBitmap(markerLayer,0,0,markerCommitPaint);
        return out;
    }
}
