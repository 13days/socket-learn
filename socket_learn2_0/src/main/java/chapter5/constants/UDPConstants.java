package chapter5.constants;

public class UDPConstants {
    // 公用头部
    public static byte[] HEADER = new byte[]{7, 7, 7, 7, 7, 7, 7, 7};
    // 服务器固化UDP接收端口,客户端广播端口
    public static int PORT_SERVER = 30201;
    // 客户端回送端口 -- 服务器处理->往该端口发信息 -- 客户端监听该端口的信息
    public static int PORT_CLIENT_RESPONSE = 30202;
}
