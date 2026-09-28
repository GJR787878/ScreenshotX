package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.content.ContentValues;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.OutputStream;

public class EditorActivity extends Activity {

    private DrawView drawView;
    private CropView cropView;
    private FrameLayout panelHost;
    private View markPanel, mosaicPanel, cropPanel;
    private int mode = 0;

    private int curColor = Color.RED;
    private ColorWheelView wheel;
    private final ImageView[] penIcons = new ImageView[5];
    private final TextView[] penTips = new TextView[5];

    private final int[] PEN_RES = {
        R.drawable.ic_pen_ball, R.drawable.ic_pen_marker, R.drawable.ic_pen_pencil,
        R.drawable.ic_pen_fountain, R.drawable.ic_pen_eraser};
    private final String[] PEN_NAMES = {"圆珠笔","荧光笔","铅笔","钢笔","橡皮擦"};

    // 马赛克
    private final TextView[] effectChips = new TextView[3];
    private final TextView[] wayChips = new TextView[2];
    private int curEffect = DrawView.MOS_PIXEL;
    private boolean curRect = false;

    // 裁剪
    private final TextView[] ratioChips = new TextView[6];
    private final float[] RATIOS = {0f,1f,4f/3f,3f/4f,16f/9f,9f/16f};
    private TextView zoomLabel;

