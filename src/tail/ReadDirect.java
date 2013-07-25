package tail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


public class ReadDirect implements ReadMethod {
	@Override
	public InputStream open(File file, long pos) throws IOException {
		InputStream i = new FileInputStream(file);
		System.out.println("open: " + file + " (offset = " + TailServer.formatHumanReadable(pos) + ", filesize = "
				+ TailServer.formatHumanReadable(file.length()) + ")");
		if (pos > 0)
			i.skip(pos);
		return i;
	}
}