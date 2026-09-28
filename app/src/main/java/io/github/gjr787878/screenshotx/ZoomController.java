package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * 共享的图片变换：FIT_CENTER 基础上叠加双指捏合缩放与双指平移。
 * DrawView（标记/马赛克）与 CropView（裁剪）共用。
 */
public class ZoomController {

    public interface OnChange { void changed(); }

    private float fit=1f, baseLeft=0f, baseTop=0f;
    private float zoom=1f, panX=0f, panY=0f;
    private final float minZoom, maxZoom;
    private final boolean coverView; // true=图片始终铺满视图不留黑边；false=允许自由平移(裁剪)
    private int vw, vh, iw, ih;
    private boolean pinching=false;
    private float focusX, focusY;
    private final OnChange onChange;
    private final ScaleGestureDetector sd;

    public ZoomController(Context c, float minZ, float maxZ, boolean cover, OnChange cb){
        minZoom=minZ; maxZoom=maxZ; coverView=cover; onChange=cb;
        sd=new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){
                float nz=Math.max(minZoom,Math.min(maxZoom,zoom*d.getScaleFactor()));
                float fx=d.getFocusX(), fy=d.getFocusY();
                float ipx=(fx-left())/dispScale();
                float ipy=(fy-top())/dispScale();
                zoom=nz;
                panX=(fx-ipx*dispScale())-baseLeft;
                panY=(fy-ipy*dispScale())-baseTop;
                clamp();
                notifyChange();
                return true;
            }
        });
    }

    public void setSize(int viewW,int viewH,int imgW,int imgH){
        vw=viewW; vh=viewH; iw=imgW; ih=imgH;
        fit=Math.min(viewW/(float)imgW, viewH/(float)imgH);
        baseLeft=(viewW-imgW*fit)/2f;
        baseTop=(viewH-imgH*fit)/2f;
        clamp();
    }

    public void reset(){ zoom=1f; panX=0f; panY=0f; clamp(); }

    public float dispScale(){return fit*zoom;}
    public float left(){return baseLeft+panX;}
    public float top(){return baseTop+panY;}
    public float getZoom(){return zoom;}
    public boolean isPinching(){return pinching;}

    public float[] toImg(float x,float y){
        return new float[]{(x-left())/dispScale(), (y-top())/dispScale()};
    }

    /** 单指平移（裁剪框内拖动）。 */
    public void panBy(float dx,float dy){
        panX+=dx; panY+=dy; clamp();
    }

    /** 处理触摸；返回当前是否处于双指状态。 */
    public boolean onTouch(MotionEvent e){
        sd.onTouchEvent(e);
        int n=e.getPointerCount();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_POINTER_DOWN:
                if(n==2){
                    pinching=true;
                    focusX=(e.getX(0)+e.getX(1))/2f;
                    focusY=(e.getY(0)+e.getY(1))/2f;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if(n==2 && pinching){
                    float fx=(e.getX(0)+e.getX(1))/2f;
                    float fy=(e.getY(0)+e.getY(1))/2f;
                    panX+=fx-focusX; panY+=fy-focusY;
                    focusX=fx; focusY=fy;
                    clamp();
                    notifyChange();
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if(n<=2) pinching=false;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pinching=false;
                break;
        }
        return pinching;
    }

    private void clamp(){
        float s=dispScale();
        float dw=iw*s, dh=ih*s;
        if(coverView){
            if(dw<=vw) panX=(vw-dw)/2f-baseLeft;
            else{
                float l=left(), r=l+dw;
                if(l>0) panX-=l; else if(r<vw) panX+=vw-r;
            }
            if(dh<=vh) panY=(vh-dh)/2f-baseTop;
            else{
                float t=top(), b=t+dh;
                if(t>0) panY-=t; else if(b<vh) panY+=vh-b;
            }
        }
    }

    private void notifyChange(){ if(onChange!=null) onChange.changed(); }
}
