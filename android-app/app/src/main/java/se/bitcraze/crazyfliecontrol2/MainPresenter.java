package se.bitcraze.crazyfliecontrol2;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import se.bitcraze.crazyflie.lib.crazyflie.ConnectionAdapter;
import se.bitcraze.crazyflie.lib.crazyflie.Crazyflie;
import se.bitcraze.crazyflie.lib.crazyradio.ConnectionData;
import se.bitcraze.crazyflie.lib.crazyradio.RadioDriver;
import se.bitcraze.crazyflie.lib.crtp.CommanderPacket;
import se.bitcraze.crazyflie.lib.crtp.CrtpDriver;
import se.bitcraze.crazyflie.lib.crtp.CrtpPacket;
import se.bitcraze.crazyflie.lib.crtp.ZDistancePacket;
import se.bitcraze.crazyflie.lib.log.LogAdapter;
import se.bitcraze.crazyflie.lib.log.LogConfig;
import se.bitcraze.crazyflie.lib.log.Logg;
import se.bitcraze.crazyflie.lib.param.Param;
import se.bitcraze.crazyflie.lib.param.ParamListener;
import se.bitcraze.crazyflie.lib.toc.Toc;
import se.bitcraze.crazyflie.lib.toc.VariableType;
import se.bitcraze.crazyfliecontrol.ble.BleLink;
import se.bitcraze.crazyfliecontrol.console.ConsoleListener;
import se.bitcraze.crazyfliecontrol.controller.AbstractController;
import se.bitcraze.crazyfliecontrol.controller.GamepadController;
import se.bitcraze.crazyfliecontrol.controller.IController;

public class MainPresenter {

    private static final String LOG_TAG = "Crazyflie-MainPresenter";

    private MainActivity mainActivity;

    private Crazyflie mCrazyflie;
    private CrtpDriver mDriver;

    private Logg mLogg;
    private LogConfig mDefaultLogConfig = null;

    private Toc mLogToc;
    private Toc mParamToc;

    private boolean mHeadlightToggle = false;
    private boolean mSoundToggle = false;
    private int mRingEffect = 0;
    private int mNoRingEffect = 0;
    private int mCpuFlash = 0;
    private boolean isZrangerAvailable = false;
    private boolean heightHold = false;

    private Thread mSendJoystickDataThread;
    private ConsoleListener mConsoleListener;

    // ---- Flight safety (arm / land / kill) ----
    public static final int STATE_DISARMED = 0;
    public static final int STATE_ARMED = 1;
    public static final int STATE_LANDING = 2;
    public static final int STATE_FAILSAFE = 3;
    public static final int STATE_SERVICE = 4;   // motor test / web tool running, motors not ours
    /** After a local arm/land/disarm, ignore drone reports this long: they may predate it. */
    private static final long LOCAL_STATE_HOLD_MS = 1000;
    private volatile long mLocalStateMs = 0;
    private static final long LAND_DESCENT_MS = 3000;
    private static final long LAND_RAMP_MS = 1500;
    private volatile boolean mFirmwareSafety = false;   // firmware has the "safety" param group
    private volatile int mFlightState = STATE_DISARMED;
    private volatile float mLastSentThrust = 0f;
    private volatile float mLandStartThrust = 0f;
    private volatile long mLandStartMs = 0;

    // ---- Height sensor (VL53L1X on a Qwiic port) ----
    private static final long TAKEOFF_MS = 1500;
    private static final float TAKEOFF_CLIMB = 0.5f;     // stick equivalent, ~0.5 m/s
    private volatile boolean mHeightMode = false;        // firmware flies altitude hold
    private volatile float mHeight = -1f;                // metres, -1 when unknown
    private volatile long mTakeoffUntilMs = 0;

    public MainPresenter(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }

    public void onDestroy() {
        this.mainActivity = null;
    }

