package se.bitcraze.crazyfliecontrol2.video;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 * The drone soft-AP has no internet, so Android may keep routing traffic over
 * mobile data. We track the Wi-Fi network and bind this process to it so both
 * the CRTP UDP link and the camera HTTP stream reach 192.168.43.42.
 */
public final class DroneNetwork {

    private static final String TAG = "DroneNetwork";

    public static final String DRONE_IP = "192.168.43.42";
    public static final String STATUS_URL = "http://" + DRONE_IP + "/status";
    public static final String CAPTURE_URL = "http://" + DRONE_IP + "/capture";
    public static final String STREAM_URL = "http://" + DRONE_IP + ":81/stream";

    /** Soft-AP name prefix and password set in the firmware (wifi_esp32.c). */
    public static final String DRONE_SSID_PREFIX = "ESP-DRONE_";
    public static final String DRONE_PASSWORD = "12345678";

    public interface JoinListener {
        void onJoined();
        void onFailed();
    }

    private static ConnectivityManager.NetworkCallback sJoinCallback;

    private static volatile Network sWifiNetwork;
    private static ConnectivityManager.NetworkCallback sCallback;
    private static ConnectivityManager sCm;

    private DroneNetwork() {}

    public static synchronized void start(Context context) {
        if (sCallback != null) {
            return;
        }
        sCm = context.getApplicationContext().getSystemService(ConnectivityManager.class);
        if (sCm == null) {
            return;
        }
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        sCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                sWifiNetwork = network;
                boolean ok = sCm.bindProcessToNetwork(network);
                Log.i(TAG, "Wi-Fi available, process bound: " + ok);
            }

            @Override
            public void onLost(Network network) {
                if (network.equals(sWifiNetwork)) {
                    sWifiNetwork = null;
                    sCm.bindProcessToNetwork(null);
                    Log.i(TAG, "Wi-Fi lost, process unbound");
                }
            }
        };
        try {
            sCm.registerNetworkCallback(request, sCallback);
        } catch (RuntimeException e) {
            Log.w(TAG, "registerNetworkCallback failed", e);
            sCallback = null;
        }
    }

    /** Quick check whether the drone answers on the current network. */
    public static boolean isDroneReachable() {
        HttpURLConnection c = null;
        try {
            c = open(STATUS_URL, 1200, 1200);
            return c.getResponseCode() == 200;
        } catch (IOException e) {
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    /**
     * Asks Android to join the drone's Wi-Fi (any SSID starting with ESP-DRONE_).
     * Android 10+ shows its own "connect to device" sheet; the user taps the drone once.
     */
    public static synchronized void joinDroneWifi(Context context, JoinListener listener) {
        ConnectivityManager cm = context.getApplicationContext().getSystemService(ConnectivityManager.class);
        if (cm == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            listener.onFailed();
            return;
        }
        releaseJoin(cm);
        android.net.wifi.WifiNetworkSpecifier spec = new android.net.wifi.WifiNetworkSpecifier.Builder()
                .setSsidPattern(new android.os.PatternMatcher(DRONE_SSID_PREFIX, android.os.PatternMatcher.PATTERN_PREFIX))
                .setWpa2Passphrase(DRONE_PASSWORD)
                .build();
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(spec)
                .build();
        final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        sJoinCallback = new ConnectivityManager.NetworkCallback() {
            private boolean done;

            @Override
            public void onAvailable(Network network) {
                sWifiNetwork = network;
                cm.bindProcessToNetwork(network);
                Log.i(TAG, "joined drone Wi-Fi");
                if (!done) {
                    done = true;
                    main.post(listener::onJoined);
                }
            }

            @Override
            public void onUnavailable() {
                if (!done) {
                    done = true;
                    main.post(listener::onFailed);
                }
            }

            @Override
            public void onLost(Network network) {
                if (network.equals(sWifiNetwork)) {
                    sWifiNetwork = null;
                    cm.bindProcessToNetwork(null);
                }
            }
        };
        try {
            cm.requestNetwork(request, sJoinCallback, 45_000);
        } catch (RuntimeException e) {
            Log.w(TAG, "requestNetwork failed", e);
            sJoinCallback = null;
            listener.onFailed();
        }
    }

    private static void releaseJoin(ConnectivityManager cm) {
        if (sJoinCallback != null) {
            try {
                cm.unregisterNetworkCallback(sJoinCallback);
            } catch (RuntimeException ignored) {
            }
            sJoinCallback = null;
        }
    }

    public static synchronized void stop() {
        if (sCm != null) {
            releaseJoin(sCm);
        }
        if (sCm != null && sCallback != null) {
            try {
                sCm.unregisterNetworkCallback(sCallback);
            } catch (RuntimeException ignored) {
            }
            sCm.bindProcessToNetwork(null);
        }
        sCallback = null;
        sWifiNetwork = null;
    }

    public static HttpURLConnection open(String url, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        URL u = new URL(url);
        Network n = sWifiNetwork;
        URLConnection c = (n != null) ? n.openConnection(u) : u.openConnection();
        HttpURLConnection http = (HttpURLConnection) c;
        http.setConnectTimeout(connectTimeoutMs);
        http.setReadTimeout(readTimeoutMs);
        http.setUseCaches(false);
        return http;
    }
}
