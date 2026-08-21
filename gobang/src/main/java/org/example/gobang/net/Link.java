package org.example.gobang.net;

/**
 * 传输抽象（spec4 §1）：P2P 直连（{@link NetLink}）与 Supabase 房间码
 * （org.example.gobang.net.supa.SupaLink）的统一语义。
 * GameView 面向本接口编程，对局逻辑与传输实现解耦。
 */
public interface Link {

    /** 启动会话：发送我方 HELLO；握手细节由实现内部消化。 */
    void start(String myName, NetLink.Listener listener);

    /** 发送一行 Protocol 编码产物。失败触发断开流程并返回 false。 */
    boolean send(String line);

    /** 本地主动离开：best-effort 告别，不触发本地 onDisconnected。幂等。 */
    void close(String reason);

    boolean isActive();

    /** 本端是否为房主（决定猜先角色）。 */
    boolean isHost();

    /** 握手完成后可得对方昵称。 */
    String peerName();
}
