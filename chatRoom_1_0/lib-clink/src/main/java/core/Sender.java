package core;

import java.io.Closeable;
import java.io.IOException;

public interface Sender extends Closeable {
    void setSenderListener(IoArgs.IoArgsEventProcessor listener);

    /**
     * 异步发送
     * @return
     * @throws IOException
     */
    boolean postSenderAsync() throws IOException;
}
