package org.example.gobang.net.supa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON（spec4 §2）：仅服务 Supabase Realtime 协议编解码，零第三方依赖。
 * 支持 RFC 8259 全语法解析（对象/数组/字符串转义/数字/字面量），
 * 编码侧只提供本项目用到的字符串转义与片段拼装。
 */
public final class JsonKit {

    public static class JsonException extends IllegalArgumentException {
        public JsonException(String msg) {
            super(msg);
        }
    }

    private JsonKit() {
    }

    // ---------- 解析 ----------

    /** 解析为 Object：Map/List/String/Boolean/Long/Double/null。非法输入抛 JsonException。 */
    public static Object parse(String json) {
        if (json == null) {
            throw new JsonException("null 输入");
        }
        P p = new P(json);
        p.ws();
        Object v = p.value();
        p.ws();
        if (!p.eof()) {
            throw new JsonException("尾部多余字符 @" + p.i);
        }
        return v;
    }

    private static final class P {
        final String s;
        int i;

        P(String s) {
            this.s = s;
        }

        boolean eof() {
            return i >= s.length();
        }

        char peek() {
            if (eof()) {
                throw new JsonException("意外结尾");
            }
            return s.charAt(i);
        }

        void ws() {
            while (!eof() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        char next() {
            char c = peek();
            i++;
            return c;
        }

        void expect(char c) {
            if (next() != c) {
                throw new JsonException("期望 '" + c + "' @" + (i - 1));
            }
        }

        Object value() {
            ws();
            char c = peek();
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': lit("true"); return Boolean.TRUE;
                case 'f': lit("false"); return Boolean.FALSE;
                case 'n': lit("null"); return null;
                default: return number();
            }
        }

        void lit(String w) {
            if (!s.startsWith(w, i)) {
                throw new JsonException("非法字面量 @" + i);
            }
            i += w.length();
        }

        Map<String, Object> object() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (!eof() && peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                String k = string();
                ws();
                expect(':');
                m.put(k, value());
                ws();
                char c = next();
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw new JsonException("对象分隔符非法 @" + (i - 1));
                }
            }
        }

        List<Object> array() {
            expect('[');
            List<Object> l = new ArrayList<>();
            ws();
            if (!eof() && peek() == ']') {
                i++;
                return l;
            }
            while (true) {
                l.add(value());
                ws();
                char c = next();
                if (c == ']') {
                    return l;
                }
                if (c != ',') {
                    throw new JsonException("数组分隔符非法 @" + (i - 1));
                }
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw new JsonException("\\u 转义截断");
                            }
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            } catch (NumberFormatException nfe) {
                                throw new JsonException("\\u 转义非法 @" + i);
                            }
                            i += 4;
                        }
                        default -> throw new JsonException("非法转义 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Object number() {
            int start = i;
            if (!eof() && peek() == '-') {
                i++;
            }
            boolean fp = false;
            while (!eof()) {
                char c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    i++;
                } else if (c == '.') {
                    fp = true;
                    i++;
                } else if (c == 'e' || c == 'E') {
                    fp = true;
                    i++;
                    // 指数符号仅允许出现在 e/E 之后
                    if (!eof() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                        i++;
                    }
                } else {
                    break;
                }
            }
            if (i == start) {
                throw new JsonException("非法数字 @" + start);
            }
            String n = s.substring(start, i);
            try {
                return fp ? (Object) Double.parseDouble(n) : (Object) Long.parseLong(n);
            } catch (NumberFormatException e) {
                throw new JsonException("数字解析失败: " + n);
            }
        }
    }

    // ---------- 编码 ----------

    /** 字符串字面量（含引号与全部必要转义）；控制字符统一转为 Unicode 转义形式。 */
    public static String str(String v) {
        if (v == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(v.length() + 8);
        sb.append('"');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /** 由已编码成员拼装数组：members 为原始 JSON 片段（如 "null"、str(...)、"{...}"）。 */
    public static String arrRaw(String... members) {
        return "[" + String.join(",", members) + "]";
    }

    /** 扁平对象拼装：kv 交替；key 自动加引号，value 必须是已编码 JSON 片段或 null。 */
    public static String objRaw(String... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("kv 必须成对");
        }
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(str(kv[i])).append(':')
                    .append(kv[i + 1] == null ? "null" : kv[i + 1]);
        }
        return sb.append('}').toString();
    }
}
