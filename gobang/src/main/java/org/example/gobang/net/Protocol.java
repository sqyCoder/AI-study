package org.example.gobang.net;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.example.gobang.model.Board;

/**
 * 行式文本协议（spec3 §2.2）：TYPE|k=v|k=v，UTF-8，一行一消息。
 * 纯 JDK、零 JavaFX 依赖；白名单严格校验，任何非法输入抛 ProtocolException，
 * 由调用方执行 fail-fast 断开（spec3 §0.1）。
 */
public final class Protocol {

    public enum Type {
        HELLO, GUESS_COMMIT, GUESS_CHOICE, GUESS_REVEAL,
        MOVE, UNDO_REQ, UNDO_OK, UNDO_DENY, REMATCH_REQ, REMATCH_OK,
        BYE, PING, PONG
    }

    /** 单行最大长度（字符），超出视为攻击/损坏直接拒绝。 */
    public static final int MAX_LINE = 256;
    public static final int PROTOCOL_VERSION = 1;
    public static final int MAX_NAME = 12;
    public static final int MAX_REASON = 40;

    /** 协议违规异常。 */
    public static class ProtocolException extends IllegalArgumentException {
        public ProtocolException(String msg) {
            super(msg);
        }
    }

    /** 一条协议消息：类型 + 有序字段（不可变）。 */
    public record Message(Type type, Map<String, String> fields) {
        public String get(String key) {
            return fields.get(key);
        }
    }

    private Protocol() {
    }

    // ---------- 编码 ----------

