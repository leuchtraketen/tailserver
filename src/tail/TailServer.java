package tail;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class TailServer {

	private static final int PORT = 8081;
	private static final long TAIL_SLEEP_INTERVAL = 200;
	private static final long GUI_UPDATE_INTERVAL = 1000;
	private static final long FILE_CHANGE_TIMEOUT = 5000;

	public static void main(String[] args) throws IOException {
		TailGui gui = new TailGui();
		gui.runGui();
		new TailServer().runServer(gui);
	}

	private File findRecordingDirectory() {
		File directory = new File(".").getAbsoluteFile();
		System.out.println("Current directory is " + directory);

		File[] files = directory.listFiles();
		for (File file : files) {
			if (file.getName().startsWith("LocalRecording") && file.isDirectory()) {
				directory = file;
				System.out.println("Recording directory found: " + directory);
			}
		}
		return directory;
	}

	private void runServer(ClientList clients) {
		File recordingDirectory = findRecordingDirectory();

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
				new ServerThread(serverSocket.accept(), clients, recordingDirectory).start();
			} catch (IOException e) {
				System.out.println("Error in accept loop!");
				e.printStackTrace();
			}
		}
	}

	private static class Tail {

		private Socket socket;
		private BufferedOutputStream stream;
		private Client client;
		private File directory;
		private int size;

		public Tail(Socket socket, BufferedOutputStream stream, Client client, File directory) {
			this.socket = socket;
			this.stream = stream;
			this.client = client;
			this.directory = directory;
		}

		private void tail(File file) {
			BufferedInputStream input = null;
			try {
				input = new BufferedInputStream(new FileInputStream(file));
				final byte[] buffer = new byte[32768];
				long timoutTicks = FILE_CHANGE_TIMEOUT / TAIL_SLEEP_INTERVAL;
				long timeout = timoutTicks;
				while (isConnected(socket) && timeout >= 0) {
					try {
						for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
							stream.write(buffer, 0, read);
							size += read;
							client.setContentLength(size);
							timeout = timoutTicks;
						}
					} catch (IOException e1) {
						e1.printStackTrace();
						break;
					}

					if (!TailServer.sleep(TAIL_SLEEP_INTERVAL)) {
						System.out.println("Tail thread interrupted :(");
						break;
					}
					--timeout;
				}
				input.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			try {
				input.close();
			} catch (Exception e1) {}
		}

		private boolean isConnected(Socket socket) {
			synchronized (socket) {
				try {
					socket.getOutputStream().write(new byte[] {}, 0, 0);
				} catch (IOException e) {
					return false;
				}
			}
			return true;
		}

		private File lastFileModified() throws FileNotFoundException {
			File[] files = directory.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return file.isFile();
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
				System.out.println("Found last modified file: " + choice);
				return choice;
			} else {
				throw new FileNotFoundException();
			}
		}

		public void run() {
			try {
				File lastFile = lastFileModified();
				client.setFile(lastFile);
				tail(lastFile);
			} catch (FileNotFoundException e) {
				System.err.println("Error! No files found.");
			}
		}
	}

	private static class ServerThread extends Thread {
		private static final CharSequence HTTP_RESPONSE = "HTTP/1.0 200 Ok\r\n"
				+ "Server: TailServer/1.0\r\n" + "Content-Type: application/octet-stream\r\n"
				+ "Connection: close\r\n" + "\r\n";

		private Socket socket = null;
		private Client client = null;
		private ClientList clients = null;
		private File directory = null;

		public ServerThread(Socket socket, ClientList clients, File directory) {
			super("ServerThread");
			this.socket = socket;
			this.client = clients.newClient();
			this.clients = clients;
			this.directory = directory;
			client.setRemoteHost(socket.getInetAddress().getCanonicalHostName());
			client.setRemotePort(socket.getPort());
			System.out.println("Client connected: " + client);
		}

		public void run() {
			try {
				BufferedOutputStream stream = new BufferedOutputStream(socket.getOutputStream());
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				new PrintWriter(stream).append(HTTP_RESPONSE);
				new Tail(socket, stream, client, directory).run();

				stream.close();
				in.close();
				socket.close();
				clients.removeClient(client);
				client.disconnect();
				System.out.println("Client disconnected: " + client);
				System.out.println("Stats: sent: " + client.getSize() + ", average speed: "
						+ client.getAverageSpeed());

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static interface Client {

		public void setRemoteHost(String canonicalHostName);

		public void setRemotePort(int port);

		public void setContentLength(long size);

		public void setFile(File file);

		public void disconnect();

		public String getAverageSpeed();

		public String getSize();

	}

	private static interface ClientList {

		public Client newClient();

		public void removeClient(Client client);

	}

	private static class TailGui implements ClientList {
		JTextArea area = new JTextArea(20, 50);
		JFrame window = new JFrame("Tail");
		Box clients = Box.createVerticalBox();

		public TailGui() {
			Font font = new Font("Monospaced", Font.PLAIN, 14);
			area.setFont(font);
			JScrollPane pane = new JScrollPane(area);
			window.getContentPane().add(BorderLayout.CENTER, pane);
			window.getContentPane().add(BorderLayout.SOUTH, clients);

			PrintStream windowStream = new PrintStream(new JTextAreaOutputStream(area));
			System.setOut(windowStream);
			System.setErr(windowStream);
		}

		public void runGui() {
			window.setSize(1000, 600);
			window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
			int x = (int) ((dimension.getWidth() - window.getWidth()) / 2);
			int y = (int) ((dimension.getHeight() - window.getHeight()) / 2);
			window.setLocation(x, y);
			window.setVisible(true);
		}

		@Override
		public Client newClient() {
			ClientBox client = new ClientBox();
			clients.add(client.getBox());
			repaint(clients);
			return client;
		}

		@Override
		public void removeClient(Client client) {
			clients.remove(((ClientBox) client).getBox());
		}

		private static void repaint(final Component component) {
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					component.revalidate();
					component.repaint();
				}
			});
		}

		public static class ClientBox implements Client, Runnable {
			Box box = Box.createHorizontalBox();

			private JTextField fieldName;
			private JTextField fieldSize;
			private JTextField fieldSpeed;
			private JTextField fieldFile;

			private String host;
			private int port;
			private long size = 0;
			private double speed = 0;
			@SuppressWarnings("unused")
			private File file;

			private boolean connected;
			private long startTime;
			private long stopTime;

			public ClientBox() {
				box.add(new JLabel("Client:"));
				box.add(Box.createHorizontalStrut(15));
				fieldName = new JTextField();
				fieldName.setEditable(false);
				box.add(fieldName);
				box.add(Box.createHorizontalStrut(15));

				box.add(new JLabel("Size:"));
				box.add(Box.createHorizontalStrut(15));
				fieldSize = new JTextField();
				fieldSize.setEditable(false);
				box.add(fieldSize);
				box.add(Box.createHorizontalStrut(15));

				box.add(new JLabel("Speed:"));
				box.add(Box.createHorizontalStrut(15));
				fieldSpeed = new JTextField();
				fieldSpeed.setEditable(false);
				box.add(fieldSpeed);
				box.add(Box.createHorizontalStrut(15));

				box.add(new JLabel("File:"));
				box.add(Box.createHorizontalStrut(15));
				fieldFile = new JTextField();
				fieldFile.setEditable(false);
				fieldFile.setPreferredSize(new Dimension(200, fieldSize.getPreferredSize().height));
				box.add(fieldFile);
				repaint(box);

				new Thread(this).start();
				startTime = System.currentTimeMillis();
			}

			public String toString() {
				return host + ":" + port;
			}

			@Override
			public void setRemoteHost(String host) {
				this.host = host;
				fieldName.setText(host + ":" + port);
			}

			@Override
			public void setRemotePort(int port) {
				this.port = port;
				fieldName.setText(host + ":" + port);
			}

			@Override
			public void setContentLength(long size) {
				this.size = size;
			}

			@Override
			public void setFile(File file) {
				this.file = file;
				fieldFile.setText(file.getName());
			}

			public Component getBox() {
				return box;
			}

			@Override
			public void run() {
				long ticks = 0;
				connected = true;
				while (connected) {
					updateSize();
					updateSpeed();
					if (ticks % 50 == 0)
						repaint(box);

					TailServer.sleep(GUI_UPDATE_INTERVAL);
					++ticks;
				}
			}

			private void updateSize() {
				fieldSize.setText(formatHumanReadable(size));
			}

			long previousSize = 0;
			long previousTime = 0;

			private void updateSpeed() {
				long time = System.currentTimeMillis();
				if (previousTime != 0 && previousSize != 0) {
					double currentSpeed = (size - previousSize) / (time - previousTime) * 1000;
					speed = speed / 2 + currentSpeed / 2;
					if (currentSpeed == 0)
						speed = 0;
					fieldSpeed.setText(formatHumanReadable(speed) + "/s");
				}
				previousTime = time;
				previousSize = size;
			}

			private String formatHumanReadable(double number) {
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

			@Override
			public String getAverageSpeed() {
				return formatHumanReadable(size / (stopTime - startTime) * 1000) + "/s";
			}

			@Override
			public String getSize() {
				return formatHumanReadable(size);
			}

			@Override
			public void disconnect() {
				connected = false;
				stopTime = System.currentTimeMillis();
			}
		}

		public static class JTextAreaOutputStream extends OutputStream {
			JTextArea ta;

			public JTextAreaOutputStream(JTextArea t) {
				super();
				ta = t;
			}

			public void write(int i) {
				ta.append(Character.toString((char) i));
			}

			@SuppressWarnings("unused")
			public void write(char[] buf, int off, int len) {
				String s = new String(buf, off, len);
				ta.append(s);
			}
		}
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
