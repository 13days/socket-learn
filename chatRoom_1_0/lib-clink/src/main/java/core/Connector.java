package core;


import box.StringReceivePacket;
import box.StringSendPacket;
import impl.SocketChannelAdapter;
import impl.async.AsyncReceiveDispatcher;
import impl.async.AsyncSendDispatcher;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.UUID;

/**
 * 隔离core和impl里的封装,外部类具体使用的工具类
 */
public class Connector implements Closeable, SocketChannelAdapter.OnChannelStatusChangeListener {
    private UUID key = UUID.randomUUID();
    private SocketChannel channel;
    private Sender sender;
    private Receiver receiver;

    private SendDispatcher sendDispatcher;
    private ReceiveDispatcher receiveDispatcher;

    public void setup(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;

        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, context.getIoProvider(), this);

        // 接收和发送职责都再适配器里
        this.sender = adapter;
        this.receiver = adapter;


        sendDispatcher = new AsyncSendDispatcher(sender);
        receiveDispatcher = new AsyncReceiveDispatcher(receiver, receivePacketCallback);

        // 启动接受
        receiveDispatcher.start();
    }

    public void send(String msg){
        SendPacket packet = new StringSendPacket(msg);
        this.sendDispatcher.send(packet);
    }


    @Override
    public void close() throws IOException {
        receiveDispatcher.close();
        sendDispatcher.close();
        sender.close();
        receiver.close();
        channel.close();
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {

    }


    /**
     * 真正处理数据的回调
     * 处理接收,直接打印到屏幕
     * @param str
     */
    protected void onReceiveNewMessage(String str) {
        System.out.println(key.toString() + ":" + str);
    }


    private ReceiveDispatcher.ReceivePacketCallback receivePacketCallback = new ReceiveDispatcher.ReceivePacketCallback() {
        @Override
        public void onReceivePacketCompleted(ReceivePacket packet) {
            if(packet instanceof StringReceivePacket){
                String msg = ((StringReceivePacket)packet).string();
                onReceiveNewMessage(msg);
            }
        }
    };
}
