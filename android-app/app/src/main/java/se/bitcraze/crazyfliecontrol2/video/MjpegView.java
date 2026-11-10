package se.bitcraze.crazyfliecontrol2.video;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

/** Draws the latest camera frame. Double-tap toggles fit / fill. */
public class MjpegView extends View {

    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Rect mSrc = new Rect();
    private final RectF mDst = new RectF();
    private final GestureDetector mGestures;
    private Bitmap mFrame;
    private boolean mFill = false;

    public MjpegView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return mFrame != null;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                mFill = !mFill;
                invalidate();
                return true;
            }
        });
    }

    /** Thread-safe: may be called from the reader thread. */
    public void setFrame(Bitmap frame) {
        synchronized (this) {
            mFrame = frame;
        }
        postInvalidateOnAnimation();
    }

    public void clear() {
        synchronized (this) {
            mFrame = null;
        }
        postInvalidate();
    }

    public boolean hasFrame() {
        synchronized (this) {
            return mFrame != null;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mGestures.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Bitmap f;
        synchronized (this) {
            f = mFrame;
        }
        if (f == null || f.isRecycled()) {
            return;
        }
        float vw = getWidth(), vh = getHeight();
        float bw = f.getWidth(), bh = f.getHeight();
        float scale = mFill ? Math.max(vw / bw, vh / bh) : Math.min(vw / bw, vh / bh);
        float dw = bw * scale, dh = bh * scale;
        mSrc.set(0, 0, f.getWidth(), f.getHeight());
        mDst.set((vw - dw) / 2f, (vh - dh) / 2f, (vw + dw) / 2f, (vh + dh) / 2f);
        canvas.drawBitmap(f, mSrc, mDst, mPaint);
    }
}
