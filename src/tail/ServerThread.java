package tail;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

class ServerThread implements Runnable {
	// "Content-Type: application/octet-stream\r\n"
	private static final CharSequence HTTP_RESPONSE = "Server: TailServer/1.0\r\n" + "Connection: close\r\n";

	private Socket socket = null;
	private Client client = null;
	private ClientList clients = null;

	public ServerThread(Socket socket, ClientList clients) {
		this.socket = socket;
		this.client = clients.newClient();
		this.clients = clients;
		client.setRemoteHost(socket.getInetAddress().getCanonicalHostName());
		client.setRemotePort(socket.getPort());
		System.out.println("Connected: " + client);
	}

	public void run() {
		try {
			BufferedOutputStream stream = new BufferedOutputStream(socket.getOutputStream());
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			String request = parseRequest(in);
			if (request.equals("log")) {
				writeLog(in, stream);
			} else if (request.equals("reset")) {
				reset(in, stream);
			} else if (request.equals("file")) {
				writeLatestFilename(in, stream);
			} else {
				writeLatestStream(in, stream);
				System.out.println("Stats: sent: " + client.getSize() + ", average speed: "
						+ client.getAverageSpeed());
			}

			stream.close();
			in.close();
			socket.close();
			clients.removeClient(client);
			client.disconnect();
			System.out.println("Disconnected: " + client);
			System.out.println();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String parseRequest(BufferedReader in) throws IOException {
		String request = in.readLine();
		if (request.contains(" ")) {
			request = request.split(" ")[1];
			if (request.startsWith("/")) {
				request = request.substring(1);
			}
		}
		return request;
	}

	private void writeLatestStream(BufferedReader in, BufferedOutputStream stream) throws IOException {
		System.out.println("Request: video stream");
		File file = null;
		try {
			file = TailDirectory.getCurrentStreamFile();

			long pos = readHeaders(in, file);
			writeStreamHeaders(stream, file, pos);

			new Tail(socket, stream, client).run(file, pos);

		} catch (FileNotFoundException e) {
			System.err.println("Error! No files found.");
			PrintWriter pw = new PrintWriter(stream);
			pw.append("HTTP/1.0 404 Not Found\r\n");
			pw.append(HTTP_RESPONSE);
			pw.append("Content-Type: text/plain\r\n");
			pw.append("\r\n");
			pw.flush();
		}
	}

	private void writeLatestFilename(BufferedReader in, BufferedOutputStream stream) throws IOException {
		System.out.println("Request: latest file name");
		PrintWriter pw = new PrintWriter(stream);
		pw.append("HTTP/1.0 200 Ok\r\n");
		pw.append(HTTP_RESPONSE);
		pw.append("Content-Type: text/plain\r\n");
		pw.append("\r\n");
		pw.flush();
		File file = null;
		try {
			file = TailDirectory.getCurrentStreamFile();
			pw.append(file.getName());
			System.out.println("=> " + file.getName());
		} catch (FileNotFoundException e) {
			System.err.println("Error! No files found.");
		}
		pw.flush();
	}

	private void writeLog(BufferedReader in, BufferedOutputStream stream) throws IOException {
		System.out.println("Request: log output");
		PrintWriter pw = new PrintWriter(stream);
		pw.append("HTTP/1.0 200 Ok\r\n");
		pw.append(HTTP_RESPONSE);
		pw.append("Content-Type: text/plain\r\n");
		pw.append("\r\n");
		pw.flush();

		if (TailServer.currentLogProvider != null) {
			String log = TailServer.currentLogProvider.getLog();
			pw.append(log);
			System.out.println("=> " + log.length() + " bytes");
		} else {
			pw.append("no log...");
		}
		pw.flush();
	}

	private void reset(BufferedReader in, BufferedOutputStream stream) {
		System.out.println("Request: cache reset");
		PrintWriter pw = new PrintWriter(stream);
		pw.append("HTTP/1.0 200 Ok\r\n");
		pw.append(HTTP_RESPONSE);
		pw.append("Content-Type: text/plain\r\n");
		pw.append("\r\n");
		pw.flush();

		TailDirectory.reset();
	}

	private void writeStreamHeaders(BufferedOutputStream stream, File file, long pos) {
		long size = TailDirectory.isFileGrowing(file) ? TailServer.MAX_FILE_LENGTH : file.length();
		PrintWriter pw = new PrintWriter(stream);
		if (pos > 0) {
			pw.append("HTTP/1.0 206 Partial Content\r\n");
			pw.append(HTTP_RESPONSE);
			pw.append("Content-Type: video/x-flv\r\n");
			pw.append("Content-Range: " + pos + "-" + (size - 1) + "/" + size + "\r\n");
			pw.append("Content-Length: " + (size - pos) + "\r\n");
		} else {
			pw.append("HTTP/1.0 200 Ok\r\n");
			pw.append(HTTP_RESPONSE);
			pw.append("Content-Type: video/x-flv\r\n");
			pw.append("Content-Length: " + size + "\r\n");
		}
		pw.append("\r\n");
		pw.flush();
	}

	private long readHeaders(BufferedReader in, File file) throws IOException {
		String pos = "0";
		String line;
		while ((line = in.readLine()).length() > 2) {
			if (line.startsWith("Range:") && line.contains("=")) {
				pos = line.split("=")[1].split("[-]")[0];
			}
		}
		return interpretPosition(pos, file);
	}

	private static long interpretPosition(String str, File file) {
		str = str.toLowerCase();
		long pos = 0;
		if (str.endsWith("%")) {
			double percent = Double.parseDouble(str.replaceAll("%", "")) / 100;
			long size = file.length();
			pos = (long) (size * percent);
		} else if (str.endsWith("k") || str.endsWith("kb")) {
			pos = 1000 * Long.parseLong(str.replaceAll("[^0-9.]", ""));
		} else if (str.endsWith("kib")) {
			pos = 1024 * Long.parseLong(str.replaceAll("[^0-9.]", ""));
		} else if (str.endsWith("m") || str.endsWith("mb")) {
			pos = 1000 * 1000 * Long.parseLong(str.replaceAll("[^0-9.]", ""));
		} else if (str.endsWith("mib")) {
			pos = 1024 * 1024 * Long.parseLong(str.replaceAll("[^0-9.]", ""));
		} else if (str.endsWith("g") || str.endsWith("gb")) {
			pos = 1000 * 1000 * 1000 * Long.parseLong(str.replaceAll("[^0-9.]", ""));
		} else if (str.endsWith("gib")) {
			pos = 1024 * 1024 * 1024 * Long.parseLong(str.replaceAll("[^0-9.]", ""));
		} else {
			try {
				pos = Long.parseLong(str);
			} catch (Exception e) {
				e.printStackTrace();
				pos = 0;
			}
		}
		return pos;
	}
}