    private final ImageView[] modeCircles = new ImageView[3];
    private final TextView[] modeLabels = new TextView[3];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String path = getIntent().getStringExtra("path");
        if(path==null){finish();return;}
        Bitmap src=BitmapFactory.decodeFile(path);
        if(src==null){finish();return;}

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        // 顶部栏
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16),dp(14),dp(16),dp(6));
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(58)));
        topIcon(top,R.drawable.ic_trash,v->finish());
        top.addView(stretch());
        topIcon(top,R.drawable.ic_undo,v->drawView.undo());
        topIcon(top,R.drawable.ic_redo,v->drawView.redo());
        top.addView(stretch());
        topIcon(top,R.drawable.ic_share,v->Toast.makeText(this,"分享",Toast.LENGTH_SHORT).show());
        topIcon(top,R.drawable.ic_check,v->topConfirm());

        // 中间：DrawView 与 CropView 叠加
        FrameLayout middle=new FrameLayout(this);
        drawView=new DrawView(this);
        drawView.setBitmap(src);
        drawView.setColor(curColor);
        middle.addView(drawView,new FrameLayout.LayoutParams(-1,-1));
        cropView=new CropView(this);
        cropView.setVisibility(View.GONE);
        middle.addView(cropView,new FrameLayout.LayoutParams(-1,-1));
        root.addView(middle,new LinearLayout.LayoutParams(-1,0,1));

        // 底部
        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16),dp(4),dp(16),dp(12));
        root.addView(bottom,new LinearLayout.LayoutParams(-1,-2));

        panelHost=new FrameLayout(this);
        bottom.addView(panelHost,new LinearLayout.LayoutParams(-1,-2));
        markPanel=buildMarkPanel();
        mosaicPanel=buildMosaicPanel();
        cropPanel=buildCropPanel();
        panelHost.addView(markPanel,new FrameLayout.LayoutParams(-1,-2));
        panelHost.addView(mosaicPanel,new FrameLayout.LayoutParams(-1,-2));
        panelHost.addView(cropPanel,new FrameLayout.LayoutParams(-1,-2));

        // 主功能行：标记 / 马赛克 / 形状裁剪
        LinearLayout funcs=new LinearLayout(this);
        funcs.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,dp(86));
        flp.topMargin=dp(4);
        bottom.addView(funcs,flp);
        addMode(funcs,R.drawable.ic_pen,"标记",0);
        addMode(funcs,R.drawable.ic_mosaic,"马赛克",1);
        addMode(funcs,R.drawable.ic_crop,"形状裁剪",2);

        setContentView(root);
        selectMode(0);
    }

    // ================= 标记面板 =================
    private View buildMarkPanel(){
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        LinearLayout penBar=new LinearLayout(this);
        penBar.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        penBar.setPadding(dp(8),dp(6),dp(8),dp(8));
        GradientDrawable barBg=new GradientDrawable();
        barBg.setCornerRadius(dp(18)); barBg.setColor(0x3326262A);
        penBar.setBackground(barBg);
        LinearLayout.LayoutParams barLp=new LinearLayout.LayoutParams(-1,-2);
        barLp.setMargins(0,0,0,dp(10));
        panel.addView(penBar,barLp);

        for(int i=0;i<5;i++){
            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);

            TextView tip=new TextView(this);
            tip.setText(PEN_NAMES[i]); tip.setTextSize(12);
            tip.setTextColor(0xFFFFFFFF); tip.setGravity(Gravity.CENTER);
            GradientDrawable tb=new GradientDrawable();
            tb.setCornerRadius(dp(10)); tb.setColor(0xFF4A4A50);
            tip.setBackground(tb);
            tip.setPadding(dp(10),dp(4),dp(10),dp(4));
            tip.setVisibility(i==0?View.VISIBLE:View.GONE);
            tip.setTranslationY(dp(-2));
            penTips[i]=tip;
            LinearLayout.LayoutParams tipLp=new LinearLayout.LayoutParams(-2,-2);
            tipLp.setMargins(0,0,0,dp(4));
            item.addView(tip,tipLp);

            ImageView pen=new ImageView(this);
            pen.setImageResource(PEN_RES[i]);
            penIcons[i]=pen;
            final int idx=i;
            pen.setOnClickListener(v->selectPen(idx));
            item.addView(pen,new LinearLayout.LayoutParams(dp(32),dp(50)));

            penBar.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        selectPen(0);

        wheel=new ColorWheelView(this);
        wheel.setCenterColor(curColor);
        wheel.setOnClickListener(v->openPicker());
        LinearLayout wheelWrap=new LinearLayout(this);
        wheelWrap.setGravity(Gravity.CENTER);
        wheelWrap.addView(wheel,new LinearLayout.LayoutParams(dp(40),dp(40)));
        penBar.addView(wheelWrap,new LinearLayout.LayoutParams(dp(50),dp(60)));

        panel.addView(buildWidthRow());
        return panel;
    }

    private View buildWidthRow(){
        LinearLayout widthRow=new LinearLayout(this);
        widthRow.setOrientation(LinearLayout.HORIZONTAL);
        widthRow.setGravity(Gravity.CENTER_VERTICAL);
        widthRow.setPadding(dp(10),0,dp(10),0);
        TextView wlabel=new TextView(this);
        wlabel.setText("粗细"); wlabel.setTextColor(0xCCFFFFFF); wlabel.setTextSize(12);
        widthRow.addView(wlabel,new LinearLayout.LayoutParams(-2,-2));
        android.widget.SeekBar widthSeek=new android.widget.SeekBar(this);
        widthSeek.setMax(570); widthSeek.setProgress(70);
        widthSeek.getProgressDrawable().setColorFilter(0xFF9AA0AA,
                android.graphics.PorterDuff.Mode.SRC_IN);
        widthSeek.getThumb().setColorFilter(0xFFFFFFFF,
                android.graphics.PorterDuff.Mode.SRC_IN);
        widthRow.addView(widthSeek,new LinearLayout.LayoutParams(0,dp(36),1));
        widthSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(android.widget.SeekBar sb,int p,boolean fromUser){
                drawView.setWidthScale(0.3f+p/100f);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb){}
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb){}
        });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(40));
        lp.bottomMargin=dp(2);
        widthRow.setLayoutParams(lp);
        return widthRow;
    }

    // ================= 马赛克面板 =================
    private View buildMosaicPanel(){
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        // 效果行
        panel.addView(label("效果"));
        LinearLayout effRow=new LinearLayout(this);
        effRow.setGravity(Gravity.CENTER);
        String[] en={"像素化","高斯模糊","黑色遮挡"};
        for(int i=0;i<3;i++){
            final int idx=i;
            effectChips[i]=chip(en[i],i==curEffect,v->selectEffect(idx));
            effRow.addView(effectChips[i],chipLp());
        }
        panel.addView(effRow);

        // 方式行
        panel.addView(label("方式"));
        LinearLayout wayRow=new LinearLayout(this);
        wayRow.setGravity(Gravity.CENTER);
        String[] wn={"涂抹","框选"};
        for(int i=0;i<2;i++){
            final int idx=i;
            wayChips[i]=chip(wn[i],(i==1)==curRect,v->selectWay(idx==1));
            wayRow.addView(wayChips[i],chipLp());
        }
        panel.addView(wayRow);

        // 粗细（涂抹用）
        panel.addView(buildWidthRow());
        return panel;
    }

    private void selectEffect(int idx){
        curEffect=idx;
        drawView.setMosaicEffect(idx);
        for(int i=0;i<3;i++) styleChip(effectChips[i],i==idx);
    }
    private void selectWay(boolean rect){
        curRect=rect;
        drawView.setMosaicRect(rect);
        for(int i=0;i<2;i++) styleChip(wayChips[i],(i==1)==rect);
    }

    // ================= 裁剪面板 =================
    private View buildCropPanel(){
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        panel.addView(label("比例"));
        LinearLayout rRow=new LinearLayout(this);
        rRow.setGravity(Gravity.CENTER);
        String[] rn={"自由","1:1","4:3","3:4","16:9","9:16"};
        for(int i=0;i<6;i++){
            final int idx=i;
            ratioChips[i]=chip(rn[i],i==0,v->selectRatio(idx));
            rRow.addView(ratioChips[i],chipLp());
        }
        panel.addView(rRow);

        // 缩放行
        LinearLayout zRow=new LinearLayout(this);
        zRow.setGravity(Gravity.CENTER);
        TextView minus=chip("－",false,v->{cropView.zoomBy(-0.25f);updateZoomLabel();});
        zoomLabel=new TextView(this);
        zoomLabel.setText("100%"); zoomLabel.setTextColor(0xFFFFFFFF);
        zoomLabel.setTextSize(13); zoomLabel.setGravity(Gravity.CENTER);
        zoomLabel.setMinWidth(dp(64));
        TextView plus=chip("＋",false,v->{cropView.zoomBy(0.25f);updateZoomLabel();});
        zRow.addView(minus,chipLp());
        zRow.addView(zoomLabel,new LinearLayout.LayoutParams(0,-2,1));
        zRow.addView(plus,chipLp());
        panel.addView(label("缩放"));
        panel.addView(zRow);

        // 操作行
        LinearLayout aRow=new LinearLayout(this);
        aRow.setGravity(Gravity.CENTER);
        TextView cancel=chip("取消",false,v->cancelCrop());
        TextView apply=chip("应用裁剪",true,v->applyCrop());
        LinearLayout.LayoutParams cl=chipLp(); cl.weight=1;
        LinearLayout.LayoutParams al=chipLp(); al.weight=1;
        aRow.addView(cancel,cl);
        aRow.addView(apply,al);
        LinearLayout.LayoutParams arp=new LinearLayout.LayoutParams(-1,dp(46));
        arp.topMargin=dp(6);
        panel.addView(aRow,arp);
        return panel;
    }

    private void selectRatio(int idx){
        cropView.setRatio(RATIOS[idx]);
        for(int i=0;i<6;i++) styleChip(ratioChips[i],i==idx);
    }
    private void updateZoomLabel(){
        zoomLabel.setText(Math.round(cropView.getZoom()*100)+"%");
    }

    private void applyCrop(){
        Bitmap c=cropView.getCropped();
        drawView.setBitmap(c);
        drawView.setColor(curColor);
        cropView.setVisibility(View.GONE);
        drawView.setVisibility(View.VISIBLE);
        selectMode(0);
        Toast.makeText(this,"已裁剪",Toast.LENGTH_SHORT).show();
    }
    private void cancelCrop(){
        cropView.setVisibility(View.GONE);
        drawView.setVisibility(View.VISIBLE);
        selectMode(0);
    }

    // ================= 模式切换 =================
    private void selectMode(int m){
        mode=m;
        markPanel.setVisibility(m==0?View.VISIBLE:View.GONE);
        mosaicPanel.setVisibility(m==1?View.VISIBLE:View.GONE);
        cropPanel.setVisibility(m==2?View.VISIBLE:View.GONE);

        if(m==2){
            Bitmap flat=drawView.getResultBitmap();
            cropView.setImage(flat);
            cropView.setVisibility(View.VISIBLE);
            drawView.setVisibility(View.GONE);
            for(int i=0;i<6;i++) styleChip(ratioChips[i],i==0);
            zoomLabel.setText("100%");
        }else{
            cropView.setVisibility(View.GONE);
            drawView.setVisibility(View.VISIBLE);
            drawView.setMosaicMode(m==1);
            if(m==1){ drawView.setMosaicEffect(curEffect); drawView.setMosaicRect(curRect); }
        }
        for(int i=0;i<3;i++) styleMode(i,i==m);
    }

    private void addMode(LinearLayout bar,int icon,String label,int index){
        LinearLayout item=new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        ImageView circle=new ImageView(this);
        circle.setImageResource(icon);
        GradientDrawable bg=new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x24FFFFFF);
        circle.setBackground(bg);
        circle.setPadding(dp(12),dp(12),dp(12),dp(12));
        modeCircles[index]=circle;
        item.addView(circle,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView tv=new TextView(this);
        tv.setText(label); tv.setTextColor(0xCCFFFFFF);
        tv.setTextSize(12); tv.setGravity(Gravity.CENTER);
        tv.setPadding(0,dp(4),0,0);
        modeLabels[index]=tv;
        item.addView(tv);
        bar.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        item.setOnClickListener(v->selectMode(index));
    }

    private void styleMode(int i,boolean sel){
        GradientDrawable bg=new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        ImageView c=modeCircles[i];
        if(sel){bg.setColor(0xFFFFFFFF);c.setColorFilter(0xFF000000);modeLabels[i].setTextColor(0xFFFFFFFF);}
        else{bg.setColor(0x24FFFFFF);c.setColorFilter(0xFFFFFFFF);modeLabels[i].setTextColor(0xCCFFFFFF);}
        c.setBackground(bg);
    }

    private void selectPen(int idx){
        drawView.setTool(idx);
        for(int i=0;i<5;i++){
            penTips[i].setVisibility(i==idx?View.VISIBLE:View.GONE);
            penIcons[i].setAlpha(i==idx?1f:0.55f);
            penIcons[i].setScaleX(i==idx?1.08f:1f);
            penIcons[i].setScaleY(i==idx?1.08f:1f);
        }
    }

    private void openPicker(){
        ColorPickerDialog d=new ColorPickerDialog(this,curColor,c->{
            curColor=c; wheel.setCenterColor(c); drawView.setColor(c);
        });
        d.show();
    }

    private void topConfirm(){
        if(mode==2) applyCrop();
        else save();
    }

    private void topIcon(LinearLayout bar,int icon,View.OnClickListener l){
        ImageView iv=new ImageView(this);
        iv.setImageResource(icon); iv.setPadding(dp(11),dp(11),dp(11),dp(11));
        iv.setOnClickListener(l);
        bar.addView(iv,new LinearLayout.LayoutParams(dp(46),dp(46)));
    }

    // ================= 通用控件 =================
    private TextView chip(String text,boolean selected,View.OnClickListener l){
        TextView t=new TextView(this);
        t.setText(text); t.setTextSize(13); t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14),dp(7),dp(14),dp(7));
        t.setOnClickListener(l);
        styleChip(t,selected);
        return t;
    }
    private void styleChip(TextView t,boolean sel){
        GradientDrawable g=new GradientDrawable();
        g.setCornerRadius(dp(20));
        if(sel){g.setColor(0xFFFFFFFF);t.setTextColor(0xFF000000);}
        else{g.setColor(0x26FFFFFF);t.setTextColor(0xFFFFFFFF);g.setStroke(dp(1),0x55FFFFFF);}
        t.setBackground(g);
    }
    private LinearLayout.LayoutParams chipLp(){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);
        lp.setMargins(dp(5),dp(4),dp(5),dp(4));
        return lp;
    }
    private TextView label(String text){
        TextView t=new TextView(this);
        t.setText(text); t.setTextColor(0xAAFFFFFF); t.setTextSize(12);
        t.setPadding(dp(10),dp(6),dp(10),dp(2));
        return t;
    }

    private void save(){
        Toast.makeText(this,"保存中…",Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try{
                Bitmap result=drawView.getResultBitmap();
                String name="ScreenshotX_"+System.currentTimeMillis()+".png";
                ContentValues values=new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
                values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Screenshots");
                values.put(MediaStore.Images.Media.IS_PENDING,1);
                Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(uri==null) throw new RuntimeException("MediaStore insert failed");
                try(OutputStream os=getContentResolver().openOutputStream(uri)){
                    if(os==null) throw new RuntimeException("openOutputStream null");
                    result.compress(Bitmap.CompressFormat.PNG,100,os);
                }
                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING,0);
                getContentResolver().update(uri,values,null,null);
                runOnUiThread(() -> {
                    Toast.makeText(this,"已保存到 Pictures/Screenshots",Toast.LENGTH_LONG).show();
                    finish();
                });
            }catch(Exception e){
                runOnUiThread(() -> Toast.makeText(this,"保存失败: "+e.getMessage(),Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private View stretch(){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(0,1,1));return v;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density);}
}
