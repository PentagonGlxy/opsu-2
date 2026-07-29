package fluddokt.opsu.android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;

import fluddokt.ex.DeviceInfo;
import fluddokt.opsu.fake.File;
import fluddokt.opsu.fake.GameOpsu;

public class AndroidLauncher extends AndroidApplication {
	private static final int STORAGE_PERMISSION_REQUEST = 1001;

	private boolean gameInitialized;

	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

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
		config.useWakelock = true;
		config.useAccelerometer = false;
		config.useCompass = false;
		config.useGyroscope = false;
		config.useRotationVectorSensor = false;
		config.renderUnderCutout = true;

		initialize(new GameOpsu(), config);
		requestHighestRefreshRate();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus)
			requestHighestRefreshRate();
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
			requestUnbufferedTouchEvents(event);
		return super.dispatchTouchEvent(event);
	}

	/**
	 * Ask Android for the fastest refresh rate compatible with the current
	 * display mode. Reflection keeps this compatible with older Android devices.
	 */
	private void requestHighestRefreshRate() {
		if (Build.VERSION.SDK_INT < 21)
			return;

		try {
			Object display = getWindowManager().getDefaultDisplay();
			Method getRefreshRate = display.getClass().getMethod("getRefreshRate");
			float currentRate = ((Number) getRefreshRate.invoke(display)).floatValue();
			float highestRate = currentRate;

			Method getSupportedRefreshRates =
				display.getClass().getMethod("getSupportedRefreshRates");
			float[] supportedRates =
				(float[]) getSupportedRefreshRates.invoke(display);
			if (supportedRates != null) {
				for (float rate : supportedRates)
					highestRate = Math.max(highestRate, rate);
			}

			if (highestRate > currentRate) {
				WindowManager.LayoutParams attributes = getWindow().getAttributes();
				Field preferredRefreshRate =
					attributes.getClass().getField("preferredRefreshRate");
				preferredRefreshRate.setFloat(attributes, highestRate);
				getWindow().setAttributes(attributes);
			}
		} catch (Throwable ignored) {
			// Refresh-rate selection is an optional optimization.
		}
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
