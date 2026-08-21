package org.example.gobang.net.supa;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spec4 §4：极简 JSON 编解码。 */
class JsonKitTest {

    @Test
    void parseScalars() {
        assertEquals("hi", JsonKit.parse("\"hi\""));
        assertEquals(Boolean.TRUE, JsonKit.parse("true"));
        assertNull(JsonKit.parse("null"));
        assertEquals(42L, JsonKit.parse("42"));
        assertEquals(-3L, JsonKit.parse("-3"));
        assertEquals(1.5, JsonKit.parse("1.5"));
        assertEquals(2.0E3, (double) JsonKit.parse("2e3"), 0.0001);
    }

    @Test
    void parseStructuresAndEscapes() {
        Object o = JsonKit.parse(
                "{\"a\":\"x\\ny\",\"b\":[1,null,true,{\"c\":\"中文\\ud83d\\ude00\"}]}");
        Map<?, ?> m = assertInstanceOf(Map.class, o);
        assertEquals("x\ny", m.get("a"));
        List<?> arr = assertInstanceOf(List.class, m.get("b"));
        assertEquals(1L, arr.get(0));
        assertNull(arr.get(1));
        assertEquals(Boolean.TRUE, arr.get(2));
        Map<?, ?> inner = assertInstanceOf(Map.class, arr.get(3));
        assertEquals("中文\ud83d\ude00", inner.get("c"));
    }

    @Test
    void malformedRejected() {
        for (String bad : new String[]{
                "{", "[1,", "\"unterminated", "{\"a\"}", "{\"a\":} ",
                "[1,2]]", "tru", "+12", "", "  ", "{\"a\":1,}", "[,]"}) {
            assertThrows(JsonKit.JsonException.class, () -> JsonKit.parse(bad),
                    "应拒绝: " + bad);
        }
    }

    @Test
    void escapeRoundTrip() {
        String tricky = "a\"b\\c\n\r\t\b\f中文\ud83d\ude00\u0001";
        String encoded = JsonKit.str(tricky);
        assertEquals(tricky, JsonKit.parse(encoded));
        // 控制字符必须转为 Unicode 转义形式
        assertTrue(encoded.contains("\\u0001"));
        assertTrue(encoded.contains("\\n"));
    }

    @Test
    void builders() {
        assertEquals("\"ab\"", JsonKit.str("ab"));
        assertEquals("null", JsonKit.str(null));
        assertEquals("{\"k\":\"v\"}", JsonKit.objRaw("k", JsonKit.str("v")));
        assertEquals("{\"k\":null}", JsonKit.objRaw("k", null));
        assertEquals("[null,\"x\",{}]",
                JsonKit.arrRaw("null", JsonKit.str("x"), "{}"));
        assertThrows(IllegalArgumentException.class,
                () -> JsonKit.objRaw("only-key"));
        // 组合体可被自身解析器读回
        Object frame = JsonKit.parse(JsonKit.arrRaw("null", JsonKit.str("1"),
                JsonKit.str("phoenix"), JsonKit.str("heartbeat"), "{}"));
        List<?> a = assertInstanceOf(List.class, frame);
        assertEquals(5, a.size());
        assertNull(a.get(0));
        assertEquals("heartbeat", a.get(3));
    }
}
