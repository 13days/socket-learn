import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Set;

public class testNio {

    public static void main(String[] args) throws IOException, InterruptedException {

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress("localhost", 8888));
        serverSocketChannel.configureBlocking(false);
        Selector selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        boolean isRun = true;
        while(isRun){
            int keyCount = selector.select();
            Set<SelectionKey> set1 = selector.keys();
            Set<SelectionKey> set2 = selector.selectedKeys();
            System.out.println("keyCount="+keyCount);
            System.out.println("set1="+set1.size());
            System.out.println("set2="+set2.size());
            System.out.println();

            Iterator<SelectionKey> iterator = set2.iterator();
            while (iterator.hasNext()){
                SelectionKey key = iterator.next();
                ServerSocketChannel channel = (ServerSocketChannel)key.channel();
                // channel.accept();
                iterator.remove();
            }
        }
        serverSocketChannel.close();
    }
}