    private ConnectionAdapter crazyflieConnectionAdapter = new ConnectionAdapter() {

        @Override
        public void connectionRequested() {
            mainActivity.showToastie("Connecting ...");
        }

        @Override
        public void connected() {
            mainActivity.showToastie("Connected");
            if (mCrazyflie == null) {
                mainActivity.setConnectionButtonConnected();
                return;
            }
            CrtpDriver driver = mCrazyflie.getDriver();
            if (driver instanceof BleLink) {
                // FIXME: Hack to circumvent BLE reconnect problem
                mainActivity.setConnectionButtonConnectedBle();
                mCrazyflie.startConnectionSetup_BLE();
            } else if (driver instanceof EspUdpDriver) {
                mainActivity.setConnectionButtonConnected();
                mCrazyflie.startConnectionSetup_BLE();
            } else {
                mainActivity.setConnectionButtonConnected();
            }
        }

        @Override
        public void setupFinished() {
            Param param = mCrazyflie.getParam();
            if (param != null) {
                final Toc paramToc = param.getToc();
                if (paramToc != null) {
                    mParamToc = paramToc;
                    mainActivity.showToastie("Parameters TOC fetch finished: " + paramToc.getTocSize());
                    checkForBuzzerDeck();
                    checkForNoOfRingEffects();
                    mFirmwareSafety = paramToc.getElementByCompleteName("safety.armed") != null;
                    checkForHeightSensor();
                    Log.d(LOG_TAG, "firmware safety module: " + mFirmwareSafety);
                }
            }
            mLogg = mCrazyflie.getLogg();
            if (mLogg != null) {
                final Toc logToc = mLogg.getToc();
                if (logToc != null) {
                    mLogToc = logToc;
                    mainActivity.showToastie("Log TOC fetch finished: " + logToc.getTocSize());
                    mDefaultLogConfig = createDefaultLogConfig();
                    startLogConfigs(mDefaultLogConfig);
                }
            }
            startSendJoystickDataThread();
            // Arm is enabled only once setup is finished; refresh it now (it was
            // last drawn when the link came up, before setup was done).
            if (mainActivity != null) {
                mainActivity.onFlightStateChanged(mFlightState);
            }
        }

        @Override
        public void connectionLost(final String msg) {
            mainActivity.showToastie(msg);
            mainActivity.setConnectionButtonDisconnected();
            disconnect();
        }

        @Override
        public void connectionFailed(final String msg) {
            mainActivity.showToastie(msg);
            mainActivity.setConnectionButtonDisconnected();
            disconnect();
        }

        @Override
        public void disconnected() {
            mainActivity.showToastie("Disconnected");
            mainActivity.setConnectionButtonDisconnected();
            mainActivity.disableButtonsAndResetBatteryLevel();
            stopLogConfigs(mDefaultLogConfig);
        }

        @Override
        public void linkQualityUpdated(final int quality) {
            mainActivity.setLinkQualityText(quality + "%");
        }
    };

    // TODO: Replace with specific test for buzzer deck
    private void checkForBuzzerDeck() {
        //activate buzzer sound button when a CF2 is recognized (a buzzer can not yet be detected separately)
        mCrazyflie.getParam().addParamListener(new ParamListener("cpu", "flash") {
            @Override
            public void updated(String name, Number value) {
                mCpuFlash = mCrazyflie.getParam().getValue("cpu.flash").intValue();
                //enable buzzer action button when a CF2 is found (cpu.flash == 1024)
                if (mCpuFlash == 1024) {
                    mainActivity.setBuzzerSoundButtonEnablement(true);
                }
                Log.d(LOG_TAG, "CPU flash: " + mCpuFlash);
            }
        });
        mCrazyflie.getParam().requestParamUpdate("cpu.flash");
    }

    private void checkForZRanger() {
        //this should return true when either a zRanger or a flow deck is connected
        mCrazyflie.getParam().addParamListener(new ParamListener("deck", "bcZRanger") {
            @Override
            public void updated(String name, Number value) {
                isZrangerAvailable = mCrazyflie.getParam().getValue("deck.bcZRanger").intValue() == 1;
                // TODO: indicate in the UI that the zRanger sensor is installed
                if (isZrangerAvailable) {
                    mainActivity.showToastie("Found zRanger sensor.");
                }
                Log.d(LOG_TAG, "is zRanger installed: " + isZrangerAvailable);
            }
        });
        mCrazyflie.getParam().requestParamUpdate("deck.bcZRanger");
    }

