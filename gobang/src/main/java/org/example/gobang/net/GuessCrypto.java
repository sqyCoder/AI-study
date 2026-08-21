package org.example.gobang.net;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 猜先承诺-揭示协议（spec3 §2.3）。
 *
 * <p>公平性论证：
 * <ul>
 *   <li>房主<b>先承诺后见猜</b>：看到对方猜单/双后再改颗数需伪造 SHA-256 原像，不可行；</li>
 *   <li>猜方<b>仅见哈希</b>：抗原像特性保证无法从 hash 反推颗数；</li>
 *   <li>角色固定「房主持子、客人猜」不影响公平——结果随机性由颗数与承诺密码学绑定；</li>
 *   <li>{@link #verify} 使用 {@link MessageDigest#isEqual} 常数时间比较，规避时序侧信道。</li>
 * </ul>
 */
public final class GuessCrypto {

    /** 房主承诺：hashHex 可外发；count/salt 仅房主内存持有直至揭示。 */
    public record Commit(String hashHex, int count, byte[] salt) {
    }

    /** 揭示数据：随 GUESS_REVEAL 外发。 */
    public record Reveal(int count, byte[] salt) {
    }

    private static final SecureRandom RND = new SecureRandom();

    private GuessCrypto() {
    }

    /** 生成承诺：salt=16 字节安全随机，count ∈ {1,2}。 */
    public static Commit createCommit() {
        byte[] salt = new byte[16];
        RND.nextBytes(salt);
        int count = 1 + RND.nextInt(2);
        return new Commit(hashOf(salt, count), count, salt);
    }

    /** SHA-256(hex(salt) + ":" + count)，返回 64 位小写 hex。 */
    public static String hashOf(byte[] salt, int count) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(
                    (Protocol.hex(salt) + ":" + count).getBytes(StandardCharsets.UTF_8));
            return Protocol.hex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * 校验揭示数据与承诺一致。
     * 结构非法（count 越界/salt 长度不符）直接返回 false，不抛异常。
     */
    public static boolean verify(Reveal r, String expectedHashHex) {
        if (r == null || expectedHashHex == null) {
            return false;
        }
        if ((r.count() != 1 && r.count() != 2)
                || r.salt() == null || r.salt().length != 16) {
            return false;
        }
        String actual = hashOf(r.salt(), r.count());
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expectedHashHex.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
