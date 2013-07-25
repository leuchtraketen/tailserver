package tail;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

public class TailDirectory implements Runnable {

	private static File RECORDING_DIR = null;
	private static Map<String, Long> filesizes;
	private static Map<String, Long> filesizeTimestamps;
	private static Map<String, Boolean> growing;
	private static boolean reset;

	static {
		init();
	}

	private static void init() {
		if (filesizes != null && !filesizes.isEmpty() && filesizeTimestamps != null
				&& !filesizeTimestamps.isEmpty() && growing != null && !growing.isEmpty()) {
			System.out.println("[#] Reset...");
		}
		filesizes = new HashMap<String, Long>();
		filesizeTimestamps = new HashMap<String, Long>();
		growing = new HashMap<String, Boolean>();
		reset = false;
	}

	private static File CURRENT_FILE = null;

	public void monitor() {
		@SuppressWarnings("unused")
		File recordingDirectory = TailDirectory.findRecordingDirectory();

		while (true) {
			init();

			File currentFile = null;
			while (!reset) {
				try {
					File newerFile = lastFileModified();

					if (currentFile == null || !newerFile.equals(currentFile)) {
						setFileGrowing(currentFile, false);

						String size = TailServer.formatBytesHumanReadable(newerFile.length());
						long age = System.currentTimeMillis() - newerFile.lastModified();
						String formattedAge = TailServer.formatMilliSecondsHumanReadable(age);
						boolean stopped = age > 30 * 1000;
						if (stopped) {
							String name = newerFile.getName();
							growing.put(name, false);
							filesizeTimestamps.put(name, newerFile.lastModified());
							filesizes.put(name, newerFile.length());
						}
						System.out.println("[+] " + (stopped ? "Stopped stream" : "Stream") + " found: "
								+ newerFile.getName() + " (filesize = " + size + ", last modified = "
								+ formattedAge + " ago)");
						currentFile = newerFile;
					}
				} catch (FileNotFoundException e) {}

				if (currentFile != null) {
					setFileSize(currentFile);
					setFileGrowing(currentFile);
					CURRENT_FILE = currentFile;
				}

				TailServer.sleep(1000);
			}

		}
	}

	private void setFileGrowing(File file) {
		if (file != null) {
			final String name = file.getName();
			long lastmodified = filesizeTimestamps.get(name);
			long now = System.currentTimeMillis();
			if (now - lastmodified > TailServer.FILE_CHANGE_TIMEOUT) {
				setFileGrowing(file, false);
			} else {
				setFileGrowing(file, true);
			}
		}
	}

	private void setFileGrowing(File file, boolean isGrowing) {
		if (file != null) {
			if (isGrowing == false && isFileGrowing(file)) {
				System.out.println("[-] Stream stopped: " + file.getName() + " (filesize = "
						+ TailServer.formatBytesHumanReadable(file.length()) + ")");
			}
			if (isGrowing == true && !isFileGrowing(file)) {
				System.out.println("[?] Fuck! This should never happen! " + file.getName() + " (filesize = "
						+ TailServer.formatBytesHumanReadable(file.length()) + ")");
			}
			final String name = file.getName();
			growing.put(name, isGrowing);
		}
	}

	public static boolean isFileGrowing(File file) {
		final String name = file.getName();
		if (growing.containsKey(name)) {
			return growing.get(name);
		} else {
			growing.put(name, true);
			return true;
		}
	}

	private static File lastFileModified() throws FileNotFoundException {
		File directory = findRecordingDirectory();

		File[] files = directory.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.isFile() && !file.getName().endsWith("_") && !file.getName().startsWith("Copy");
			}
		});
		File choice = null;
		if (files != null) {
			long lastMod = Long.MIN_VALUE;

			for (File file : files) {
				if (file.lastModified() > lastMod) {
					choice = file;
					lastMod = file.lastModified();
				}
			}
		}
		if (choice != null) {
			return choice;
		} else {
			throw new FileNotFoundException();
		}
	}

	static File findRecordingDirectory() {
		if (RECORDING_DIR == null) {
			File directory = new File(".").getAbsoluteFile();
			System.out.println("Current directory is " + directory);

			File[] files = directory.listFiles();
			for (File file : files) {
				if (file.getName().startsWith("LocalRecording") && file.isDirectory()) {
					directory = file;
					System.out.println("Recording directory found: " + directory);
				}
			}
			RECORDING_DIR = directory;
			System.out.println();
		}
		return RECORDING_DIR;
	}

	@Override
	public void run() {
		monitor();
	}

	private static void setFileSize(File file) {
		final String name = file.getName();
		final long length = file.length();
		if (!filesizes.containsKey(name) || filesizes.get(name) != length) {
			filesizeTimestamps.put(name, System.currentTimeMillis());
			filesizes.put(name, length);
		}
	}

	public static File getCurrentStreamFile() throws FileNotFoundException {
		if (CURRENT_FILE != null) {
			return CURRENT_FILE;
		} else {
			throw new FileNotFoundException();
		}
	}

	public static void reset() {
		reset = true;
	}
}
