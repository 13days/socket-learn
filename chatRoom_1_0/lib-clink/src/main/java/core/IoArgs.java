package core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 封装读写
 */
public class IoArgs {
    // todo 单消息不完整-->把字节数变小
    private int limit = 256;
    private byte[] byteBuffer = new byte[256];
    private ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);


    /**
     * 读取
     * @param bytes
     * @param offset
     * @return
     */
    public int readFrom(byte[] bytes, int offset){
        int size = Math.min(bytes.length-offset, buffer.remaining());
        buffer.put(bytes, offset, size);
        return size;
    }

    /**
     * 写数据到bytes
     * @param bytes
     * @param offset
     * @return
     */
    public int writeTo(byte[] bytes, int offset){
        int size = Math.min(bytes.length-offset, buffer.remaining());
        buffer.get(bytes, offset, size);
        return size;
    }

    /**
     * 从SocketChannel 读数据
     * @param channel
     * @return
     * @throws IOException
     */
    public int readFrom(SocketChannel channel) throws IOException{
        startWriting();
        int bytesProduced = 0;
        while(buffer.hasRemaining()){
            int len = channel.read(buffer);
            if(len<0){
                throw new EOFException();
            }
            bytesProduced += len;
        }

        finishWriting();
        return bytesProduced;
    }

    /**
     * 从socketCannel中写数据
     * @param channel
     * @return
     * @throws IOException
     */
    public int writeTo (SocketChannel channel)throws IOException{
        int bytesProduced = 0;
        while(buffer.hasRemaining()){
            int len = channel.write(buffer);
            if(len<0){
                throw new EOFException();
            }
            bytesProduced += len;
        }
        return bytesProduced;
    }


    /**
     * 开始写如数据到IoArgs
     */
    public void startWriting(){
        buffer.clear();
        // 定义容纳取键
        buffer.limit(limit);
    }

    /**
     * 写完数据
     */
    public void finishWriting(){
        buffer.flip();
    }

    /**
     * 单次写操作的容纳区间
     * @param limit
     */
    public void limit(int limit){
        this.limit = limit;
    }

    public void writeLength(int total) {
        buffer.putInt(total);
    }

    public int readLength(){
        return buffer.getInt();
    }

    public int capacity() {
        return buffer.capacity();
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
