package chapter3.chapter_3_2;


import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class UDPSearcher {
    private static final int LISTEN_PORT = 30000;

    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println ("UDPSearcher Started...");

        Listener listen = listen ();
        sentBroadcast ();

        // 读取任意信息后退出
        System.in.read ();
        List<Device> deviceList = listen.getDevicesAndClose();
        for(Device device : deviceList){
            System.out.println ("Device: " + device.toString ());
        }
        // 完成
        System.out.println ("UDPSearcher Finished.");
    }

    private static Listener listen() throws InterruptedException {
        System.out.println("UDPSearcher start listen.");
        CountDownLatch countDownLatch = new CountDownLatch (1);
        Listener listener = new Listener (LISTEN_PORT,countDownLatch);
        // 异步线程启动
        listener.start ();

        countDownLatch.await ();
        return listener;
    }

    /**
     * 发送广播
     */
    private static void sentBroadcast() throws IOException {
        System.out.println ("UDPSearcher sentBroadcast Started...");

        // 作为搜索方,让系统分配一个端口
        DatagramSocket ds = new DatagramSocket ();

        // 构建一份请求数据
        String requestData = MessageCreator.buildWithPort (LISTEN_PORT);
        byte[] requestDataBytes = requestData.getBytes ();
        // 直接构建packet
        DatagramPacket requestPacket = new DatagramPacket (requestDataBytes, requestDataBytes.length);

        // 20000端口,广播地址
        requestPacket.setAddress (InetAddress.getByName ("255.255.255.255"));
        requestPacket.setPort (20000);

        // 发送数据
        ds.send (requestPacket);

        // 完成
        System.out.println ("UDPSearcher sentBroadcast Finished.");
        ds.close ();
    }

    /**
     * 设备信息
     */
    private static class Device{
        final int port;
        final String ip;
        final String sn;

        public Device(int port, String ip, String sn) {
            this.port = port;
            this.ip = ip;
            this.sn = sn;
        }

        @Override
        public String toString() {
            return "Device{" +
                    "port=" + port +
                    ", ip='" + ip + '\'' +
                    ", sn='" + sn + '\'' +
                    '}';
        }
    }

    /**
     * 监听回送消息
     * 若有多个设备被广播到并且回送消息到本线程的监听端口
     * 本线程将收到多个设备的信息
     */
    private static class Listener extends Thread{
        private final int listenPort; // 本线程监听的端口
        private final CountDownLatch countDownLatch;
        private final List<Device> deviceList = new ArrayList<Device> ();
        private boolean done = false;
        private DatagramSocket ds = null;

        public Listener(int listenPort, CountDownLatch countDownLatch) {
            this.listenPort = listenPort;
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            super.run ();

            // 通知启动
            countDownLatch.countDown ();
            try{
                ds = new DatagramSocket (listenPort);

                // 循环监听
                while(!done){
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

                    String sn = MessageCreator.parseSn (data);
                    if(sn!=null){
                        Device device = new Device (port, ip, sn);
                        deviceList.add (device);
                    }
                }
            }catch (Exception e){

            }finally {
                close();
            }
            System.out.println("UDPSearcher listener finished.");
        }

        private void close(){
            if(ds!=null){
                ds.close ();
                ds = null;
            }
        }

        /**
         * 提供结束
         */
        public List<Device> getDevicesAndClose(){
            done = true;
            close(); // 会抛出异常，打断阻塞
            return deviceList;
        }
    }
}

