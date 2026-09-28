package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** 形状裁剪：比例选项 + 手柄调整 + 双指缩放/平移，确认后输出裁剪位图。 */
public class CropView extends View {

    private Bitmap img;
    private final ZoomController zc;
    private final RectF crop=new RectF();
    private float ratio=0f; // 0=自由
    private boolean inited=false;

    // 拖拽模式：0-3 角(TL,TR,BR,BL) 4-7 边(T,R,B,L) 8 内部平移
    private int dragMode=-1;
    private float lastX,lastY;

    private final Paint dimPaint=new Paint();
    private final Paint borderPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleRingPaint=new Paint(Paint.ANTI_ALIAS_FLAG);

    public CropView(Context c){
        super(c);
        setBackgroundColor(Color.BLACK);
        zc=new ZoomController(c,1f,5f,false,()->invalidate());
        dimPaint.setColor(0xB3000000);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStrokeWidth(dp(2));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setColor(0x55FFFFFF);
        gridPaint.setStrokeWidth(dp(1));
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(Color.WHITE);
        handleRingPaint.setStyle(Paint.Style.STROKE);
        handleRingPaint.setColor(0xFF111111);
        handleRingPaint.setStrokeWidth(dp(1.5f));
    }

    public void setImage(Bitmap b){
        img=b; ratio=0f; inited=false;
        relayout();
        invalidate();
    }

    /** 按当前视图尺寸重新计算变换与默认裁剪框；解决进入时显示不全。 */
    private void relayout(){
        if(img==null) return;
        if(getWidth()>0 && getHeight()>0){
            zc.setSize(getWidth(),getHeight(),img.getWidth(),img.getHeight());
            zc.reset();
            if(!inited){ initDefault(); inited=true; }
        }
    }

    private void initDefault(){
        // 默认裁剪框直接贴合整张已适配图片：进入即完整显示，状态栏/底部条不被压入暗区
        float il=zc.left(), it=zc.top();
        float ir=il+img.getWidth()*zc.dispScale();
        float ib=it+img.getHeight()*zc.dispScale();
        crop.set(il,it,ir,ib);
    }

    public void setRatio(float r){
        ratio=r;
        if(r>0 && crop.width()>0){
            float cx=crop.centerX(), cy=crop.centerY();
            float h=crop.height(), w=h*r;
            float maxW=maxCropW(cx), maxH=maxCropH(cy);
            if(w>maxW){ w=maxW; h=w/r; }
            if(h>maxH){ h=maxH; w=h*r; }
            crop.set(cx-w/2,cy-h/2,cx+w/2,cy+h/2);
        }
        afterTransform();
    }

    private float dispScale(){return zc.dispScale();}
    private float imgLeft(){return zc.left();}
    private float imgTop(){return zc.top();}

