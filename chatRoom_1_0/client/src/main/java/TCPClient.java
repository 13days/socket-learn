

import bean.ServerInfo;
import core.Connector;
import utils.CloseUtils;

import javax.jws.soap.SOAPBinding;
import java.io.*;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.channels.SocketChannel;

/**
 * 可操作的TCP连接
 */
public class TCPClient extends Connector {
    /**
     * 私有构造方法
     * @throws IOException
     */
    private TCPClient(SocketChannel socketChannel) throws IOException {
        setup(socketChannel);
    }

    /**
     * 外部退出提供
     */
    public void exit(){

        CloseUtils.close(this);
    }


    @Override
    public void onChannelClosed(SocketChannel channel) {
        super.onChannelClosed(channel);
        System.out.println("连接已关闭");
    }

    /**
     * 获取本类的实例
     * @param info
     * @return
     * @throws IOException
     */
    public static TCPClient startWith(ServerInfo info) throws IOException {
        SocketChannel socketChannel = SocketChannel.open();

        // 连接本地，端口2000；超时时间3000ms
        socketChannel.connect(new InetSocketAddress(Inet4Address.getByName(info.getAddress()), info.getPort()));

        System.out.println("已发起服务器连接，并进入后续流程～");
        System.out.println("客户端信息：" + socketChannel.getLocalAddress().toString());
        System.out.println("服务器信息：" + socketChannel.getRemoteAddress().toString());

        try {

            return new TCPClient(socketChannel);
        } catch (Exception e) {
            System.out.println("连接异常");
            CloseUtils.close(socketChannel);
        }
        return null;
    }

}
