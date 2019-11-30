package box;

import core.ReceivePacket;

import java.io.IOException;

/**
 * 接受包实现
 */
public class StringReceivePacket extends ReceivePacket {
    private byte[] buffer;
    private int position;

    public StringReceivePacket(int len) {
        this.buffer = new byte[len];
        this.length = len;
    }

    @Override
    public void save(byte[] bytes, int count) {
        System.arraycopy(bytes, 0, buffer, position, count);
        position += count;
    }

    public String string(){
        return new String(buffer);
    }

    @Override
    public void close() throws IOException {

    }
}
