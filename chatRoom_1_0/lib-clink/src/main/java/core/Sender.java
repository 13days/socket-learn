package core;

import java.io.Closeable;
import java.io.IOException;

public interface Sender extends Closeable {
    void setSenderListener(IoArgs.IoArgsEventProcessor listener);

    boolean postSenderAsync() throws IOException;
}
