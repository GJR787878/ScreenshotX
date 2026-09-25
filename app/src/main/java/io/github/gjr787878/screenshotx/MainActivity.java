package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.DataOutputStream;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFF000000);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(140));
        sv.addView(root);

        // 标题
        GlassTextView title = new GlassTextView(this);
        title.setText("ScreenshotX");
        title.setTextSize(28);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, dp(20), 0, dp(8));
        root.addView(title);

        GlassTextView desc = new GlassTextView(this);
        desc.setText("毛玻璃截屏工具\n电源键+音量下触发");
        desc.setTextSize(14);
        desc.setTextColor(0xFFCCCCCC);
        desc.setPadding(0, 0, 0, dp(30));
        root.addView(desc);

        // 选项1：模块状态说明
        GlassTextView lbl1 = new GlassTextView(this);
        lbl1.setText("模块状态");
        lbl1.setTextSize(14);
        lbl1.setTextColor(0xFFCCCCCC);
        lbl1.setPadding(0, 0, 0, dp(8));
        root.addView(lbl1);

        GlassCapsuleButton btnStatus = new GlassCapsuleButton(this);
        btnStatus.setLabel("在 LSPosed 中启用本模块");
        btnStatus.setOnClickListener(v ->
                Toast.makeText(this, "请在 LSPosed Manager 中启用模块，作用域选 SystemUI，然后重启 SystemUI", Toast.LENGTH_LONG).show());
        root.addView(btnStatus);

        // 选项2：Root 授权
        GlassTextView lbl2 = new GlassTextView(this);
        lbl2.setText("Root 权限");
        lbl2.setTextSize(14);
        lbl2.setTextColor(0xFFCCCCCC);
        lbl2.setPadding(0, dp(24), 0, dp(8));
        root.addView(lbl2);

        GlassCapsuleButton btnRoot = new GlassCapsuleButton(this);
        btnRoot.setLabel("授权 Root");
        btnRoot.setOnClickListener(v -> requestRoot());
        root.addView(btnRoot);

        // 选项3：截屏服务
        GlassTextView lbl3 = new GlassTextView(this);
        lbl3.setText("截屏服务");
        lbl3.setTextSize(14);
        lbl3.setTextColor(0xFFCCCCCC);
        lbl3.setPadding(0, dp(24), 0, dp(8));
        root.addView(lbl3);

        GlassCapsuleButton btnService = new GlassCapsuleButton(this);
        btnService.setLabel("开启后台服务");
        btnService.setOnClickListener(v -> {
            startService(new Intent(this, ScreenshotService.class));
            Toast.makeText(this, "服务已开启", Toast.LENGTH_SHORT).show();
        });
        root.addView(btnService);

        // 选项4：测试截屏
        GlassTextView lbl4 = new GlassTextView(this);
        lbl4.setText("测试");
        lbl4.setTextSize(14);
        lbl4.setTextColor(0xFFCCCCCC);
        lbl4.setPadding(0, dp(24), 0, dp(8));
        root.addView(lbl4);

        GlassCapsuleButton btnTest = new GlassCapsuleButton(this);
        btnTest.setLabel("立即截屏");
        btnTest.setOnClickListener(v -> {
            Intent i = new Intent(this, ScreenshotService.class);
            i.setAction(ScreenshotService.ACTION_SHOOT);
            startService(i);
        });
        root.addView(btnTest);

        setContentView(sv);
    }

    private void requestRoot() {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("echo ok\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            Toast.makeText(this, "Root 授权成功", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Root 授权失败", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
