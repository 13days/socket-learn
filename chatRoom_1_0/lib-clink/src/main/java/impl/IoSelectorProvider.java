package impl;

import core.IoProvider;
import utils.CloseUtils;

import java.io.IOException;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
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
                new IoProviderThreadFactory("IoProvider-Output-Thread-"));

        // 读写selector监听异步线程在构造的时候就启动了
        startRead();
        startWrite();
    }

    /**
     * 起一个异步线程来接受读连接
     */
    private void startRead() {
        Thread thread = new SelectorThread("Clink IoSelectorProvider ReadSelector Thread",
                inRegInput, readSelector, inputCallbackMap, inputHandlePool,SelectionKey.OP_READ,isClosed);
        thread.start();
    }

    /**
     * 起来一个异步线程后处理写
     */
    private void startWrite() {
        Thread thread = new SelectorThread("Clink IoSelectorProvider WriteSelector Thread",
                inRegOutput, writeSelector, outputCallbackMap, outputHandlePool,SelectionKey.OP_WRITE,isClosed);
        thread.start();
    }

    @Override
    public boolean registerInput(SocketChannel channel, HandleInputCallback callback) {
        return registerSelection(channel, readSelector, SelectionKey.OP_READ, inRegInput, inputCallbackMap, callback)!=null;
    }

    @Override
    public boolean registerOutput(SocketChannel channel, HandleOutputCallback callback) {
        return registerSelection(channel, writeSelector, SelectionKey.OP_WRITE, inRegOutput, outputCallbackMap, callback)!=null;
    }

    @Override
    public void unRegisterInput(SocketChannel channel) {
        unRegisterSelection(channel, readSelector, inputCallbackMap, inRegInput);
    }

    @Override
    public void unRegisterOutput(SocketChannel channel) {
        unRegisterSelection(channel, writeSelector, outputCallbackMap, inRegOutput);
    }

    @Override
    public void close() throws IOException {
        if (isClosed.compareAndSet(false, true)) {
            // 关闭线程池
            inputHandlePool.shutdown();
            outputHandlePool.shutdown();

            // 情况key-runnable
            inputCallbackMap.clear();
            outputCallbackMap.clear();

            // 直接关闭,不需要唤醒
            CloseUtils.close(readSelector, writeSelector);
        }
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
    private static void handleSelection(SelectionKey selectionKey,
                                        int keyOps, HashMap<SelectionKey, Runnable> map,
                                        ExecutorService pool, AtomicBoolean locker) {
        // 重点
        // 取消继续对keyOps的监听
        // 重点~~~异步丢任务了,收到就要马上取消丢读的监听,否则会一直读就绪,读到很多null,导致客户端收到null误以为连接断开
        synchronized (locker){
            // 对selectionKey的修改实际上是对列队的修改
            try {
                selectionKey.interestOps(selectionKey.readyOps() & ~keyOps);
            }catch (CancelledKeyException e){
                return;
            }
        }
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
                // 唤醒当前的selector，让selector不处于select()状态,才可以操作selector
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
            } catch (ClosedChannelException|CancelledKeyException|ClosedSelectorException e) {
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
    private static void unRegisterSelection(SocketChannel channel,
                                            Selector selector, Map<SelectionKey, Runnable> map,AtomicBoolean locker){
        synchronized (locker) {
            locker.set(true);
            selector.wakeup();
            try{
                if (channel.isRegistered()) {
                    SelectionKey key = channel.keyFor(selector);
                    if (key != null) {
                        // 取消所有监听-->其实这里只有一个
                        key.cancel();
                        map.remove(key);
                    }
                }
            }finally {
                locker.set(false);
                try {
                    locker.notify();
                }catch (Exception ignore){

                }
            }
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


    /**
     * 读写线程
     */
    private static class SelectorThread extends Thread{
        private final  AtomicBoolean locker;
        private final Selector selector;
        private final HashMap<SelectionKey, Runnable> callMap;
        private final ExecutorService pool;
        private final int keyOps;
        private final AtomicBoolean isClosed;

        public SelectorThread(String name,
                              AtomicBoolean locker,
                              Selector selector,
                              HashMap<SelectionKey, Runnable> callMap,
                              ExecutorService pool, int keyOps, AtomicBoolean isClosed) {
            super(name);
            this.locker = locker;
            this.selector = selector;
            this.callMap = callMap;
            this.pool = pool;
            this.keyOps = keyOps;
            this.isClosed = isClosed;
            this.setPriority(Thread.MAX_PRIORITY);
        }

        @Override
        public void run() {
            super.run();
            AtomicBoolean locker = this.locker;
            AtomicBoolean isClosed = this.isClosed;
            Selector selector = this.selector;
            HashMap<SelectionKey, Runnable> callMap = this.callMap;
            ExecutorService pool = this.pool;
            int keyOps = this.keyOps;
            while (!isClosed.get()) {
                try {
                    // select()不阻塞的原因,有channel没有accept,返回0
                    if (selector.select() == 0) {
                        // 等待是否完成注册
                        waitSelection(locker);
                        continue;
                    }else if(locker.get()){
                        waitSelection(locker);
                    }

                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while(iterator.hasNext()){
                        SelectionKey selectionKey = iterator.next();
                        if(selectionKey.isValid()){
                            handleSelection(selectionKey, keyOps, callMap, pool, locker);
                        }
                        iterator.remove();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (ClosedSelectorException ignore){
                    break;
                }
            }
        }
    }
}
