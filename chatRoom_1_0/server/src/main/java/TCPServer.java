import handle.ClientHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPServer implements ClientHandler.ClientHandlerCallback {
    private final int port;
    private ClientListener mListener;

    // 同步处理列队
    private List<ClientHandler> clientHandlerList = new ArrayList<ClientHandler> ();


    private final ExecutorService forwardingThreadPoolExecutor;

    public TCPServer(int port) {
        this.port = port;
        forwardingThreadPoolExecutor = Executors.newSingleThreadExecutor ();
    }

    /**
     * 开启TCP监听
     * @return
     */
    public boolean start() {
        try {
            ClientListener listener = new ClientListener(port);
            mListener = listener;
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
        if (mListener != null) {
            mListener.exit();
        }

        synchronized (TCPServer.this) {
            for (ClientHandler clientHandler : clientHandlerList) {
                clientHandler.exit();
            }

            clientHandlerList.clear();
        }
    }

    /**
     * 广播所有客户端
     * @param str
     */
    public synchronized void broadcast(String str) {
        for (ClientHandler clientHandler : clientHandlerList) {
            clientHandler.send(str);
        }
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
        // 打印到屏幕
        System.out.println("Received-" + handler.getClientInfo() + ":" + msg);

        // 异步转发任务
        forwardingThreadPoolExecutor.execute (()->{
            synchronized (TCPServer.this){
                for(ClientHandler clientHandler : clientHandlerList){
                    if(clientHandler.equals (handler)){
                        // 跳过自己
                        continue;
                    }
                    clientHandler.send ("Received-" + handler.getClientInfo() + ":" + msg);
                }
            }
        });
    }


    /**
     * 服务器监听客户端线程 -- 等待客户端连接
     */
    private class ClientListener extends Thread {
        private ServerSocket server;
        private boolean done = false;

        private ClientListener(int port) throws IOException {
            server = new ServerSocket(port);
            System.out.println("服务器信息：" + server.getInetAddress() + " P:" + server.getLocalPort());
        }

        @Override
        public void run() {
            super.run();

            System.out.println("服务器准备就绪～");
            // 等待客户端连接
            do {
                // 得到客户端
                Socket client;
                try {
                    client = server.accept();
                } catch (IOException e) {
                    continue;
                }
                try {
                    // 客户端构建异步线程
                    ClientHandler clientHandler = new ClientHandler(client, TCPServer.this);
                    // 读取数据并打印
                    clientHandler.readToPrint();
                    // 添加同步处理
                    synchronized (TCPServer.this) {
                        clientHandlerList.add(clientHandler);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("客户端连接异常：" + e.getMessage());
                }
            } while (!done);

            System.out.println("服务器已关闭！");
        }

        void exit() {
            done = true;
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
