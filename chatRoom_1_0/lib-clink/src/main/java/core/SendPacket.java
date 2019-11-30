package core;

public abstract class SendPacket extends Packet{
    private boolean isCanceled;

    public abstract byte[] bytes();

    public boolean isCanceled(){
        return this.isCanceled;
    }
}
