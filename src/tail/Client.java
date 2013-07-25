package tail;

import java.io.File;

public interface Client {

	public void setRemoteHost(String canonicalHostName);

	public void setRemotePort(int port);

	public void setContentLength(long size);

	public void setFile(File file);

	public void disconnect();

	public String getAverageSpeed();

	public String getSize();

}