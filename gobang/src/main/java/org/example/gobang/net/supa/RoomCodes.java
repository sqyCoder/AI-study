package org.example.gobang.net.supa;

import java.security.SecureRandom;

/**
 * 4 位房号（spec4 §0）：字母表去除易混字符 0/O/1/I/L，共 30 字符。
 */
public final class RoomCodes {

    public static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    public static final int LENGTH = 4;

    private static final SecureRandom RND = new SecureRandom();

    private RoomCodes() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RND.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** 规范化用户输入（去空白+大写）；合法返回房号，非法返回 null。 */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String up = input.trim().toUpperCase();
        if (up.length() != LENGTH) {
            return null;
        }
        for (int i = 0; i < up.length(); i++) {
            if (ALPHABET.indexOf(up.charAt(i)) < 0) {
                return null;
            }
        }
        return up;
    }
}
