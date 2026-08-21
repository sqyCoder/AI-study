package org.example.gobang.net.supa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** spec4 §4：房号生成与规范化。 */
class RoomCodesTest {

    @Test
    void generatedCodesValid() {
        for (int i = 0; i < 1000; i++) {
            String c = RoomCodes.generate();
            assertEquals(RoomCodes.LENGTH, c.length());
            assertEquals(c, RoomCodes.normalize(c), "生成码必须能通过自身校验");
        }
    }

    @Test
    void noConfusableChars() {
        String forbidden = "01IOL";
        for (int i = 0; i < 500; i++) {
            String c = RoomCodes.generate();
            for (char ch : forbidden.toCharArray()) {
                assertEquals(-1, c.indexOf(ch), "不应出现易混字符 " + ch);
            }
        }
    }

    @Test
    void normalizeAccepts() {
        assertEquals("AB2C", RoomCodes.normalize(" ab2c "));
        assertEquals("9999", RoomCodes.normalize("9999"));
    }

    @Test
    void normalizeRejects() {
        assertNull(RoomCodes.normalize(null));
        assertNull(RoomCodes.normalize(""));
        assertNull(RoomCodes.normalize("ABC"));      // 长度不足
        assertNull(RoomCodes.normalize("ABC12"));    // 超长
        assertNull(RoomCodes.normalize("AB0D"));     // 含 0
        assertNull(RoomCodes.normalize("A1CD"));     // 含 1
        assertNull(RoomCodes.normalize("AI CD"));    // 含 I 与空格内嵌
    }
}
