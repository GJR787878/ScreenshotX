package io.github.gjr787878.screenshotx;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class DrawView extends View {

    public static final int BALL=0, MARKER=1, PENCIL=2, FOUNTAIN=3, ERASER=4;

    private Bitmap base;          // 原截图
    private Bitmap overlay;       // 标注层(透明)
    private Canvas overlayCanvas;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
    private final Path path = new Path();
    private float curX, curY;

    private int tool = BALL;
    private int color = Color.RED;

    // 撤销栈：保存每一笔之前的 overlay 快照
    private final List<Bitmap> undoStack = new ArrayList<>();
    private final List<Bitmap> redoStack = new ArrayList<>();

    public DrawView(Context c) {
        super(c);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setBitmap(Bitmap b) {
        base = b.copy(Bitmap.Config.ARGB_8888, true);
        overlay = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
        overlayCanvas = new Canvas(overlay);
        invalidate();
    }

    public void setTool(int t) { tool = t; applyStyle(); }
    public void setColor(int c) { color = c; if (tool != ERASER) applyStyle(); }
    public int getColor() { return color; }

    private float widthFor(int t) {
        switch (t) {
            case MARKER: return base!=null? base.getWidth()*0.018f:22;
            case ERASER: return base!=null? base.getWidth()*0.03f:28;
            case PENCIL: return base!=null? base.getWidth()*0.0035f:4;
            case BALL:   return base!=null? base.getWidth()*0.0045f:5;
            case FOUNTAIN:return base!=null? base.getWidth()*0.006f:7;
        }
        return 6;
    }

    private void applyStyle() {
        paint.setStrokeWidth(widthFor(tool));
        if (tool == ERASER) {
            paint.setXfermode(new android.graphics.PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            paint.setAlpha(255);
        } else {
            paint.setXfermode(null);
            paint.setColor(color);
            paint.setAlpha(tool == MARKER ? 90 : 255);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (base != null) canvas.drawBitmap(base, 0, 0, null);
        if (overlay != null) canvas.drawBitmap(overlay, 0, 0, null);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (base == null) return false;
        // 把 View 坐标映射到底图像素
        float sx = base.getWidth() / (float) getWidth();
        float sy = base.getHeight() / (float) getHeight();
        float x = e.getX() * sx, y = e.getY() * sy;
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pushUndo();
                applyStyle();
                path.reset();
                path.moveTo(x, y);
                curX = x; curY = y;
                overlayCanvas.drawPoint(x, y, paint);
                break;
            case MotionEvent.ACTION_MOVE:
                path.quadTo(curX, curY, (x+curX)/2, (y+curY)/2);
                overlayCanvas.drawPath(path, paint);
                curX = x; curY = y;
                break;
            case MotionEvent.ACTION_UP:
                overlayCanvas.drawPath(path, paint);
                path.reset();
                break;
            default: return false;
        }
        invalidate();
        return true;
    }

    private void pushUndo() {
        redoStack.clear();
        undoStack.add(overlay.copy(Bitmap.Config.ARGB_8888, false));
        if (undoStack.size() > 25) undoStack.remove(0);
    }

    public void undo() {
        if (undoStack.isEmpty() || overlay==null) return;
        redoStack.add(overlay.copy(Bitmap.Config.ARGB_8888, false));
        Bitmap prev = undoStack.remove(undoStack.size()-1);
        overlay.eraseColor(Color.TRANSPARENT);
        new Canvas(overlay).drawBitmap(prev, 0, 0, null);
        invalidate();
    }

    public void redo() {
        if (redoStack.isEmpty() || overlay==null) return;
        undoStack.add(overlay.copy(Bitmap.Config.ARGB_8888, false));
        Bitmap next = redoStack.remove(redoStack.size()-1);
        overlay.eraseColor(Color.TRANSPARENT);
        new Canvas(overlay).drawBitmap(next, 0, 0, null);
        invalidate();
    }

    public Bitmap getResultBitmap() {
        Bitmap out = base.copy(Bitmap.Config.ARGB_8888, true);
        new Canvas(out).drawBitmap(overlay, 0, 0, null);
        return out;
    }
}
