package com.serverlock.fabric;

import java.util.ArrayList;
import java.util.List;

/**
 * ServerLock 的核心常量与规则。
 *
 * <p>本类不依赖任何 Minecraft 类型，纯字符串规则，因此测试与审计都很直接。
 */
public final class ServerLockRules {

    private ServerLockRules() {
    }

    /** 主服务器名称。 */
    public static final String PRIMARY_NAME = "山之城";
    /** 主服务器地址。 */
    public static final String PRIMARY_IP = "xiaohu1994.top";

    /** 备用服务器名称。 */
    public static final String BACKUP_NAME = "山之城备用ip";
    /** 备用服务器地址。 */
    public static final String BACKUP_IP = "by.xiaohu1994.top";

    /** 允许存在的主机名白名单（小写，不含端口）。 */
    private static final List<String> ALLOWED_HOSTS = List.of(PRIMARY_IP, BACKUP_IP);

    /** 允许存在的“完整地址”白名单，含默认端口形式，归一化后比较。 */
    private static final List<String> ALLOWED_ADDRESSES = List.of(
            PRIMARY_IP, PRIMARY_IP + ":25565",
            BACKUP_IP, BACKUP_IP + ":25565");

    /**
     * 归一化地址：去空白、转小写。
     *
     * <p>Minecraft 的 {@code ServerAddress#parseString} 把主机名原样保留，
     * 只在写入 NBT 时可能带上默认端口，因此这里同时接受带/不带 {@code :25565}。
     */
    public static String normalize(String address) {
        if (address == null) {
            return "";
        }
        return address.trim().toLowerCase();
    }

    /**
     * 判断一个服务器地址是否为允许的两个之一。
     *
     * @param address servers.dat 中记录的 ip 字段
     * @return 在允许列表内返回 true
     */
    public static boolean isAllowedAddress(String address) {
        return ALLOWED_ADDRESSES.contains(normalize(address));
    }

    /** 判断主机名是否允许（不接受端口后缀）。 */
    public static boolean isAllowedHost(String host) {
        return ALLOWED_HOSTS.contains(normalize(host));
    }

    /**
     * “单人游戏”按钮的本地化文字兜底匹配。
     *
     * <p>主判据是翻译键 {@code menu.singleplayer}；这里只作为键匹配失败时的补充，
     * 覆盖常见语言，避免因为资源包改动翻译键导致漏删。
     */
    public static boolean matchesSingleplayerLabel(String label) {
        if (label == null) {
            return false;
        }
        String s = label.trim();
        return s.equals("单人游戏")
                || s.equals("Singleplayer")
                || s.equals("單人遊戲")
                || s.equals("シングルプレイ")
                || s.equals("싱글 플레이")
                || s.equals("Einzelspieler")
                || s.equals("Un jugador")
                || s.equals("Solo")
                || s.equals("Одиночная игра");
    }

    /** “添加服务器”按钮的本地化文字兜底匹配。 */
    public static boolean matchesAddServerLabel(String label) {
        if (label == null) {
            return false;
        }
        String s = label.trim();
        return s.equals("添加服务器")
                || s.equals("Add Server")
                || s.equals("新增伺服器")
                || s.equals("サーバーを追加")
                || s.equals("서버 추가")
                || s.equals("Server hinzufügen")
                || s.equals("Añadir servidor")
                || s.equals("Добавить сервер");
    }

    /**
     * “禁用”红叉的颜色（ARGB）。
     *
     * <p>取值 {@code 0xFFFF3B30}：iOS 系统红，在灰态按钮上对比度足够，
     * 又不至于像纯红 {@code 0xFFFF0000} 那样刺眼。
     */
    public static final int CROSS_COLOR = 0xFFFF3B30;


    /**
     * 生成标准的两个服务器条目（名称 + 地址），顺序固定：主服务器在前。
     *
     * @return 长度恒为 2 的列表
     */
    public static List<ServerEntry> requiredServers() {
        List<ServerEntry> list = new ArrayList<>(2);
        list.add(new ServerEntry(PRIMARY_NAME, PRIMARY_IP));
        list.add(new ServerEntry(BACKUP_NAME, BACKUP_IP));
        return list;
    }

    /** 服务器条目（不可变值对象）。 */
    public static final class ServerEntry {
        private final String name;
        private final String ip;

        public ServerEntry(String name, String ip) {
            this.name = name;
            this.ip = ip;
        }

        public String name() {
            return name;
        }

        public String ip() {
            return ip;
        }

        @Override
        public String toString() {
            return name + " (" + ip + ")";
        }
    }
}
