import bean.ServerInfo;
import core.IoContext;
import foo.Foo;
import impl.IoSelectorProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClientTest {
    private static boolean done = false;

    public static void main(String[] args) throws IOException, InterruptedException {
        File cachePath = Foo.getCacheDir("client");
        IoContext.setup().ioProvider(new IoSelectorProvider()).start();

        ServerInfo info =  UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);
        if(info == null){
            return;
        }

        // 当前连接数量
        List<TCPClient> tcpClients = new ArrayList<>();
        for(int i=0; i<1000; i++){
            try {
                TCPClient tcpClient = TCPClient.startWith(info, cachePath);
                tcpClients.add(tcpClient);
                if(tcpClient == null){
                    throw new NullPointerException();
                }
            } catch (IOException | NullPointerException e) {
                System.out.println("连接异常");
            }

            // 服务器队列默认最多接受50个连接在列队里,超过部分会抛异常
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        

        // 读取键盘命令,发送数据
        System.in.read();
        System.out.println("准备压测发生消息给服务器了");


        Runnable runnable = () -> {
            while (!done){
                for(TCPClient tcpClient : tcpClients){
                    tcpClient.send("Hello ~~");
                }

                // 每隔一秒钟所有客户端向服务器发送消息
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();


        // 读取键盘命令关闭发送
        System.in.read();
        System.out.println("压测结束,准备断开所有连接");
        done = true;
        // 等待线程完成
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 客户端结束
        for(TCPClient tcpClient : tcpClients){
            tcpClient.exit();
        }

        IoContext.close();
    }
}
