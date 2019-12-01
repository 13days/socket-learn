package core;

import java.io.Closeable;
import java.io.IOException;

public interface Receiver extends Closeable {
    void setReceiveListener(IoArgs.IoArgsEventProcessor listener);

    /**
     * 异步接收
     * @return
     * @throws IOException
     */
    boolean postReceiveAsync() throws IOException;
}
