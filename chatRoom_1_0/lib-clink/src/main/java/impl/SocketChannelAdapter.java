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

    // 参数事件都回调抽象
    private IoArgs.IoArgsEventProcessor receiveIoEventProcessor;
    private IoArgs.IoArgsEventProcessor sendIoEventProcessor;

    public SocketChannelAdapter(SocketChannel channel, IoProvider ioProvider, OnChannelStatusChangeListener listener) throws IOException {
        this.channel = channel;
        this.ioProvider = ioProvider;
        this.listener = listener;

        channel.configureBlocking(false);
    }

    @Override
    public void setReceiveListener(IoArgs.IoArgsEventProcessor processor) {
        receiveIoEventProcessor = processor;
    }

    @Override
    public boolean postReceiveAsync() throws IOException {
        if(isClosed.get()){
            throw new IOException("Current channel is closed!");
        }
        // 注册到ioProvider中
        return ioProvider.registerInput(channel, inputCallback);
    }

    @Override
    public void setSenderListener(IoArgs.IoArgsEventProcessor processor) {
        sendIoEventProcessor = processor;
    }

    @Override
    public boolean postSenderAsync() throws IOException {
        if(isClosed.get()){
            throw new IOException("Current channel is closed!");
        }
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

            IoArgs.IoArgsEventProcessor processor = SocketChannelAdapter.this.receiveIoEventProcessor;
            IoArgs args = processor.provideIoArgs();

            try {
                if(args == null){
                    processor.onConsumeFailed(null, new IOException("提供的IoArgs是空的"));
                } else if(args.readFrom(channel)>0){
                    // 关闭异常java.io.IOException: 远程主机强迫关闭了一个现有的连接。
                    // 读取完成回调,消费成功
                    processor.onConsumeCompleted(args);
                }else{
                    // 消费失败
                    processor.onConsumeFailed(args,new IOException("Can read any data!"));
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }
        }
    };

    // 输出回调具体实现
    private IoProvider.HandleOutputCallback outputCallback = new IoProvider.HandleOutputCallback() {
        @Override
        protected void canProviderOutput() {
            if(isClosed.get()){
                return;
            }
            IoArgs.IoArgsEventProcessor processor = sendIoEventProcessor;
            IoArgs args = processor.provideIoArgs();

            try {
                // 具体读取操作
                if(args.writeTo(channel)>0){
                    // 读取完成回调,消费完成
                    processor.onConsumeCompleted(args);
                }else{
                    // 消费失败,args==0
                    processor.onConsumeFailed(args, new IOException("Can write any data!"));
                }
            } catch (IOException e) {
                CloseUtils.close(SocketChannelAdapter.this);
            }

        }
    };


    /**
     * channel状态变化的回调接口
     */
    public interface OnChannelStatusChangeListener{
        void onChannelClosed(SocketChannel channel);
    }
}
