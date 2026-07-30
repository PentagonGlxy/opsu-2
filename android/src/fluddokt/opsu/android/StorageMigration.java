package fluddokt.opsu.android;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * One-time migration of the legacy external-storage tree into internal storage.
 *
 * <p>Builds before 1.1 stored everything in {@code <external>/opsu} because
 * {@code fluddokt.opsu.fake.File} preferred {@code Gdx.files.external}. This
 * copies that tree into {@code getFilesDir()/opsu} exactly once, tracked by a
 * marker file.
 *
 * <p>Design choices worth keeping:
 * <ul>
 *   <li><b>Copy, never move.</b> A failure part-way through must not destroy the
 *       user's only copy of a large beatmap library.</li>
 *   <li><b>Per-file tolerance.</b> One unreadable file cannot abort the run.</li>
 *   <li><b>Never fatal.</b> Every entry point swallows {@link Throwable}: a
 *       broken migration must not stop the game from starting.</li>
 * </ul>
 */
public final class StorageMigration {

	private static final String TAG = "opsu.migration";
	private static final String MARKER_NAME = ".internal-migration-complete";

	private StorageMigration() {}

	/**
	 * Returns whether a migration attempt is still outstanding. Used to decide
	 * whether requesting the legacy read permission is worth doing at all, so
	 * users on fresh installs are never prompted.
	 */
	public static boolean isNeeded() {
		try {
			return !new File(InternalStorage.getGameRoot(), MARKER_NAME).exists();
		} catch (Throwable t) {
			Log.e(TAG, "Could not determine migration state", t);
			return false;
		}
	}

	/**
	 * Runs the migration if it has not run before. Safe to call on every launch.
	 *
	 * @return true if at least one file was copied during this call
	 */
	public static boolean migrateIfNeeded() {
		try {
			File target = InternalStorage.getGameRoot();
			File marker = new File(target, MARKER_NAME);
			if (marker.exists())
				return false;

			File legacy = legacyRoot();
			if (legacy == null || !legacy.isDirectory()) {
				// Nothing to migrate. Write the marker anyway so we do not
				// re-check (and possibly re-prompt) on every future launch.
				touch(marker);
				return false;
			}

			Log.i(TAG, "Migrating " + legacy + " -> " + target);
			int copied = copyTree(legacy, target);
			Log.i(TAG, "Migration copied " + copied + " file(s)");

			if (copied > 0)
				resetStalePaths(target);

			touch(marker);
			return copied > 0;
		} catch (Throwable t) {
			// Deliberately broad: a broken migration is never fatal.
			Log.e(TAG, "Migration failed", t);
			return false;
		}
	}

	/**
	 * The legacy external root, or null when external storage is unreadable.
	 * Only ever read from; nothing is written or deleted there.
	 */
	private static File legacyRoot() {
		String state = Environment.getExternalStorageState();
		if (!Environment.MEDIA_MOUNTED.equals(state) &&
		    !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
			return null;
		return new File(Environment.getExternalStorageDirectory(),
		                InternalStorage.ROOT_NAME);
	}

	/**
	 * Options persists ABSOLUTE paths for BeatmapDirectory, SkinDirectory and
	 * friends. After migrating, those still point at external storage, so the
	 * config is removed and Options falls back to its internal defaults.
	 *
	 * <p>This resets user preferences, which is a real cost, but leaving stale
	 * external paths in place would silently defeat the whole migration.
	 */
	private static void resetStalePaths(File gameRoot) {
		File cfg = new File(gameRoot, "opsu.cfg");
		if (cfg.isFile() && !cfg.delete())
			Log.w(TAG, "Could not reset opsu.cfg; directory options may be stale");
	}

	/**
	 * Recursively copies {@code src} into {@code dst}, skipping files that
	 * already exist at the destination. Returns the number of files copied.
	 */
	private static int copyTree(File src, File dst) {
		int count = 0;
		File[] children = src.listFiles();
		if (children == null)
			return 0;

		for (int i = 0; i < children.length; i++) {
			File child = children[i];
			File out = new File(dst, child.getName());
			if (child.isDirectory()) {
				if (!out.isDirectory() && !out.mkdirs()) {
					Log.w(TAG, "Could not create " + out);
					continue;
				}
				count += copyTree(child, out);
			} else if (!out.exists()) {
				try {
					copyFile(child, out);
					count++;
				} catch (IOException e) {
					Log.w(TAG, "Skipped " + child + ": " + e.getMessage());
				}
			}
		}
		return count;
	}

	/**
	 * Streams one file using NIO channels, avoiding a large heap buffer.
	 * transferTo() can short-write, so the copy loops until it is complete.
	 */
	private static void copyFile(File from, File to) throws IOException {
		FileInputStream in = new FileInputStream(from);
		try {
			FileOutputStream out = new FileOutputStream(to);
			try {
				FileChannel inCh = in.getChannel();
				FileChannel outCh = out.getChannel();
				long size = inCh.size();
				long pos = 0;
				while (pos < size)
					pos += inCh.transferTo(pos, size - pos, outCh);
			} finally {
				out.close();
			}
		} finally {
			in.close();
		}
	}

	private static void touch(File marker) {
		try {
			new FileOutputStream(marker).close();
		} catch (IOException e) {
			Log.w(TAG, "Could not write migration marker", e);
		}
	}
}
