package core;


import java.io.IOException;

/**
 * IO上下文
 */
public class IoContext {
    private volatile static IoContext INSTANCE;
    private final IoProvider ioProvider;

    public IoContext(IoProvider ioPrivider) {
        this.ioProvider = ioPrivider;
    }

    public IoProvider getIoProvider(){
        return ioProvider;
    }

    public static IoContext get(){
        return INSTANCE;
    }

    public static StartedBoot setup(){
        return  new StartedBoot();
    }

    public static void close() throws IOException{
        if (INSTANCE != null) {
            INSTANCE.callClose();
        }
    }

    private void callClose() throws IOException{
        ioProvider.close();
    }

    public static class StartedBoot {
        private IoProvider ioProvider;
        private StartedBoot(){

        }

        public StartedBoot ioProvider(IoProvider ioProvider){
            this.ioProvider = ioProvider;
            return this;
        }

        public IoContext start(){

            if (INSTANCE == null) {
               synchronized (IoContext.class){
                   if(INSTANCE==null){
                       INSTANCE = new IoContext(ioProvider);
                   }
               }
            }
            return INSTANCE;
        }
    }
}
