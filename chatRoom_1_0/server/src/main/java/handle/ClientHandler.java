package handle;



import utils.CloseUtils;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler {
    private final Socket socket;
    private final ClientReadHandler readHandler;
    private final ClientWriteHandler writeHandler;
    private final ClientHandlerCallback clientHandlerCallback;
    // 客户端信息
    private final String clientInfo;

    /**
     * 构建客户端处理实例
     * @param socket
     * @param clientHandlerCallback
     * @throws IOException
     */
    public ClientHandler(Socket socket, ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.socket = socket;
        this.readHandler = new ClientReadHandler(socket.getInputStream());
        this.writeHandler = new ClientWriteHandler(socket.getOutputStream());
        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = "A[" + socket.getInetAddress().getHostAddress()
                + "] P[" + socket.getPort() + "]";
        System.out.println("新客户端连接：" + clientInfo);
    }

    public String getClientInfo() {
        return clientInfo;
    }

    /**
     * 提供给外部关闭掉客户端连接实例
     */
    public void exit() {
        readHandler.exit();
        writeHandler.exit();
        CloseUtils.close(socket);
        System.out.println("客户端已退出：" + socket.getInetAddress() +
                " P:" + socket.getPort());
    }

    /**
     * 发送一条消息给客户端
     * @param str
     */
    public void send(String str) {
        writeHandler.send(str);
    }

    /**
     * 从客户端读取信息并打印到屏幕 -- 线程启动,等待客户端发信息过来
     */
    public void readToPrint() {
        readHandler.start();
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
        private final InputStream inputStream;

        ClientReadHandler(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            super.run();
            try {
                // 得到输入流，用于接收数据
                BufferedReader socketInput = new BufferedReader(new InputStreamReader(inputStream));

                do {
                    // 客户端拿到一条数据
                    String str = socketInput.readLine();
                    if (str == null) {
                        System.out.println("客户端已无法读取数据！");
                        // 退出当前客户端
                        ClientHandler.this.exitBySelf();
                        break;
                    }
                    // 回调,通知到TCPServer
                    clientHandlerCallback.onNewMessageArrived(ClientHandler.this, str);
                } while (!done);
            } catch (Exception e) {
                if (!done) {
                    System.out.println("连接异常断开");
                    ClientHandler.this.exitBySelf();
                }
            } finally {
                // 连接关闭
                CloseUtils.close(inputStream);
            }
        }

        void exit() {
            done = true;
            CloseUtils.close(inputStream);
        }
    }

    /**
     * 本类处理发送一条消息给客户端print出去
     * 采用一个单例线程池处理所有的发送
     */
    class ClientWriteHandler {
        private boolean done = false;
        private final PrintStream printStream;
        private final ExecutorService executorService;

        ClientWriteHandler(OutputStream outputStream) {
            this.printStream = new PrintStream(outputStream);
            this.executorService = Executors.newSingleThreadExecutor();
        }

        void exit() {
            done = true;
            CloseUtils.close(printStream);
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
                this.msg = msg;
            }

            @Override
            public void run() {
                if (ClientWriteHandler.this.done) {
                    return;
                }

                try {
                    ClientWriteHandler.this.printStream.println(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
