package fluddokt.opsu.android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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
	/**
	 * Request code for the one-time legacy storage READ permission.
	 *
	 * <p>The game itself needs no storage permission at all now that everything
	 * lives in internal storage. This is requested only to migrate a pre-1.1
	 * external tree, and only when such a tree may still exist.
	 */
	private static final int MIGRATION_PERMISSION_REQUEST = 1001;

	private boolean gameInitialized;

	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Bind internal storage before anything can touch the filesystem.
		InternalStorage.init(this);

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
				// Downloads land in the private Import folder. The public
				// Downloads directory lives on external storage and is
				// off-limits under the internal-storage-only policy.
				return new File(new FileHandle(InternalStorage.getImportDir()));
			}
		};

		// Migration is the only reason this app would ever read external
		// storage. Users on a fresh install are never