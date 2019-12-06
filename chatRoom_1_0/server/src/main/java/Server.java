import constants.TCPConstants;
import core.IoContext;
import foo.Foo;
import impl.IoSelectorProvider;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Currency;

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

        // 开启收发数据监视
        new MonitorServer().start();

        // 传入回送给客户端的TCP端口
        UDPProvider.start(TCPConstants.PORT_SERVER);

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        String str;
        do {
            str = bufferedReader.readLine();
            if (str == null || str.length() == 0 || "00bye00".equalsIgnoreCase(str)) {
                break;
            }
            // 发送字符串
            tcpServer.broadcast(str);
        } while (true);

        UDPProvider.stop();
        tcpServer.stop();

        ioContext.close();
    }

    static class MonitorServer extends Thread {
        private final long startTime = System.currentTimeMillis();


        static JFrame frm = new JFrame();
        static JTextField t1 = new JTextField(50);
        static JTextField t2 = new JTextField(50);
        static JTextField t3 = new JTextField(50);
        static{
            frm.setSize(300, 200);
            frm.setTitle("发送与接收数据");
            // 设置颜色，这里使用RGB三颜色
            Container c = frm.getContentPane();
            c.setBackground(new Color(200, 200, 255)); // RGB色
            frm.setLayout(null);


            // 创建标签
            JLabel L1 = new JLabel("运行时间: ");
            L1.setBounds(40, 50, 90, 20);
            frm.setResizable(false);
            // 创建文本框
            t1.setBounds(130, 50, 100, 20);


            // 创建标签
            JLabel L2 = new JLabel("当前接收数量: ");
            L2.setBounds(40, 80, 90, 20);
            // 创建文本框
            t2.setBounds(130, 80, 100, 20);

            // 创建标签
            JLabel L3 = new JLabel("当前发收数量: ");
            L3.setBounds(40, 110, 90, 20);
            // 创建文本框
            t3.setBounds(130, 110, 100, 20);


            // 将组件添加到frm中
            frm.add(t1);
            frm.add(L1);
            frm.add(L2);
            frm.add(t2);
            frm.add(L3);
            frm.add(t3);
            frm.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frm.setVisible(true);
        }

        @Override
        public void run() {
            super.run();
            while (true) {
                long nowTime = System.currentTimeMillis();
                t1.setText((nowTime - startTime) + "ms");
                t2.setText(String.valueOf(TCPServer.receiveSize));
                t3.setText(String.valueOf(TCPServer.sendSize));
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        private void clearConsole() {
            try {
                String os = System.getProperty("os.name");
                System.out.println(os);
                if (os.startsWith("Windows")) {
                    Runtime.getRuntime().exec("cls");
                } else {
                    Runtime.getRuntime().exec("clear");
                }
            } catch (Exception exception) {
                //  Handle exception.
            }
        }
    }
}
