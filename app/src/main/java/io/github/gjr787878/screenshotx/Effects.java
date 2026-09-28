package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * 马赛克效果：像素化 / 高斯模糊(纯Java实现，不依赖已废弃的RenderScript) / 纯色遮挡。
 * 方法签名以 DrawView 的实际调用为准。
 */
public class Effects {

    /** 像素化：把区域按 block 取平均。 */
    public static Bitmap pixelate(Bitmap src, int block){
        if(block<2) return src.copy(Bitmap.Config.ARGB_8888,true);
        int w=src.getWidth(),h=src.getHeight();
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        int[] px=new int[w*h];
        src.getPixels(px,0,w,0,0,w,h);
        int[] res=new int[w*h];
        for(int by=0;by<h;by+=block){
            for(int bx=0;bx<w;bx+=block){
                int x2=Math.min(bx+block,w), y2=Math.min(by+block,h);
                long r=0,g=0,b=0,n=0;
                for(int y=by;y<y2;y++)
                    for(int x=bx;x<x2;x++){
                        int p=px[y*w+x];
                        r+=(p>>16)&255; g+=(p>>8)&255; b+=p&255; n++;
                    }
                int c=0xFF000000 | ((int)(r/n)<<16) | ((int)(g/n)<<8) | (int)(b/n);
                for(int y=by;y<y2;y++)
                    for(int x=bx;x<x2;x++) res[y*w+x]=c;
            }
        }
        out.setPixels(res,0,w,0,0,w,h);
        return out;
    }

    /**
     * 高斯模糊：用 3 次 O(n) 滑动窗口盒模糊逼近高斯，纯 Java 实现。
     * 不使用 RenderScript（ScriptIntrinsicBlur 在部分 ROM / target34 上会返回原图，导致涂抹后看不到效果）。
     * Context 参数仅为兼容调用方，内部不使用。
     */
    public static Bitmap gaussian(Context ctx, Bitmap src, float radius){
        int w=src.getWidth(),h=src.getHeight();
        Bitmap out=src.copy(Bitmap.Config.ARGB_8888,true);
        // 3 次盒模糊等价于较大半径的高斯；单次半径取约 0.6 即可达到 sigma≈radius 的观感
        int br=Math.max(1,Math.round(radius*0.6f));
        int[] a=new int[w*h];
        int[] b=new int[w*h];
        out.getPixels(a,0,w,0,0,w,h);
        for(int i=0;i<3;i++){
            boxH(a,b,w,h,br);
            boxV(b,a,w,h,br);
        }
        out.setPixels(a,0,w,0,0,w,h);
        return out;
    }

    private static int clamp255(int v){return v<0?0:(v>255?255:v);}

    /** 横向盒模糊（滑动窗口，O(n)）。 */
    private static void boxH(int[] src,int[] dst,int w,int h,int r){
        for(int y=0;y<h;y++){
            int o=y*w;
            int first=src[o];
            int fr=(first>>>16)&255,fg=(first>>>8)&255,fb=first&255;
            int sr=fr*(r+1),sg=fg*(r+1),sb=fb*(r+1);
            for(int j=1;j<=r;j++){
                int p=src[o+Math.min(j,w-1)];
                sr+=(p>>>16)&255; sg+=(p>>>8)&255; sb+=p&255;
            }
            int div=r+r+1;
            for(int x=0;x<w;x++){
                dst[o+x]=0xFF000000 | (clamp255(sr/div)<<16) | (clamp255(sg/div)<<8) | clamp255(sb/div);
                int po=src[o+Math.max(x-r,0)];
                int pi=src[o+Math.min(x+r+1,w-1)];
                sr+=((pi>>>16)&255)-((po>>>16)&255);
                sg+=((pi>>>8)&255)-((po>>>8)&255);
                sb+=(pi&255)-(po&255);
            }
        }
    }

    /** 纵向盒模糊（滑动窗口，O(n)）。 */
    private static void boxV(int[] src,int[] dst,int w,int h,int r){
        for(int x=0;x<w;x++){
            int first=src[x];
            int fr=(first>>>16)&255,fg=(first>>>8)&255,fb=first&255;
            int sr=fr*(r+1),sg=fg*(r+1),sb=fb*(r+1);
            for(int j=1;j<=r;j++){
                int p=src[Math.min(j,h-1)*w+x];
                sr+=(p>>>16)&255; sg+=(p>>>8)&255; sb+=p&255;
            }
            int div=r+r+1;
            for(int y=0;y<h;y++){
                dst[y*w+x]=0xFF000000 | (clamp255(sr/div)<<16) | (clamp255(sg/div)<<8) | clamp255(sb/div);
                int po=src[Math.max(y-r,0)*w+x];
                int pi=src[Math.min(y+r+1,h-1)*w+x];
                sr+=((pi>>>16)&255)-((po>>>16)&255);
                sg+=((pi>>>8)&255)-((po>>>8)&255);
                sb+=(pi&255)-(po&255);
            }
        }
    }

    /** 纯色遮挡：返回与 src 同尺寸、填充指定颜色（强制不透明）的位图。 */
    public static Bitmap solid(Bitmap src, int color){
        Bitmap b=Bitmap.createBitmap(src.getWidth(),src.getHeight(),Bitmap.Config.ARGB_8888);
        b.eraseColor(color|0xFF000000);
        return b;
    }
}
