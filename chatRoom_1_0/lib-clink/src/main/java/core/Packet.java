package core;

import java.io.Closeable;
import java.io.IOException;

/**
 * 公共数据的封装
 * 提供了类型以及基本的长度的定义
 */
public abstract class Packet<T extends Closeable> implements Closeable {
    protected byte type;
    protected long length;
    private T stream;

    public byte type(){
        return this.type;
    }

    public long length(){
        return this.length;
    }

    public final T open() {
        if(stream==null){
            stream = createStream();
        }
        return stream;
    }

    @Override
    public final void close() throws IOException {
        if(stream!=null){
            closeStream(stream);
            stream = null;
        }
    }

    /**
     * 工厂方法
     * @return
     */
    protected abstract T createStream();

    protected void closeStream(T stream) throws IOException {
        stream.close();
    }

}
