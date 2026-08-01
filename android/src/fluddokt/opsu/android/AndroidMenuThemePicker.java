package fluddokt.opsu.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import fluddokt.ex.MenuThemePicker;

/** Android document picker for direct audio and osu! beatmap archives. */
final class AndroidMenuThemePicker extends MenuThemePicker {
	static final int REQUEST_CODE = 2004;

	private static final long MAX_AUDIO_BYTES = 128L * 1024L * 1024L;
	private static final long MAX_ARCHIVE_BYTES = 256L * 1024L * 1024L;
	private static final long MAX_ARCHIVE_CONTENT_BYTES = 512L * 1024L * 1024L;
	private static final int MAX_ARCHIVE_ENTRIES = 4096;
	private static final int MAX_OSU_BYTES = 2 * 1024 * 1024;
	private static final String DEFAULT_TIMING_POINT = "0,500,4,1,0,100,1,0";

	private final AndroidLauncher activity;
	private String pendingDestination;
	private String pendingTitle;
	private String pendingArtist;

	AndroidMenuThemePicker(AndroidLauncher activity) {
		this.activity = activity;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public synchronized void chooseMenuTheme(
		final String currentTitle,
		final String currentArtist,
		final String destinationDirectory
	) {
		pendingDestination = destinationDirectory;
		pendingTitle = cleanMetadata(currentTitle, "Custom Theme");
		pendingArtist = cleanMetadata(currentArtist, "Unknown Artist");
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				try {
					Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
					intent.addCategory(Intent.CATEGORY_OPENABLE);
					intent.setType("*/*");
					intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
						"audio/mpeg",
						"audio/ogg",
						"application/ogg",
						"application/zip",
						"application/octet-stream"
					});
					activity.startActivityForResult(
						Intent.createChooser(intent, "Choose MP3, OGG, or osu! beatmap"),
						REQUEST_CODE
					);
				} catch (Throwable error) {
					notifyThemeError("The menu-theme picker could not be opened.");
				}
			}
		});
	}

	/** Returns true when the activity result belonged to this picker. */
	boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode != REQUEST_CODE)
			return false;

		final String destination;
		final String fallbackTitle;
		final String fallbackArtist;
		synchronized (this) {
			destination = pendingDestination;
			fallbackTitle = pendingTitle;
			fallbackArtist = pendingArtist;
			pendingDestination = null;
			pendingTitle = null;
			pendingArtist = null;
		}

		if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null)
			return true;

		final Uri source = data.getData();
		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					ResolvedTheme theme = importTheme(
						source,
						destination,
						fallbackTitle,
						fallbackArtist
					);
					showMetadataDialog(theme);
				} catch (Throwable error) {
					notifyThemeError(cleanImportError(error));
				}
			}
		}, "opsu-menu-theme-import");
		worker.setDaemon(true);
		worker.start();
		return true;
	}

	private ResolvedTheme importTheme(
		Uri source,
		String destinationDirectory,
		String fallbackTitle,
		String fallbackArtist
	) throws Exception {
		File root = validateDestination(destinationDirectory);
		String displayName = getDisplayName(source);
		String lowerName = displayName.toLowerCase(Locale.US);
		if (lowerName.endsWith(".osz") || lowerName.endsWith(".zip"))
			return importArchive(source, root, fallbackTitle, fallbackArtist);
		if (lowerName.endsWith(".mp3"))
			return importDirectAudio(source, root, ".mp3", displayName, fallbackTitle, fallbackArtist);
		if (lowerName.endsWith(".ogg"))
			return importDirectAudio(source, root, ".ogg", displayName, fallbackTitle, fallbackArtist);

		String mime = activity.getContentResolver().getType(source);
		if (mime != null && (mime.equals("application/zip") ||
			mime.equals("application/x-osu-beatmap-archive")))
			return importArchive(source, root, fallbackTitle, fallbackArtist);
		if (mime != null && (mime.equals("audio/mpeg") || mime.equals("audio/mp3")))
			return importDirectAudio(source, root, ".mp3", displayName, fallbackTitle, fallbackArtist);
		if (mime != null && (mime.equals("audio/ogg") || mime.equals("application/ogg")))
			return importDirectAudio(source, root, ".ogg", displayName, fallbackTitle, fallbackArtist);
		throw new IllegalArgumentException("Choose an MP3, OGG, or .osz file.");
	}

	private ResolvedTheme importDirectAudio(
		Uri source,
		File root,
		String extension,
		String displayName,
		String fallbackTitle,
		String fallbackArtist
	) throws Exception {
		File destination = new File(root, "custom-menu-theme" + extension);
		try (InputStream input = activity.getContentResolver().openInputStream(source)) {
			if (input == null)
				throw new IllegalArgumentException("The selected audio file could not be opened.");
			installAudio(input, destination);
		}

		ResolvedTheme theme = readAudioMetadata(destination);
		String filenameTitle = stripExtension(displayName);
		theme.title = cleanMetadata(theme.title,
			cleanMetadata(filenameTitle, fallbackTitle));
		theme.artist = cleanMetadata(theme.artist, fallbackArtist);
		return theme;
	}

	private ResolvedTheme importArchive(
		Uri source,
		File root,
		String fallbackTitle,
		String fallbackArtist
	) throws Exception {
		File temporaryArchive = new File(root, "menu-theme-import.osz.tmp");
		try {
			try (InputStream input = activity.getContentResolver().openInputStream(source)) {
				if (input == null)
					throw new IllegalArgumentException("The selected .osz file could not be opened.");
				copyLimited(input, temporaryArchive, MAX_ARCHIVE_BYTES);
			}

			try (ZipFile archive = new ZipFile(temporaryArchive)) {
				Map<String, ZipEntry> entries = new HashMap<String, ZipEntry>();
				List<ZipEntry> osuEntries = new ArrayList<ZipEntry>();
				Enumeration<? extends ZipEntry> enumeration = archive.entries();
				int entryCount = 0;
				long declaredBytes = 0L;
				while (enumeration.hasMoreElements()) {
					ZipEntry entry = enumeration.nextElement();
					if (++entryCount > MAX_ARCHIVE_ENTRIES)
						throw new IllegalArgumentException("That .osz contains too many files.");
					String normalized = normalizeArchivePath(entry.getName());
					if (normalized == null)
						throw new IllegalArgumentException("That .osz contains an unsafe file path.");
					long size = entry.getSize();
					if (size > 0L) {
						declaredBytes += size;
						if (declaredBytes > MAX_ARCHIVE_CONTENT_BYTES)
							throw new IllegalArgumentException("That .osz is too large to import safely.");
					}
					entries.put(normalized.toLowerCase(Locale.US), entry);
					if (!entry.isDirectory() && normalized.toLowerCase(Locale.US).endsWith(".osu"))
						osuEntries.add(entry);
				}

				for (ZipEntry osuEntry : osuEntries) {
					OsuThemeMetadata metadata;
					try (InputStream input = archive.getInputStream(osuEntry)) {
						metadata = parseOsuFile(input);
					}
					if (metadata.audioFilename == null)
						continue;
					String audioPath = resolveArchivePath(osuEntry.getName(), metadata.audioFilename);
					if (audioPath == null)
						continue;
					ZipEntry audioEntry = entries.get(audioPath.toLowerCase(Locale.US));
					if (audioEntry == null || audioEntry.isDirectory())
						continue;

					String lowerAudio = audioPath.toLowerCase(Locale.US);
					String extension;
					if (lowerAudio.endsWith(".mp3"))
						extension = ".mp3";
					else if (lowerAudio.endsWith(".ogg"))
						extension = ".ogg";
					else
						continue;

					File destination = new File(root, "custom-menu-theme" + extension);
					try (InputStream input = archive.getInputStream(audioEntry)) {
						installAudio(input, destination);
					}

					ResolvedTheme theme = readAudioMetadata(destination);
					theme.title = cleanMetadata(metadata.title,
						cleanMetadata(theme.title, fallbackTitle));
					theme.artist = cleanMetadata(metadata.artist,
						cleanMetadata(theme.artist, fallbackArtist));
					theme.timingPoint = cleanTimingPoint(metadata.timingPoint);
					return theme;
				}
			}
			throw new IllegalArgumentException("No playable MP3 or OGG referenced by an .osu file was found in that .osz.");
		} finally {
			if (temporaryArchive.isFile())
				temporaryArchive.delete();
		}
	}

	private File validateDestination(String destinationDirectory) throws Exception {
		if (destinationDirectory == null)
			throw new IllegalArgumentException("The menu-theme destination is missing.");
		File allowed = new File(
			Environment.getExternalStorageDirectory(),
			"opsu/menuthemes"
		).getCanonicalFile();
		File destination = new File(destinationDirectory).getCanonicalFile();
		if (!allowed.equals(destination))
			throw new SecurityException("The menu-theme destination is invalid.");
		if (!destination.isDirectory() && !destination.mkdirs())
			throw new IllegalStateException("The menu-theme folder could not be created.");
		return destination;
	}

	private void installAudio(InputStream input, File destination) throws Exception {
		File temporary = new File(destination.getAbsolutePath() + ".tmp");
		try {
			copyLimited(input, temporary, MAX_AUDIO_BYTES);
			if (destination.isFile() && !destination.delete())
				throw new IllegalStateException("The previous menu theme could not be replaced.");
			if (!temporary.renameTo(destination))
				throw new IllegalStateException("The menu theme could not be installed.");
		} finally {
			if (temporary.isFile())
				temporary.delete();
		}
	}

	private void copyLimited(InputStream input, File destination, long maximumBytes)
		throws Exception {
		long total = 0L;
		byte[] buffer = new byte[64 * 1024];
		try (FileOutputStream output = new FileOutputStream(destination)) {
			int read;
			while ((read = input.read(buffer)) != -1) {
				total += read;
				if (total > maximumBytes)
					throw new IllegalArgumentException("The selected theme file is too large.");
				output.write(buffer, 0, read);
			}
			output.flush();
			output.getFD().sync();
		}
	}

	private OsuThemeMetadata parseOsuFile(InputStream input) throws Exception {
		OsuThemeMetadata metadata = new OsuThemeMetadata();
		BufferedReader reader = new BufferedReader(
			new InputStreamReader(new LimitedInputStream(input, MAX_OSU_BYTES), StandardCharsets.UTF_8)
		);
		String section = "";
		String line;
		while ((line = reader.readLine()) != null) {
			line = stripBom(line).trim();
			if (line.isEmpty() || line.startsWith("//"))
				continue;
			if (line.startsWith("[") && line.endsWith("]")) {
				section = line;
				continue;
			}
			if (section.equals("[General]")) {
				String value = readKeyValue(line, "AudioFilename");
				if (value != null)
					metadata.audioFilename = stripQuotes(value);
			} else if (section.equals("[Metadata]")) {
				String title = readKeyValue(line, "Title");
				String artist = readKeyValue(line, "Artist");
				if (title != null)
					metadata.title = title;
				if (artist != null)
					metadata.artist = artist;
			} else if (section.equals("[TimingPoints]") && metadata.timingPoint == null) {
				String[] tokens = line.split(",");
				if (tokens.length >= 7 && tokens[6].trim().equals("1")) {
					try {
						if (Double.parseDouble(tokens[1].trim()) > 0d)
							metadata.timingPoint = line;
					} catch (NumberFormatException ignored) {
					}
				}
			}
		}
		return metadata;
	}

	private ResolvedTheme readAudioMetadata(File audio) {
		ResolvedTheme theme = new ResolvedTheme();
		theme.path = audio.getAbsolutePath();
		theme.timingPoint = DEFAULT_TIMING_POINT;
		MediaMetadataRetriever retriever = new MediaMetadataRetriever();
		try {
			retriever.setDataSource(audio.getAbsolutePath());
			theme.title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
			theme.artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
			String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
			if (duration != null) {
				long parsed = Long.parseLong(duration);
				theme.duration = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, parsed));
			}
		} catch (Throwable ignored) {
		} finally {
			try {
				retriever.release();
			} catch (Throwable ignored) {
			}
		}
		if (theme.duration <= 0)
			theme.duration = 180000;
		return theme;
	}

	private void showMetadataDialog(final ResolvedTheme theme) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				final EditText titleInput = new EditText(activity);
				titleInput.setSingleLine(true);
				titleInput.setText(cleanMetadata(theme.title, "Custom Theme"));
				final EditText artistInput = new EditText(activity);
				artistInput.setSingleLine(true);
				artistInput.setText(cleanMetadata(theme.artist, "Unknown Artist"));

				LinearLayout form = new LinearLayout(activity);
				form.setOrientation(LinearLayout.VERTICAL);
				int padding = Math.round(20f * activity.getResources().getDisplayMetrics().density);
				form.setPadding(padding, padding / 2, padding, 0);
				TextView titleLabel = new TextView(activity);
				titleLabel.setText("Title");
				TextView artistLabel = new TextView(activity);
				artistLabel.setText("Artist");
				form.addView(titleLabel);
				form.addView(titleInput, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT
				));
				form.addView(artistLabel);
				form.addView(artistInput, new LinearLayout.LayoutParams(
					ViewGroup.LayoutParams.MATCH_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT
				));

				new AlertDialog.Builder(activity)
					.setTitle("Menu theme details")
					.setMessage("Confirm how this song should appear in Now Playing.")
					.setView(form)
					.setPositiveButton("Use theme", (dialog, which) -> notifyThemeSelected(
						theme.path,
						cleanMetadata(titleInput.getText().toString(), "Custom Theme"),
						cleanMetadata(artistInput.getText().toString(), "Unknown Artist"),
						theme.duration,
						cleanTimingPoint(theme.timingPoint)
					))
					.setNegativeButton("Cancel", null)
					.show();
			}
		});
	}

	private String getDisplayName(Uri source) {
		Cursor cursor = null;
		try {
			cursor = activity.getContentResolver().query(
				source,
				new String[] { OpenableColumns.DISPLAY_NAME },
				null,
				null,
				null
			);
			if (cursor != null && cursor.moveToFirst()) {
				int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				if (index >= 0) {
					String displayName = cursor.getString(index);
					if (displayName != null && !displayName.trim().isEmpty())
						return displayName;
				}
			}
		} catch (Throwable ignored) {
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return "menu-theme";
	}

	private static String resolveArchivePath(String osuEntryName, String audioFilename) {
		String normalizedOsu = normalizeArchivePath(osuEntryName);
		if (normalizedOsu == null)
			return null;
		int slash = normalizedOsu.lastIndexOf('/');
		String parent = (slash >= 0) ? normalizedOsu.substring(0, slash + 1) : "";
		return normalizeArchivePath(parent + stripQuotes(audioFilename));
	}

	private static String normalizeArchivePath(String path) {
		if (path == null || path.indexOf('\0') >= 0)
			return null;
		path = path.replace('\\', '/');
		if (path.startsWith("/") || path.matches("^[A-Za-z]:.*"))
			return null;
		StringBuilder normalized = new StringBuilder();
		for (String part : path.split("/")) {
			if (part.isEmpty() || part.equals("."))
				continue;
			if (part.equals(".."))
				return null;
			if (normalized.length() > 0)
				normalized.append('/');
			normalized.append(part);
		}
		return normalized.toString();
	}

	private static String readKeyValue(String line, String key) {
		int colon = line.indexOf(':');
		if (colon < 0 || !line.substring(0, colon).trim().equalsIgnoreCase(key))
			return null;
		return line.substring(colon + 1).trim();
	}

	private static String cleanMetadata(String value, String fallback) {
		if (value == null)
			return fallback;
		String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
		return cleaned.isEmpty() ? fallback : cleaned;
	}

	private static String cleanTimingPoint(String value) {
		if (value == null)
			return DEFAULT_TIMING_POINT;
		String cleaned = value.replace('\n', ' ').replace('\r', ' ').trim();
		return cleaned.isEmpty() ? DEFAULT_TIMING_POINT : cleaned;
	}

	private static String stripQuotes(String value) {
		String stripped = cleanMetadata(value, "");
		if (stripped.length() >= 2 && stripped.startsWith("\"") && stripped.endsWith("\""))
			return stripped.substring(1, stripped.length() - 1);
		return stripped;
	}

	private static String stripExtension(String filename) {
		if (filename == null)
			return "";
		int dot = filename.lastIndexOf('.');
		return (dot > 0) ? filename.substring(0, dot) : filename;
	}

	private static String stripBom(String value) {
		return (!value.isEmpty() && value.charAt(0) == '\ufeff') ? value.substring(1) : value;
	}

	private static String cleanImportError(Throwable error) {
		String message = error.getMessage();
		if (message == null || message.trim().isEmpty())
			return "That file could not be imported as a menu theme.";
		return cleanMetadata(message, "That file could not be imported as a menu theme.");
	}

	private static final class ResolvedTheme {
		String path;
		String title;
		String artist;
		int duration;
		String timingPoint;
	}

	private static final class OsuThemeMetadata {
		String audioFilename;
		String title;
		String artist;
		String timingPoint;
	}

	/** Guards metadata parsing from oversized .osu entries with unknown ZIP sizes. */
	private static final class LimitedInputStream extends InputStream {
		private final InputStream input;
		private final long maximum;
		private long count;

		LimitedInputStream(InputStream input, long maximum) {
			this.input = input;
			this.maximum = maximum;
		}

		@Override
		public int read() throws java.io.IOException {
			int value = input.read();
			if (value >= 0 && ++count > maximum)
				throw new java.io.IOException("The .osu metadata file is too large.");
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
			int read = input.read(buffer, offset, length);
			if (read > 0 && (count += read) > maximum)
				throw new java.io.IOException("The .osu metadata file is too large.");
			return read;
		}
	}
}
