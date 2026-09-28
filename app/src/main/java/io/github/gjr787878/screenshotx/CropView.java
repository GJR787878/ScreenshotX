package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** 形状裁剪：比例选项 + 手柄调整 + 图片缩放/平移，确认后输出裁剪位图。 */
public class CropView extends View {

    private Bitmap img;
    private float fit=1f, baseLeft=0f, baseTop=0f;
    private float zoom=1f, panX=0f, panY=0f;
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
    private final Matrix matrix=new Matrix();

    public CropView(Context c){
        super(c);
        setBackgroundColor(Color.BLACK);
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
        img=b; zoom=1f; panX=0f; panY=0f; inited=false; ratio=0f;
        requestLayout(); invalidate();
    }

    public void setRatio(float r){
        ratio=r;
        if(r>0 && crop.width()>0){
            float cx=crop.centerX(), cy=crop.centerY();
            float h=crop.height();
            float w=h*r;
            // 若超出可显示范围则改用宽度约束
            float maxW=maxCropW(cx), maxH=maxCropH(cy);
            if(w>maxW){ w=maxW; h=w/r; }
            if(h>maxH){ h=maxH; w=h*r; }
            crop.set(cx-w/2,cy-h/2,cx+w/2,cy+h/2);
            clampCrop();
        }
        invalidate();
    }

    public float getZoom(){return zoom;}
    public void zoomBy(float d){
        if(img==null) return;
        float nz=Math.max(1f,Math.min(3f,zoom+d));
        if(nz==zoom) return;
        float ccx=crop.centerX(), ccy=crop.centerY();
        float ipx=(ccx-imgLeft())/dispScale();
        float ipy=(ccy-imgTop())/dispScale();
        zoom=nz;
        panX=(ccx-ipx*dispScale())-baseLeft;
        panY=(ccy-ipy*dispScale())-baseTop;
        clampPan();
        invalidate();
    }

    private float dispScale(){return fit*zoom;}
    private float imgLeft(){return baseLeft+panX;}
    private float imgTop(){return baseTop+panY;}

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        if(img==null) return;
        fit=Math.min(w/(float)img.getWidth(),h/(float)img.getHeight());
        baseLeft=(w-img.getWidth()*fit)/2f;
        baseTop=(h-img.getHeight()*fit)/2f;
        if(!inited){
            // 初始裁剪框 = 图片显示区域内缩 8%
            float il=baseLeft, it=baseTop;
            float ir=baseLeft+img.getWidth()*fit, ib=baseTop+img.getHeight()*fit;
            float ix=(ir-il)*0.08f, iy=(ib-it)*0.08f;
            crop.set(il+ix,it+iy,ir-ix,ib-iy);
            inited=true;
        }
        clampPan();
    }

    private float maxCropW(float cx){
        float avail=Math.min(cx-imgLeft(), imgLeft()+img.getWidth()*dispScale()-cx);
        return Math.max(60,avail*2f-8);
    }
    private float maxCropH(float cy){
        float avail=Math.min(cy-imgTop(), imgTop()+img.getHeight()*dispScale()-cy);
        return Math.max(60,avail*2f-8);
    }

    @Override protected void onDraw(Canvas canvas){
        if(img==null) return;
        matrix.reset();
        matrix.postTranslate(imgLeft(),imgTop());
        matrix.postScale(dispScale(),dispScale(),imgLeft(),imgTop());
        canvas.drawBitmap(img,matrix,null);

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
        switch(e.getAction()){
            case MotionEvent.ACTION_DOWN:
                lastX=x; lastY=y;
                dragMode=hitHandle(x,y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(dragMode==8){
                    panX+=x-lastX; panY+=y-lastY;
                    clampPan();
                }else if(dragMode>=0){
                    resize(dragMode,x,y);
                }
                lastX=x; lastY=y;
                invalidate();
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
            // 锁定比例：角拖以对角为锚，边拖以中心为锚
            float ax,ay;
            if(mode==0){ax=r;ay=b;} else if(mode==1){ax=l;ay=b;}
            else if(mode==2){ax=l;ay=t;} else if(mode==3){ax=r;ay=t;}
            else {ax=crop.centerX();ay=crop.centerY();}
            float w=Math.abs(x-ax),h=Math.abs(y-ay);
            if(mode>=4){ // 边：按移动的那个维度算
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
        clampCrop();
        clampPan();
    }

    /** 裁剪框不得超出图片映射区域。 */
    private void clampCrop(){
        float il=imgLeft(),it=imgTop();
        float ir=il+img.getWidth()*dispScale(),ib=it+img.getHeight()*dispScale();
        float l=crop.left,t=crop.top,r=crop.right,b=crop.bottom;
        l=Math.max(il,l); r=Math.min(ir,r);
        t=Math.max(it,t); b=Math.min(ib,b);
        crop.set(l,t,r,b);
    }

    /** 平移后图片必须始终覆盖裁剪框。 */
    private void clampPan(){
        if(img==null) return;
        float iw=img.getWidth()*dispScale(), ih=img.getHeight()*dispScale();
        float il=imgLeft(),it=imgTop(),ir=il+iw,ib=it+ih;
        if(il>crop.left) panX+=crop.left-il;
        if(ir<crop.right) panX+=crop.right-ir;
        if(it>crop.top) panY+=crop.top-it;
        if(ib<crop.bottom) panY+=crop.bottom-ib;
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
