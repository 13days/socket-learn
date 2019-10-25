package chapter_2_3;

import java.io.*;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Client {
    public static final String BYE = "bye";
    public static void main(String [] args) throws IOException {
        Socket socket = new Socket ();
        // 超时时间
        socket.setSoTimeout (3000);

        // 连接本地,端口2000,超时时间3000ms
        socket.connect (new InetSocketAddress (Inet4Address.getLocalHost (), 2000), 3000);

        System.out.println ("已发起服务器连接,并进入后续流程~");
        System.out.println ("客户端信息: "+socket.getLocalAddress ()+" P:"+socket.getLocalPort ());
        System.out.println ("服务端端信息: "+socket.getInetAddress ()+" P:"+socket.getPort ());

        try {
            // 发送数据
            todo (socket);
        }catch (Exception E){
            System.out.println ("异常关闭");
        }finally {
            // 释放资源
            socket.close ();
            System.out.println ("客户端已经退出~");
        }
    }

    private static void todo(Socket client) throws IOException{
        // 构建键盘输入流
        InputStream in = System.in;
        BufferedReader input =  new BufferedReader (new InputStreamReader (in));

        // 得到Socket的输出流,并转换为打印流
        OutputStream outputStream = client.getOutputStream ();
        PrintStream socketPrintStream = new PrintStream (outputStream);

        // 得到Socket的输入流
        InputStream inputStream = client.getInputStream ();
        BufferedReader socketBufferedReader = new BufferedReader (new InputStreamReader (inputStream));


        boolean flag = true;
        do {
            // 键盘读取一行
            String str = input.readLine ();
            socketPrintStream.println (str);

            // 从服务器读取一行
            String echo = socketBufferedReader.readLine ();
            if(BYE.equalsIgnoreCase (echo)){
                flag = false;
            }else{
                System.out.println (echo);
            }
        }while(flag);
        socketPrintStream.close ();
        socketBufferedReader.close ();
    }
}
