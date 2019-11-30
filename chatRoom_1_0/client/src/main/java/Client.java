

import bean.ServerInfo;
import core.IoContext;
import impl.IoSelectorProvider;

import java.io.*;
import java.net.Socket;

public class Client {
    public static void main(String[] args) throws IOException {
        IoContext.setup().ioProvider(new IoSelectorProvider()).start();

        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        TCPClient tcpClient = null;
        if (info != null) {
            try {
                tcpClient = TCPClient.startWith(info);
                if(tcpClient==null){
                    return;
                }
                write(tcpClient);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if(tcpClient!=null){
                    tcpClient.exit();
                }
            }
        }

        IoContext.close();
    }

    private static void write(TCPClient tcpClient) throws IOException {
        // 构建键盘输入流
        InputStream in = System.in;
        BufferedReader input = new BufferedReader(new InputStreamReader(in));

        do {
            // 键盘读取一行
            String str = input.readLine();
            // 发送到服务器
            tcpClient.send(str);
            // todo 测试消息粘包
            tcpClient.send(str);
            tcpClient.send(str);
            tcpClient.send(str);
            if ("00bye00".equalsIgnoreCase(str)) {
                break;
            }
        } while (true);
    }
}
