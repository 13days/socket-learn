package impl;

import com.sun.security.ntlm.Server;
import core.IoProvider;
import utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IoSelectorProvider implements IoProvider {
    private final AtomicBoolean isClosed = new AtomicBoolean(false);

    // 是否处于某个过程,作为锁
    private final AtomicBoolean inRegInput = new AtomicBoolean(false);
    private final AtomicBoolean inRegOutput = new AtomicBoolean(false);

    private final Selector readSelector;
    private final Selector writeSelector;

    private final HashMap<SelectionKey, Runnable> inputCallbackMap = new HashMap<>();
    private final HashMap<SelectionKey, Runnable> outputCallbackMap = new HashMap<>();

    private final ExecutorService inputHandlePool;
    private final ExecutorService outputHandlePool;

    public IoSelectorProvider() throws IOException {
        this.readSelector = Selector.open();
        this.writeSelector = Selector.open();

        this.inputHandlePool = Executors.newFixedThreadPool(4,
                new IoProviderThreadFactory("IoProvider-Input-Thread-"));
        this.outputHandlePool = Executors.newFixedThreadPool(4,
                new IoProviderThreadFactory("IoProvider-Outuput-Thread-"));

        // 读写selector监听异步线程在构造的时候就启动了
        startRead();
        startWrite();
    }

    /**
     * 起一个异步线程来接受读连接
     */
    private void startRead() {
        Thread thread = new Thread("Clink IoSelectorProvider ReadSelector Thread"){
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        // select()不阻塞的原因,有channel没有accept,返回0
                        if (readSelector.select() == 0) {
                            // 等待是否完成注册
                            waitSelection(inRegInput);
                            continue;
                        }

                        Set<SelectionKey> selectionKeys = readSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                // todo 测试ServerSocketChannel.accept() --> 客户端时SocketChannel
                                // ServerSocketChannel channel = (ServerSocketChannel)selectionKey.channel();
                                // channel.accept(); ==> socketChannel
                                // 处理每一个selectionKey
                                // 异步的,马上返回的,读取会未执行完,会继续被捕获到
                                // readSelector.select()会一直捕获到,所以需要取消对这个连接的读的监听,完成后再加回来
                                Class<? extends SelectableChannel> aClass = selectionKey.channel().getClass();
                                System.out.println("channel的类型为:"+aClass.getName());
                                handleSelection(selectionKey, SelectionKey.OP_READ, inputCallbackMap, inputHandlePool);
                            }
                        }

                        // System.out.println("收到消息个数:"+selectionKeys.size());

                        selectionKeys.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    /**
     * 起来一个异步线程后处理写
     */
    private void startWrite() {
        Thread thread = new Thread("Clink IoSelectorProvider WriteSelector Thread") {
            @Override
            public void run() {
                while (!isClosed.get()) {
                    try {
                        if (writeSelector.select() == 0) {
                            waitSelection(inRegOutput);
                            continue;
                        }

                        Set<SelectionKey> selectionKeys = writeSelector.selectedKeys();
                        for (SelectionKey selectionKey : selectionKeys) {
                            if (selectionKey.isValid()) {
                                handleSelection(selectionKey, SelectionKey.OP_WRITE, outputCallbackMap, outputHandlePool);
                            }
                        }
                        selectionKeys.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }


    /**
     * 等待
     * @param locker
     */
    private static void waitSelection(final AtomicBoolean locker) {
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (locker) {
            if (locker.get()) {
                try {
                    locker.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 异步处理每个接受到的连接
     * @param selectionKey
     * @param keyOps
     * @param map
     * @param pool
     */
    private static void handleSelection(SelectionKey selectionKey, int keyOps, HashMap<SelectionKey, Runnable> map, ExecutorService pool) {
        // 重点
        // 取消继续对keyOps的监听
        // 重点~~~异步丢任务了,收到就要马上取消丢读的监听,否则会一直读就绪,读到很多null,导致客户端收到null误以为连接断开
        selectionKey.interestOps(selectionKey.readyOps() & ~keyOps);
        Runnable runnable = null;
        try {
            runnable = map.get(selectionKey);
        }catch (Exception ignored){

        }
        // 真正处理一个连接的动作的地方,也就是一个handlerCallback
        if(runnable != null && !pool.isShutdown()){
            // 异步调度
            pool.execute(runnable);
        }
    }


    @Override
    public boolean registerInput(SocketChannel channel, HandleInputCallback callback) {
        return registerSelection(channel, readSelector, SelectionKey.OP_READ, inRegInput, inputCallbackMap, callback)!=null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleOutputCallback callback) {
        return registerSelection(channel, readSelector, SelectionKey.OP_WRITE, inRegOutput, outputCallbackMap, callback)!=null;
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallbackMap);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, outputCallbackMap);
    }

    /**
     * 注册一个SocketChanel到selector中,发挥selector多路复用的优势,监听该channel的事件,channel的回调存在map里
     * 线程池会统一处理这些回调
     * @param channel
     * @param selector
     * @param registerOps
     * @param lock
     * @param map
     * @param runnable
     * @return
     */
    private static SelectionKey registerSelection(SocketChannel channel, Selector selector, int registerOps, AtomicBoolean lock,
                                                  Map<SelectionKey, Runnable> map, Runnable runnable){
        synchronized(lock){
            // 设置锁定状态
            lock.set(true);

            try{
                // 唤醒当前的selector，让selector不处于select()状态
                selector.wakeup();

                SelectionKey key = null;
                // 查询是否已经注册过
                if(channel.isRegistered()){
                    key = channel.keyFor(selector);
                    if(key != null){
                        // 添加新的监听
                        key.interestOps(key.interestOps() | registerOps);
                        // 注册回调
                        map.put(key, runnable);
                    }
                }
                if(key == null){
                    key = channel.register(selector, registerOps);
                    // 注册回调
                    map.put(key, runnable);
                }

                return key;
            } catch (ClosedChannelException e) {
                return null;
            } finally {
                // 接触锁定
                lock.set(false);
                try {
                    // 通知
                    lock.notify();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 解除注册
     * @param channel
     * @param selector
     * @param map
     */
    private static void unRegisterSelection(SocketChannel channel, Selector selector, Map<SelectionKey, Runnable> map){
        if(channel.isRegistered()){
            SelectionKey key = channel.keyFor(selector);
            if(key!=null){
                // 取消所有监听-->其实这里只有一个
                key.channel();
                map.remove(key);
                selector.wakeup();
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            // 关闭线程池
            inputHandlePool.shutdown();
            outputHandlePool.shutdown();

            // 情况key-runnbale
            inputCallbackMap.clear();
            outputCallbackMap.clear();

            // 唤醒阻塞
            readSelector.wakeup();
            writeSelector.wakeup();

            CloseUtils.close(readSelector, writeSelector);
        }
    }


    /**
     * 线程构造器
     */
    static class IoProviderThreadFactory implements ThreadFactory {
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public IoProviderThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }
}
