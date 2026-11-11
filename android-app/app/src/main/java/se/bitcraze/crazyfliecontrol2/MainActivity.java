/**
 *    ||          ____  _ __
 * +------+      / __ )(_) /_______________ _____  ___
 * | 0xBC |     / __  / / __/ ___/ ___/ __ `/_  / / _ \
 * +------+    / /_/ / / /_/ /__/ /  / /_/ / / /_/  __/
 *  ||  ||    /_____/_/\__/\___/_/   \__,_/ /___/\___/
 *
 * Copyright (C) 2013 Bitcraze AB
 *
 * Crazyflie Nano Quadcopter Client
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package se.bitcraze.crazyfliecontrol2;

import java.io.File;
import java.util.List;
import java.util.Locale;

import se.bitcraze.crazyflie.lib.crazyradio.Crazyradio;
import se.bitcraze.crazyfliecontrol.controller.Controls;
import se.bitcraze.crazyfliecontrol.controller.GamepadController;
import se.bitcraze.crazyfliecontrol.controller.GyroscopeController;
import se.bitcraze.crazyfliecontrol.controller.IController;
import se.bitcraze.crazyfliecontrol.controller.TouchController;
import se.bitcraze.crazyfliecontrol.prefs.PreferencesActivity;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.MobileAnarchy.Android.Widgets.Joystick.JoystickView;
import com.espressif.espdrone.android.R;

import android.graphics.Bitmap;
import android.content.res.ColorStateList;
import com.google.android.material.button.MaterialButton;
import android.view.WindowManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import se.bitcraze.crazyfliecontrol2.video.DroneNetwork;
import se.bitcraze.crazyfliecontrol2.video.DroneStatus;
import se.bitcraze.crazyfliecontrol2.video.DroneStatusPoller;
import se.bitcraze.crazyfliecontrol2.video.MediaSaver;
import se.bitcraze.crazyfliecontrol2.video.MjpegStreamReader;
import se.bitcraze.crazyfliecontrol2.video.MjpegView;
import se.bitcraze.crazyfliecontrol2.video.Mp4FrameRecorder;

public class MainActivity extends EspActivity {

    private static final String LOG_TAG = "CrazyflieControl";
    private static final int MY_PERMISSIONS_REQUEST_LOCATION = 42;

    private JoystickView mJoystickViewLeft;
    private JoystickView mJoystickViewRight;
    private FlightDataView mFlightDataView;
    private ImageButton mJoystickLeftHLock;

    private ScrollView mConsoleScrollView;
    private TextView mConsoleTextView;

    private SharedPreferences mPreferences;

    private IController mController;
    private GamepadController mGamepadController;

    private String mRadioChannelDefaultValue;
    private String mRadioDatarateDefaultValue;

    private boolean mDoubleBackToExitPressedOnce = false;
    private Controls mControls;
    private SoundPool mSoundPool;
    private boolean mLoaded;
    private int mSoundConnect;
    private int mSoundDisconnect;

    private MaterialButton mToggleConnectButton;
    private ImageButton mRingEffectButton;
    private ImageButton mHeadlightButton;
    private ImageButton mBuzzerSoundButton;
    private File mCacheDir;

    private TextView mTextView_battery;
    private TextView mTextView_batteryVoltage;
    private BatteryView mBatteryView;
    private TextView mTextView_linkQuality;
    private MainPresenter mPresenter;

    // ---- XIAO ESP32-S3 Sense camera ----
    private static final int RECORD_FPS = 20;
    private MjpegView mVideoView;
    private View mVideoPlaceholder;
    private TextView mVideoPlaceholderTitle;
    private TextView mVideoPlaceholderSubtitle;
    // ---- Flight controls ----
    /** Rough hover time on a full 1S pack with 720 brushed motors; refined in flight. */
    private static final float NOMINAL_FLIGHT_MIN = 6f;
    private static final int BATTERY_RESERVE_PCT = 10;
    private MaterialButton mArmButton;
    private MaterialButton mLandButton;
    private MaterialButton mStopButton;
    private TextView mFlightTime;
    private TextView mFlightStatus;
    private long mFlightSampleStartMs;
    private int mFlightSampleStartPct = -1;
    private boolean mConnecting = false;
    private TextView mBatteryWarning;
    private int mBatteryWarnLevel = 0;   // 0 none, 1 low (25%), 2 critical (15%)

    private MaterialButton mPhotoButton;
    private MaterialButton mRecordButton;
    private MaterialButton mLiveButton;
    private TextView mChipLive;
    private TextView mChipRec;
    private DroneStatusPoller mStatusPoller;
    private MjpegStreamReader mStreamReader;
    private final Mp4FrameRecorder mRecorder = new Mp4FrameRecorder();
    private final ExecutorService mMediaExecutor = Executors.newSingleThreadExecutor();
    private final Handler mUiHandler = new Handler(android.os.Looper.getMainLooper());
    private volatile byte[] mLatestJpeg;
    private volatile int mFrameWidth;
    private volatile int mFrameHeight;
    private boolean mDroneReachable = false;
    private boolean mCameraPresent = false;
    private boolean mLiveWanted = false;
    private boolean mAutoLiveDone = false;
    private volatile boolean mStreamConnected = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPresenter = new MainPresenter(this);

        setDefaultPreferenceValues();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.content.SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.contains(DroneToolsActivity.PREF_FLYING_STYLE)) {
            DroneToolsActivity.applyFlyingStyle(sp, DroneToolsActivity.STYLE_NORMAL);
        }
        setupEdgeToEdge();

        mTextView_battery = (TextView) findViewById(R.id.battery_text);
        mTextView_batteryVoltage = (TextView) findViewById(R.id.battery_voltage);
        mBatteryView = (BatteryView) findViewById(R.id.battery_icon);
        mTextView_linkQuality = (TextView) findViewById(R.id.linkQuality_text);

        setBatteryLevel(-1.0f);
        setLinkQualityText("N/A");

        mControls = new Controls(this, mPreferences);
        mControls.setDefaultPreferenceValues(getResources());

        //Default controller
        mJoystickViewLeft = (JoystickView) findViewById(R.id.joystick_left);
        mJoystickViewRight = (JoystickView) findViewById(R.id.joystick_right);
        mJoystickViewRight.setLeft(false);
        mController = new TouchController(mControls, this, mJoystickViewLeft, mJoystickViewRight);
        mJoystickLeftHLock = findViewById(R.id.joystick_left_hlock);
        mJoystickLeftHLock.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                boolean targetState = !mJoystickViewLeft.isHorizontalLocked();
                mJoystickViewLeft.setHorizontalLocked(targetState);
                mJoystickLeftHLock.setBackgroundResource(targetState ? R.drawable.bg_lock_on :
                        R.drawable.bg_lock_off);
            }
        });

        //initialize gamepad controller
        mGamepadController = new GamepadController(mControls, this, mPreferences);
        mGamepadController.setDefaultPreferenceValues(getResources());

        //initialize buttons
        mToggleConnectButton = findViewById(R.id.imageButton_connect);
        initializeMenuButtons();

        mFlightDataView = (FlightDataView) findViewById(R.id.flightdataview);

        mConsoleScrollView = (ScrollView) findViewById(R.id.console_scrollView);
        mConsoleTextView = (TextView) findViewById(R.id.console_textView);
        registerForContextMenu(mConsoleTextView);

        //action buttons
        mRingEffectButton = (ImageButton) findViewById(R.id.button_ledRing);
        mHeadlightButton = (ImageButton) findViewById(R.id.button_headLight);
        mBuzzerSoundButton = (ImageButton) findViewById(R.id.button_buzzerSound);

        IntentFilter filter = new IntentFilter();
        filter.addAction(this.getPackageName()+".USB_PERMISSION");
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        ContextCompat.registerReceiver(this, mUsbReceiver, filter, ContextCompat.RECEIVER_EXPORTED);

        initializeSounds();

        setCacheDir();

        initializeCameraUi();
        DroneNetwork.start(this);

        findViewById(R.id.button_help).setOnClickListener(v -> showHowToFly());
        initializeFlightControls();
        if (!mPreferences.getBoolean(PREF_HELP_SEEN, false)) {
            mUiHandler.postDelayed(this::showHowToFly, 600);
        }
        getOnBackPressedDispatcher().addCallback(this, mBackCallback);
    }

    // =====================================================================
    //  Arm / Land / Stop, flight time, one-tap connect
    // =====================================================================

    private void initializeFlightControls() {
        mArmButton = findViewById(R.id.button_arm);
        mLandButton = findViewById(R.id.button_land);
        mStopButton = findViewById(R.id.button_stop);
        mFlightTime = findViewById(R.id.flight_time);
        mFlightStatus = findViewById(R.id.flight_status);
        mBatteryWarning = findViewById(R.id.battery_warning);

        mArmButton.setOnClickListener(v -> {
            if (!mPresenter.isConnected()) {
                return;
            }
            if (mPresenter.getFlightState() == MainPresenter.STATE_DISARMED) {
                if (mController != null && mController.getThrust() > 0) {
                    showToastie(getString(R.string.toast_lower_throttle));
                    return;
                }
                mPresenter.arm();
                showToastie(getString(R.string.toast_armed));
            } else {
                if (mPresenter.isFlying()) {
                    showToastie(getString(R.string.toast_land_first));
                    return;
                }
                mPresenter.disarm();
            }
        });

        mLandButton.setOnClickListener(v -> {
            if (mPresenter.getFlightState() == MainPresenter.STATE_LANDING) {
                mPresenter.cancelLanding();
            } else if (canTakeOff()) {
                mPresenter.takeOff();
            } else {
                mPresenter.autoLand();
            }
        });

        // Stop must be held (~0.5 s) so a stray tap cannot drop the drone.
        mStopButton.setOnClickListener(v -> showToastie(getString(R.string.toast_hold_stop)));
        mStopButton.setOnLongClickListener(v -> {
            mPresenter.kill();
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            showToastie(getString(R.string.toast_motors_stopped));
            return true;
        });
        updateFlightUi();
    }

    /** Called by the presenter (any thread) when arm/landing state changes. */
    public void onFlightStateChanged(int state) {
        runOnUiThread(this::updateFlightUi);
    }

    private void updateFlightUi() {
        if (mArmButton == null) {
            return;
        }
        boolean connected = mPresenter != null && mPresenter.isConnected();
        int state = mPresenter != null ? mPresenter.getFlightState() : MainPresenter.STATE_DISARMED;
        boolean armed = state != MainPresenter.STATE_DISARMED;
        boolean landing = state == MainPresenter.STATE_LANDING || state == MainPresenter.STATE_FAILSAFE;

        mArmButton.setEnabled(connected);
        mArmButton.setAlpha(connected ? 1f : 0.4f);
        mArmButton.setText(armed ? R.string.button_disarm : R.string.button_arm);
        int warn = ContextCompat.getColor(this, R.color.status_warn);
        mArmButton.setBackgroundTintList(ColorStateList.valueOf(armed ? warn : ContextCompat.getColor(this, R.color.transparent)));
        mArmButton.setTextColor(armed ? Color.BLACK : ContextCompat.getColor(this, R.color.brand_on_surface));

        mLandButton.setEnabled(armed && state != MainPresenter.STATE_FAILSAFE);
        mLandButton.setAlpha(mLandButton.isEnabled() ? 1f : 0.4f);
        if (state == MainPresenter.STATE_LANDING) {
            mLandButton.setText(R.string.button_cancel_land);
            mLandButton.setIconResource(R.drawable.ic_land);
        } else if (canTakeOff()) {
            mLandButton.setText(R.string.button_takeoff);
            mLandButton.setIconResource(R.drawable.ic_takeoff);
        } else {
            mLandButton.setText(R.string.button_land);
            mLandButton.setIconResource(R.drawable.ic_land);
        }
        if (!armed) {
            mBatteryWarnLevel = 0;
            mBatteryWarning.setVisibility(View.GONE);
        }

        mStopButton.setEnabled(armed);
        mStopButton.setAlpha(armed ? 1f : 0.4f);

        if (landing) {
            mFlightStatus.setText(state == MainPresenter.STATE_FAILSAFE ? R.string.status_failsafe : R.string.status_landing);
            mFlightStatus.setVisibility(View.VISIBLE);
        } else {
            mFlightStatus.setVisibility(View.GONE);
        }
    }

    private void updateFlightTime(int percent) {
        if (mFlightTime == null) {
            return;
        }
        if (percent < 0) {
            mFlightTime.setVisibility(View.GONE);
            mFlightSampleStartPct = -1;
            return;
        }
        float usable = Math.max(0, percent - BATTERY_RESERVE_PCT);
        float minutes = usable / (100f - BATTERY_RESERVE_PCT) * NOMINAL_FLIGHT_MIN;
        boolean flying = mPresenter != null && mPresenter.isFlying();
        checkBatteryWarning(percent);
        long now = System.currentTimeMillis();
        if (flying) {
            if (mFlightSampleStartPct < 0) {
                mFlightSampleStartPct = percent;
                mFlightSampleStartMs = now;
            }
            float elapsedMin = (now - mFlightSampleStartMs) / 60000f;
            int drop = mFlightSampleStartPct - percent;
            if (elapsedMin > 0.75f && drop >= 2) {
                minutes = usable / (drop / elapsedMin);   // measured drain rate
            }
        } else {
            mFlightSampleStartPct = -1;
        }
        mFlightTime.setText(minutes < 1f ? getString(R.string.flight_time_short)
                : getString(R.string.flight_time_fmt, Math.round(minutes)));
        mFlightTime.setVisibility(View.VISIBLE);
    }

    private boolean canTakeOff() {
        return mPresenter != null && mPresenter.isHeightMode()
                && mPresenter.getFlightState() == MainPresenter.STATE_ARMED && !mPresenter.isFlying();
    }

    /** Called by the presenter when a VL53L1X height sensor is (not) found. */
    public void onHeightModeChanged(boolean heightMode) {
        runOnUiThread(() -> {
            mFlightDataView.setHeightMode(heightMode);
            if (heightMode) {
                showToastie(getString(R.string.toast_height_sensor));
            }
            updateFlightUi();
        });
    }

    public void setHeight(float metres) {
        runOnUiThread(() -> {
            mFlightDataView.setHeight(metres);
            updateFlightUi();
        });
    }

    /** Warn once at 25 % and again at 15 % while armed. */
    private void checkBatteryWarning(int percent) {
        boolean armed = mPresenter != null && mPresenter.getFlightState() != MainPresenter.STATE_DISARMED;
        if (!armed || percent < 0) {
            return;
        }
        int level = percent <= 15 ? 2 : percent <= 25 ? 1 : 0;
        if (level > mBatteryWarnLevel) {
            mBatteryWarnLevel = level;
            mBatteryWarning.setText(level == 2 ? R.string.battery_critical : R.string.battery_low);
            mBatteryWarning.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                    level == 2 ? R.color.status_danger : R.color.status_warn)));
            mBatteryWarning.setTextColor(level == 2 ? Color.WHITE : Color.BLACK);
            mBatteryWarning.setVisibility(View.VISIBLE);
            vibrate(level == 2 ? new long[]{0, 250, 120, 250, 120, 400} : new long[]{0, 300});
        }
    }

    private void vibrate(long[] pattern) {
        android.os.Vibrator v = getSystemService(android.os.Vibrator.class);
        if (v != null && v.hasVibrator()) {
            v.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1));
        }
    }

    /** Connect button: join the drone Wi-Fi if needed, then open the CRTP link. */
    private void connectToDrone() {
        if (mConnecting) {
            return;
        }
        mConnecting = true;
        mToggleConnectButton.setText(R.string.button_connecting);
        mMediaExecutor.execute(() -> {
            boolean reachable = DroneNetwork.isDroneReachable();
            mUiHandler.post(() -> {
                if (reachable) {
                    finishConnect();
                    return;
                }
                showToastie(getString(R.string.toast_finding_wifi));
                DroneNetwork.joinDroneWifi(this, new DroneNetwork.JoinListener() {
                    @Override
                    public void onJoined() {
                        finishConnect();
                    }

                    @Override
                    public void onFailed() {
                        mConnecting = false;
                        setConnectionButtonDisconnected();
                        showToastie(getString(R.string.toast_wifi_failed));
                    }
                });
            });
        });
    }

    private void finishConnect() {
        mConnecting = false;
        connect();
    }

    // =====================================================================
    //  "How to fly" guide: first launch + the ? button
    // =====================================================================

    private static final String PREF_HELP_SEEN = "pref_help_seen_v1";

    private void showHowToFly() {
        if (isFinishing()) {
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.dialog_help, null);
        androidx.appcompat.app.AlertDialog dialog =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setView(content)
                        .setPositiveButton(R.string.help_done, (d, w) ->
                                mPreferences.edit().putBoolean(PREF_HELP_SEEN, true).apply())
                        .setOnCancelListener(d ->
                                mPreferences.edit().putBoolean(PREF_HELP_SEEN, true).apply())
                        .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            int w = Math.min(getResources().getDisplayMetrics().widthPixels * 9 / 10,
                    (int) (720 * getResources().getDisplayMetrics().density));
            dialog.getWindow().setLayout(w, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    // =====================================================================
    //  Full screen / edge-to-edge (targetSdk 36 always draws edge-to-edge)
    // =====================================================================

    private void setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.displayCutout()
                    | WindowInsetsCompat.Type.systemBars());
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        c.hide(WindowInsetsCompat.Type.systemBars());
    }

    // =====================================================================
    //  Camera: live view, photo, record
    // =====================================================================

    private void initializeCameraUi() {
        mVideoView = findViewById(R.id.video_view);
        mVideoPlaceholder = findViewById(R.id.video_placeholder);
        mVideoPlaceholderTitle = findViewById(R.id.video_placeholder_title);
        mVideoPlaceholderSubtitle = findViewById(R.id.video_placeholder_subtitle);
        mPhotoButton = findViewById(R.id.button_photo);
        mRecordButton = findViewById(R.id.button_record);
        mLiveButton = findViewById(R.id.button_live);
        mChipLive = findViewById(R.id.chip_live);
        mChipRec = findViewById(R.id.chip_rec);

        mLiveButton.setOnClickListener(v -> {
            if (mLiveWanted) {
                stopLive();
            } else {
                startLive();
            }
        });
        mPhotoButton.setOnClickListener(v -> takePhoto());
        mRecordButton.setOnClickListener(v -> {
            if (mRecorder.isRecording()) {
                stopRecording();
            } else {
                startRecording();
            }
        });

        mStatusPoller = new DroneStatusPoller(new DroneStatusPoller.Listener() {
            @Override
            public void onDroneStatus(DroneStatus status) {
                mDroneReachable = true;
                mCameraPresent = status.cameraPresent;
                if (status.batteryPercent >= 0) {
                    setBattery(status.batteryPercent, status.batteryVoltage);
                }
                // "if XIAO Sense is connected" -> show the live feed automatically once
                if (mCameraPresent && !mAutoLiveDone) {
                    mAutoLiveDone = true;
                    startLive();
                }
                if (!mCameraPresent && mLiveWanted) {
                    stopLive();
                }
                updateVideoUi();
            }

            @Override
            public void onDroneUnreachable() {
                boolean wasReachable = mDroneReachable;
                mDroneReachable = false;
                if (wasReachable && (mPresenter == null || mPresenter.getCrazyflie() == null)) {
                    setBatteryLevel(-1f);
                }
                updateVideoUi();
            }
        });
        updateVideoUi();
    }

    private void startLive() {
        mLiveWanted = true;
        if (mStreamReader == null) {
            mStreamReader = new MjpegStreamReader(DroneNetwork.STREAM_URL, new MjpegStreamReader.Listener() {
                @Override
                public void onStreamConnected() {
                    mUiHandler.post(MainActivity.this::updateVideoUi);
                }

                @Override
                public void onFrame(byte[] jpeg, Bitmap bitmap) {
                    mLatestJpeg = jpeg;
                    mFrameWidth = bitmap.getWidth();
                    mFrameHeight = bitmap.getHeight();
                    mVideoView.setFrame(bitmap);
                    mRecorder.addFrame(bitmap);
                    if (!mStreamConnected) {
                        mStreamConnected = true;
                        mUiHandler.post(MainActivity.this::updateVideoUi);
                    }
                }

                @Override
                public void onStreamError(String message) {
                    mStreamConnected = false;
                    mUiHandler.post(() -> {
                        if (mRecorder.isRecording()) {
                            stopRecording();
                        }
                        updateVideoUi();
                    });
                }
            });
        }
        mStreamReader.start();
        updateVideoUi();
    }

    private void stopLive() {
        mLiveWanted = false;
        if (mRecorder.isRecording()) {
            stopRecording();
        }
        if (mStreamReader != null) {
            mStreamReader.stop();
            mStreamReader = null;
        }
        mStreamConnected = false;
        mLatestJpeg = null;
        mVideoView.clear();
        updateVideoUi();
    }

    private void takePhoto() {
        if (!mCameraPresent && !mStreamConnected) {
            showToastie(getString(R.string.toast_no_camera));
            return;
        }
        final byte[] frame = mStreamConnected ? mLatestJpeg : null;
        mPhotoButton.setEnabled(false);
        flashVideo();
        mMediaExecutor.execute(() -> {
            try {
                byte[] jpeg = frame != null ? frame : fetchCapture();
                String where = MediaSaver.savePhoto(getApplicationContext(), jpeg);
                showToastie(getString(R.string.toast_photo_saved, where));
            } catch (Exception e) {
                Log.e(LOG_TAG, "photo failed", e);
                showToastie(getString(R.string.toast_photo_failed, String.valueOf(e.getMessage())));
            } finally {
                mUiHandler.post(this::updateVideoUi);
            }
        });
    }

    /** Full-resolution still from GET /capture (used when the live feed is off). */
    private static byte[] fetchCapture() throws Exception {
        HttpURLConnection c = DroneNetwork.open(DroneNetwork.CAPTURE_URL, 3000, 5000);
        try {
            if (c.getResponseCode() != 200) {
                throw new Exception("camera returned HTTP " + c.getResponseCode());
            }
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream(128 * 1024);
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private void flashVideo() {
        mVideoView.animate().cancel();
        mVideoView.setAlpha(0.25f);
        mVideoView.animate().alpha(1f).setDuration(250).start();
    }

    private void startRecording() {
        if (!mStreamConnected || mFrameWidth == 0) {
            showToastie(getString(R.string.toast_need_live));
            return;
        }
        final int w = mFrameWidth, h = mFrameHeight;
        mRecordButton.setEnabled(false);
        mMediaExecutor.execute(() -> {
            try {
                mRecorder.start(getApplicationContext(), w, h, RECORD_FPS);
            } catch (Exception e) {
                Log.e(LOG_TAG, "record failed", e);
                showToastie(getString(R.string.toast_record_failed, String.valueOf(e.getMessage())));
            }
            mUiHandler.post(() -> {
                updateVideoUi();
                mUiHandler.post(mRecTicker);
            });
        });
    }

    private void stopRecording() {
        mUiHandler.removeCallbacks(mRecTicker);
        mRecordButton.setEnabled(false);
        mMediaExecutor.execute(() -> {
            String where = mRecorder.stop();
            if (where != null) {
                showToastie(getString(R.string.toast_video_saved, where));
            }
            mUiHandler.post(this::updateVideoUi);
        });
    }

    private final Runnable mRecTicker = new Runnable() {
        @Override
        public void run() {
            if (!mRecorder.isRecording()) {
                return;
            }
            long s = mRecorder.elapsedMs() / 1000;
            mChipRec.setText(getString(R.string.chip_rec_fmt, (int) (s / 60), (int) (s % 60)));
            mChipRec.setAlpha(((s & 1) == 0) ? 1f : 0.75f);
            mUiHandler.postDelayed(this, 500);
        }
    };

    private void updateVideoUi() {
        if (mVideoView == null) {
            return;
        }
        boolean recording = mRecorder.isRecording();
        boolean showingVideo = mLiveWanted && mStreamConnected && mVideoView.hasFrame();

        mVideoPlaceholder.setVisibility(showingVideo ? View.GONE : View.VISIBLE);
        if (!mDroneReachable && !mStreamConnected) {
            mVideoPlaceholderTitle.setText(R.string.video_no_drone);
            mVideoPlaceholderSubtitle.setText(R.string.video_no_drone_hint);
        } else if (!mCameraPresent && !mStreamConnected) {
            mVideoPlaceholderTitle.setText(R.string.video_no_camera);
            mVideoPlaceholderSubtitle.setText(R.string.video_no_camera_hint);
        } else if (mLiveWanted) {
            mVideoPlaceholderTitle.setText(R.string.video_connecting);
            mVideoPlaceholderSubtitle.setText("");
        } else {
            mVideoPlaceholderTitle.setText(R.string.video_ready);
            mVideoPlaceholderSubtitle.setText(R.string.video_ready_hint);
        }

        boolean cameraUsable = mCameraPresent || mStreamConnected;
        int surface = ContextCompat.getColor(this, R.color.brand_surface_high);
        int accent = ContextCompat.getColor(this, R.color.brand_primary);
        int danger = ContextCompat.getColor(this, R.color.status_danger);

        mLiveButton.setEnabled(cameraUsable || mLiveWanted);
        mLiveButton.setText(mLiveWanted ? R.string.button_live_off : R.string.button_live);
        mLiveButton.setBackgroundTintList(ColorStateList.valueOf(mLiveWanted ? accent : surface));
        mLiveButton.setAlpha(mLiveButton.isEnabled() ? 1f : 0.4f);

        mPhotoButton.setEnabled(cameraUsable);
        mPhotoButton.setAlpha(cameraUsable ? 1f : 0.4f);

        mRecordButton.setEnabled(recording || mStreamConnected);
        mRecordButton.setAlpha(mRecordButton.isEnabled() ? 1f : 0.4f);
        mRecordButton.setIconResource(recording ? R.drawable.ic_stop_white : R.drawable.ic_record);
        mRecordButton.setText(recording ? R.string.button_stop : R.string.button_record);
        mRecordButton.setBackgroundTintList(ColorStateList.valueOf(recording ? danger : surface));

        mChipLive.setVisibility(showingVideo ? View.VISIBLE : View.GONE);
        mChipRec.setVisibility(recording ? View.VISIBLE : View.GONE);
    }

    private void initializeSounds() {
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Load sounds
        mSoundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        mSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                mLoaded = true;
            }
        });
        mSoundConnect = mSoundPool.load(this, R.raw.proxima, 1);
        mSoundDisconnect = mSoundPool.load(this, R.raw.tejat, 1);
    }

    private void setCacheDir() {
        if (isExternalStorageWriteable()) {
            Log.d(LOG_TAG, "External storage is writeable.");
            if (mCacheDir == null) {
                File appDir = getApplicationContext().getExternalFilesDir(null);
                mCacheDir = new File(appDir, "TOC_cache");
                mCacheDir.mkdirs();
            }
        } else {
            Log.d(LOG_TAG, "External storage is not writeable.");
            mCacheDir = null;
        }
    }

    private boolean isExternalStorageWriteable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private void setDefaultPreferenceValues(){
        // Set default preference values
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        // Initialize preferences
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mRadioChannelDefaultValue = getString(R.string.preferences_radio_channel_defaultValue);
        mRadioDatarateDefaultValue = getString(R.string.preferences_radio_datarate_defaultValue);
    }

    private void checkScreenLock() {
        boolean isScreenLock = mPreferences.getBoolean(PreferencesActivity.KEY_PREF_SCREEN_ROTATION_LOCK_BOOL, false);
        if(isScreenLock){
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else{
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    private void checkConsole() {
        boolean showConsole = mPreferences.getBoolean(PreferencesActivity.KEY_PREF_SHOW_CONSOLE_BOOL, false);
        if (showConsole) {
            mConsoleScrollView.setVisibility(View.VISIBLE);
        } else {
            mConsoleScrollView.setVisibility(View.INVISIBLE);
        }
    }

    private void initializeMenuButtons() {
        mToggleConnectButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                try {
                    if (mPresenter != null && mPresenter.getCrazyflie() != null && mPresenter.getCrazyflie().isConnected()) {
                        mPresenter.disconnect();
                    } else {
                        // TODO: FIXME
                        if(isCrazyradioAvailable(MainActivity.this)) {
                            connectCrazyradio();
                        } else {
                            // ESP-Drone talks CRTP over the drone's Wi-Fi access point
                            connectToDrone();
                        }
                    }
                } catch (IllegalStateException e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        ImageButton settingsButton = (ImageButton) findViewById(R.id.imageButton_settings);
        settingsButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
              Intent intent = new Intent(MainActivity.this, DroneToolsActivity.class);
              startActivity(intent);
            }
        });
    }

    private void connectCrazyradio() {
        int radioChannel = Integer.parseInt(mPreferences.getString(PreferencesActivity.KEY_PREF_RADIO_CHANNEL, mRadioChannelDefaultValue));
        int radioDatarate = Integer.parseInt(mPreferences.getString(PreferencesActivity.KEY_PREF_RADIO_DATARATE, mRadioDatarateDefaultValue));
        mPresenter.connectCrazyradio(radioChannel, radioDatarate, mCacheDir);
    }

    private void connectBlePreChecks() {
        // Check if Bluetooth LE is supported by the Android version
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Log.e(LOG_TAG, Build.VERSION.SDK_INT + "does not support Bluetooth LE.");
            Toast.makeText(this, Build.VERSION.SDK_INT + "does not support Bluetooth LE. Please use a Crazyradio to connect to the Crazyflie instead.", Toast.LENGTH_LONG).show();
            return;
        }
        // Check if Bluetooth LE is supported by the hardware
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(LOG_TAG, "Device does not support Bluetooth LE.");
            Toast.makeText(this,  "Device does not support Bluetooth LE. Please use a Crazyradio to connect to the Crazyflie instead.", Toast.LENGTH_LONG).show();
            return;
        }
        // Since Android version 6, ACCESS_COARSE_LOCATION is required for Bluetooth scanning
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.e(LOG_TAG, "Android version >= 6 requires ACCESS_COARSE_LOCATION permissions for Bluetooth scanning.");
            requestPermissions(Manifest.permission.ACCESS_FINE_LOCATION, MY_PERMISSIONS_REQUEST_LOCATION);
        } else {
            connect();
        }
    }

    private void connect() {
//        connectBle();
        connectUDP();
    }

    private void connectBle() {
        boolean writeWithResponse = mPreferences.getBoolean(PreferencesActivity.KEY_PREF_BLATENCY_BOOL, false);
        Log.d(LOG_TAG, "Using bluetooth write with response - " + writeWithResponse);
        mPresenter.connectBle(writeWithResponse, mCacheDir);
    }

    private void connectUDP() {
        mPresenter.connectUDP(mCacheDir);
    }

    private void checkLocationSettings() {
        LocationManager service = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean isEnabled = service.isProviderEnabled(LocationManager.GPS_PROVIDER);
        if (!isEnabled) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Location Access");
            builder.setMessage("The app needs location access for Bluetooth scanning. Please enable it in the settings menu.");
            builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }
            });
            builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Log.d(LOG_TAG, "Location access request has been denied.");
                }
            });
            builder.show();
        } else {
            connect();
        }

    }

    private void requestPermissions(String permission, int request) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted. Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, permission)) {
                // Show an explanation to the user *asynchronously* -- don't block this thread waiting for the user's response!
                // After the user sees the explanation, try again to request the permission.
                Log.d(LOG_TAG, "ACCESS_COARSE_LOCATION permission request has been denied.");
                //Toast.makeText(this,  "Android version >= 6 requires ACCESS_COARSE_LOCATION permissions for Bluetooth scanning.", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, request);
            } else {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{permission}, request);
            }
        } else {
            // Permission has already been granted
            checkLocationSettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the contacts-related task you need to do.
                    checkLocationSettings();
                } else {
                    // permission denied, boo! Disable the functionality that depends on this permission.
                    Log.d(LOG_TAG, "ACCESS_COARSE_LOCATION permission request has been denied.");
                    Toast.makeText(this,  "Android version >= 6 requires ACCESS_COARSE_LOCATION permissions for Bluetooth scanning.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        //TODO: improve
        PreferencesActivity.setDefaultJoystickSize(this);
        mJoystickViewLeft.setPreferences(mPreferences);
        mJoystickViewRight.setPreferences(mPreferences);
        mControls.setControlConfig();
        mGamepadController.setControlConfig();
        resetInputMethod();
        checkScreenLock();
        checkConsole();
        //disable action buttons
        mRingEffectButton.setEnabled(false);
        mHeadlightButton.setEnabled(false);
        mBuzzerSoundButton.setEnabled(false);
        hideSystemBars();
        mStatusPoller.start();
        if (mLiveWanted) {
            startLive();
        }
        //mJoystickViewLeft.requestLayout();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "onPause()");
        if (mControls != null) {
            mControls.resetAxisValues();
        }
        if (mController != null) {
            mController.disable();
        }
        updateFlightData();
        mPresenter.disconnect();
        mStatusPoller.stop();
        if (mRecorder.isRecording()) {
            stopRecording();
        }
        if (mStreamReader != null) {
            mStreamReader.stop();
            mStreamReader = null;
        }
        mStreamConnected = false;
    }

    @Override
    protected void onDestroy() {
        Log.d(LOG_TAG, "onDestroy()");
        unregisterReceiver(mUsbReceiver);
        mSoundPool.release();
        mSoundPool = null;
        mPresenter.onDestroy();
        mUiHandler.removeCallbacksAndMessages(null);
        mMediaExecutor.shutdown();
        DroneNetwork.stop();
        super.onDestroy();
    }

    /** Back (button or predictive gesture) must be pressed twice so a stray swipe cannot quit mid-flight. */
    private final androidx.activity.OnBackPressedCallback mBackCallback = new androidx.activity.OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (mDoubleBackToExitPressedOnce) {
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                return;
            }
            mDoubleBackToExitPressedOnce = true;
            Toast.makeText(MainActivity.this, "Press BACK again to exit", Toast.LENGTH_SHORT).show();
            mUiHandler.postDelayed(() -> mDoubleBackToExitPressedOnce = false, 2000);
        }
    };

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.console_textView) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            menu.add(Menu.NONE, 0, 0, "Copy to clipboard");
            menu.add(Menu.NONE, 1, 1, "Clear console");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                ClipboardManager cm = (ClipboardManager) getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("console text", mConsoleTextView.getText());
                cm.setPrimaryClip(clipData);
                showToastie("Copied to clipboard");
                break;
            case 1:
                mConsoleTextView.setText("");
                showToastie("Console cleared");
                break;
            default:
                break;
        }
        return true;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void setHideyBar() {
        Log.i(LOG_TAG, "Activating immersive mode");
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;

        if(Build.VERSION.SDK_INT >= 14){
            newUiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        if(Build.VERSION.SDK_INT >= 16){
            newUiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        if(Build.VERSION.SDK_INT >= 18){
            newUiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }

    //TODO: fix indirection
    public void updateFlightData(){
        mFlightDataView.updateFlightData(mController.getPitch(), mController.getRoll(), mController.getThrust(), mController.getYaw());
    }

    public void appendToConsole(String text) {
        final String ftext = text;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConsoleTextView.append("\n" + ftext);
                mConsoleScrollView.post(new Runnable() {
                    @Override
                    public void run() {
                        mConsoleScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        // Check that the event came from a joystick since a generic motion event could be almost anything.
        if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0 && event.getAction() == MotionEvent.ACTION_MOVE && mController instanceof GamepadController) {
            mGamepadController.dealWithMotionEvent(event);
            updateFlightData();
            return true;
        } else {
            return super.dispatchGenericMotionEvent(event);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // do not call super if key event comes from a gamepad, otherwise the buttons can quit the app
        if (isJoystickButton(event.getKeyCode()) && mController instanceof GamepadController) {
            mGamepadController.dealWithKeyEvent(event);
            // exception for OUYA controllers
            if (!Build.MODEL.toUpperCase(Locale.getDefault()).contains("OUYA")) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    // this workaround is necessary because DPad buttons are not considered to be "Gamepad buttons"
    private static boolean isJoystickButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return KeyEvent.isGamepadButton(keyCode);
        }
    }

    private void resetInputMethod() {
        mController.disable();
        updateFlightData();
        switch (mControls.getControllerType()) {
            case 0:
                // Use GyroscopeController if activated in the preferences
                if (mControls.isUseGyro()) {
                    mController = new GyroscopeController(mControls, this, mJoystickViewLeft, mJoystickViewRight);
                } else {
                    // TODO: reuse existing touch controller?
                    mController = new TouchController(mControls, this, mJoystickViewLeft, mJoystickViewRight);
                }
                break;
            case 1:
                    // TODO: show warning if no game pad is found?
                    mController = mGamepadController;
                break;
            default:
                break;

        }
        mController.enable();
        Toast.makeText(this, "Using " + mController.getControllerName(), Toast.LENGTH_SHORT).show();
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(LOG_TAG, "mUsbReceiver action: " + action);
            if ((MainActivity.this.getPackageName()+".USB_PERMISSION").equals(action)) {
                //reached only when USB permission on physical connect was canceled and "Connect" or "Radio Scan" is clicked
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Toast.makeText(MainActivity.this, "Crazyradio attached", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(LOG_TAG, "permission denied for device " + device);
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && UsbLinkAndroid.isUsbDevice(device, Crazyradio.CRADIO_VID, Crazyradio.CRADIO_PID)) {
                    Log.d(LOG_TAG, "Crazyradio detached");
                    Toast.makeText(MainActivity.this, "Crazyradio detached", Toast.LENGTH_SHORT).show();
                    playSound(mSoundDisconnect);
                    if (mPresenter != null && mPresenter.getCrazyflie() != null) {
                        Log.d(LOG_TAG, "linkDisconnect()");
                        mPresenter.disconnect();
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && UsbLinkAndroid.isUsbDevice(device, Crazyradio.CRADIO_VID, Crazyradio.CRADIO_PID)) {
                    Log.d(LOG_TAG, "Crazyradio attached");
                    Toast.makeText(MainActivity.this, "Crazyradio attached", Toast.LENGTH_SHORT).show();
                    playSound(mSoundConnect);
                }
            }
        }
    };

    private void playSound(int sound){
        if (mLoaded) {
            float volume = 1.0f;
            mSoundPool.play(sound, volume, volume, 1, 0, 1f);
        }
    }

    // extra method for onClick attribute in XML
    public void switchLedRingEffect(View view) {
        if (mPresenter != null) {
            mPresenter.runAltAction("ring.effect");
        }
    }

    // extra method for onClick attribute in XML
    public void toggleHeadlight(View view) {
        if (mPresenter != null) {
            mPresenter.runAltAction("ring.headlightEnable");
        }
    }

    // extra method for onClick attribute in XML
    public void playBuzzerSound(View view) {
        if (mPresenter != null) {
            mPresenter.runAltAction("sound.effect:10");
        }
    }

    public MainPresenter getPresenter() {
        return mPresenter;
    }

    public IController getController(){
        return mController;
    }

    public Controls getControls(){
        return mControls;
    }

    public static boolean isCrazyradioAvailable(Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            throw new IllegalArgumentException("UsbManager == null!");
        }
        List<UsbDevice> usbDeviceList = UsbLinkAndroid.findUsbDevices(usbManager, (short) Crazyradio.CRADIO_VID, (short) Crazyradio.CRADIO_PID);
        return !usbDeviceList.isEmpty();
    }

    /** 1S LiPo open-circuit voltage at 0,5..100 % (same table as the firmware). */
    private static final float[] LIPO_1S = {
            3.27f, 3.61f, 3.69f, 3.71f, 3.73f, 3.75f, 3.77f, 3.79f, 3.80f, 3.82f,
            3.84f, 3.85f, 3.87f, 3.91f, 3.95f, 3.98f, 4.02f, 4.08f, 4.11f, 4.15f, 4.20f};

    public static int percentFromVoltage(float v) {
        if (v <= 0f) {
            return -1;
        }
        if (v <= LIPO_1S[0]) {
            return 0;
        }
        for (int i = 1; i < LIPO_1S.length; i++) {
            if (v < LIPO_1S[i]) {
                float frac = (v - LIPO_1S[i - 1]) / (LIPO_1S[i] - LIPO_1S[i - 1]);
                return Math.round((i - 1) * 5 + frac * 5);
            }
        }
        return 100;
    }

    /** Legacy entry point: voltage only (or -1 for unknown). */
    public void setBatteryLevel(float voltage) {
        setBattery(percentFromVoltage(voltage), voltage);
    }

    /** @param percent 0..100 or -1 when unknown; @param voltage battery volts or <= 0 */
    public void setBattery(final int percent, final float voltage) {
        runOnUiThread(() -> {
            if (mTextView_battery == null) {
                return;
            }
            if (percent < 0) {
                mTextView_battery.setText("--%");
                mTextView_battery.setTextColor(ContextCompat.getColor(this, R.color.brand_on_surface));
                mTextView_batteryVoltage.setText("");
            } else {
                int p = Math.max(0, Math.min(100, percent));
                mTextView_battery.setText(format(R.string.battery_text, p));
                mTextView_battery.setTextColor(p <= 20 ? BatteryView.colorFor(this, p)
                        : ContextCompat.getColor(this, R.color.brand_on_surface));
                mTextView_batteryVoltage.setText(voltage > 0
                        ? String.format(Locale.US, getString(R.string.battery_voltage_fmt), voltage) : "");
            }
            mBatteryView.setPercent(percent < 0 ? -1 : Math.min(100, percent));
            updateFlightTime(percent < 0 ? -1 : Math.min(100, percent));
        });
    }

    public void setLinkQualityText(final String quality){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView_linkQuality.setText(format(R.string.linkQuality_text, quality));
            }
        });
    }

    private String format(int identifier, Object o){
        return String.format(getResources().getString(identifier), o);
    }

    public void showToastie(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void setConnectionButtonConnected() {
        setConnectionButtonState(true);
    }

    public void setConnectionButtonConnectedBle() {
        setConnectionButtonState(true);
    }

    public void setConnectionButtonDisconnected() {
        setConnectionButtonState(false);
    }

    private void setConnectionButtonState(final boolean connected) {
        runOnUiThread(() -> {
            updateFlightUi();
            if (mConnecting && !connected) {
                return;
            }
            mToggleConnectButton.setText(connected ? R.string.menu_disconnect : R.string.menu_connect);
            mToggleConnectButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                    connected ? R.color.brand_surface_high : R.color.brand_primary)));
        });
    }

    public void setBuzzerSoundButtonEnablement(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mBuzzerSoundButton.setEnabled(enabled);
            }
        });
    }

    public void setRingEffectButtonEnablement(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRingEffectButton.setEnabled(enabled);
            }
        });
    }

    public void setHeadlightButtonEnablement(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mHeadlightButton.setEnabled(enabled);
            }
        });
    }

    public void toggleHeadlightButtonColor(final boolean toggle) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mHeadlightButton.setColorFilter(toggle ? ContextCompat.getColor(MainActivity.this, R.color.brand_primary) : Color.WHITE);
            }
        });
    }

    public void disableButtonsAndResetBatteryLevel() {
        setRingEffectButtonEnablement(false);
        setHeadlightButtonEnablement(false);
        setBuzzerSoundButtonEnablement(false);
        setBatteryLevel(-1.0f);
    }
}
