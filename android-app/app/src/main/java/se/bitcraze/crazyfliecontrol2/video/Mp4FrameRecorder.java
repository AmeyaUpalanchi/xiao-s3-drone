package se.bitcraze.crazyfliecontrol2.video;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Re-encodes the MJPEG live view into an H.264 MP4 using the hardware encoder.
 * Frames are drawn onto the encoder's input surface with a hardware canvas.
 */
public class Mp4FrameRecorder {

    private static final String TAG = "Mp4FrameRecorder";
    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;

    private final Object mLock = new Object();
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect mDst = new Rect();

    private MediaCodec mCodec;
    private MediaMuxer mMuxer;
    private Surface mInput;
    private Thread mDrainThread;
    private MediaSaver.VideoTarget mTarget;
    private Context mContext;
    private volatile boolean mRecording;
    private volatile boolean mMuxerStarted;
    private int mTrack = -1;
    private long mFrames;
    private long mStartMs;

    public boolean isRecording() {
        return mRecording;
    }

    public long elapsedMs() {
        return mRecording ? System.currentTimeMillis() - mStartMs : 0;
    }

    public void start(Context ctx, int frameWidth, int frameHeight, int fps) throws IOException {
        synchronized (mLock) {
            if (mRecording) {
                return;
            }
            mContext = ctx.getApplicationContext();
            int w = Math.max(16, (frameWidth / 16) * 16);
            int h = Math.max(16, (frameHeight / 16) * 16);
            MediaFormat format = MediaFormat.createVideoFormat(MIME, w, h);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(1_500_000, w * h * 8));
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            mTarget = MediaSaver.createVideo(mContext);
            try {
                mCodec = MediaCodec.createEncoderByType(MIME);
                mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mInput = mCodec.createInputSurface();
                mCodec.start();
                mMuxer = new MediaMuxer(mTarget.pfd.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException | RuntimeException e) {
                releaseQuietly();
                MediaSaver.finishVideo(mContext, mTarget, false);
                mTarget = null;
                throw (e instanceof IOException) ? (IOException) e : new IOException(e);
            }
            mDst.set(0, 0, w, h);
            mTrack = -1;
            mFrames = 0;
            mMuxerStarted = false;
            mStartMs = System.currentTimeMillis();
            mRecording = true;
            mDrainThread = new Thread(this::drainLoop, "mp4-drain");
            mDrainThread.start();
            Log.i(TAG, "recording " + w + "x" + h + " to " + mTarget.displayPath);
        }
    }

    /** Called from the stream thread for every decoded frame. */
    public void addFrame(Bitmap frame) {
        synchronized (mLock) {
            if (!mRecording || mInput == null) {
                return;
            }
            try {
                Canvas c = mInput.lockHardwareCanvas();
                try {
                    c.drawBitmap(frame, null, mDst, mPaint);
                } finally {
                    mInput.unlockCanvasAndPost(c);
                }
                mFrames++;
            } catch (RuntimeException e) {
                Log.w(TAG, "drop frame: " + e);
            }
        }
    }

    /** Stops and finalises the file. Returns its location, or null if nothing was recorded. */
    public String stop() {
        Thread drain;
        synchronized (mLock) {
            if (!mRecording) {
                return null;
            }
            mRecording = false;
            try {
                mCodec.signalEndOfInputStream();
            } catch (RuntimeException e) {
                Log.w(TAG, "signalEndOfInputStream", e);
            }
            drain = mDrainThread;
        }
        if (drain != null) {
            try {
                drain.join(3000);
            } catch (InterruptedException ignored) {
            }
        }
        synchronized (mLock) {
            boolean keep = mMuxerStarted && mFrames > 0;
            // A muxer that fails to stop leaves an unplayable file: don't publish it.
            keep &= releaseQuietly();
            String path = mTarget != null ? mTarget.displayPath : null;
            if (mTarget != null) {
                MediaSaver.finishVideo(mContext, mTarget, keep);
                mTarget = null;
            }
            return keep ? path : null;
        }
    }

    private void drainLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaCodec codec = mCodec;
        try {
            while (true) {
                int idx = codec.dequeueOutputBuffer(info, 10_000);
                if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue;
                } else if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mTrack = mMuxer.addTrack(codec.getOutputFormat());
                    mMuxer.start();
                    mMuxerStarted = true;
                } else if (idx >= 0) {
                    ByteBuffer buf = codec.getOutputBuffer(idx);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0;
                    }
                    if (info.size > 0 && mMuxerStarted && buf != null) {
                        buf.position(info.offset);
                        buf.limit(info.offset + info.size);
                        mMuxer.writeSampleData(mTrack, buf, info);
                    }
                    codec.releaseOutputBuffer(idx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "encoder drain failed", e);
        }
    }

    private boolean releaseQuietly() {
        try {
            if (mCodec != null) {
                mCodec.stop();
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (mCodec != null) {
                mCodec.release();
            }
        } catch (RuntimeException ignored) {
        }
        mCodec = null;
        if (mInput != null) {
            mInput.release();
            mInput = null;
        }
        boolean ok = true;
        if (mMuxer != null) {
            try {
                if (mMuxerStarted) {
                    mMuxer.stop();
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "muxer stop", e);
                ok = false;
            } finally {
                try {
                    mMuxer.release();
                } catch (RuntimeException ignored) {
                }
            }
        }
        mMuxerStarted = false;
        mMuxer = null;
        return ok;
    }
}
