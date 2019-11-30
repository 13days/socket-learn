package impl.async;

import core.IoArgs;
import core.SendDispatcher;
import core.SendPacket;
import core.Sender;
import utils.CloseUtils;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 发送数据的调度这
 * 缓存所有需要发送的数据,通过队列的方式尽心发送
 */
public class AsyncSendDispatcher implements SendDispatcher {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final Sender sender;
    private final Queue<SendPacket> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isSending = new AtomicBoolean();

    private IoArgs ioArgs = new IoArgs();
    // 缓存当前发送的数据
    private SendPacket packetTemp;

    // 发送进度控制,发送数据比ioArgs更大
    // 当前大小
    private int total;
    // 当前进度
    private int position;

    public AsyncSendDispatcher(Sender sender) {
        this.sender = sender;
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
            SendPacket packet = this.packetTemp;
            if(packet!=null){
                packetTemp = null;
                CloseUtils.close(packet);
            }
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
        IoArgs args = this.ioArgs;
        args.startWriting();
        if(position>=total){
            // 这个packet发送完了,发送下一个
            sendNextPacket();
        }else if(position == 0){
            // 首包,需要携带长度信息
            args.writeLength(total);
        }
        byte[] bytes = packetTemp.bytes();
        // 把bytes的数据写入到IoArgs
        int count = args.readFrom(bytes, position);
        // 记录当前包封装到ioArgs里的位置
        position += count;

        // 完成封装
        args.finishWriting();

        try {
            sender.sendAsync(args, ioArgsEventListener);
        } catch (IOException e) {
            closeAndNotify();
        }
    }

    private void closeAndNotify() {
        CloseUtils.close(this);
    }

    // ioArgs的发送回调
    private final IoArgs.IoArgsEventListener ioArgsEventListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {

        }

        @Override
        public void onCompleted(IoArgs args) {
            // 继续发送当前包
            sendCurrentPacket();
        }
    };

}
