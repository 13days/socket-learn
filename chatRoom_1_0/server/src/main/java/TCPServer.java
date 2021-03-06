import box.FileSendPacket;
import com.sun.org.apache.bcel.internal.generic.Select;
import handle.ClientHandler;
import utils.CloseUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPServer implements ClientHandler.ClientHandlerCallback {
    private final int port;
    private final File cachePath;
    private ClientListener listener;
    private Selector selector;
    private ServerSocketChannel server;

    // 同步处理列队
    private List<ClientHandler> clientHandlerList = new ArrayList<ClientHandler> ();
    
    private final ExecutorService forwardingThreadPoolExecutor;

    public static long sendSize = 0L;
    public static long receiveSize = 0L;

    /**
     * 配置
     * @param port
     */
    public TCPServer(int port, File cachePath) {
        this.port = port;
        this.cachePath = cachePath;
        forwardingThreadPoolExecutor = Executors.newSingleThreadExecutor ();
    }

    /**
     * 开启TCP监听
     * @return
     */
    public boolean start() {
        try {
            selector = Selector.open();
            ServerSocketChannel server = ServerSocketChannel.open();
            // 设置为非阻塞
            server.configureBlocking(false);
            // 绑定本地端口
            server.socket().bind(new InetSocketAddress(port));
            // 注册客户端连接到达监听
            server.register(selector, SelectionKey.OP_ACCEPT);

            this.server = server;

            System.out.println("服务器信息：" + server.getLocalAddress().toString());


            ClientListener listener = this.listener = new ClientListener();
            listener.start();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 关闭tcp监听,把所有客户端移除
     */
    public void stop() {
        if (listener != null) {
            listener.exit();
        }
        CloseUtils.close(server, selector);
        synchronized (TCPServer.this) {
            for (ClientHandler clientHandler : clientHandlerList) {
                clientHandler.exit();
            }

            clientHandlerList.clear();
        }
        // 停止线程池
        forwardingThreadPoolExecutor.shutdownNow();
    }

    /**
     * 广播所有客户端
     * @param str
     */
    public synchronized void broadcast(String str) {
        for (ClientHandler clientHandler : clientHandlerList) {
            clientHandler.send(str);
        }
        // 发送数量增加
        sendSize += clientHandlerList.size();
    }

    /**
     * 移除某个客户端--异步线程调用
     * @param handler
     */
    @Override
    public synchronized void onSelfClosed(ClientHandler handler) {
        clientHandlerList.remove(handler);
    }

    /**
     * 转发客户端消息给其他客户端--异步线程调用
     * @param handler
     * @param msg
     */
    @Override
    public void onNewMessageArrived(final ClientHandler handler, String msg) {
        // 接收增加
        receiveSize++;
        // todo 消息粘包
        // System.out.println(msg.replace("\r\n","-\\r\\n-"));
        // 异步转发任务
        forwardingThreadPoolExecutor.execute (()->{
            synchronized (TCPServer.this){
                for(ClientHandler clientHandler : clientHandlerList){
                    if(clientHandler.equals (handler)){
                        // 跳过自己
                        continue;
                    }
                    clientHandler.send ("Received-" + handler.getClientInfo() + ":" + msg);
                    sendSize++;
                }
            }
        });
    }


    /**
     * 服务器监听客户端线程 -- 等待客户端连接
     */
    private class ClientListener extends Thread {
        private boolean done = false;

        @Override
        public void run() {
            super.run();

            Selector selector = TCPServer.this.selector;


            System.out.println("服务器准备就绪～");
            // 等待客户端连接
            do {
                // 得到客户端
                Socket client;
                try {
                    // 阻塞失败,重试
                    if(selector.select() == 0){
                        if(done){
                            break;
                        }
                        continue;
                    }
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while(iterator.hasNext()){
                        if(done){
                            break;
                        }
                        SelectionKey key = iterator.next();
                        iterator.remove();

                        // 检查当前Key的状态是否是我们关注的
                        // 客户端到达状态
                        if(key.isAcceptable()){
                            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
                            // 当前accept是可用的,非阻塞状态拿到客户端
                            SocketChannel socketChannel = serverSocketChannel.accept();


                            try {
                                // 客户端构建异步线程
                                ClientHandler clientHandler = new ClientHandler(socketChannel, TCPServer.this, cachePath);

                                // 添加同步处理
                                synchronized (TCPServer.this) {
                                    clientHandlerList.add(clientHandler);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.out.println("客户端连接异常：" + e.getMessage());
                            }
                        }

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } while (!done);

            System.out.println("服务器已关闭！");
        }

        void exit() {
            done = true;
            // 唤醒阻塞
            selector.wakeup();
        }
    }
}
