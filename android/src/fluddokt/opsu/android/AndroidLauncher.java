package fluddokt.opsu.android;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Method;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;

import fluddokt.ex.DeviceInfo;
import fluddokt.ex.DevicePerformance;
import fluddokt.ex.MenuThemePicker;
import fluddokt.ex.ProfilePicturePicker;
import fluddokt.opsu.fake.File;
import fluddokt.opsu.fake.GameOpsu;

public class AndroidLauncher extends AndroidApplication {
	private static final int STORAGE_PERMISSION_REQUEST = 1001;
	private static final float BATTERY_SAVER_REFRESH_RATE = 60f;

	private boolean gameInitialized;
	private AndroidProfilePicturePicker profilePicturePicker;
	private AndroidMenuThemePicker menuThemePicker;
	private volatile boolean batterySaverEnabled = true;
	private volatile boolean gameplayActive;
	private volatile boolean windowHasFocus = true;
	private int appliedDisplayModeId = -1;
	private float appliedRefreshRate = -1f;

	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// libGDX maps "external" files to Android/data/<package>/files. Tell
		// the shared core where Android's user-visible storage root lives.
		System.setProperty(
				"opsu.sharedStorageRoot",
				Environment.getExternalStorageDirectory().getAbsolutePath());

		DeviceInfo.info = new DeviceInfo() {
			@Override
			public String getInfo() {
				return 
						"BOARD: "+Build.BOARD
						+"\nFINGERPRINT: "+Build.FINGERPRINT
						+"\nHOST: "+Build.HOST
						+"\nMODEL: "+Build.MODEL
						+"\nINCREMENTAL: "+Build.VERSION.INCREMENTAL
						+"\nRELEASE: "+Build.VERSION.RELEASE
						+"\n"
						;
			}

			@Override
			public File getDownloadDir() {
				if (!hasLegacyStoragePermission())
					return null;
				return new File(new FileHandle(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)));
			}

		};
		DevicePerformance.info = new DevicePerformance() {
			@Override
			public void setBatterySaverEnabled(boolean enabled) {
				batterySaverEnabled = enabled;
				applyPerformanceProfile();
			}

			@Override
			public void setGameplayActive(boolean active) {
				gameplayActive = active;
				setScreenAwake(active);
				applyPerformanceProfile();
			}
		};
		profilePicturePicker = new AndroidProfilePicturePicker(this);
		ProfilePicturePicker.info = profilePicturePicker;
		menuThemePicker = new AndroidMenuThemePicker(this);
		MenuThemePicker.info = menuThemePicker;

		if (hasLegacyStoragePermission()) {
			initializeGame();
		} else {
			requestPermissions(
				new String[] {
					Manifest.permission.READ_EXTERNAL_STORAGE,
					Manifest.permission.WRITE_EXTERNAL_STORAGE
				},
				STORAGE_PERMISSION_REQUEST
			);
		}
	}

	@Override
	public void onRequestPermissionsResult(
		int requestCode,
		String[] permissions,
		int[] grantResults
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == STORAGE_PERMISSION_REQUEST)
			initializeGame();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (profilePicturePicker != null &&
			profilePicturePicker.handleActivityResult(requestCode, resultCode, data))
			return;
		if (menuThemePicker != null &&
			menuThemePicker.handleActivityResult(requestCode, resultCode, data))
			return;
		super.onActivityResult(requestCode, resultCode, data);
	}

	private boolean hasLegacyStoragePermission() {
		return Build.VERSION.SDK_INT < 23 ||
			checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
				PackageManager.PERMISSION_GRANTED;
	}

	private void initializeGame() {
		if (gameInitialized)
			return;
		gameInitialized = true;

		AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
		config.useImmersiveMode = true;
		config.useWakelock = false;
		config.useAccelerometer = false;
		config.useCompass = false;
		config.useGyroscope = false;
		config.useRotationVectorSensor = false;
		config.renderUnderCutout = true;

		initialize(new GameOpsu(), config);
		applyPerformanceProfile();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		windowHasFocus = hasFocus;
		if (hasFocus) {
			applyPerformanceProfile();
		} else {
			appliedDisplayModeId = -1;
			appliedRefreshRate = -1f;
		}
	}

	@Override
	protected void onPause() {
		appliedDisplayModeId = -1;
		appliedRefreshRate = -1f;
		setScreenAwake(false);
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (gameInitialized) {
			setScreenAwake(gameplayActive);
			applyPerformanceProfile();
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
			requestUnbufferedTouchEvents(event);
		return super.dispatchTouchEvent(event);
	}

	/**
	 * Use a balanced refresh rate outside gameplay when battery saver is enabled,
	 * and reserve the display's fastest compatible mode for active gameplay.
	 */
	private void applyPerformanceProfile() {
		if (!gameInitialized || !windowHasFocus)
			return;

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					applyDisplayRefreshRate();
				} catch (Throwable ignored) {
					// Display-mode selection is an optional optimization.
				}
			}
		});
	}

	private void applyDisplayRefreshRate() {
		Display display = getWindowManager().getDefaultDisplay();
		boolean preferHighest = gameplayActive || !batterySaverEnabled;
		WindowManager.LayoutParams attributes = getWindow().getAttributes();

		if (Build.VERSION.SDK_INT >= 23) {
			Display.Mode currentMode = display.getMode();
			Display.Mode bestMode = currentMode;
			for (Display.Mode mode : display.getSupportedModes()) {
				if (mode.getPhysicalWidth() != currentMode.getPhysicalWidth() ||
					mode.getPhysicalHeight() != currentMode.getPhysicalHeight())
					continue;

				if (isBetterRefreshRate(
						mode.getRefreshRate(),
						bestMode.getRefreshRate(),
						preferHighest))
					bestMode = mode;
			}

			int modeId = bestMode.getModeId();
			if (modeId != appliedDisplayModeId) {
				attributes.preferredDisplayModeId = modeId;
				attributes.preferredRefreshRate = bestMode.getRefreshRate();
				getWindow().setAttributes(attributes);
				appliedDisplayModeId = modeId;
				appliedRefreshRate = bestMode.getRefreshRate();
			}
			return;
		}

		float bestRate = display.getRefreshRate();
		float[] supportedRates = display.getSupportedRefreshRates();
		if (supportedRates != null) {
			for (float rate : supportedRates) {
				if (isBetterRefreshRate(rate, bestRate, preferHighest))
					bestRate = rate;
			}
		}

		if (Math.abs(bestRate - appliedRefreshRate) > 0.1f) {
			attributes.preferredRefreshRate = bestRate;
			getWindow().setAttributes(attributes);
			appliedRefreshRate = bestRate;
			appliedDisplayModeId = -1;
		}
	}

	private boolean isBetterRefreshRate(
		float candidate,
		float currentBest,
		boolean preferHighest
	) {
		if (preferHighest)
			return candidate > currentBest;

		float candidateDistance = Math.abs(candidate - BATTERY_SAVER_REFRESH_RATE);
		float currentDistance = Math.abs(currentBest - BATTERY_SAVER_REFRESH_RATE);
		if (Math.abs(candidateDistance - currentDistance) < 0.1f)
			return candidate < currentBest;
		return candidateDistance < currentDistance;
	}

	/**
	 * Keep the display awake only while a beatmap is actively being played.
	 */
	private void setScreenAwake(final boolean awake) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (awake) {
					getWindow().addFlags(
						WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				} else {
					getWindow().clearFlags(
						WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				}
			}
		});
	}

	@Override
	protected void onDestroy() {
		if (gameInitialized) {
			setScreenAwake(false);
		}
		super.onDestroy();
	}

	/**
	 * Avoid Android batching gameplay touches when the platform supports
	 * unbuffered input dispatch.
	 */
	private void requestUnbufferedTouchEvents(MotionEvent event) {
		if (Build.VERSION.SDK_INT < 21)
			return;

		try {
			View decorView = getWindow().getDecorView();
			Method requestUnbufferedDispatch =
				decorView.getClass().getMethod(
					"requestUnbufferedDispatch",
					MotionEvent.class
				);
			requestUnbufferedDispatch.invoke(decorView, event);
		} catch (Throwable ignored) {
			// Older vendors may omit the API despite the reported SDK level.
		}
	}
}
