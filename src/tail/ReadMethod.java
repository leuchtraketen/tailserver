package tail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface ReadMethod {
	InputStream open(File file, long pos) throws IOException;
}