package se.bitcraze.crazyfliecontrol2.video;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Polls the drone /status endpoint (camera presence + battery) every 2 s. */
public class DroneStatusPoller {

    public interface Listener {
        void onDroneStatus(DroneStatus status);
        void onDroneUnreachable();
    }

    private final Listener mListener;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService mExecutor;

    public DroneStatusPoller(Listener listener) {
        mListener = listener;
    }

    public synchronized void start() {
        if (mExecutor != null) {
            return;
        }
        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mExecutor.scheduleWithFixedDelay(this::pollOnce, 0, 2, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
        }
    }

    private void pollOnce() {
        HttpURLConnection c = null;
        try {
            c = DroneNetwork.open(DroneNetwork.STATUS_URL, 1500, 1500);
            if (c.getResponseCode() != 200) {
                throw new Exception("HTTP " + c.getResponseCode());
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            final DroneStatus s = DroneStatus.parse(out.toString("UTF-8"));
            mMain.post(() -> mListener.onDroneStatus(s));
        } catch (Exception e) {
            mMain.post(mListener::onDroneUnreachable);
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
