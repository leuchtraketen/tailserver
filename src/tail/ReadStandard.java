package tail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;


public class ReadStandard implements ReadMethod {
	@Override
	public InputStream open(File file, long pos) throws IOException {
		InputStream i = null;
		if (i == null)
			i = new ReadDirect().open(file, pos);
		return i;
	}
}