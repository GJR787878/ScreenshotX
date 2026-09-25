package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(60), dp(24), dp(32));
        root.setBackgroundColor(0xFF000000);

        TextView title = new TextView(this);
        title.setText("ScreenshotX");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, dp(24));
        root.addView(title);

        TextView steps = new TextView(this);
        steps.setText("使用方法：\n"
                + "1. 在 LSPosed 中启用本模块\n"
                + "2. 作用域勾选「系统框架」(system)\n"
                + "3. 重启手机（或重启系统框架）\n"
                + "4. 按 电源键+音量下 截屏\n\n"
                + "下面按钮可测试 Root 截屏是否可用：");
        steps.setTextColor(0xFFCCCCCC);
        steps.setTextSize(15);
        steps.setLineSpacing(dp(4), 1f);
        steps.setPadding(0, 0, 0, dp(24));
        root.addView(steps);

        Button test = new Button(this);
        test.setText("测试截屏");
        test.setOnClickListener(v -> {
            Intent svc = new Intent(this, ScreenshotService.class);
            svc.setAction(ScreenshotService.ACTION_SHOOT);
            startService(svc);
            Toast.makeText(this, "若已授权 Root，将打开编辑器", Toast.LENGTH_LONG).show();
        });
        root.addView(test);

        setContentView(root);
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