    private boolean mZRanger2Present = false;   // VL53L1X (deck.bcZRanger2)
    private boolean mZRanger1Present = false;   // VL53L0X (deck.bcZRanger)

    /** Height mode when the drone reports a VL53L1X or a VL53L0X height sensor. */
    private void checkForHeightSensor() {
        mZRanger2Present = false;
        mZRanger1Present = false;
        if (mParamToc == null) {
            return;
        }
        watchHeightSensor("bcZRanger2");
        watchHeightSensor("bcZRanger");
    }

    private void watchHeightSensor(final String param) {
        final String full = "deck." + param;
        if (mParamToc.getElementByCompleteName(full) == null) {
            return;
        }
        mCrazyflie.getParam().addParamListener(new ParamListener("deck", param) {
            @Override
            public void updated(String name, Number value) {
                boolean present = mCrazyflie != null
                        && mCrazyflie.getParam().getValue(full).intValue() == 1;
                if ("bcZRanger2".equals(param)) {
                    mZRanger2Present = present;
                } else {
                    mZRanger1Present = present;
                }
                mHeightMode = (mZRanger2Present || mZRanger1Present) && mFirmwareSafety;
                if (mainActivity != null) {
                    mainActivity.onHeightModeChanged(mHeightMode);
                }
                Log.d(LOG_TAG, full + ": " + present);
            }
        });
        mCrazyflie.getParam().requestParamUpdate(full);
    }

    private void checkForNoOfRingEffects() {
        //set number of LED ring effects
        mCrazyflie.getParam().addParamListener(new ParamListener("ring", "neffect") {
            @Override
            public void updated(String name, Number value) {
                mNoRingEffect = mCrazyflie.getParam().getValue("ring.neffect").intValue();
                //enable LED ring action buttons only when ring.neffect parameter is set correctly (=> hence it's a CF2 with a LED ring)
                if (mNoRingEffect > 0) {
                    mainActivity.setRingEffectButtonEnablement(true);
                    mainActivity.setHeadlightButtonEnablement(true);
                }
                Log.d(LOG_TAG, "No of ring effects: " + mNoRingEffect);
            }
        });
        mCrazyflie.getParam().requestParamUpdate("ring.neffect");
    }

    private void sendPacket(CrtpPacket packet) {
        if (mCrazyflie != null) {
            mCrazyflie.sendPacket(packet);
        }
    }

