package fluddokt.opsu.android;

import android.content.Context;
import android.util.Log;

import java.io.File;

/**
 * Central authority for internal-storage locations.
 *
 * <p>Every user-writable path in the app resolves underneath
 * {@link Context#getFilesDir()}. Nothing is ever written to external storage,
 * the SD card, or Android/data. This class exists so that policy is stated in
 * exactly one place and can be asserted in logs and tests.
 *
 * <p>Core code does not call this class directly: it goes through
 * {@code fluddokt.opsu.fake.File}, which resolves to {@code Gdx.files.local},
 * which libGDX maps to {@code getFilesDir()} on Android. This helper covers the
 * Android-only callers (migration, the download directory) and documents the
 * layout those two agree on.
 */
public final class InternalStorage {

	private static final String TAG = "opsu.storage";

	/** Game root folder name inside getFilesDir(). Matches Options.DATA_DIR ("./opsu"). */
	public static final String ROOT_NAME = "opsu";

	/** Folder holding user-supplied seasonal background images. */
	public static final String SEASONAL_DIR_NAME = "seasonalbackground";

	/** Folder that beatmap/skin/replay imports are dropped into. */
	public static final String IMPORT_DIR_NAME = "Import";

	/**
	 * Application context. Deliberately the application context and never an
	 * Activity, so this static field cannot leak a destroyed window across a
	 * configuration change.
	 */
	private static Context appContext;

	private InternalStorage() {}

	/**
	 * Binds the helper to the application context. Must be called before any
	 * other method, from {@code Activity.onCreate()}.
	 *
	 * @param context any context; the application context is extracted from it
	 */
	public static void init(Context context) {
		appContext = context.getApplicationContext();
	}

	private static Context requireContext() {
		if (appContext == null)
			throw new IllegalStateException("InternalStorage.init() was not called");
		return appContext;
	}

	/**
	 * Returns the private internal files directory, for example
	 * {@code /data/user/0/fluddokt.opsu.android/files}.
	 */
	public static File getFilesDir() {
		return requireContext().getFilesDir();
	}

	/** Returns the game root ({@code files/opsu}), creating it if absent. */
	public static File getGameRoot() {
		return ensure(new File(getFilesDir(), ROOT_NAME));
	}

	/**
	 * Returns the import folder, creating it if absent. This replaces the public
	 * Downloads directory, which lives on external storage.
	 */
	public static File getImportDir() {
		return ensure(new File(getGameRoot(), IMPORT_DIR_NAME));
	}

	/**
	 * Returns the seasonal background folder, creating it if absent, so the
	 * folder is guaranteed to exist the first time the option is switched on,
	 * even on a completely fresh install.
	 *
	 * <p>Core reaches the same folder via
	 * {@code Options.getSeasonalBackgroundDir()}; both resolve to
	 * {@code files/opsu/seasonalbackground}.
	 */
	public static File getSeasonalBackgroundDir() {
		return ensure(new File(getGameRoot(), SEASONAL_DIR_NAME));
	}

	/**
	 * Creates a private subdirectory using Android's own helper, for callers
	 * that want {@link Context#MODE_PRIVATE} directory semantics.
	 */
	public static File getPrivateDir(String name) {
		return requireContext().getDir(name, Context.MODE_PRIVATE);
	}

	/** Free bytes on the internal volume, for pre-import capacity checks. */
	public static long getFreeSpaceBytes() {
		return getFilesDir().getUsableSpace();
	}

	/** Creates {@code dir} (and parents) when needed; returns it either way. */
	private static File ensure(File dir) {
		if (!dir.isDirectory() && !dir.mkdirs())
			Log.w(TAG, "Could not create directory: " + dir);
		return dir;
	}
}
