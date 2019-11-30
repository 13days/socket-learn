package impl.async;

import box.StringReceivePacket;
import core.IoArgs;
import core.ReceiveDispatcher;
import core.ReceivePacket;
import core.Receiver;
import utils.CloseUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncReceiveDispatcher implements ReceiveDispatcher {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Receiver receiver;
    private final ReceivePacketCallback callback;

    private IoArgs ioArgs = new IoArgs();
    private ReceivePacket packetTemp;
    private byte[] buffer;
    private int total;
    private int position;

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        this.receiver.setReceiveListener(ioArgsEventListener);
        this.callback = callback;
    }

    @Override
    public void start() {
        registerReceive();
    }


    @Override
    public void stop() {

    }

    @Override
    public void close() {
        if (isClosed.compareAndSet(false, true)) {
            ReceivePacket packet = packetTemp;
            if(packet != null){
                packetTemp = null;
                CloseUtils.close(packet);
            }
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    private void registerReceive() {
        try {
            receiver.receiveAsync(ioArgs);
        } catch (IOException e) {
            closeAndNotify();
        }
    }


    private IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {
            int receiveSize;
            if(packetTemp == null){
                // 长度
                receiveSize = 4;
            }else{
                receiveSize = Math.min(total-position, args.capacity());
            }
            // 设置本次接受数据大小
            args.limit(receiveSize);
        }

        /**
         * 异步接受完成时回调
         * @param args
         */
        @Override
        public void onCompleted(IoArgs args) {
            // 解析接受到的数据
            assemblePacket(args);
            // 继续接受下一条数据
            registerReceive();
        }
    };

    /**
     * 解析数据到Packet
     * @param args
     */
    private void assemblePacket(IoArgs args) {
        if(packetTemp == null){
            int length = args.readLength();
            packetTemp = new StringReceivePacket(length);
            buffer = new byte[length];
            total = length;
            position = 0;
        }

        int count = args.writeTo(buffer, 0);
        if(count>0){
            packetTemp.save(buffer, count);
            position += count;
            if(position == count){
                completePacket();
                packetTemp = null;
            }
        }
    }

    /**
     * 完成数据接受操作
     */
    private void completePacket() {
        ReceivePacket packet = this.packetTemp;
        CloseUtils.close(packet);
        callback.onReceivePacketCompleted(packet);
    }
}