    /**
     * Start thread to periodically send commands containing the user input
     */
    private void startSendJoystickDataThread() {
        mSendJoystickDataThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (mainActivity != null && mCrazyflie != null) {
                    IController controller = mainActivity.getController();
                    if (controller == null) {
                        Log.d(LOG_TAG, "SendJoystickDataThread: controller is null.");
                        break;
                    }
                    float roll = controller.getRoll();
                    float pitch = controller.getPitch();
                    float yaw = controller.getYaw();
                    float thrustAbsolute = gateThrust(mHeightMode
                            ? heightModeThrust(controller.getThrustAxis())
                            : controller.getThrustAbsolute());
                    if (mFlightState == STATE_LANDING || mFlightState == STATE_FAILSAFE) {
                        roll = 0;
                        pitch = 0;
                        yaw = 0;
                    }
                    boolean xmode = mainActivity.getControls().isXmode();
                    if (heightHold) {
                        float targetHeight = controller.getTargetHeight();
                        sendPacket(new ZDistancePacket(roll, pitch, yaw, targetHeight));
                    } else {
                        sendPacket(new CommanderPacket(roll, pitch, yaw, (char) thrustAbsolute, xmode));
                    }
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Log.d(LOG_TAG, "SendJoystickDataThread was interrupted.");
                        break;
                    }
                }
            }
        });
        mSendJoystickDataThread.start();
    }

    public void connectCrazyradio(int radioChannel, int radioDatarate, File mCacheDir) {
        Log.d(LOG_TAG, "connectCrazyradio()");
        // ensure previous link is disconnected
        disconnect();
        mDriver = null;
        try {
            mDriver = new RadioDriver(new UsbLinkAndroid(mainActivity));
        } catch (IllegalArgumentException e) {
            Log.d(LOG_TAG, e.getMessage());
            mainActivity.showToastie(e.getMessage());
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage());
            mainActivity.showToastie(e.getMessage());
        }
        connect(mCacheDir, new ConnectionData(radioChannel, radioDatarate));
    }

    public void connectUDP(File cacheDir) {
        Log.d(LOG_TAG, "connectUDP()");
        disconnect();
        mDriver = null;
        mDriver = new EspUdpDriver(mainActivity);
        connect(cacheDir, null);
    }

    public void connectBle(boolean writeWithResponse, File mCacheDir) {
        Log.d(LOG_TAG, "connectBle()");
        // ensure previous link is disconnected
        disconnect();
        mDriver = null;
        mDriver = new BleLink(mainActivity, writeWithResponse);
        connect(mCacheDir, null);
    }

    private void connect(File mCacheDir, ConnectionData connectionData) {
        if (mDriver != null) {
            // add listener for connection status
            mDriver.addConnectionListener(crazyflieConnectionAdapter);

            mCrazyflie = new Crazyflie(mDriver, mCacheDir);
            if (mDriver instanceof RadioDriver) {
                mCrazyflie.setConnectionData(connectionData);
            }
            // connect
            mCrazyflie.connect();

            // add console listener
            if (mCrazyflie != null) {
                mConsoleListener = new ConsoleListener();
                mConsoleListener.setMainActivity(mainActivity);
                mCrazyflie.addDataListener(mConsoleListener);
            }
        } else {
            mainActivity.showToastie("Cannot connect: Crazyradio not attached and Bluetooth LE not available");
        }
    }

    public void disconnect() {
        Log.d(LOG_TAG, "disconnect()");
        // kill sendJoystickDataThread first to avoid NPE
        if (mSendJoystickDataThread != null) {
            mSendJoystickDataThread.interrupt();
            mSendJoystickDataThread = null;
        }

        if (mCrazyflie != null) {
            mCrazyflie.removeDataListener(mConsoleListener);
            mCrazyflie.disconnect();
            mCrazyflie = null;
        }

        if (mDriver != null) {
            mDriver.removeConnectionListener(crazyflieConnectionAdapter);
        }

        setFlightState(STATE_DISARMED);
        mHeightMode = false;
        mHeight = -1f;
        if (mainActivity != null) {
            mainActivity.onHeightModeChanged(false);
        }
        // link quality is not available when there is no active connection
        if (mainActivity != null) {
            mainActivity.setLinkQualityText("N/A");
        }
    }

    public void enableAltHoldMode(boolean hover) {
        // For safety reasons, altHold mode is only supported when the Crazyradio and a game pad are used
        if (mCrazyflie != null && mCrazyflie.getDriver() instanceof RadioDriver && mainActivity.getController() instanceof GamepadController) {
            if (isZrangerAvailable) {
                heightHold = hover;
                // reset target height, when hover is deactivated
                if (!hover) {
                    ((GamepadController) mainActivity.getController()).setTargetHeight(AbstractController.INITIAL_TARGET_HEIGHT);
                }
            } else {
//                Log.i(LOG_TAG, "flightmode.althold: getThrust(): " + mController.getThrustAbsolute());
                mCrazyflie.setParamValue("flightmode.althold", hover ? 1 : 0);
            }
        }
    }

    //TODO: make runAltAction more universal
    public void runAltAction(String action) {
        Log.i(LOG_TAG, "runAltAction: " + action);
        if (mCrazyflie != null) {
            if ("ring.headlightEnable".equalsIgnoreCase(action)) {
                // Toggle LED ring headlight
                mHeadlightToggle = !mHeadlightToggle;
                mCrazyflie.setParamValue(action, mHeadlightToggle ? 1 : 0);
                mainActivity.toggleHeadlightButtonColor(mHeadlightToggle);
            } else if ("ring.effect".equalsIgnoreCase(action)) {
                // Cycle through LED ring effects
                Log.i(LOG_TAG, "Ring effect: " + mRingEffect);
                mCrazyflie.setParamValue(action, mRingEffect);
                mRingEffect++;
                mRingEffect = (mRingEffect > mNoRingEffect) ? 0 : mRingEffect;
            } else if (action.startsWith("sound.effect")) {
                // Toggle buzzer deck sound effect
                String[] split = action.split(":");
                Log.i(LOG_TAG, "Sound effect: " + split[1]);
                mCrazyflie.setParamValue(split[0], mSoundToggle ? Integer.parseInt(split[1]) : 0);
                mSoundToggle = !mSoundToggle;
            }
        } else {
            Log.d(LOG_TAG, "runAltAction - crazyflie is null");
        }
    }

    public Crazyflie getCrazyflie() {
        return mCrazyflie;
    }

    // =====================================================================
    //  Arm / disarm, auto-land, kill
    // =====================================================================

    public boolean isConnected() {
        return mCrazyflie != null && mCrazyflie.isConnected();
    }

    public int getFlightState() {
        return mFlightState;
    }

    /** Thrust actually sent last (0 when disarmed). */
    public float getLastSentThrust() {
        return mLastSentThrust;
    }

    public boolean isHeightMode() {
        return mHeightMode;
    }

    public float getHeight() {
        return mHeight;
    }

    /** Airborne: measured height when there is a sensor, otherwise thrust above zero. */
    public boolean isFlying() {
        if (mFlightState == STATE_DISARMED) {
            return false;
        }
        if (mHeightMode && mHeight >= 0) {
            return mHeight > 0.10f;
        }
        return mLastSentThrust > 0;
    }

    /** Height sensor only: climb to about half a metre and hold. */
    public void takeOff() {
        if (mHeightMode && mFlightState == STATE_ARMED && !isFlying()) {
            mTakeoffUntilMs = System.currentTimeMillis() + TAKEOFF_MS;
        }
    }

    /**
     * Altitude hold (firmware crtp_commander_rpyt): 32767 = hold height,
     * above = climb, below = descend, up to 1 m/s at full stick.
     */
    private float heightModeThrust(float axis) {
        if (System.currentTimeMillis() < mTakeoffUntilMs) {
            axis = Math.max(axis, TAKEOFF_CLIMB);
        }
        axis = Math.max(-1f, Math.min(1f, axis));
        return 32767f + axis * 32767f;
    }

    private void setLocalFlightState(int state) {
        mLocalStateMs = System.currentTimeMillis();
        setFlightState(state);
    }

    private void setFlightState(int state) {
        if (mFlightState != state) {
            mFlightState = state;
            if (mainActivity != null) {
                mainActivity.onFlightStateChanged(state);
            }
        }
    }

    public void arm() {
        if (!isConnected()) {
            return;
        }
        if (mFirmwareSafety) {
            mCrazyflie.setParamValue("safety.kill", 0);
            mCrazyflie.setParamValue("safety.land", 0);
            mCrazyflie.setParamValue("safety.armed", 1);
        }
        setLocalFlightState(STATE_ARMED);
    }

    public void disarm() {
        if (mFirmwareSafety && isConnected()) {
            mCrazyflie.setParamValue("safety.armed", 0);
        }
        setLocalFlightState(STATE_DISARMED);
    }

    public void autoLand() {
        if (mFlightState != STATE_ARMED) {
            return;
        }
        if (mFirmwareSafety && isConnected()) {
            mCrazyflie.setParamValue("safety.land", 1);
        }
        mLandStartThrust = mLastSentThrust;
        mLandStartMs = System.currentTimeMillis();
        setLocalFlightState(STATE_LANDING);
    }

    public void cancelLanding() {
        if (mFlightState != STATE_LANDING) {
            return;
        }
        if (mFirmwareSafety && isConnected()) {
            mCrazyflie.setParamValue("safety.land", 0);
        }
        setLocalFlightState(STATE_ARMED);
    }

    /** Emergency: cut the motors now. */
    public void kill() {
        if (mFirmwareSafety && isConnected()) {
            mCrazyflie.setParamValue("safety.kill", 1);
        }
        setLocalFlightState(STATE_DISARMED);
    }

    /** Applies the arm state to the pilot's thrust before it is sent. */
    private float gateThrust(float pilotThrust) {
        float out;
        switch (mFlightState) {
            case STATE_ARMED:
                out = pilotThrust;
                break;
            case STATE_LANDING:
            case STATE_FAILSAFE:
                if (mFirmwareSafety) {
                    // The drone flies the landing itself and ignores this, but it hands
                    // control back the moment the link recovers. Sending 0 here would
                    // stop the motors in the air until the next state report.
                    out = pilotThrust;
                } else {
                    // Old firmware: run the same timed descent from the app
                    long t = System.currentTimeMillis() - mLandStartMs;
                    if (t < LAND_DESCENT_MS) {
                        out = mLandStartThrust * 0.85f;
                    } else if (t < LAND_DESCENT_MS + LAND_RAMP_MS) {
                        out = mLandStartThrust * 0.85f * (1f - (t - LAND_DESCENT_MS) / (float) LAND_RAMP_MS);
                    } else {
                        out = 0;
                        setFlightState(STATE_DISARMED);
                    }
                }
                break;
            default:
                out = 0;
        }
        mLastSentThrust = out;
        return out;
    }

    private LogAdapter standardLogAdapter = new LogAdapter() {

        public void logDataReceived(LogConfig logConfig, Map<String, Number> data, int timestamp) {
            super.logDataReceived(logConfig, data, timestamp);

            if ("Standard".equals(logConfig.getName()) && mainActivity != null) {
                Number vbat = data.get("pm.vbat");
                Number level = data.get("pm.batteryLevel");
                float voltage = vbat != null ? vbat.floatValue() : -1f;
                // Firmware >= xiao port reports a real 0..100 % in pm.batteryLevel
                int percent = level != null ? level.intValue() : MainActivity.percentFromVoltage(voltage);
                mainActivity.setBattery(percent, voltage);
                Number height = data.get("safety.height");
                if (height != null) {
                    mHeight = height.floatValue();
                    mainActivity.setHeight(mHeight);
                }
                Number safety = data.get("safety.state");
                if (safety != null && mFirmwareSafety
                        && System.currentTimeMillis() - mLocalStateMs > LOCAL_STATE_HOLD_MS) {
                    setFlightState(safety.intValue());
                }
            }
            for (Map.Entry<String, Number> entry : data.entrySet()) {
                Log.d(LOG_TAG, "\t Name: " + entry.getKey() + ", data: " + entry.getValue());
            }
        }

    };

    private LogConfig createDefaultLogConfig() {
        LogConfig logConfigStandard = new LogConfig("Standard", 250);   // safety.state must follow the drone quickly
        logConfigStandard.addVariable("pm.vbat", VariableType.FLOAT);
        if (mLogToc != null && mLogToc.getElementByCompleteName("pm.batteryLevel") != null) {
            logConfigStandard.addVariable("pm.batteryLevel", VariableType.UINT8_T);
        }
        if (mLogToc != null && mLogToc.getElementByCompleteName("safety.state") != null) {
            logConfigStandard.addVariable("safety.state", VariableType.UINT8_T);
        }
        if (mLogToc != null && mLogToc.getElementByCompleteName("safety.height") != null) {
            logConfigStandard.addVariable("safety.height", VariableType.FLOAT);
        }
        return logConfigStandard;
    }

    /**
     * Start logging config
     */
    private void startLogConfigs(LogConfig logConfig) {
        if (mLogg == null) {
            Log.e(LOG_TAG, "startLogConfigs: mLogg was null!!");
            return;
        }
        if (logConfig == null) {
            Log.e(LOG_TAG, "startLogConfigs: Logg was null!!");
            return;
        }
        mLogg.addLogListener(standardLogAdapter);
        mLogg.addConfig(logConfig);
        mLogg.start(logConfig);
    }

    /**
     * Stop logging config
     */
    private void stopLogConfigs(LogConfig logConfig) {
        if (mLogg == null) {
            Log.e(LOG_TAG, "stopLogConfigs: mLogg was null!!");
            return;
        }
        if (logConfig == null) {
            Log.e(LOG_TAG, "stopLogConfigs: Logg was null!!");
            return;
        }
        mLogg.stop(logConfig);
        mLogg.delete(logConfig);
        mLogg.removeLogListener(standardLogAdapter);
    }
}