    private float maxCropW(float cx){
        float avail=Math.min(cx-imgLeft(), imgLeft()+img.getWidth()*dispScale()-cx);
        return Math.max(60,avail*2f-8);
    }
    private float maxCropH(float cy){
        float avail=Math.min(cy-imgTop(), imgTop()+img.getHeight()*dispScale()-cy);
        return Math.max(60,avail*2f-8);
    }

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        relayout();
    }

    @Override protected void onDraw(Canvas canvas){
        if(img==null) return;
        canvas.save();
        canvas.translate(imgLeft(),imgTop());
        canvas.scale(dispScale(),dispScale());
        canvas.drawBitmap(img,0,0,null);
        canvas.restore();

        // 裁剪框外压暗（EVEN_ODD 挖洞）
        Path p=new Path();
        p.addRect(0,0,getWidth(),getHeight(),Path.Direction.CW);
        p.addRect(crop,Path.Direction.CCW);
        canvas.drawPath(p,dimPaint);

        // 九宫格
        float l=crop.left,t=crop.top,r=crop.right,b=crop.bottom;
        for(int i=1;i<3;i++){
            canvas.drawLine(l+(r-l)*i/3f,t,l+(r-l)*i/3f,b,gridPaint);
            canvas.drawLine(l,t+(b-t)*i/3f,r,t+(b-t)*i/3f,gridPaint);
        }
        canvas.drawRect(crop,borderPaint);

        // 角/边手柄
        float[][] pts=handlePoints();
        float hr=dp(5);
        for(float[] pt:pts){
            canvas.drawCircle(pt[0],pt[1],hr+dp(1),handleRingPaint);
            canvas.drawCircle(pt[0],pt[1],hr,handlePaint);
        }
    }

    private float[][] handlePoints(){
        float l=crop.left,t=crop.top,r=crop.right,b=crop.bottom;
        float cx=crop.centerX(),cy=crop.centerY();
        return new float[][]{
            {l,t},{r,t},{r,b},{l,b},
            {cx,t},{r,cy},{cx,b},{l,cy}};
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        if(img==null) return false;
        float x=e.getX(),y=e.getY();
        int am=e.getActionMasked(), pc=e.getPointerCount();

        zc.onTouch(e);

        if(am==MotionEvent.ACTION_POINTER_DOWN && pc==2){ dragMode=-1; return true; }
        if(zc.isPinching()||pc>=2){ afterTransform(); return true; }
        if(am==MotionEvent.ACTION_POINTER_UP){ return true; }

        switch(am){
            case MotionEvent.ACTION_DOWN:
                lastX=x; lastY=y;
                dragMode=hitHandle(x,y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(dragMode==8){
                    zc.panBy(x-lastX,y-lastY);
                    afterTransform();
                }else if(dragMode>=0){
                    resize(dragMode,x,y);
                    afterTransform();
                }
                lastX=x; lastY=y;
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragMode=-1;
                return true;
        }
        return false;
    }

    private int hitHandle(float x,float y){
        float[][] pts=handlePoints();
        float tol=dp(14);
        for(int i=0;i<pts.length;i++){
            if(Math.abs(x-pts[i][0])<=tol && Math.abs(y-pts[i][1])<=tol) return i;
        }
        if(crop.contains(x,y)) return 8;
        return -1;
    }

    private void resize(int mode,float x,float y){
        float l=crop.left,t=crop.top,r=crop.right,b=crop.bottom;
        float il=imgLeft(),it=imgTop(),ir=il+img.getWidth()*dispScale(),ib=it+img.getHeight()*dispScale();
        x=Math.max(il+dp(10),Math.min(ir-dp(10),x));
        y=Math.max(it+dp(10),Math.min(ib-dp(10),y));

        if(ratio<=0){
            switch(mode){
                case 0: l=x; t=y; break;
                case 1: r=x; t=y; break;
                case 2: r=x; b=y; break;
                case 3: l=x; b=y; break;
                case 4: t=y; break;
                case 5: r=x; break;
                case 6: b=y; break;
                case 7: l=x; break;
            }
        }else{
            float ax,ay;
            if(mode==0){ax=r;ay=b;} else if(mode==1){ax=l;ay=b;}
            else if(mode==2){ax=l;ay=t;} else if(mode==3){ax=r;ay=t;}
            else {ax=crop.centerX();ay=crop.centerY();}
            float w=Math.abs(x-ax),h=Math.abs(y-ay);
            if(mode>=4){
                if(mode==4||mode==6){ h=Math.abs(y-ay); w=h*ratio; }
                else { w=Math.abs(x-ax); h=w/ratio; }
            }else{
                if(w/h>ratio) w=h*ratio; else h=w/ratio;
            }
            float nl,nr,nt,nb;
            boolean left =x<ax, top=y<ay;
            if(mode>=4){
                nl=crop.centerX()-w/2; nr=crop.centerX()+w/2;
                nt=crop.centerY()-h/2; nb=crop.centerY()+h/2;
                if(mode==4) nt=ay-h; else if(mode==6) nb=ay+h;
                if(mode==5) nr=ax+w; else if(mode==7) nl=ax-w;
            }else{
                nl=left?ax-w:ax; nr=left?ax:ax+w;
                nt=top?ay-h:ay; nb=top?ay:ay+h;
            }
            l=nl;t=nt;r=nr;b=nb;
        }
        if(r-l<dp(30)){ float c=(l+r)/2;l=c-dp(15);r=c+dp(15);}
        if(b-t<dp(30)){ float c=(t+b)/2;t=c-dp(15);b=c+dp(15);}
        crop.set(l,t,r,b);
    }

    /** 双指/单指平移后：先尽量让图片覆盖框，再把框限制在图片范围内。 */
    private void afterTransform(){
        coverFrame();
        clampCrop();
        invalidate();
    }

    /** 最小平移使图片覆盖裁剪框（zoom 足够时）。 */
    private void coverFrame(){
        float il=imgLeft(),it=imgTop();
        float ir=il+img.getWidth()*dispScale(),ib=it+img.getHeight()*dispScale();
        float dx=0f,dy=0f;
        if(il>crop.left) dx=crop.left-il;
        if(ir<crop.right) dx=crop.right-ir;
        if(it>crop.top) dy=crop.top-it;
        if(ib<crop.bottom) dy=crop.bottom-ib;
        if(dx!=0f||dy!=0f) zc.panBy(dx,dy);
    }

    /** 裁剪框不得超出图片映射区域；锁定比例时同步收缩另一维度。 */
    private void clampCrop(){
        float il=imgLeft(),it=imgTop();
        float ir=il+img.getWidth()*dispScale(),ib=it+img.getHeight()*dispScale();
        float l=Math.max(crop.left,il), t=Math.max(crop.top,it);
        float r=Math.min(crop.right,ir), b=Math.min(crop.bottom,ib);
        if(ratio>0){
            float w=r-l,h=b-t;
            if(w/h>ratio){ float nw=h*ratio; float cx=(l+r)/2; l=cx-nw/2;r=cx+nw/2; }
            else { float nh=w/ratio; float cy=(t+b)/2; t=cy-nh/2;b=cy+nh/2; }
        }
        crop.set(l,t,r,b);
    }

    /** 输出裁剪后的位图（裁剪框映射回原图像素）。 */
    public Bitmap getCropped(){
        float s=dispScale();
        int ix=Math.round((crop.left-imgLeft())/s);
        int iy=Math.round((crop.top-imgTop())/s);
        int iw=Math.round(crop.width()/s);
        int ih=Math.round(crop.height()/s);
        ix=Math.max(0,Math.min(img.getWidth()-1,ix));
        iy=Math.max(0,Math.min(img.getHeight()-1,iy));
        iw=Math.min(iw,img.getWidth()-ix);
        ih=Math.min(ih,img.getHeight()-iy);
        return Bitmap.createBitmap(img,ix,iy,iw,ih);
    }

    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
