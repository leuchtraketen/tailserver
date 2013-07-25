package tail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;


class Tail {

	private Socket socket;
	private BufferedOutputStream stream;
	private Client client;
	private long size;

	public Tail(Socket socket, BufferedOutputStream stream, Client client) {
		this.socket = socket;
		this.stream = stream;
		this.client = client;
	}

	private void tail(File file, long pos) {
		BufferedInputStream input = null;
		try {
			input = new BufferedInputStream(new ReadStandard().open(file, pos));
			final byte[] buffer = new byte[32768];
			long timoutTicks = TailServer.FILE_CHANGE_TIMEOUT / TailServer.TAIL_SLEEP_INTERVAL;
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

				if (!TailServer.sleep(TailServer.TAIL_SLEEP_INTERVAL)) {
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

	public void run(File file, long pos) {
		client.setFile(file);
		tail(file, pos);
	}
}