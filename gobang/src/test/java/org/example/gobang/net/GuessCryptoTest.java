package org.example.gobang.net;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spec3 §11 用例 7~11：承诺-揭示安全性质。 */
class GuessCryptoTest {

    // 7. createCommit → verify 通过
    @Test
    void commitVerifyPasses() {
        for (int i = 0; i < 50; i++) {
            GuessCrypto.Commit c = GuessCrypto.createCommit();
            assertTrue(GuessCrypto.verify(new GuessCrypto.Reveal(c.count(), c.salt()), c.hashHex()));
        }
    }

    // 8. count 篡改后 verify 失败
    @Test
    void tamperedCountFails() {
        GuessCrypto.Commit c = GuessCrypto.createCommit();
        int forged = c.count() == 1 ? 2 : 1;
        assertFalse(GuessCrypto.verify(new GuessCrypto.Reveal(forged, c.salt()), c.hashHex()));
    }

    // 9. salt 篡改后 verify 失败
    @Test
    void tamperedSaltFails() {
        GuessCrypto.Commit c = GuessCrypto.createCommit();
        byte[] bad = Arrays.copyOf(c.salt(), 16);
        bad[0] ^= 0x01;
        assertFalse(GuessCrypto.verify(new GuessCrypto.Reveal(c.count(), bad), c.hashHex()));
        // 结构非法同样拒绝
        assertFalse(GuessCrypto.verify(null, c.hashHex()));
        assertFalse(GuessCrypto.verify(new GuessCrypto.Reveal(5, c.salt()), c.hashHex()));
        assertFalse(GuessCrypto.verify(new GuessCrypto.Reveal(c.count(), new byte[8]), c.hashHex()));
    }

    // 10. 同 salt 同 count 哈希确定、异 salt 哈希不同
    @Test
    void hashDeterministicAndSalted() {
        byte[] salt = new byte[16];
        Arrays.fill(salt, (byte) 7);
        String h1 = GuessCrypto.hashOf(salt, 1);
        String h2 = GuessCrypto.hashOf(salt, 1);
        assertEquals(h1, h2);
        assertEquals(64, h1.length());
        assertNotEquals(h1, GuessCrypto.hashOf(salt, 2));
        byte[] other = Arrays.copyOf(salt, 16);
        other[15] ^= 0x55;
        assertNotEquals(h1, GuessCrypto.hashOf(other, 1));
    }

    // 11. 1000 次 createCommit 的 count 分布合理（防退化成常量）
    @Test
    void countDistributionSane() {
        int ones = 0;
        for (int i = 0; i < 1000; i++) {
            if (GuessCrypto.createCommit().count() == 1) {
                ones++;
            }
        }
        assertTrue(ones > 250 && ones < 750, "count=1 出现次数异常: " + ones);
    }
}
