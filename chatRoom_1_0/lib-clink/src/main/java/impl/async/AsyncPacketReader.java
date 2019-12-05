package impl.async;

import core.Frame;
import core.IoArgs;
import core.SendPacket;
import core.ds.BytePriorityNode;
import frames.AbsSendPacketFrame;
import frames.CancelSendFrame;
import frames.SendEntityFrame;
import frames.SendHeaderFrame;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Packet转换为帧序列，并进行读取发送的封装管理类
 */
public class AsyncPacketReader implements Closeable {
    private final PacketProvider provider;
    private volatile IoArgs args = new IoArgs();

    // Frame队列
    private volatile BytePriorityNode<Frame> node;
    private volatile int nodeSize = 0;

    // 1,2,3.....255
    private AtomicInteger lastIdentifier = new AtomicInteger(1);
    private volatile AtomicBoolean[] idFlag = new AtomicBoolean[256];
    private Semaphore idLimit = new Semaphore(1);

    AsyncPacketReader(PacketProvider provider) {
        this.provider = provider;
        for(int i=0; i<=255; ++i){
            idFlag[i] = new AtomicBoolean(false);
        }
    }

    /**
     * 请求从 {@link #provider}队列中拿一份Packet进行发送
     *
     * @return 如果当前Reader中有可以用于网络发送的数据，则返回True
     */
    boolean requestTakePacket() {
        synchronized (this) {
            // 如果优先列队里有帧存放,则不进行拿包,直接返回
            if (nodeSize >= 1) {
                return true;
            }
        }

        SendPacket packet = provider.takePacket();
        if (packet != null) {
            short identifier = generateIdentifier(1);
            SendHeaderFrame frame = new SendHeaderFrame(identifier, packet);
            appendNewFrame(frame);
        }

        synchronized (this) {
            return nodeSize != 0;
        }
    }


