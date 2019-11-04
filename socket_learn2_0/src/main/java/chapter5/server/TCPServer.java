package chapter5.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


public class TCPServer {
    private final int port;
    private ClientListener mListener;

    public TCPServer(int port) {
        this.port = port;
    }

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

    public void stop() throws IOException{
        if (mListener != null) {
            mListener.exit();
        }
    }

    /**
     *  TCP监听所有客户端线程 -- 1个主线程构建多个异步线程处理客服端连接
     */
    private static class ClientListener extends Thread {
        private ServerSocket server;
        private boolean done = false;
        // 钩子:存放所有处理客户端的异步线程引用
        private List<ClientHandler> clientHandlers = new ArrayList<ClientHandler> ();


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
                // 客户端构建异步线程
                ClientHandler clientHandler = new ClientHandler(client);
                // 启动线程
                clientHandler.start();
                clientHandlers.add (clientHandler);
            } while (!done);

            System.out.println("服务器已关闭！");
        }

        private void close() throws IOException {
            if(server!=null){
                server.close ();
                server = null;
            }
        }
        private void closeClientHandler() {
            for(ClientHandler clientHandler : clientHandlers){
                try{
                    clientHandler.exit ();
                }catch (Exception e){
                    System.out.println (e.getMessage ());
                }
            }
        }
        /**
         * 外部关闭TCP服务器的方法
         * 先关闭所有客服端连接再关闭服务器
         * @throws IOException
         */
        void exit() throws IOException {
            done = true;
            closeClientHandler();
            close();
        }

    }

    /**
     * 客户端消息处理 : 接收客户端消息并且回送
     */
    private static class ClientHandler extends Thread {
        private Socket socket;
        private boolean flag = true;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            super.run();
            System.out.println("新客户端连接：" + socket.getInetAddress() +
                    " P:" + socket.getPort());

            try {
                // 得到打印流，用于数据输出；服务器回送数据使用
                PrintStream socketOutput = new PrintStream(socket.getOutputStream());
                // 得到输入流，用于接收数据
                BufferedReader socketInput = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));

                do {
                    // 客户端拿到一条数据
                    String str = socketInput.readLine();
                    if ("bye".equalsIgnoreCase(str)) {
                        flag = false;
                        // 回送
                        socketOutput.println("bye");
                    } else {
                        // 打印到屏幕。并回送数据长度
                        System.out.println(str);
                        socketOutput.println("回送：" + str.length());
                    }

                } while (flag);

                socketInput.close();
                socketOutput.close();

            } catch (Exception e) {
                System.out.println("连接异常断开");
            } finally {
                // 连接关闭
                try {
                    close ();
                } catch (IOException e) {
                    e.printStackTrace ();
                }
            }
        }
        private void close() throws IOException {
            if(socket!=null){
                socket.close ();
                System.out.println("客户端已退出：" + socket.getInetAddress() +
                        " P:" + socket.getPort());
                socket = null;
            }
        }

        /**
         * 外部关闭本线程
         * @throws IOException
         */
        void exit() throws IOException {
            flag = true;
            close();
        }
    }
}
