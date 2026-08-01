package fluddokt.opsu.android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import fluddokt.ex.ProfilePicturePicker;

/** Android image picker and local profile-picture processor. */
final class AndroidProfilePicturePicker extends ProfilePicturePicker {
	static final int REQUEST_CODE = 2003;

	private static final int OUTPUT_SIZE = 512;
	private static final int MAX_DECODE_EDGE = 2048;

	private final AndroidLauncher activity;
	private String pendingUser;
	private String pendingDestination;

	AndroidProfilePicturePicker(AndroidLauncher activity) {
		this.activity = activity;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public synchronized void chooseProfilePicture(
		final String userName,
		final String destinationPath
	) {
		pendingUser = userName;
		pendingDestination = destinationPath;
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					intent.setType("image/*");
					activity.startActivityForResult(
						Intent.createChooser(intent, "Choose profile picture"),
						REQUEST_CODE
					);
				} catch (Throwable error) {
					notifyProfilePictureError("The image picker could not be opened.");
				}
			}
		});
	}

	/** Returns true when the activity result belonged to this picker. */
	boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode != REQUEST_CODE)
			return false;

		final String userName;
		final String destinationPath;
		synchronized (this) {
			userName = pendingUser;
			destinationPath = pendingDestination;
			pendingUser = null;
			pendingDestination = null;
		}

		if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null)
			return true;

		final Uri source = data.getData();
		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					saveSquareProfilePicture(source, destinationPath);
					notifyProfilePictureSaved(userName);
				} catch (Throwable error) {
					notifyProfilePictureError(
						"That image could not be saved as a profile picture.");
				}
			}
		}, "opsu-profile-picture");
		worker.setDaemon(true);
		worker.start();
		return true;
	}

	private void saveSquareProfilePicture(Uri source, String destinationPath)
		throws Exception {
		if (source == null || destinationPath == null)
			throw new IllegalArgumentException("Missing profile picture source or destination.");

		File profileRoot = new File(
			Environment.getExternalStorageDirectory(),
			"opsu/profilepictures"
		).getCanonicalFile();
		File destination = new File(destinationPath).getCanonicalFile();
		if (!profileRoot.equals(destination.getParentFile()))
			throw new SecurityException("Invalid profile picture destination.");
		if (!profileRoot.isDirectory() && !profileRoot.mkdirs())
			throw new IllegalStateException("Could not create the profile picture directory.");

		BitmapFactory.Options bounds = new BitmapFactory.Options();
		bounds.inJustDecodeBounds = true;
		try (InputStream stream = activity.getContentResolver().openInputStream(source)) {
			BitmapFactory.decodeStream(stream, null, bounds);
		}
		if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
			throw new IllegalArgumentException("Unsupported image.");

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Bitmap.Config.ARGB_8888;
		options.inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight);
		Bitmap decoded;
		try (InputStream stream = activity.getContentResolver().openInputStream(source)) {
			decoded = BitmapFactory.decodeStream(stream, null, options);
		}
		if (decoded == null)
			throw new IllegalArgumentException("Unsupported image.");

		Bitmap square = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888);
		try {
			int side = Math.min(decoded.getWidth(), decoded.getHeight());
			int left = (decoded.getWidth() - side) / 2;
			int top = (decoded.getHeight() - side) / 2;
			Canvas canvas = new Canvas(square);
			Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
			canvas.drawBitmap(
				decoded,
				new Rect(left, top, left + side, top + side),
				new Rect(0, 0, OUTPUT_SIZE, OUTPUT_SIZE),
				paint
			);

			File temporary = new File(destination.getAbsolutePath() + ".tmp");
			try (FileOutputStream output = new FileOutputStream(temporary)) {
				if (!square.compress(Bitmap.CompressFormat.PNG, 100, output))
					throw new IllegalStateException("PNG encoding failed.");
				output.flush();
			}
			if (destination.isFile() && !destination.delete())
				throw new IllegalStateException("Could not replace the old profile picture.");
			if (!temporary.renameTo(destination)) {
				temporary.delete();
				throw new IllegalStateException("Could not finish saving the profile picture.");
			}
		} finally {
			decoded.recycle();
			square.recycle();
		}
	}

	private int calculateSampleSize(int width, int height) {
		int sampleSize = 1;
		while (width / sampleSize > MAX_DECODE_EDGE ||
				height / sampleSize > MAX_DECODE_EDGE)
			sampleSize *= 2;
		return sampleSize;
	}
}
