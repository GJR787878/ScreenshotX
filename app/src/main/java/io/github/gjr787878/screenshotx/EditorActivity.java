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

import java.io.File;
import java.io.FileOutputStream;

public class EditorActivity extends Activity {

    private DrawView drawView;
    private int selectedFunc = 0;
    private int curColor = Color.RED;
    private ColorWheelView wheel;
    private final ImageView[] penIcons = new ImageView[5];
    private final TextView[] penTips = new TextView[5];

    private final int[] PEN_RES = {
        R.drawable.ic_pen_ball, R.drawable.ic_pen_marker, R.drawable.ic_pen_pencil,
        R.drawable.ic_pen_fountain, R.drawable.ic_pen_eraser};
    private final String[] PEN_NAMES = {"圆珠笔","荧光笔","铅笔","钢笔","橡皮擦"};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String path = getIntent().getStringExtra("path");
        if (path==null){finish();return;}
        Bitmap src=BitmapFactory.decodeFile(path);
        if(src==null){finish();return;}

        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        drawView=new DrawView(this);
        drawView.setBitmap(src);
        drawView.setColor(curColor);
        FrameLayout.LayoutParams dvp=new FrameLayout.LayoutParams(-1,-1);
        dvp.topMargin=dp(64); dvp.bottomMargin=dp(200);
        root.addView(drawView,dvp);

        // 顶部栏
        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(16),dp(18),dp(16),dp(8));
        root.addView(top,frameTop(dp(58)));
        topIcon(top,R.drawable.ic_trash,v->finish());
        top.addView(stretch());
        topIcon(top,R.drawable.ic_undo,v->drawView.undo());
        topIcon(top,R.drawable.ic_redo,v->drawView.redo());
        top.addView(stretch());
        topIcon(top,R.drawable.ic_share,v->Toast.makeText(this,"分享",Toast.LENGTH_SHORT).show());
        topIcon(top,R.drawable.ic_check,v->save());

        // 底部
        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16),dp(6),dp(16),dp(16));
        root.addView(bottom,frameBottom(dp(192)));

        // 笔条(深色圆角横条)
        LinearLayout penBar=new LinearLayout(this);
        penBar.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        penBar.setPadding(dp(8),dp(8),dp(8),dp(8));
        GradientDrawable barBg=new GradientDrawable();
        barBg.setCornerRadius(dp(18)); barBg.setColor(0x3326262A);
        penBar.setBackground(barBg);
        LinearLayout.LayoutParams barLp=new LinearLayout.LayoutParams(-1,dp(78));
        barLp.setMargins(0,0,0,dp(12));
        bottom.addView(penBar,barLp);

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
            penTips[i]=tip;
            item.addView(tip);

            ImageView pen=new ImageView(this);
            pen.setImageResource(PEN_RES[i]);
            pen.setPadding(dp(4),dp(2),dp(4),dp(2));
            penIcons[i]=pen;
            final int idx=i;
            pen.setOnClickListener(v->selectPen(idx));
            LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(dp(34),dp(52));
            item.addView(pen,ilp);

            LinearLayout.LayoutParams itemLp=new LinearLayout.LayoutParams(0,-2,1);
            penBar.addView(item,itemLp);
        }
        selectPen(0);

        // 右侧颜色轮
        FrameLayout wheelWrap=new FrameLayout(this);
        wheel=new ColorWheelView(this);
        wheel.setCenterColor(curColor);
        wheel.setOnClickListener(v->openPicker());
        wheelWrap.addView(wheel,new FrameLayout.LayoutParams(dp(42),dp(42)));
        penBar.addView(wheelWrap,new LinearLayout.LayoutParams(dp(50),dp(64)));

        // 功能行
        LinearLayout funcs=new LinearLayout(this);
        funcs.setGravity(Gravity.CENTER);
        bottom.addView(funcs,new LinearLayout.LayoutParams(-1,dp(92)));
        addFunc(funcs,R.drawable.ic_pen,"标记",0);
        addFunc(funcs,R.drawable.ic_text,"文字",1);
        addFunc(funcs,R.drawable.ic_mosaic,"马赛克",2);
        addFunc(funcs,R.drawable.ic_scan,"识文",3);
        addFunc(funcs,R.drawable.ic_crop,"形状裁剪",4);
        addFunc(funcs,R.drawable.ic_pen,"高级编辑",5);

        setContentView(root);
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
        ColorPickerDialog d=new ColorPickerDialog(this, curColor, c->{
            curColor=c;
            wheel.setCenterColor(c);
            drawView.setColor(c);
        });
        d.show();
    }

    private void addFunc(LinearLayout bar,int icon,String label,int index){
        LinearLayout item=new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        ImageView circle=new ImageView(this);
        circle.setImageResource(icon);
        GradientDrawable bg=new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        boolean sel=index==selectedFunc;
        if(sel){bg.setColor(0xFFFFFFFF);circle.setColorFilter(0xFF000000);}
        else{bg.setColor(0x24FFFFFF);circle.setColorFilter(0xFFFFFFFF);}
        circle.setBackground(bg);
        circle.setPadding(dp(12),dp(12),dp(12),dp(12));
        item.addView(circle,new LinearLayout.LayoutParams(dp(50),dp(50)));
        TextView tv=new TextView(this);
        tv.setText(label); tv.setTextColor(sel?0xFFFFFFFF:0xCCFFFFFF);
        tv.setTextSize(12); tv.setGravity(Gravity.CENTER);
        tv.setPadding(0,dp(5),0,0);
        item.addView(tv);
        bar.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        item.setOnClickListener(v->{
            if(index==0)return;
            Toast.makeText(this,label+" 开发中",Toast.LENGTH_SHORT).show();
        });
    }

    private void topIcon(LinearLayout bar,int icon,View.OnClickListener l){
        ImageView iv=new ImageView(this);
        iv.setImageResource(icon); iv.setPadding(dp(11),dp(11),dp(11),dp(11));
        iv.setOnClickListener(l);
        bar.addView(iv,new LinearLayout.LayoutParams(dp(46),dp(46)));
    }

    private void save(){
        try{
            Bitmap result=drawView.getResultBitmap();
            File dir=android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_PICTURES+"/Screenshots");
            dir.mkdirs();
            File f=new File(dir,"ScreenshotX_"+System.currentTimeMillis()+".png");
            FileOutputStream fos=new FileOutputStream(f);
            result.compress(Bitmap.CompressFormat.PNG,100,fos); fos.close();
            Toast.makeText(this,"已保存到 Pictures/Screenshots",Toast.LENGTH_LONG).show();
            finish();
        }catch(Exception e){Toast.makeText(this,"保存失败: "+e.getMessage(),Toast.LENGTH_SHORT).show();}
    }

    private FrameLayout.LayoutParams frameTop(int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,h);p.gravity=Gravity.TOP;return p;}
    private FrameLayout.LayoutParams frameBottom(int h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,h);p.gravity=Gravity.BOTTOM;return p;}
    private View stretch(){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(0,1,1));return v;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density);}
}
