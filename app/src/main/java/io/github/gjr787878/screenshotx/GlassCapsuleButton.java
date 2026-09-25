package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;

// §3.6 毛玻璃胶囊按钮
public class GlassCapsuleButton extends Button {

    public GlassCapsuleButton(Context c) {
        super(c);
        setAllCaps(false);
        setTextColor(0xFFFFFFFF);
        setTextSize(14);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(24));
        bg.setColor(0x33FFFFFF);
        bg.setStroke(1, 0x40FFFFFF);
        setBackground(bg);
        setPadding(dp(16), dp(10), dp(16), dp(10));
    }

    public void setLabel(String s) { setText(s); }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
