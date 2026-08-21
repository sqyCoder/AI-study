package org.example.gobang.net;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本机局域网 IPv4 枚举（spec3 §2.4）：
 * 过滤 up 且非回环的 Inet4Address；私网段（10/172.16-31/192.168）优先排序，去重。
 * 结果为空表示当前无可用局域网地址。
 */
public final class LocalIp {

    private LocalIp() {
    }

    /** 返回去重后的候选地址列表（可能为空）。 */
    public static List<String> list() {
        Set<String> out = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                try {
                    if (!ni.isUp() || ni.isLoopback()) {
                        continue;
                    }
                } catch (SocketException ignored) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) {
                        out.add(a.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
            // 无网络接口：返回空列表
        }
        List<String> sorted = new ArrayList<>(out);
        sorted.sort((x, y) -> Boolean.compare(!isPrivateText(x), !isPrivateText(y)));
        return List.copyOf(sorted);
    }

    static boolean isPrivateText(String ip) {
        String[] p = ip.split("\\.");
        if (p.length != 4) {
            return false;
        }
        int a, b;
        try {
            a = Integer.parseInt(p[0]);
            b = Integer.parseInt(p[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        return a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168);
    }
}
