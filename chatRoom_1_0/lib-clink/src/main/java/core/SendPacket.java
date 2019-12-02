package core;

import java.io.IOException;
import java.io.InputStream;

public abstract class SendPacket<T extends InputStream> extends Packet<T>{
    private boolean isCanceled;

    public boolean isCanceled() {
        return isCanceled;
    }

    /**
     * 设置取消发送标记
     */
    public void cancel() {
        isCanceled = true;
    }
}
