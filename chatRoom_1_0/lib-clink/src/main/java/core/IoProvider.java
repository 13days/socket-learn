package core;

import java.io.Closeable;
import java.nio.channels.SocketChannel;

/**
 * Io基础接口,读写注册,注销,提供回调
 */
public interface IoProvider extends Closeable {
    boolean registerInput(SocketChannel channel, HandleInputCallback  callback);
    boolean registerOutput(SocketChannel channel, HandleOutputCallback callback);
    void unRegisterInput(SocketChannel channel);
    void unRegisterOutput(SocketChannel channel);


    /**
     * 输入回调
     */
    abstract class HandleInputCallback implements Runnable{
        @Override
        public final void run() {
            canProviderInput();
        }

        protected abstract void canProviderInput();
    }

    /**
     * 输出回调
     */
    abstract class HandleOutputCallback implements Runnable{
        private Object attach;

        @Override
        public final void run(){
            canProviderOutput(attach);
        }

        public final void setAttach(Object attach){
            this.attach = attach;
        }

        protected abstract void canProviderOutput(Object object);
    }
}
