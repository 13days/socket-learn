package impl;

import core.IoArgs;
import core.IoProvider;
import core.Receiver;
import core.Sender;
import utils.CloseUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 封装SocketChannel的适配器类
 * @Author 沙漠西瓜
 */
public class SocketChannelAdapter implements Sender, Receiver, Closeable {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final SocketChannel channel;
    private final IoProvider ioProvider;
    private final OnChannelStatusChangeListener listener;
    private IoArgs receiveArgsTemp;

    // 参数事件都回调抽象
    private IoArgs.IoArgsEventListener receiveIoEventListener;
    private IoArgs.IoArgsEventListener sendIoEventListener;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider, OnChannelStatusChangeListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventListener listener) {
        // 修改接收事件回调实现--类似策略模式
        receiveIoEventListener = listener;
    }

    @Override
    public boolean receiveAsync(IoArgs args) throws IOException {
        if(isClosed.get()){
            throw new IOException("Current channel is closed!");
        }
        this.receiveArgsTemp = args;
        // 注册到ioProvider中
        return ioProvider.registerInput(channel, inputCallback);
    }


    @Override
    public boolean sendAsync(IoArgs args, IoArgs.IoArgsEventListener listener) throws IOException {
        if(isClosed.get()){
            throw new IOException("Current channel is closed!");
        }
        // 修改发送事件回调实现--类似策略模式
        sendIoEventListener = listener;
        // 当前发送的数据附加到回调中
        outputCallback.setAttach(args);
        // 注册到ioProvider中
        return ioProvider.registerOutput(channel, outputCallback);
    }

    @Override
    public void close() throws IOException {
        if(isClosed.compareAndSet(false,true)){
            // 解除注册回调
            ioProvider.unRegisterInput(channel);
            ioProvider.unRegisterOutput(channel);
            // 关闭
            CloseUtils.close(channel);
            // 回调当前的channel已经关闭
            listener.onChannelClosed(channel);
        }
    }

    // todo 消息重复处理
    // private boolean runed = false;
    // 输入回调具体实现
    private IoProvider.HandleInputCallback inputCallback = new IoProvider.HandleInputCallback() {
        @Override
        protected void canProviderInput() {
            if(isClosed.get()){
                return;
            }

            // todo 消息重复处理
//            if(runed){
//                return;
//            }
//            runed = true;
//            try {
//                Thread.sleep(5000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }

            IoArgs args = receiveArgsTemp;
            IoArgs.IoArgsEventListener listener = SocketChannelAdapter.this.receiveIoEventListener;
            listener.onStarted(args);

            try {
                // 具体读取操作
                if(args.readFrom(channel)>0){
                    // 读取完成回调
                    listener.onCompleted(args);
                }else{
                    throw new IOException("Can read any data!");
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    // 输出回调具体实现
    private IoProvider.HandleOutputCallback outputCallback = new IoProvider.HandleOutputCallback() {
        @Override
        protected void canProviderOutput(Object object) {
            if(isClosed.get()){
                return;
            }
            IoArgs args = getAttach();
            IoArgs.IoArgsEventListener listener = sendIoEventListener;
            listener.onStarted(args);

            try {
                // 具体读取操作
                if(args.writeTo(channel)>0){
                    // 读取完成回调
                    listener.onCompleted(args);
                }else{
                    throw new IOException("Can write any data!");
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }


            // TODO
            sendIoEventListener.onCompleted(null);
        }
    };


    /**
     * channel状态变化的回调接口
     */
    public interface OnChannelStatusChangeListener{
        void onChannelClosed(SocketChannel channel);
    }
}
