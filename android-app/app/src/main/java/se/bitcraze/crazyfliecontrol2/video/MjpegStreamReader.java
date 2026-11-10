package se.bitcraze.crazyfliecontrol2.video;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

/**
 * Reads a multipart/x-mixed-replace MJPEG stream (ESP32 camera_stream :81/stream)
 * on a background thread and hands out each JPEG + decoded bitmap.
 * Reconnects automatically while running.
 */
public class MjpegStreamReader {

    private static final String TAG = "MjpegStreamReader";
    private static final int MAX_FRAME = 2 * 1024 * 1024;

    public interface Listener {
        void onStreamConnected();
        /** Called on the reader thread. */
        void onFrame(byte[] jpeg, Bitmap bitmap);
        void onStreamError(String message);
    }

    private final String mUrl;
    private final Listener mListener;
    private volatile boolean mRunning;
    private volatile HttpURLConnection mConn;
    private Thread mThread;

    public MjpegStreamReader(String url, Listener listener) {
        mUrl = url;
        mListener = listener;
    }

    public synchronized void start() {
        if (mRunning) {
            return;
        }
        mRunning = true;
        mThread = new Thread(this::loop, "mjpeg-reader");
        mThread.start();
    }

    public synchronized void stop() {
        mRunning = false;
        HttpURLConnection c = mConn;
        if (c != null) {
            c.disconnect();
        }
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
    }

    public boolean isRunning() {
        return mRunning;
    }

    private void loop() {
        while (mRunning) {
            try {
                HttpURLConnection c = DroneNetwork.open(mUrl, 3000, 5000);
                mConn = c;
                int code = c.getResponseCode();
                if (code != 200) {
                    throw new IOException("camera returned HTTP " + code);
                }
                InputStream in = new BufferedInputStream(c.getInputStream(), 64 * 1024);
                mListener.onStreamConnected();
                readFrames(in);
            } catch (IOException e) {
                if (mRunning) {
                    Log.w(TAG, "stream error: " + e);
                    mListener.onStreamError(e.getMessage() == null ? e.toString() : e.getMessage());
                }
            } finally {
                HttpURLConnection c = mConn;
                if (c != null) {
                    c.disconnect();
                }
                mConn = null;
            }
            if (mRunning) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void readFrames(InputStream in) throws IOException {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        while (mRunning) {
            int length = -1;
            while (true) {
                String line = readLine(in);
                if (line.isEmpty()) {
                    if (length > 0) {
                        break;      // end of part headers
                    }
                    continue;       // CRLF before the boundary
                }
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                    try {
                        length = Integer.parseInt(line.substring(colon + 1).trim());
                    } catch (NumberFormatException ignored) {
                        length = -1;
                    }
                }
            }
            if (length > MAX_FRAME) {
                throw new IOException("frame too large: " + length);
            }
            byte[] jpeg = new byte[length];
            readFully(in, jpeg);
            Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length, opts);
            if (bmp != null) {
                mListener.onFrame(jpeg, bmp);
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder(64);
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("stream closed");
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                sb.append((char) b);
            }
            if (sb.length() > 1024) {
                throw new IOException("header line too long");
            }
        }
        return sb.toString();
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                throw new EOFException("stream closed mid-frame");
            }
            off += n;
        }
    }
}
