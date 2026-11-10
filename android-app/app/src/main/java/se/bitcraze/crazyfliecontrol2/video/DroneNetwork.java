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

    public static synchronized void stop() {
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
