import constants.TCPConstants;
import core.IoContext;
import foo.Foo;
import impl.IoSelectorProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class Server {
    public static void main(String[] args) throws IOException {
        File cacheFile = Foo.getCacheDir("server");
        IoContext ioContext = IoContext.setup().ioProvider(new IoSelectorProvider()).start();

        // TCP监听开启
        TCPServer tcpServer = new TCPServer(TCPConstants.PORT_SERVER, cacheFile);
        boolean isSucceed = tcpServer.start();
        if (!isSucceed) {
            System.out.println("Start TCP server failed!");
            return;
        }

        // 传入回送给客户端的TCP端口
        UDPProvider.start(TCPConstants.PORT_SERVER);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String str;
        do {
            str = bufferedReader.readLine();
            if("00bye00".equalsIgnoreCase(str)){
                break;
            }
            // 发送字符串
            tcpServer.broadcast(str);
        } while (true);

        UDPProvider.stop();
        tcpServer.stop();

        ioContext.close();
    }
}
