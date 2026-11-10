package se.bitcraze.crazyfliecontrol2.video;

import org.json.JSONObject;

/** Parsed reply of GET http://192.168.43.42/status (camera_stream.c). */
public final class DroneStatus {
    public final boolean cameraPresent;
    public final String sensor;
    public final String frame;
    public final float batteryVoltage;
    public final int batteryPercent;

    private DroneStatus(boolean cameraPresent, String sensor, String frame, float v, int pct) {
        this.cameraPresent = cameraPresent;
        this.sensor = sensor;
        this.frame = frame;
        this.batteryVoltage = v;
        this.batteryPercent = pct;
    }

    public static DroneStatus parse(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        return new DroneStatus(
                o.optBoolean("camera", false),
                o.optString("sensor", "none"),
                o.optString("frame", ""),
                (float) o.optDouble("battery_v", -1),
                o.optInt("battery_pct", -1));
    }
}
