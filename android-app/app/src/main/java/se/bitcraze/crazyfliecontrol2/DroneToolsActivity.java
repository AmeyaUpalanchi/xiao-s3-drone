package se.bitcraze.crazyfliecontrol2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.espressif.espdrone.android.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.bitcraze.crazyfliecontrol.prefs.PreferencesActivity;
import se.bitcraze.crazyfliecontrol2.video.DroneApi;
import se.bitcraze.crazyfliecontrol2.video.DroneNetwork;

/**
 * Drone settings: flying style, motor test, level calibration, find my drone,
 * Wi-Fi name/password and firmware update. Talks to firmware drone_api.c.
 */
public class DroneToolsActivity extends AppCompatActivity {

    public static final String PREF_FLYING_STYLE = "pref_flying_style";
    public static final String STYLE_BEGINNER = "beginner";
    public static final String STYLE_NORMAL = "normal";
    public static final String STYLE_SPORT = "sport";

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final List<View> mDroneButtons = new ArrayList<>();
    private SharedPreferences mPrefs;
    private TextView mStatus;
    private TextView mLevelResult;
    private TextView mWifiCurrent;
    private TextView mFwCurrent;
    private TextView mFwResult;
    private TextView mStyleDesc;
    private LinearProgressIndicator mFwProgress;
    private TextInputEditText mWifiName;
    private TextInputEditText mWifiPass;
    private TextInputLayout mWifiPassLayout;
    private boolean mBusy;

