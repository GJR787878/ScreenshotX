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
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.OutputStream;

public class EditorActivity extends Activity {

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

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

    // 马赛克：单行 5 个按钮（0-2 效果，3-4 方式）
    private final TextView[] mosChips = new TextView[5];
    private int curEffect = DrawView.MOS_PIXEL;
    private boolean curRect = false;

    // 裁剪
    private final TextView[] ratioChips = new TextView[6];
    private final float[] RATIOS = {0f,1f,4f/3f,3f/4f,16f/9f,9f/16f};

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
        topIcon(top,R.drawable.ic_share,v->Toast.makeText(this,R.string.share,Toast.LENGTH_SHORT).show());
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
        // 固定面板高度：三种模式面板高度一致，切换时中间预览图不随之变动
        bottom.addView(panelHost,new LinearLayout.LayoutParams(-1,dp(134)));
        markPanel=buildMarkPanel();
        mosaicPanel=buildMosaicPanel();
        cropPanel=buildCropPanel();
        panelHost.addView(markPanel,new FrameLayout.LayoutParams(-1,-1));
        panelHost.addView(mosaicPanel,new FrameLayout.LayoutParams(-1,-1));
        panelHost.addView(cropPanel,new FrameLayout.LayoutParams(-1,-1));

        // 主功能行：标记 / 马赛克 / 形状裁剪
        LinearLayout funcs=new LinearLayout(this);
        funcs.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,dp(86));
        flp.topMargin=dp(4);
        bottom.addView(funcs,flp);
        addMode(funcs,R.drawable.ic_pen,getString(R.string.mode_mark),0);
        addMode(funcs,R.drawable.ic_mosaic,getString(R.string.mode_mosaic),1);
        addMode(funcs,R.drawable.ic_crop,getString(R.string.mode_crop),2);

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

        String[] penNames={getString(R.string.pen_ball),getString(R.string.pen_marker),
                getString(R.string.pen_pencil),getString(R.string.pen_fountain),getString(R.string.pen_eraser)};
        for(int i=0;i<5;i++){
            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);

            TextView tip=new TextView(this);
            tip.setText(penNames[i]); tip.setTextSize(12);
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
        wlabel.setText(R.string.width); wlabel.setTextColor(0xCCFFFFFF); wlabel.setTextSize(12);
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
        panel.setGravity(Gravity.CENTER_VERTICAL);

        // 单行：像素 / 模糊 /黑块 ｜ 涂抹 / 框选
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        String[] names={getString(R.string.mos_pixel),getString(R.string.mos_blur),getString(R.string.mos_black),getString(R.string.mos_paint),getString(R.string.mos_rect)};
        for(int i=0;i<5;i++){
            if(i==3){ // 效果与方式之间加一条竖向分隔
                View d=new View(this);
                LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(dp(1),dp(24));
                dlp.setMargins(dp(5),0,dp(5),0);
                d.setBackgroundColor(0x44FFFFFF);
                row.addView(d,dlp);
            }
            final int idx=i;
            boolean sel=i<3 ? i==curEffect : (i==4)==curRect;
            mosChips[i]=chip(names[i],sel,v->{
                if(idx<3) selectEffect(idx); else selectWay(idx==4);
            });
            row.addView(mosChips[i],chipLp());
        }
        panel.addView(row,new LinearLayout.LayoutParams(-1,-2));

        // 粗细（涂抹用）
        panel.addView(buildWidthRow());
        return panel;
    }

    private void selectEffect(int idx){
        curEffect=idx;
        drawView.setMosaicEffect(idx);
        refreshMosChips();
    }
    private void selectWay(boolean rect){
        curRect=rect;
        drawView.setMosaicRect(rect);
        refreshMosChips();
    }
    private void refreshMosChips(){
        for(int i=0;i<5;i++){
            boolean sel=i<3 ? i==curEffect : (i==4)==curRect;
            styleChip(mosChips[i],sel);
        }
    }

    // ================= 裁剪面板 =================
    private View buildCropPanel(){
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);

        panel.addView(label(getString(R.string.ratio)));
        LinearLayout rRow=new LinearLayout(this);
        rRow.setGravity(Gravity.CENTER);
        String[] rn={getString(R.string.ratio_free),"1:1","4:3","3:4","16:9","9:16"};
        for(int i=0;i<6;i++){
            final int idx=i;
            ratioChips[i]=chip(rn[i],i==0,v->selectRatio(idx));
            rRow.addView(ratioChips[i],chipLp());
        }
        panel.addView(rRow);

        // 操作行（缩放统一由双指捏合完成，不再提供按钮）
        LinearLayout aRow=new LinearLayout(this);
        aRow.setGravity(Gravity.CENTER);
        TextView cancel=chip(getString(R.string.cancel),false,v->cancelCrop());
        TextView apply=chip(getString(R.string.apply_crop),true,v->applyCrop());
        LinearLayout.LayoutParams cl=chipLp(); cl.weight=1;
        LinearLayout.LayoutParams al=chipLp(); al.weight=1;
        aRow.addView(cancel,cl);
        aRow.addView(apply,al);
        LinearLayout.LayoutParams arp=new LinearLayout.LayoutParams(-1,dp(48));
        arp.topMargin=dp(14);
        panel.addView(aRow,arp);
        return panel;
    }

    private void selectRatio(int idx){
        cropView.setRatio(RATIOS[idx]);
        for(int i=0;i<6;i++) styleChip(ratioChips[i],i==idx);
    }

    private void applyCrop(){
        Bitmap c=cropView.getCropped();
        drawView.setBitmap(c);
        drawView.setColor(curColor);
        cropView.setVisibility(View.GONE);
        drawView.setVisibility(View.VISIBLE);
        selectMode(0);
        Toast.makeText(this,R.string.cropped,Toast.LENGTH_SHORT).show();
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
            cropView.setVisibility(View.VISIBLE);
            drawView.setVisibility(View.GONE);
            for(int i=0;i<6;i++) styleChip(ratioChips[i],i==0);
            if(!drawView.isEdited()){
                // 无标注：直接复用原图，避免主线程整屏 copy，进入裁剪更快
                cropView.setImage(drawView.getBase());
            }else{
                // 有标注：先用原图占位（裁剪框按已知尺寸初始化），后台合成标注后再替换
                cropView.setImage(drawView.getBase());
                new Thread(()->{
                    Bitmap flat=drawView.getResultBitmap();
                    runOnUiThread(()->cropView.setImage(flat));
                }).start();
            }
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
        Toast.makeText(this,R.string.saving,Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(this,R.string.saved_to,Toast.LENGTH_LONG).show();
                    finish();
                });
            }catch(Exception e){
                runOnUiThread(() -> Toast.makeText(this,getString(R.string.save_fail_prefix)+e.getMessage(),Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private View stretch(){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(0,1,1));return v;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density);}
}
