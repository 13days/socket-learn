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
public class AsyncSendDispatcher implements SendDispatcher, IoArgs.IoArgsEventProcessor,AsyncPacketReader.PacketProvider{
    private final Sender sender;
    private final Queue<SendPacket> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isSending = new AtomicBoolean();
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    private AsyncPacketReader reader = new AsyncPacketReader(this);

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
        sender.setSenderListener(this);
    }

    @Override
    public void send(SendPacket packet) {
        queue.offer(packet);
        requestSend();
    }


    @Override
    public void cancel(SendPacket packet) {
        boolean ret = queue.remove(packet);
        if (ret) {
            packet.cancel();
            return;
        }
        reader.cancel(packet);
    }

    @Override
    public SendPacket takePacket(){
        // Retrieves and removes
        SendPacket packet;
        packet = queue.poll();
        if(packet == null){
            return null;
        }

        if(packet!=null && packet.isCanceled()){
            // 已取消,不用发送,递归拿下一条
            return takePacket();
        }
        return packet;
    }

    /**
     * 完成Packet发送
     * @param packet
     * @param isSucceed
     */
    @Override
    public void completedPacket(SendPacket packet, boolean isSucceed) {
        CloseUtils.close(packet);
    }

    /**
     * 请求网络进行数据发送
     */
    private void requestSend() {
        synchronized (isSending){
            if(isSending.get() || isClosed.get()){
                return;
            }
            if (reader.requestTakePacket()){
                try {
                    // 注册发送下一条数据的操作
                    boolean isSucceed = sender.postSenderAsync();
                    if(isSucceed){
                        isSending.set(true);
                    }
                } catch (IOException e) {
                    closeAndNotify();
                }
            }
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }


    @Override
    public void close() {
        if(isClosed.compareAndSet(false, true)){
            // 异常关闭的完成操作
            reader.close();
            // 情况列队,防止内存泄漏
            queue.clear();
            synchronized (isSending){
                isSending.set(false);
            }
        }
    }

    @Override
    public IoArgs provideIoArgs() {
        return isClosed.get() ? null : reader.fillData();
    }

    @Override
    public void onConsumeFailed(IoArgs args, Exception e) {
        e.printStackTrace();
        synchronized (isSending){
            isSending.set(false);
        }
        // 继续请求发送当前数据
        requestSend();
    }

    @Override
    public void onConsumeCompleted(IoArgs args) {
        // 继续发送当前包
        synchronized (isSending){
            isSending.set(false);
        }
        // 继续请求发送当前数据
        requestSend();
    }
}
