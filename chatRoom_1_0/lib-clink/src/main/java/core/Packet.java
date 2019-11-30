package core;

import java.io.Closeable;

/**
 * 公共数据的封装
 * 提供了类型以及基本的长度的定义
 */
public abstract class Packet implements Closeable {
    protected byte type;
    protected int length;

    public byte type(){
        return this.type;
    }

    public int length(){
        return this.length;
    }
}
