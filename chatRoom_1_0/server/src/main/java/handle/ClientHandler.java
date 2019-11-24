package handle;



import core.Connector;
import utils.CloseUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler {
    private final SocketChannel socketChannel;
    private final ClientWriteHandler writeHandler;
    private final ClientHandlerCallback clientHandlerCallback;
    private final Connector connector;

    // 客户端信息
    private final String clientInfo;

    /**
     * 构建客户端处理实例
     * @param socketChannel
     * @param clientHandlerCallback
     * @throws IOException
     */
    public ClientHandler(SocketChannel socketChannel, ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socketChannel = socketChannel;

        connector = new Connector(){
            @Override
            public void onChannelClosed(SocketChannel channel) {
                super.onChannelClosed(channel);
                exitBySelf();
            }

            // 重写连接的回调!!!
            @Override
            protected void onReceiveNewMessage(String str) {
                super.onReceiveNewMessage(str);
                clientHandlerCallback.onNewMessageArrived(ClientHandler.this, str);
            }
        };
        connector.setup(socketChannel);

        // 写选择器
        Selector writeSelector  = Selector.open();
        socketChannel.register(writeSelector, SelectionKey.OP_WRITE);
        this.writeHandler = new ClientWriteHandler(writeSelector);


        this.clientHandlerCallback = clientHandlerCallback;
        // 拿到远端数据:客户都
        this.clientInfo = socketChannel.getRemoteAddress().toString();
        System.out.println("新客户端连接：" + clientInfo);
    }

    public String getClientInfo() {
        return clientInfo;
    }

    /**
     * 提供给外部关闭掉客户端连接实例
     */
    public void exit() {
        CloseUtils.close(connector);
        writeHandler.exit();
        CloseUtils.close(socketChannel);
        System.out.println("客户端已退出：" + clientInfo);
    }

    /**
     * 发送一条消息给客户端
     * @param str
     */
    public void send(String str) {
        writeHandler.send(str);
    }


    /**
     * 内部关闭
     */
    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClosed(this);
    }

    /**
     * 客户端处理回调
     */
    public interface ClientHandlerCallback {
        // handler客户端自己关闭自己时,服务器的行为
        void onSelfClosed(ClientHandler handler);

        // 收到handler客户端传来的消息时,服务器的行为
        void onNewMessageArrived(ClientHandler handler, String msg);
    }

    /**
     * 对于每个客户端的输入,起一个线程 -- 处理一个TCP的读入流
     */
    class ClientReadHandler extends Thread {
        private boolean done = false;
        private final Selector selector;
        private final ByteBuffer byteBuffer;

        ClientReadHandler(Selector selector) {
            this.selector = selector;
            byteBuffer = ByteBuffer.allocate(256);
        }

        @Override
        public void run() {
            super.run();
            try {

                do {
                    // 客户端拿到一条数据

                    if(selector.select()==0){
                        if(done){
                            break;
                        }
                        continue;
                    }

                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()){
                        if(done){
                            break;
                        }
                        SelectionKey key = iterator.next();
                        // 避免重复处理
                        iterator.remove();
                        // 一定要写对到底是哪种动作就绪了
                        if(key.isReadable()){
                            SocketChannel channel = (SocketChannel)key.channel();

                            // 把数据读到Buffer里
                            byteBuffer.clear();
                            int read = channel.read(byteBuffer);
                            if(read>0){
                                // 删除行结束符,避免出现两个
                                String str = new String(byteBuffer.array(), 0, read-1);
                                // 回调,通知到TCPServer
                                clientHandlerCallback.onNewMessageArrived(ClientHandler.this, str);
                            }else{
                                System.out.println("客户端已无法读取数据!");
                                // 退出当前客户端
                                ClientHandler.this.exitBySelf();
                                break;
                            }
                        }
                    }
                } while (!done);
            } catch (Exception e) {
                if (!done) {
                    System.out.println("连接异常断开");
                    ClientHandler.this.exitBySelf();
                }
            } finally {
                // 连接关闭
                CloseUtils.close(selector);
            }
        }

        void exit() {
            done = true;
            // 可能阻塞,唤醒
            selector.wakeup();
            CloseUtils.close(selector);
        }
    }

    /**
     * 本类处理发送一条消息给客户端print出去
     * 采用一个单例线程池处理所有的发送
     */
    class ClientWriteHandler {
        private boolean done = false;
        private final Selector selector;
        private ByteBuffer byteBuffer;
        private final ExecutorService executorService;

        ClientWriteHandler(Selector selector) {
            this.selector = selector;
            this.byteBuffer = ByteBuffer.allocate(256);
            this.executorService = Executors.newSingleThreadExecutor();
        }

        void exit() {
            done = true;
            CloseUtils.close(selector);
            executorService.shutdownNow();
        }

        /**
         * 发送一条消息给该客户端
         * @param str
         */
        void send(String str) {
            if(done){
                return;
            }
            executorService.execute(new WriteRunnable(str));
        }

        class WriteRunnable implements Runnable {
            private final String msg;

            WriteRunnable(String msg) {
                // buffer读进来会丢弃结束符
                msg += '\n';
                this.msg = msg;
            }

            @Override
            public void run() {
                if (ClientWriteHandler.this.done) {
                    return;
                }

                try {
                    byteBuffer.clear();
                    byteBuffer.put(msg.getBytes());
                    // 反转,limit = position, position = 0,
                    byteBuffer.flip();
                    while(!done && byteBuffer.hasRemaining()){
                        int len = socketChannel.write(byteBuffer);
                        if(len<0){
                            // len = 0 是可以的(因为是异步的,直接发送,有可能是不可以真正发送数据)
                            System.out.println("客户端已无法发送数据!");
                            ClientHandler.this.exitBySelf();
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
