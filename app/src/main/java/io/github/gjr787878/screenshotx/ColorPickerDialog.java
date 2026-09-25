package io.github.gjr787878.screenshotx;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ColorPickerDialog {

    public interface OnPicked { void picked(int color); }

    private final Context ctx;
    private final Dialog dialog;
    private HSVView hsv;
    private AlphaSlider alphaSlider;
    private GridLayout grid;
    private TextView gridTab, ringTab;
    private View bigSwatch;
    private int current;
    private final OnPicked callback;

    private static final int[] PRESETS = {
        0xFFF04040,0xFFFF7A3D,0xFFF5C518,0xFF2BB24C,0xFF1E90FF,
        0xFFA24DE8,0xFF000000,0xFF555555,0xFFFFFFFF};

    public ColorPickerDialog(Context c, int initial, OnPicked cb) {
        ctx=c; current=initial; callback=cb;
        dialog=new Dialog(c,android.R.style.Theme_Translucent_NoTitleBar);
        build();
    }

    public void show(){dialog.show();}

    private TextView makeTab(String label){
        TextView t=new TextView(ctx);
        t.setText(label); t.setTextSize(15); t.setGravity(Gravity.CENTER);
        t.setPadding(dp(26),dp(9),dp(26),dp(9));
        t.setOnClickListener(v->showMode(label.equals("色环")));
        return t;
    }

    private void styleTab(TextView t,boolean selected){
        GradientDrawable bg=new GradientDrawable();
        bg.setCornerRadius(dp(20));
        if(selected){bg.setColor(0xFFEDEDEF);t.setTextColor(0xFF111111);}
        else{bg.setColor(0x33FFFFFF);t.setTextColor(0xFFDDDDDD);}
        t.setBackground(bg);
    }

    private void build(){
        LinearLayout root=new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(16),dp(18),dp(18));
        GradientDrawable bg=new GradientDrawable();
        bg.setCornerRadius(dp(26)); bg.setColor(0xFF2E2E30);
        root.setBackground(bg);

        // 顶部
        LinearLayout top=new LinearLayout(ctx);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView pip=new ImageView(ctx);
        pip.setImageResource(R.drawable.ic_pen); pip.setColorFilter(0xFFFFFFFF);
        pip.setPadding(dp(8),dp(8),dp(8),dp(8));

        LinearLayout tabs=new LinearLayout(ctx);
        tabs.setGravity(Gravity.CENTER);
        gridTab=makeTab("网格"); ringTab=makeTab("色环");
        tabs.addView(gridTab); tabs.addView(box(dp(8),1)); tabs.addView(ringTab);
        styleTab(gridTab,false); styleTab(ringTab,true);

        ImageView close=new ImageView(ctx);
        close.setImageResource(R.drawable.ic_check);
        close.setRotation(45); close.setColorFilter(0xFFFFFFFF);
        close.setPadding(dp(10),dp(10),dp(10),dp(10));
        close.setOnClickListener(v->dialog.dismiss());

        top.addView(pip); top.addView(stretch()); top.addView(tabs);
        top.addView(stretch()); top.addView(close);
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(48)));

        // HSV
        hsv=new HSVView(ctx);
        hsv.setColor(current);
        hsv.setListener(col->{current=col; alphaSlider.setColor(col); refreshBig();});
        root.addView(hsv,new LinearLayout.LayoutParams(-1,dp(300)));

        // 网格
        grid=new GridLayout(ctx);
        grid.setColumnCount(12);
        buildGrid();
        LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(-1,dp(300));
        glp.setMargins(0,dp(10),0,dp(10));
        root.addView(grid,glp);
        grid.setVisibility(View.GONE);

        // 透明度
        alphaSlider=new AlphaSlider(ctx);
        alphaSlider.setColor(current);
        alphaSlider.setAlpha(Color.alpha(current));
        alphaSlider.setListener(a->{current=(current&0x00FFFFFF)|(a<<24); hsv.setColor(current); refreshBig();});
        root.addView(alphaSlider,new LinearLayout.LayoutParams(-1,dp(44)));

        View line=new View(ctx); line.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams llp=new LinearLayout.LayoutParams(-1,dp(1));
        llp.setMargins(0,dp(14),0,dp(14));
        root.addView(line,llp);

        // 预设
        LinearLayout presets=new LinearLayout(ctx);
        presets.setGravity(Gravity.CENTER_VERTICAL);
        bigSwatch=swatch(current,dp(54),true);
        presets.addView(bigSwatch);
        LinearLayout grid2=new LinearLayout(ctx);
        grid2.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r1=new LinearLayout(ctx),r2=new LinearLayout(ctx);
        for(int i=0;i<5;i++) r1.addView(swatch(PRESETS[i],dp(34),false));
        for(int i=5;i<9;i++) r2.addView(swatch(PRESETS[i],dp(34),false));
        r2.addView(plus());
        grid2.addView(r1); grid2.addView(r2);
        presets.addView(grid2);
        root.addView(presets,new LinearLayout.LayoutParams(-1,dp(90)));

        android.widget.FrameLayout container=new android.widget.FrameLayout(ctx);
        android.widget.FrameLayout.LayoutParams cp=new android.widget.FrameLayout.LayoutParams(dp(360),-2);
        cp.gravity=Gravity.CENTER;
        container.setPadding(dp(12),dp(12),dp(12),dp(12));
        container.addView(root,cp);
        container.setOnClickListener(v->{callback.picked(current);dialog.dismiss();});
        dialog.setContentView(container);
    }

    private void refreshBig(){
        GradientDrawable d=new GradientDrawable();
        d.setShape(GradientDrawable.OVAL); d.setColor(current);
        d.setStroke(dp(1.5f),0x88FFFFFF);
        bigSwatch.setBackground(d);
    }

    private void buildGrid(){
        for(int c=0;c<12;c++) addCell(Color.HSVToColor(new float[]{0,0,1-c/11f}));
        for(int r=0;r<10;r++){
            float hh=r*36f;
            for(int c=0;c<12;c++){
                float vv=0.2f+c*0.072f;
                addCell(Color.HSVToColor(new float[]{hh,1f,Math.min(1,vv)}));
            }
        }
    }

    private void addCell(final int col){
        View cell=new View(ctx); cell.setBackgroundColor(col);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
        lp.width=0; lp.height=dp(24);
        lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
        cell.setLayoutParams(lp);
        cell.setOnClickListener(v->{current=col|0xFF000000;hsv.setColor(current);alphaSlider.setColor(current);refreshBig();});
        grid.addView(cell);
    }

    private void showMode(boolean ring){
        hsv.setVisibility(ring?View.VISIBLE:View.GONE);
        grid.setVisibility(ring?View.GONE:View.VISIBLE);
        styleTab(gridTab,!ring); styleTab(ringTab,ring);
    }

    private View swatch(final int col,int size,boolean bigFlag){
        View v=new View(ctx);
        GradientDrawable d=new GradientDrawable();
        d.setShape(GradientDrawable.OVAL); d.setColor(col);
        d.setStroke(dp(1.5f),0x88FFFFFF);
        v.setBackground(d);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(size,size);
        lp.setMargins(dp(5),dp(5),dp(5),dp(5));
        v.setLayoutParams(lp);
        v.setOnClickListener(x->{
            current=col; hsv.setColor(col); alphaSlider.setColor(col); refreshBig();
            if(bigFlag){callback.picked(col);dialog.dismiss();}
        });
        return v;
    }

    private View plus(){
        TextView t=new TextView(ctx);
        t.setText("+"); t.setTextColor(0xFFDDDDDD); t.setTextSize(22);
        t.setGravity(Gravity.CENTER);
        GradientDrawable d=new GradientDrawable();
        d.setShape(GradientDrawable.OVAL); d.setColor(0x22FFFFFF);
        t.setBackground(d);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(34),dp(34));
        lp.setMargins(dp(5),dp(5),dp(5),dp(5));
        t.setLayoutParams(lp);
        return t;
    }

    private View stretch(){View v=new View(ctx);v.setLayoutParams(new LinearLayout.LayoutParams(0,1,1));return v;}
    private View box(int w,int h){View v=new View(ctx);v.setLayoutParams(new LinearLayout.LayoutParams(w,h));return v;}
    private int dp(float v){return (int)(v*ctx.getResources().getDisplayMetrics().density);}
}
