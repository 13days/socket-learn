package impl.async;

import box.StringReceivePacket;
import core.IoArgs;
import core.ReceiveDispatcher;
import core.ReceivePacket;
import core.Receiver;
import utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncReceiveDispatcher implements ReceiveDispatcher, IoArgs.IoArgsEventProcessor{
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private final Receiver receiver;
    private final ReceivePacketCallback callback;

    private IoArgs ioArgs = new IoArgs();
    private ReceivePacket<?> packetTemp;
    private WritableByteChannel packetChannel;
    private long total;
    private long position;

    public AsyncReceiveDispatcher(Receiver receiver, ReceivePacketCallback callback) {
        this.receiver = receiver;
        this.receiver.setReceiveListener(this);
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
            completePacket(false);
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    private void registerReceive() {
        try {
            receiver.postReceiveAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    /**
     * 解析数据到Packet
     * @param args
     */
    private void assemblePacket(IoArgs args) {
        if(packetTemp == null){
            int length = args.readLength();
            packetTemp = new StringReceivePacket(length);
            packetChannel = Channels.newChannel(packetTemp.open());

            total = length;
            position = 0;
        }

        try{
            int count = args.writeTo(packetChannel);
            position += count;

            if(position ==total){
                completePacket(true);
                packetTemp = null;
            }
        }catch (IOException e){
            e.printStackTrace();
            completePacket(false);
        }
    }

    /**
     * 完成数据接受操作
     */
    private void completePacket(boolean isSuccessed) {
        ReceivePacket packet = this.packetTemp;
        CloseUtils.close(packet);
        packetTemp = null;

        WritableByteChannel channel = this.packetChannel;
        CloseUtils.close(channel);
        packetChannel = null;

        if(packet!=null){
            callback.onReceivePacketCompleted(packet);
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        IoArgs args = ioArgs;

        int receiveSize;
        if(packetTemp == null){
            // 长度
            receiveSize = 4;
        }else{
            receiveSize = (int)Math.min(total-position, args.capacity());
        }
        // 设置本次接受数据大小
        args.limit(receiveSize);
        return args;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        assemblePacket(args);
        registerReceive();
    }
}
