package tail;

import java.io.IOException;
import java.net.ServerSocket;

public class TailServer {

	private static final int PORT = 8081;
	static final long TAIL_SLEEP_INTERVAL = 200;
	static final long GUI_UPDATE_INTERVAL = 1000;
	static final long FILE_CHANGE_TIMEOUT = 5000;
	static final long MAX_FILE_LENGTH = 5L * 1024L * 1024L * 1024L;

	public static void main(String[] args) throws IOException {
		TailGui gui = new TailGui();
		gui.runGui();
		new Thread(new TailDirectory()).start();
		new TailServer().runServer(gui);
	}

	static LogProvider currentLogProvider = null;

	private void runServer(ClientList clients) {
		ServerSocket serverSocket = null;
		boolean listening = true;

		while (serverSocket == null) {
			try {
				serverSocket = new ServerSocket(PORT);
				System.out.println("Server listening on port: " + PORT);
			} catch (IOException e) {
				System.err.println("Could not listen on port: " + PORT + ".");
				serverSocket = null;
				TailServer.sleep(1000);
			}
		}

		while (listening) {
			try {
				new Thread(new ServerThread(serverSocket.accept(), clients)).start();
			} catch (IOException e) {
				System.out.println("Error in accept loop!");
				e.printStackTrace();
			}
		}
	}

	public static String formatBytesHumanReadable(double number) {
		String unit = "bytes";
		if (number > 1024) {
			number /= 1024;
			unit = "KiB";
		}
		if (number > 1024) {
			number /= 1024;
			unit = "MiB";
		}
		number = (double) ((long) (number * 1000)) / 1000;
		return number + " " + unit;
	}

	public static String formatMilliSecondsHumanReadable(long x) {
		long millis = 0;
		long seconds = 0;
		long minutes = 0;
		long hours = 0;
		long days = 0;

		seconds = (int) (x / 1000);
		millis = x % 1000;

		minutes = (int) (seconds / 60);
		seconds = seconds % 60;

		hours = (int) (minutes / 60);
		hours = hours % 60;

		days = (int) (hours / 24);
		days = days % 24;

		String str = seconds + "";
		if (millis >= 100)
			str += "." + millis;
		else if (millis >= 10)
			str += ".0" + millis;
		else if (millis >= 1)
			str += ".00" + millis;
		str += " seconds";

		if (minutes > 0)
			str = minutes + " minutes " + str;
		if (hours > 0)
			str = hours + " hours " + str;
		if (days > 0)
			str = days + " days " + str;

		return str;
	}

	public static void setLogProvider(LogProvider log) {
		TailServer.currentLogProvider = log;
	}

	public static boolean sleep(long sleepTime) {
		try {
			Thread.sleep(sleepTime);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