    /**
     * 填充数据到IoArgs中
     *
     * @return 如果当前有可用于发送的帧，则填充数据并返回，如果填充失败可返回null
     */
    IoArgs fillData() {
        Frame currentFrame = gerCurrentFrame();
        if (currentFrame == null) {
            return null;
        }


        try {
            // 返回false继续消费帧header和body
            if (currentFrame.handle(args)) {
                // 消费完本帧
                // 尝试基于本帧构建后续帧
                Frame nextFrame = currentFrame.nextFrame();
                if (nextFrame != null) {
                    appendNewFrame(nextFrame);
                } else if (currentFrame instanceof SendEntityFrame) {
                    // 末尾实体帧
                    // 通知完成
                    provider.completedPacket(((SendEntityFrame) currentFrame).getPacket(),
                            true);

                    // 释放本链接的占位,以防并发发送时,两个相同的identifier同时发送
                    short bodyIdentifier = currentFrame.getBodyIdentifier();
                    idFlag[bodyIdentifier].compareAndSet(true, false);
                    idLimit.release();
                }

                // 从链头弹出
                popCurrentFrame();
            }

            return args;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 取消Packet对应的帧发送，如果当前Packet已发送部分数据（就算只是头数据）
     * 也应该在当前帧队列中发送一份取消发送的标志{@link CancelSendFrame}
     *
     * @param packet 待取消的packet
     */
    synchronized void cancel(SendPacket packet) {
        if (nodeSize == 0) {
            return;
        }

        for (BytePriorityNode<Frame> x = node, before = null; x != null; before = x, x = x.next) {
            Frame frame = x.item;
            if (frame instanceof AbsSendPacketFrame) {
                AbsSendPacketFrame packetFrame = (AbsSendPacketFrame) frame;
                if (packetFrame.getPacket() == packet) {
                    boolean removable = packetFrame.abort();
                    if (removable) {
                        // A B C
                        removeFrame(x, before);
                        if (packetFrame instanceof SendHeaderFrame) {
                            // 头帧，并且未被发送任何数据，直接取消后不需要添加取消发送帧
                            break;
                        }
                    }

                    // 添加终止帧，通知到接收方
                    CancelSendFrame cancelSendFrame = new CancelSendFrame(packetFrame.getBodyIdentifier());
                    appendNewFrame(cancelSendFrame);

                    // 意外终止，返回失败
                    provider.completedPacket(packet, false);

                    break;
                }
            }
        }
    }

    /**
     * 关闭当前Reader，关闭时应关闭所有的Frame对应的Packet
     */
    @Override
    public synchronized void close() {
        while (node != null) {
            Frame frame = node.item;
            if (frame instanceof AbsSendPacketFrame) {
                SendPacket packet = ((AbsSendPacketFrame) frame).getPacket();
                provider.completedPacket(packet, false);
            }
            node = node.next;
        }

        nodeSize = 0;
        node = null;
    }

    /**
     * 添加新的帧
     *
     * @param frame 新帧
     */
    private synchronized void appendNewFrame(Frame frame) {
        BytePriorityNode<Frame> newNode = new BytePriorityNode<>(frame);
        if (node != null) {
            // 使用优先级别添加到链表
            node.appendWithPriority(newNode);
        } else {
            node = newNode;
        }
        nodeSize++;
    }

    /**
     * 获取当前链表头的帧
     *
     * @return Frame
     */
    private synchronized Frame gerCurrentFrame() {
        if (node == null) {
            return null;
        }
        return node.item;
    }


    /**
     * 弹出链表头的帧
     */
    private synchronized void popCurrentFrame() {
        node = node.next;
        nodeSize--;
        if (node == null) {
            requestTakePacket();
        }
    }

    /**
     * 删除某帧对应的链表节点
     *
     * @param removeNode 待删除的节点
     * @param before     当前删除节点的前一个节点，用于构建新的链表结构
     */
    private synchronized void removeFrame(BytePriorityNode<Frame> removeNode, BytePriorityNode<Frame> before) {
        if (before == null) {
            // A B C
            // B C
            node = removeNode.next;
        } else {
            // A B C
            // A C
            before.next = removeNode.next;
        }
        nodeSize--;
        if (node == null) {
            requestTakePacket();
        }
    }

    /**
     * 构建一份Packet惟一标志
     * todo bug? 当一个id还没完全发送完,又发送了255个包,又发送了一个同样的id混在网络里...
     *
     * @return 标志为：1～255
     */
    private short generateIdentifier(int deep) {
        // 控制递归深度,虽然不是真的控制了,但是阻塞有效缓冲了栈的深度
        if(deep>=255*2){
            try {
                idLimit.acquire();
                // 唤醒后重来
                return generateIdentifier(1);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        short identifier = (short) lastIdentifier.getAndAdd(1);
        if (identifier > 255) {
            lastIdentifier.getAndSet(1);
            identifier = (short)lastIdentifier.get();
        }

        // 如果拿到的id被占用了,拿下一个
        // 可能出现的后果,一个连接中255个id都被长时间占用了
        // 导致不断递归,最终爆栈
        // 解决方案:控制发送数据的上限,手动控制栈的深度,递归的时间 todo 未控制发送数据上限
        if(idFlag[identifier].get()==true){
            return generateIdentifier(deep+1);
        }
        // 声明id被占用了
        if(idFlag[identifier].compareAndSet(false,true)){
            return identifier;
        }else{
            return generateIdentifier(deep+1);
        }
    }

    /**
     * Packet提供者
     */
    interface PacketProvider {
        /**
         * 拿Packet操作
         *
         * @return 如果队列有可以发送的Packet则返回不为null
         */
        SendPacket takePacket();

        /**
         * 结束一份Packet
         *
         * @param packet    发送包
         * @param isSucceed 是否成功发送完成
         */
        void completedPacket(SendPacket packet, boolean isSucceed);
    }

}