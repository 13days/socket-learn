package impl.async;

import core.IoArgs;
import core.SendDispatcher;
import core.SendPacket;
import core.Sender;
import utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 发送数据的调度这
 * 缓存所有需要发送的数据,通过队列的方式尽心发送
 */
public class AsyncSendDispatcher implements SendDispatcher, IoArgs.IoArgsEventProcessor {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final Sender sender;
    private final Queue<SendPacket> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isSending = new AtomicBoolean();

    private IoArgs ioArgs = new IoArgs();
    // 缓存当前发送的数据
    private SendPacket<?> packetTemp;

    private ReadableByteChannel packetChannel;

    // 发送进度控制,发送数据比ioArgs更大
    // 当前大小
    private long total;
    // 当前进度
    private long position;

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        sender.setSenderListener(this);
    }

    @Override
    public void send(SendPacket packet) {
        queue.offer(packet);
        // 如果不是发送状态,激活起来
        if(isSending.compareAndSet(false, true)){
            sendNextPacket();
        }
    }


    @Override
    public void cancel(SendPacket packet) {

    }

    @Override
    public void close() throws IOException {
        if(isClosed.compareAndSet(false, true)){
            isSending.set(false);
            // 异常关闭的完成操作
            completePacket(false);
        }
    }

    public SendPacket takePacket(){
        // Retrieves and removes
        SendPacket packet = queue.poll();
        if(packet!=null && packet.isCanceled()){
            // 已取消,不用发送,递归拿下一条
            return takePacket();
        }
        return packet;
    }

    private void sendNextPacket() {
        SendPacket temp = packetTemp;
        // 避免内存泄漏
        if(temp != null){
            CloseUtils.close(temp);
        }
        SendPacket packet = packetTemp =takePacket();
        if(packet == null){
            // 队列为空,取消状态发送
            isSending.set(false);
            return;
        }
        total = packet.length();
        position = 0;

        // 发送当前包
        sendCurrentPacket();
    }

    /**
     * 发送当前包
     */
    private void sendCurrentPacket() {
        if(position>=total){
            completePacket(position == total);
            // 这个packet发送完了,发送下一个
            sendNextPacket();
            return;
        }
        try {
            // 注册发送下一条数据的操作
            sender.postSenderAsync();
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    private void completePacket(boolean isSuccessed){
        SendPacket packet = this.packetTemp;
        if(packet==null){
            return;
        }
        CloseUtils.close(packet);
        CloseUtils.close(packetChannel);

        packetTemp = null;
        packetChannel = null;
        total = 0;
        position = 0;
    }

    @Override
    public IoArgs provideIoArgs() {
        IoArgs args = this.ioArgs;

        if(packetChannel == null){
            packetChannel = Channels.newChannel(packetTemp.open());
            args.limit(4);
            // todo 需要规避的错误,丢包
            args.writeLength((int)packetTemp.length());
        }else{
            args.limit((int)Math.min(args.capacity(), total-position));
            try {
                int count = args.readFrom(packetChannel);
                position += count;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        return args;
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        // 继续发送当前包
        sendCurrentPacket();
    }
}
