package core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 封装读写
 */
public class IoArgs {
    private byte[] byteBuffer = new byte[256];
    private ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);

    public int read(SocketChannel channel) throws IOException{
        buffer.clear();
        return channel.read(buffer);
    }

    public int write (SocketChannel channel)throws IOException{
        return channel.write(buffer);
    }

    public String bufferString(){
        // 丢弃读进来的换行符
        return new String(byteBuffer, 0, buffer.position()-1);
    }

    /**
     * 监听接口
     */
    public interface IoArgsEventListener {
        /**
         * 开启回调方法
         * @param args
         */
        void onStarted(IoArgs args);

        /**
         * 完成回调方法
         * @param args
         */
        void onCompleted(IoArgs args);
    }
}
