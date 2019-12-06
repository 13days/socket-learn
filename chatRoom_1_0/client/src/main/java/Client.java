

import bean.ServerInfo;
import box.FileSendPacket;
import core.IoContext;
import foo.Foo;
import impl.IoSelectorProvider;

import java.io.*;

public class Client {
    public static void main(String[] args) throws IOException {
        File cachePath = Foo.getCacheDir("client");
        IoContext.setup().ioProvider(new IoSelectorProvider()).start();

        ServerInfo info = UDPSearcher.searchServer(10000);
        System.out.println("Server:" + info);

        if (info != null) {
            TCPClient tcpClient = null;
            try {
                tcpClient = TCPClient.startWith(info,cachePath);
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
            if (str==null||str.length()==0||"00bye00".equalsIgnoreCase(str)) {
                break;
            }
            // 发送到服务器
            // --f url
            if(str.startsWith("--f")){
                String[] array = str.split(" ");
                if(array.length>=2){
                    String filePath = array[1];
                    File file = new File(filePath);
                    if(file.exists() && file.isFile()){
                        FileSendPacket packet = new FileSendPacket(file);
                        tcpClient.send(packet);
                        continue;
                    }
                }
            }
            // 发送字符串
            tcpClient.send(str);
            // todo 测试消息粘包
            StringBuffer sb = new StringBuffer();
            for(int x = 0; x<1000; x++){
                sb.append(x);
            }
            for(int i=0; i<10000; i++){
                tcpClient.send(sb.toString());
            }
        } while (true);
    }
}
