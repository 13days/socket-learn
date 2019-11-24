package core;


import impl.SocketChannelAdapter;

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

    public void setup(SocketChannel socketChannel) throws IOException {
        this.channel = socketChannel;

        IoContext context = IoContext.get();
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, context.getIoProvider(), this);

        // 接收和发送职责都再适配器里
        this.sender = adapter;
        this.receiver = adapter;

        // 读数据
        readNextMessage();
    }

    private void readNextMessage() {
        if (receiver != null) {
            try {
                // 该连接异步接受消息的回调
                receiver.receiveAsync(echoReceiveListener);
            } catch (IOException e) {
                System.out.println("异步接收数据异常：" + e.getMessage());
            }
        }
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public void onChannelClosed(SocketChannel channel) {

    }


    /**
     * 接收监听
     */
    private IoArgs.IoArgsEventListener echoReceiveListener = new IoArgs.IoArgsEventListener() {
        @Override
        public void onStarted(IoArgs args) {

        }

        @Override
        public void onCompleted(IoArgs args) {
            // 使用回调
            onReceiveNewMessage(args.bufferString());
            // 读取下一条数据
            readNextMessage();
        }
    };

    /**
     * 真正处理数据的回调
     * 处理接收,直接打印到屏幕
     * @param str
     */
    protected void onReceiveNewMessage(String str) {
        System.out.println(key.toString() + ":" + str);
    }
}