    private final ActivityResultLauncher<String[]> mPickFirmware =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    uploadFirmware(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_drone_tools);
        View root = findViewById(R.id.tools_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        mStatus = findViewById(R.id.tools_status);
        mLevelResult = findViewById(R.id.level_result);
        mWifiCurrent = findViewById(R.id.wifi_current);
        mFwCurrent = findViewById(R.id.fw_current);
        mFwResult = findViewById(R.id.fw_result);
        mFwProgress = findViewById(R.id.fw_progress);
        mStyleDesc = findViewById(R.id.style_desc);
        mWifiName = findViewById(R.id.wifi_name);
        mWifiPass = findViewById(R.id.wifi_pass);
        mWifiPassLayout = findViewById(R.id.wifi_pass_layout);

        findViewById(R.id.tools_back).setOnClickListener(v -> finish());
        findViewById(R.id.open_controls).setOnClickListener(v ->
                startActivity(new Intent(this, PreferencesActivity.class)));

        setupFlyingStyle();

        bindDroneAction(R.id.motor_1, "/api/motor?m=1", null);
        bindDroneAction(R.id.motor_2, "/api/motor?m=2", null);
        bindDroneAction(R.id.motor_3, "/api/motor?m=3", null);
        bindDroneAction(R.id.motor_4, "/api/motor?m=4", null);
        bindDroneAction(R.id.motor_all, "/api/motor?m=0", null);
        bindDroneAction(R.id.find_beep, "/api/beep", null);
        bindDroneAction(R.id.level_run, "/api/level", json -> mLevelResult.setText(getString(R.string.level_ok,
                json.optDouble("pitch"), json.optDouble("roll"))));
        bindDroneAction(R.id.level_reset, "/api/level/reset", json -> mLevelResult.setText(R.string.level_cleared));

        MaterialButton fw = findViewById(R.id.fw_choose);
        mDroneButtons.add(fw);
        fw.setOnClickListener(v -> mPickFirmware.launch(new String[]{"application/octet-stream", "*/*"}));

        findViewById(R.id.wifi_save).setOnClickListener(v -> saveWifi());

        setDroneButtonsEnabled(false);
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------ flying style

    private void setupFlyingStyle() {
        MaterialButtonToggleGroup group = findViewById(R.id.style_group);
        String style = mPrefs.getString(PREF_FLYING_STYLE, STYLE_NORMAL);
        group.check(STYLE_BEGINNER.equals(style) ? R.id.style_beginner
                : STYLE_SPORT.equals(style) ? R.id.style_sport : R.id.style_normal);
        showStyleDescription(style);
        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) {
                return;
            }
            String s = id == R.id.style_beginner ? STYLE_BEGINNER : id == R.id.style_sport ? STYLE_SPORT : STYLE_NORMAL;
            applyFlyingStyle(mPrefs, s);
            showStyleDescription(s);
        });
    }

    private void showStyleDescription(String style) {
        mStyleDesc.setText(STYLE_BEGINNER.equals(style) ? R.string.style_desc_beginner
                : STYLE_SPORT.equals(style) ? R.string.style_desc_sport : R.string.style_desc_normal);
    }

    /** Writes the limits the stick controller already understands (advanced flight control). */
    public static void applyFlyingStyle(SharedPreferences prefs, String style) {
        String angle, yaw, maxThrust;
        switch (style) {
            case STYLE_BEGINNER:
                angle = "10"; yaw = "60"; maxThrust = "70";
                break;
            case STYLE_SPORT:
                angle = "28"; yaw = "150"; maxThrust = "95";
                break;
            default:
                angle = "18"; yaw = "90"; maxThrust = "85";
                break;
        }
        prefs.edit()
                .putString(PREF_FLYING_STYLE, style)
                .putBoolean(PreferencesActivity.KEY_PREF_AFC_BOOL, true)
                .putString(PreferencesActivity.KEY_PREF_MAX_ROLLPITCH_ANGLE, angle)
                .putString(PreferencesActivity.KEY_PREF_MAX_YAW_ANGLE, yaw)
                .putString(PreferencesActivity.KEY_PREF_MAX_THRUST, maxThrust)
                .putString(PreferencesActivity.KEY_PREF_MIN_THRUST, "25")
                .apply();
    }

    // ------------------------------------------------------------ drone calls

    private interface OnJson {
        void done(JSONObject json);
    }

    private void bindDroneAction(int id, String path, OnJson onSuccess) {
        View b = findViewById(id);
        mDroneButtons.add(b);
        b.setOnClickListener(v -> runDrone(() -> DroneApi.post(path), json -> {
            if (onSuccess != null) {
                onSuccess.done(json);
            } else {
                toast(getString(R.string.tools_done));
            }
        }));
    }

    private interface Call {
        JSONObject run() throws DroneApi.ApiException;
    }

    private void runDrone(Call call, OnJson onSuccess) {
        if (mBusy) {
            return;
        }
        mBusy = true;
        setDroneButtonsEnabled(false);
        mExecutor.execute(() -> {
            try {
                JSONObject json = call.run();
                mMain.post(() -> {
                    mBusy = false;
                    setDroneButtonsEnabled(true);
                    onSuccess.done(json);
                });
            } catch (DroneApi.ApiException e) {
                mMain.post(() -> {
                    mBusy = false;
                    setDroneButtonsEnabled(true);
                    toast(capitalise(e.getMessage()));
                });
            }
        });
    }

    private void refreshStatus() {
        mExecutor.execute(() -> {
            try {
                JSONObject s = DroneApi.get("/status");
                mMain.post(() -> {
                    String ssid = s.optString("ssid", "?");
                    String fw = s.optString("fw", "?");
                    mStatus.setText(getString(R.string.tools_online, ssid, fw));
                    mWifiCurrent.setText(getString(R.string.wifi_current_fmt, ssid));
                    mFwCurrent.setText(getString(R.string.fw_current_fmt, fw));
                    if (ssid.startsWith(DroneNetwork.DRONE_SSID_PREFIX) && mWifiName.getText() != null
                            && mWifiName.getText().length() == 0) {
                        mWifiName.setText(ssid.substring(DroneNetwork.DRONE_SSID_PREFIX.length()));
                    }
                    setDroneButtonsEnabled(true);
                });
            } catch (DroneApi.ApiException e) {
                mMain.post(() -> {
                    mStatus.setText(R.string.tools_offline);
                    mWifiCurrent.setText(R.string.tools_offline);
                    mFwCurrent.setText(R.string.tools_offline);
                    setDroneButtonsEnabled(false);
                });
            }
        });
    }

    private void setDroneButtonsEnabled(boolean enabled) {
        for (View b : mDroneButtons) {
            b.setEnabled(enabled && !mBusy);
            b.setAlpha(b.isEnabled() ? 1f : 0.45f);
        }
    }

    // ------------------------------------------------------------ wifi

    private void saveWifi() {
        String name = mWifiName.getText() != null ? mWifiName.getText().toString().trim() : "";
        String pass = mWifiPass.getText() != null ? mWifiPass.getText().toString() : "";
        if (!pass.isEmpty() && (pass.length() < 8 || pass.length() > 63)) {
            mWifiPassLayout.setError(getString(R.string.wifi_err_pass));
            return;
        }
        mWifiPassLayout.setError(null);
        if (!pass.isEmpty()) {
            // remember it for auto-join, even if the drone isn't reachable right now
            mPrefs.edit().putString(DroneNetwork.PREF_DRONE_PASSWORD, pass).apply();
        }
        runDrone(() -> {
            try {
                String form = "name=" + URLEncoder.encode(name, "UTF-8")
                        + "&password=" + URLEncoder.encode(pass, "UTF-8");
                return DroneApi.postForm("/api/wifi", form);
            } catch (java.io.UnsupportedEncodingException e) {
                throw new DroneApi.ApiException(e.getMessage());
            }
        }, json -> toast(getString(R.string.wifi_saved)));
        if (!pass.isEmpty() && !mStatus.getText().toString().contains("·")) {
            toast(getString(R.string.wifi_saved_local));
        }
    }

    // ------------------------------------------------------------ firmware

    private void uploadFirmware(Uri uri) {
        long size = -1;
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                size = c.getLong(0);
            }
        } catch (RuntimeException ignored) {
        }
        if (size <= 0) {
            toast("Couldn't read that file");
            return;
        }
        final long total = size;
        mFwProgress.setVisibility(View.VISIBLE);
        mFwProgress.setIndeterminate(false);
        mFwProgress.setProgressCompat(0, false);
        runDrone(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new DroneApi.ApiException("Couldn't open that file");
                }
                return DroneApi.postStream("/api/ota", in, total, (sent, all) -> mMain.post(() -> {
                    int pct = (int) (sent * 100 / all);
                    mFwProgress.setProgressCompat(pct, true);
                    mFwResult.setText(getString(R.string.fw_uploading, pct));
                }));
            } catch (java.io.IOException e) {
                throw new DroneApi.ApiException("Couldn't read that file");
            }
        }, json -> {
            mFwProgress.setVisibility(View.GONE);
            mFwResult.setText(R.string.fw_done);
            mMain.postDelayed(this::refreshStatus, 12000);
        });
    }

    // ------------------------------------------------------------ util

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) {
            return "Something went wrong";
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
