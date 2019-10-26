package chapter3.chapter_3_2;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.UUID;

public class UDPProvider {
    public static void main(String[] args) throws IOException {
        // 生成唯一ID
        String sn = UUID.randomUUID ().toString ();
        Provider provider = new Provider (sn);
        provider.start ();

        // 按键退出
        System.in.read ();
        provider.exit ();
    }
    private static class Provider extends Thread{
        private final String sn;
        private boolean done = false;
        private DatagramSocket ds = null;

        public Provider(String sn) {
            super();
            this.sn = sn;
        }

        @Override
        public void run() {
            super.run ();
            System.out.println ("UDPProvider Started...");

            try {
                // 监听20000端口
                ds = new DatagramSocket (20000);

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
                    System.out.println ("UDPProvider recevie from ip:"+ip+"\tprot:"+port+"\tdata:"+data);


                    // 解析端口号,对方的监听端口号藏在发送数据里
                    int responsePort = MessageCreator.parsePort (data);
                    if(responsePort!=-1){
                        // 构建一份回送数据
                        String responseData = MessageCreator.buildWithSn (sn);
                        byte[] responseDataBytes = responseData.getBytes ();
                        // 直接根据发送者构建一份回送信息,InetAddress.getLocalHost ()+20000端口发送给->receivePack.getAddress ()+receivePack.getPort ()
                        DatagramPacket responsePacket = new DatagramPacket (responseDataBytes, responseDataBytes.length,
                                receivePack.getAddress (), responsePort);

                        ds.send (responsePacket);
                    }
                }
            }catch (Exception e){

            }finally {
                close();
            }
            // 完成
            System.out.println ("UDPProvider Finished.");
        }

        private void close(){
            if(ds!=null){
                ds.close();
                ds = null;
            }
        }

        /**
         * 提供结束
         */
        void exit(){
            done = true;
            close ();
        }
    }
}
