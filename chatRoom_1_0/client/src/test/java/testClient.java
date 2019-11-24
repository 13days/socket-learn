import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Set;

public class testClient {
    public static void main(String[] args) throws IOException, InterruptedException {
        Socket socket = new Socket("localhost",8888);
        socket.close();
    }
}
