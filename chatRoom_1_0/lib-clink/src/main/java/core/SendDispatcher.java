package core;

import java.io.Closeable;

/**
 * 发送数据的调度
 * 缓存所有许哟啊发哦是那个的数据,通过队列对数据进行发送
 * 并且再发送数据时,实现对数据的基本包装
 */
public interface SendDispatcher extends Closeable {

    /**
     * 发送一份份数据
     * @param packet
     */
    void send(SendPacket packet);

    /**
     * 取消发送数据
     * @param packet
     */
    void cancel(SendPacket packet);
}
