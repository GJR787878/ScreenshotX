package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.graphics.Rect;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawView extends View {

    private Bitmap srcBitmap;
    private Bitmap drawBitmap;
    private Canvas drawCanvas;
    private Paint paint;
    private Path currentPath;
    private List<Path> paths = new ArrayList<>();
    private List<Path> undonePaths = new ArrayList<>();
    private float scale = 1f;
    private float offsetX = 0, offsetY = 0;

    public DrawView(Context c) {
        super(c);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setBitmap(Bitmap bmp) {
        srcBitmap = bmp;
        requestLayout();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (srcBitmap == null) return;

        float scaleX = (float) w / srcBitmap.getWidth();
        float scaleY = (float) h / srcBitmap.getHeight();
        scale = Math.min(scaleX, scaleY);
        int dw = (int)(srcBitmap.getWidth() * scale);
        int dh = (int)(srcBitmap.getHeight() * scale);
        offsetX = (w - dw) / 2f;
        offsetY = (h - dh) / 2f;

        drawBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(drawBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (srcBitmap == null) return;

        int dw = (int)(srcBitmap.getWidth() * scale);
        int dh = (int)(srcBitmap.getHeight() * scale);
        Rect dst = new Rect((int)offsetX, (int)offsetY, (int)offsetX + dw, (int)offsetY + dh);
        canvas.drawBitmap(srcBitmap, null, dst, null);

        if (drawBitmap != null) canvas.drawBitmap(drawBitmap, 0, 0, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                currentPath = new Path();
                currentPath.moveTo(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                currentPath.lineTo(x, y);
                drawCanvas.drawPath(currentPath, paint);
                break;
            case MotionEvent.ACTION_UP:
                paths.add(currentPath);
                currentPath = null;
                break;
        }
        invalidate();
        return true;
    }

    public void undo() {
        if (!paths.isEmpty()) {
            undonePaths.add(paths.remove(paths.size() - 1));
            redrawAll();
        }
    }

    public void redo() {
        if (!undonePaths.isEmpty()) {
            paths.add(undonePaths.remove(undonePaths.size() - 1));
            redrawAll();
        }
    }

    private void redrawAll() {
        if (drawBitmap == null) return;
        drawBitmap.eraseColor(Color.TRANSPARENT);
        for (Path p : paths) drawCanvas.drawPath(p, paint);
        invalidate();
    }

    public Bitmap getResultBitmap() {
        Bitmap result = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(result);
        draw(c);
        return result;
    }
}
