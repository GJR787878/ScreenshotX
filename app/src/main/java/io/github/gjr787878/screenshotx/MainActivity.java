package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;

public class MainActivity extends Activity {

    private TextView rootStatus;

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
        title.setPadding(0, 0, 0, dp(20));
        root.addView(title);

        // Root 授权状态（打开 App 即在前台主动请求一次，保证 Magisk 弹窗可交互）
        rootStatus = new TextView(this);
        rootStatus.setText("Root 状态：检测中…");
        rootStatus.setTextColor(0xFFCCCCCC);
        rootStatus.setTextSize(15);
        rootStatus.setPadding(0, 0, 0, dp(20));
        root.addView(rootStatus);
        requestRoot();

        TextView steps = new TextView(this);
        steps.setText("使用方法：\n"
                + "1. 打开本 App，在 Magisk 弹窗中授予 Root 权限\n"
                + "2. 在 LSPosed 中启用本模块\n"
                + "3. 作用域勾选「系统框架」(system)\n"
                + "4. 重启手机（或重启系统框架）\n"
                + "5. 按 电源键+音量下 截屏\n\n"
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

    /** 在前台主动执行 su，触发 Magisk 授权弹窗并回读结果 */
    private void requestRoot() {
        new Thread(() -> {
            boolean ok = false;
            Process p = null;
            try {
                p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("id\n");
                os.writeBytes("exit\n");
                os.flush();
                ok = p.waitFor() == 0;
            } catch (Throwable t) {
                ok = false;
            } finally {
                if (p != null) p.destroy();
            }
            final boolean granted = ok;
            runOnUiThread(() -> rootStatus.setText(granted
                    ? "Root 状态：已授权 ✓"
                    : "Root 状态：未授权 ✗（请在 Magisk 中允许）"));
        }).start();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
