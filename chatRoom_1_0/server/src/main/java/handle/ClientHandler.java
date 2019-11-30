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

public class ClientHandler extends Connector{
    private final ClientHandlerCallback clientHandlerCallback;

    // 客户端信息
    private final String clientInfo;

    /**
     * 构建客户端处理实例
     * @param socketChannel
     * @param clientHandlerCallback
     * @throws IOException
     */
    public ClientHandler(SocketChannel socketChannel, ClientHandlerCallback clientHandlerCallback) throws IOException {
        this.clientHandlerCallback = clientHandlerCallback;
        this.clientInfo = socketChannel.getRemoteAddress().toString();

        System.out.println("新客户端连接：" + clientInfo);
        setup(socketChannel);
    }


    /**
     * 提供给外部关闭掉客户端连接实例
     */
    public void exit() {
        CloseUtils.close(this);
        System.out.println("客户端已退出:"+clientInfo);
    }

    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        exitBySelf();
    }

    /**
     * 内部关闭
     */
    private void exitBySelf() {
        exit();
        clientHandlerCallback.onSelfClosed(this);
    }

    @Override
    protected void onReceiveNewMessage(String str) {
        super.onReceiveNewMessage(str);
        clientHandlerCallback.onNewMessageArrived(this, str);
    }

    public String getClientInfo() {
        return this.clientInfo;
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

}
