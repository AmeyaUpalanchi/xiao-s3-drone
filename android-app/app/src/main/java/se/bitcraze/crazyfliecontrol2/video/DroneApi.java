package se.bitcraze.crazyfliecontrol2.video;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/** Calls the drone's web API (firmware drone_api.c). Blocking: use a background thread. */
public final class DroneApi {

    public static final String BASE = "http://" + DroneNetwork.DRONE_IP;

    public interface Progress {
        void onProgress(long sent, long total);
    }

    /** Thrown with the drone's own error text ("disarm the drone first", ...). */
    public static class ApiException extends Exception {
        public ApiException(String msg) {
            super(msg);
        }
    }

    private DroneApi() {}

    public static JSONObject get(String path) throws ApiException {
        return request("GET", path, null, null, 0, null, 4000);
    }

    public static JSONObject post(String path) throws ApiException {
        return request("POST", path, new byte[0], null, 0, null, 15000);
    }

    public static JSONObject postForm(String path, String form) throws ApiException {
        return request("POST", path, form.getBytes(StandardCharsets.UTF_8), null, 0, null, 15000);
    }

    /** Streams a file body (firmware upload). */
    public static JSONObject postStream(String path, InputStream body, long length, Progress progress) throws ApiException {
        return request("POST", path, null, body, length, progress, 60000);
    }

    private static JSONObject request(String method, String path, byte[] bytes, InputStream stream,
                                      long length, Progress progress, int readTimeoutMs) throws ApiException {
        HttpURLConnection c = null;
        try {
            c = DroneNetwork.open(BASE + path, 3000, readTimeoutMs);
            c.setRequestMethod(method);
            if (bytes != null || stream != null) {
                c.setDoOutput(true);
                if (stream != null) {
                    c.setFixedLengthStreamingMode(length);
                    c.setRequestProperty("Content-Type", "application/octet-stream");
                } else {
                    c.setFixedLengthStreamingMode(bytes.length);
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                }
                try (OutputStream out = c.getOutputStream()) {
                    if (stream != null) {
                        byte[] buf = new byte[8192];
                        long sent = 0;
                        int n;
                        while ((n = stream.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            sent += n;
                            if (progress != null) {
                                progress.onProgress(sent, length);
                            }
                        }
                    } else {
                        out.write(bytes);
                    }
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in != null ? readAll(in) : "";
            JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code >= 400 || !json.optBoolean("ok", true)) {
                throw new ApiException(json.optString("error", "drone replied " + code));
            }
            return json;
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException("Drone not reachable. Join its Wi-Fi and try again.");
        } catch (Exception e) {
            throw new ApiException(String.valueOf(e.getMessage()));
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }
}