    private static String encode(Type type, String... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("kv 必须成对");
        }
        StringBuilder sb = new StringBuilder(type.name());
        for (int i = 0; i < kv.length; i += 2) {
            sb.append('|').append(kv[i]).append('=').append(kv[i + 1]);
        }
        return sb.toString();
    }

    public static String hello(String name) {
        return encode(Type.HELLO, "v", String.valueOf(PROTOCOL_VERSION), "name", sanitize(name, MAX_NAME));
    }

    public static String guessCommit(String hashHex) {
        return encode(Type.GUESS_COMMIT, "hash", hashHex);
    }

    public static String guessChoice(boolean odd) {
        return encode(Type.GUESS_CHOICE, "odd", odd ? "1" : "0");
    }

    public static String guessReveal(int count, byte[] salt) {
        return encode(Type.GUESS_REVEAL, "count", String.valueOf(count), "salt", hex(salt));
    }

    public static String move(int r, int c, int color) {
        return encode(Type.MOVE, "r", String.valueOf(r), "c", String.valueOf(c),
                "color", String.valueOf(color));
    }

    public static String undoReq() {
        return encode(Type.UNDO_REQ);
    }

    public static String undoOk() {
        return encode(Type.UNDO_OK);
    }

    public static String undoDeny() {
        return encode(Type.UNDO_DENY);
    }

    public static String rematchReq() {
        return encode(Type.REMATCH_REQ);
    }

    public static String rematchOk() {
        return encode(Type.REMATCH_OK);
    }

    public static String bye(String reason) {
        return encode(Type.BYE, "reason", sanitize(reason, MAX_REASON));
    }

    public static String ping() {
        return encode(Type.PING);
    }

    public static String pong() {
        return encode(Type.PONG);
    }

    // ---------- 解码 ----------

    /**
     * 严格解码：TYPE 白名单 + 字段名/顺序/数量/取值域逐项校验。
     * 任何不符抛 {@link ProtocolException}。
     */
    public static Message decode(String line) {
        if (line == null || line.isEmpty()) {
            throw new ProtocolException("空消息");
        }
        if (line.length() > MAX_LINE) {
            throw new ProtocolException("消息超长");
        }
        String[] parts = line.split("\\|", -1);
        Type type;
        try {
            type = Type.valueOf(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("未知类型");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq <= 0) {
                throw new ProtocolException("字段格式非法");
            }
            fields.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
        }
        validate(type, fields);
        return new Message(type, Collections.unmodifiableMap(fields));
    }

    /** 每个 TYPE 的合法字段名序列（固定顺序）。 */
    private static String[] expectedKeys(Type type) {
        return switch (type) {
            case HELLO -> new String[]{"v", "name"};
            case GUESS_COMMIT -> new String[]{"hash"};
            case GUESS_CHOICE -> new String[]{"odd"};
            case GUESS_REVEAL -> new String[]{"count", "salt"};
            case MOVE -> new String[]{"r", "c", "color"};
            case BYE -> new String[]{"reason"};
            default -> new String[0];
        };
    }

    private static void validate(Type type, Map<String, String> f) throws ProtocolException {
        String[] keys = expectedKeys(type);
        if (f.size() != keys.length) {
            throw new ProtocolException(type + " 字段数量不符");
        }
        int i = 0;
        for (Map.Entry<String, String> e : f.entrySet()) {
            if (!e.getKey().equals(keys[i++])) {
                throw new ProtocolException(type + " 字段名/顺序不符");
            }
        }
        switch (type) {
            case HELLO -> {
                if (!String.valueOf(PROTOCOL_VERSION).equals(f.get("v"))) {
                    throw new ProtocolException("版本不符");
                }
                sanitize(f.get("name"), MAX_NAME); // 非法字符即抛出
            }
            case GUESS_COMMIT -> requireHex(f.get("hash"), 64, "hash");
            case GUESS_CHOICE -> {
                String odd = f.get("odd");
                if (!"0".equals(odd) && !"1".equals(odd)) {
                    throw new ProtocolException("odd 取值非法");
                }
            }
            case GUESS_REVEAL -> {
                String count = f.get("count");
                if (!"1".equals(count) && !"2".equals(count)) {
                    throw new ProtocolException("count 取值非法");
                }
                requireHex(f.get("salt"), 32, "salt");
            }
            case MOVE -> {
                intInRange(f.get("r"), 0, Board.SIZE - 1, "r");
                intInRange(f.get("c"), 0, Board.SIZE - 1, "c");
                String color = f.get("color");
                if (!"1".equals(color) && !"2".equals(color)) {
                    throw new ProtocolException("color 取值非法");
                }
            }
            case BYE -> sanitize(f.get("reason"), MAX_REASON);
            default -> {
                // 无字段类型，数量校验已覆盖
            }
        }
    }

    // ---------- 工具 ----------

    private static void requireHex(String v, int len, String what) {
        if (v == null || v.length() != len) {
            throw new ProtocolException(what + " 长度非法");
        }
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            boolean ok = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F');
            if (!ok) {
                throw new ProtocolException(what + " 含非 hex 字符");
            }
        }
    }

    private static void intInRange(String v, int lo, int hi, String what) {
        if (v == null || v.isEmpty()) {
            throw new ProtocolException(what + " 缺失");
        }
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new ProtocolException(what + " 含非数字字符");
            }
        }
        long n;
        try {
            n = Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new ProtocolException(what + " 数值溢出");
        }
        if (n < lo || n > hi) {
            throw new ProtocolException(what + " 越界");
        }
    }

    /**
     * 清洗自由文本（昵称/理由）：剔除控制字符与分隔符 '|' '='，按码点截断。
     * 空结果或含非法字符由调用方语义决定：昵称清洗后允许为空串吗？不允许——抛异常。
     */
    static String sanitize(String raw, int maxLen) {
        if (raw == null) {
            throw new ProtocolException("文本字段缺失");
        }
        StringBuilder sb = new StringBuilder();
        raw.codePoints().limit(maxLen * 2L).forEach(cp -> {
            boolean bad = cp < 0x20 || cp == 0x7F || cp == '|' || cp == '=';
            if (!bad) {
                sb.appendCodePoint(cp);
            }
        });
        // 按码点截断到 maxLen
        while (sb.codePointCount(0, sb.length()) > maxLen) {
            sb.deleteCharAt(sb.length() - 1);
        }
        String out = sb.toString().trim();
        if (out.isEmpty()) {
            throw new ProtocolException("文本字段为空");
        }
        return out;
    }

    /** 小写 hex 编码。 */
    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
