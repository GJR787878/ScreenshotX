package io.github.gjr787878.screenshotx;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_SCREENSHOT = 1001;

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
        title.setPadding(0, 0, 0, dp(30));
        root.addView(title);

        TextView desc = new TextView(this);
        desc.setText("点下面按钮授权截屏\n然后下拉通知栏点「截屏」");
        desc.setTextColor(0xFFCCCCCC);
        desc.setTextSize(14);
        desc.setPadding(0, 0, 0, dp(30));
        root.addView(desc);

        Button btn = new Button(this);
        btn.setText("授权并开启服务");
        btn.setOnClickListener(v -> {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_SCREENSHOT);
        });
        root.addView(btn);

        setContentView(root);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCREENSHOT && resultCode == RESULT_OK) {
            Intent svc = new Intent(this, ScreenshotService.class);
            svc.putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode);
            svc.putExtra(ScreenshotService.EXTRA_RESULT_DATA, data);
            startForegroundService(svc);
            Toast.makeText(this, "服务已开启，下拉通知栏点截屏", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }
}
