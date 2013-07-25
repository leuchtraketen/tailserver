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
		System.out.println();

		while (listening) {
			try {
				new Thread(new ServerThread(serverSocket.accept(), clients)).start();
			} catch (IOException e) {
				System.out.println("Error in accept loop!");
				e.printStackTrace();
			}
		}
	}

	public static String formatHumanReadable(double number) {
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
