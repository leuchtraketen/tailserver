package tail;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.swing.Box;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

class TailGui implements ClientList {
	JTextArea area = new JTextArea(20, 50);
	JFrame window = new JFrame("Tail");
	Box clients = Box.createVerticalBox();

	public TailGui() {
		Font font = new Font("Monospaced", Font.PLAIN, 12);
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
		TailGui.ClientBox client = new ClientBox();
		clients.add(client.getBox());
		repaint(clients);
		return client;
	}

	@Override
	public void removeClient(Client client) {
		clients.remove(((TailGui.ClientBox) client).getBox());
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

				TailServer.sleep(TailServer.GUI_UPDATE_INTERVAL);
				++ticks;
			}
		}

		private void updateSize() {
			fieldSize.setText(TailServer.formatHumanReadable(size));
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
				fieldSpeed.setText(TailServer.formatHumanReadable(speed) + "/s");
			}
			previousTime = time;
			previousSize = size;
		}

		@Override
		public String getAverageSpeed() {
			return TailServer.formatHumanReadable(size / (stopTime - startTime) * 1000) + "/s";
		}

		@Override
		public String getSize() {
			return TailServer.formatHumanReadable(size);
		}

		@Override
		public void disconnect() {
			connected = false;
			stopTime = System.currentTimeMillis();
		}
	}

	public static class JTextAreaOutputStream extends OutputStream implements LogProvider {
		JTextArea ta;

		public JTextAreaOutputStream(JTextArea t) {
			super();
			ta = t;
			TailServer.setLogProvider(this);
		}

		public void write(int i) {
			ta.append(Character.toString((char) i));
		}

		@SuppressWarnings("unused")
		public void write(char[] buf, int off, int len) {
			String s = new String(buf, off, len);
			ta.append(s);
		}

		@Override
		public String getLog() {
			return ta.getText();
		}
	}
}