package org.example.gobang.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** spec3 §11 用例 1~6：协议编解码往返与非法输入拒绝。 */
class ProtocolTest {

    // 1. 全类型消息 encode→decode 往返相等
    @Test
    void roundTripAllTypes() {
        assertRoundTrip(Protocol.hello("棋客12"), Protocol.Type.HELLO);
        assertRoundTrip(Protocol.guessCommit("a".repeat(64)), Protocol.Type.GUESS_COMMIT);
        assertRoundTrip(Protocol.guessChoice(true), Protocol.Type.GUESS_CHOICE);
        assertRoundTrip(Protocol.guessChoice(false), Protocol.Type.GUESS_CHOICE);
        assertRoundTrip(Protocol.guessReveal(2, new byte[16]), Protocol.Type.GUESS_REVEAL);
        assertRoundTrip(Protocol.move(0, 14, 1), Protocol.Type.MOVE);
        assertRoundTrip(Protocol.undoReq(), Protocol.Type.UNDO_REQ);
        assertRoundTrip(Protocol.undoOk(), Protocol.Type.UNDO_OK);
        assertRoundTrip(Protocol.undoDeny(), Protocol.Type.UNDO_DENY);
        assertRoundTrip(Protocol.rematchReq(), Protocol.Type.REMATCH_REQ);
        assertRoundTrip(Protocol.rematchOk(), Protocol.Type.REMATCH_OK);
        assertRoundTrip(Protocol.bye("对方已离开"), Protocol.Type.BYE);
        assertRoundTrip(Protocol.ping(), Protocol.Type.PING);
        assertRoundTrip(Protocol.pong(), Protocol.Type.PONG);

        Protocol.Message m = Protocol.decode(Protocol.move(7, 8, 2));
        assertEquals("7", m.get("r"));
        assertEquals("8", m.get("c"));
        assertEquals("2", m.get("color"));
    }

    private void assertRoundTrip(String line, Protocol.Type type) {
        Protocol.Message decoded = Protocol.decode(line);
        assertEquals(type, decoded.type());
        // 字段值原样保留（往返无损）
        for (String part : line.split("\\|")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                assertEquals(part.substring(eq + 1), decoded.get(part.substring(0, eq)));
            }
        }
    }

    // 2. 未知 TYPE 抛 ProtocolException
    @Test
    void unknownTypeRejected() {
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("CHAT|msg=hi"));
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("NOT_A_TYPE"));
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode(""));
    }

    // 3. r/c/color/odd/count 越界值抛异常
    @Test
    void outOfRangeValuesRejected() {
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("MOVE|r=15|c=0|color=1"));
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("MOVE|r=-1|c=0|color=1"));
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("MOVE|r=0|c=99|color=1"));
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("MOVE|r=0|c=0|color=3"));
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("MOVE|r=0|c=0|color=0"));
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("GUESS_CHOICE|odd=2"));
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("GUESS_REVEAL|count=0|salt=" + "ab".repeat(16)));
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("GUESS_REVEAL|count=3|salt=" + "ab".repeat(16)));
        // 非数字注入
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("MOVE|r=+1|c=0|color=1"));
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode("MOVE|r=1x|c=0|color=1"));
    }

    // 4. hash/salt 非 hex 抛异常
    @Test
    void nonHexRejected() {
        assertThrows(Protocol.ProtocolException.class,
                () -> Protocol.decode("GUESS_COMMIT|hash=" + "g".repeat(64)));
        assertThrows(Protocol.ProtocolException.class,
                () -> Protocol.decode("GUESS_COMMIT|hash=" + "ab".repeat(31))); // 长度不足
        assertThrows(Protocol.ProtocolException.class,
                () -> Protocol.decode("GUESS_REVEAL|count=1|salt=" + "zz".repeat(16)));
        assertThrows(Protocol.ProtocolException.class,
                () -> Protocol.decode("GUESS_REVEAL|count=1|salt=abc")); // 长度不符
        // 合法大写 hex 应通过
        assertEquals(Protocol.Type.GUESS_COMMIT,
                Protocol.decode("GUESS_COMMIT|hash=" + "AB".repeat(32)).type());
    }

    // 5. 超长行拒绝
    @Test
    void overlongLineRejected() {
        String longName = "a".repeat(300);
        String line = "HELLO|v=1|name=" + longName;
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode(line));
    }

    // 6. name 控制字符过滤与截断
    @Test
    void nameSanitized() {
        // 控制字符与分隔符被剔除
        Protocol.Message m = Protocol.decode(
                Protocol.hello("a\u0000b\n\u001bc|d=e"));
        assertEquals("abcde", m.get("name"));
        // 超长按码点截断到 12
        Protocol.Message m2 = Protocol.decode(Protocol.hello("一二三四五六七八九十甲乙丙丁"));
        assertEquals(12, m2.get("name").codePointCount(0, m2.get("name").length()));
        assertEquals("一二三四五六七八九十甲乙", m2.get("name"));
        // 纯非法字符清洗后为空 → 拒绝
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.hello("|||\u0000"));
        // 空昵称拒绝
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.hello("  "));
        // 版本不符拒绝
        assertThrows(Protocol.ProtocolException.class,
                () -> Protocol.decode("HELLO|v=2|name=x"));
        // 字段顺序/数量篡改拒绝
        assertThrows(Protocol.ProtocolException.class,
                () -> Protocol.decode("HELLO|name=x|v=1"));
        assertThrows(Protocol.ProtocolException.class,
                () -> Protocol.decode("MOVE|r=0|c=0"));
        assertNotEquals(null, Protocol.decode(Protocol.ping()));
    }
}
