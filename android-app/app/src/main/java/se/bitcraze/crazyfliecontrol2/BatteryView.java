package se.bitcraze.crazyfliecontrol2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.espressif.espdrone.android.R;

/** Small battery glyph whose fill and colour follow the state of charge. */
public class BatteryView extends View {

    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mBody = new RectF();
    private final RectF mCap = new RectF();
    private final RectF mLevel = new RectF();
    private int mPercent = -1;

    public BatteryView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(1.5f * d);
        mStroke.setColor(ContextCompat.getColor(context, R.color.brand_on_surface));
        mFill.setStyle(Paint.Style.FILL);
    }

    /** @param percent 0..100, or -1 when unknown. */
    public void setPercent(int percent) {
        mPercent = percent;
        invalidate();
    }

    public static int colorFor(Context ctx, int percent) {
        if (percent < 0) {
            return ContextCompat.getColor(ctx, R.color.brand_muted);
        } else if (percent <= 20) {
            return ContextCompat.getColor(ctx, R.color.status_danger);
        } else if (percent <= 45) {
            return ContextCompat.getColor(ctx, R.color.status_warn);
        }
        return ContextCompat.getColor(ctx, R.color.status_ok);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float w = getWidth(), h = getHeight();
        float capW = 2.5f * d;
        float sw = mStroke.getStrokeWidth();
        mBody.set(sw, sw, w - capW - sw, h - sw);
        mCap.set(w - capW - sw / 2f, h * 0.3f, w, h * 0.7f);
        float r = 2.5f * d;
        canvas.drawRoundRect(mBody, r, r, mStroke);
        mFill.setColor(ContextCompat.getColor(getContext(), R.color.brand_on_surface));
        canvas.drawRoundRect(mCap, r / 2, r / 2, mFill);
        if (mPercent >= 0) {
            float inset = sw / 2f + 1.5f * d;
            float left = mBody.left + inset, top = mBody.top + inset;
            float right = mBody.right - inset, bottom = mBody.bottom - inset;
            float lw = Math.max(1.5f * d, (right - left) * Math.min(100, mPercent) / 100f);
            mLevel.set(left, top, left + lw, bottom);
            mFill.setColor(colorFor(getContext(), mPercent));
            canvas.drawRoundRect(mLevel, r / 2, r / 2, mFill);
        }
    }
}
