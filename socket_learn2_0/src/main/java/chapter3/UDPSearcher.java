package chapter3;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPSearcher {
    public static void main(String[] args) throws IOException {
        System.out.println ("UDPSearcher Started...");

        // 作为搜索方,让系统分配一个端口
        DatagramSocket ds = new DatagramSocket ();

        // 构建一份请求数据
        String requestData = "Hello World!";
        byte[] requestDataBytes = requestData.getBytes ();
        // 本机20000端口
        DatagramPacket requestPacket = new DatagramPacket (requestDataBytes, requestDataBytes.length);
        requestPacket.setAddress (InetAddress.getLocalHost ());
        requestPacket.setPort (20000);

        // 发送数据
        ds.send (requestPacket);

        // 构建接收实体
        final byte[] buf = new byte[512];
        DatagramPacket receivePack = new DatagramPacket (buf, buf.length);

        // 接收
        ds.receive (receivePack);

        // 打印接收到的信息个发送者的信息
        // 发送者的IP地址
        String ip = receivePack.getAddress ().getHostAddress ();
        int port = receivePack.getPort ();
        int dataLen = receivePack.getLength ();
        String data = new String(receivePack.getData (), 0 , dataLen);
        System.out.println ("UDPSearcher recevie from ip:"+ip+"\tprot:"+port+"\tdata:"+data);


        // 完成
        System.out.println ("UDPSearcher Finished.");
        ds.close ();
    }
}
