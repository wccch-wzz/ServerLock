package com.serverlock.internal;

import com.serverlock.fabric.ServerLockRules;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

import java.util.List;

/**
 * 服务器列表纠正器。
 *
 * <p>目标不变式：{@code servers.dat} 中恰好包含两个服务器，顺序固定为
 * 「山之城」在前、「山之城备用ip」在后，且不存在其它 host。
 *
 * <p>实现方式：遍历现有列表，剔除所有不在白名单内的条目以及重复项，
 * 然后补齐缺失的必需条目。这个做法是幂等的 —— 对一个已经合规的列表调用不会产生改动，
 * 因此可以放心地在每次打开界面时执行。
 *
 * <p>注意：{@code ServerList#serverList} 是私有字段，这里通过
 * {@link ServerListAccessor} 读写。该接口本身是<b>纯 Java 接口</b>
 * （不含 Mixin 注解），由 {@code ServerListMixin} 用 {@code @Shadow} 实现，
 * 因此本包对 Mixin 无任何依赖。
 */
public final class ServerLockEnforcer {

    private ServerLockEnforcer() {
    }

    /**
     * 执行纠正。
     *
     * @param list 待纠正的服务器列表
     * @return 是否发生了改动（调用方据此决定是否 save 与提示）
     */
    public static boolean enforce(ServerList list) {
        if (list == null) {
            return false;
        }

        ServerListAccessor accessor = (ServerListAccessor) (Object) list;
        List<ServerData> existing = accessor.serverlock$getServerList();

        // 字段为 null 的情况：类初始化未执行完（Mixin 应用失败、或构造被异常中断）。
        // 此时不能直接 return false —— 那会让列表永远空下去，玩家看到“多人游戏内没有服务器”。
        // 这里主动补一个空列表进去，再走下面的补齐逻辑。
        if (existing == null) {
            existing = new java.util.ArrayList<>();
            accessor.serverlock$setServerList(existing);
        }

        boolean changed = false;

        // 阶段一：清除所有非法条目（不在白名单内的地址）
        int before = existing.size();
        existing.removeIf(data -> data == null || !ServerLockRules.isAllowedAddress(data.ip));
        if (existing.size() != before) {
            changed = true;
        }

        // 阶段二：清除重复条目（同一地址只保留第一条）
        changed |= serverlock$dedupe(existing);

        // 阶段三：按固定顺序补齐必需条目
        List<ServerLockRules.ServerEntry> required = ServerLockRules.requiredServers();

        // 先按期望顺序重排：把已存在的必需条目移动到正确下标
        for (int i = 0; i < required.size(); i++) {
            ServerLockRules.ServerEntry entry = required.get(i);
            int found = serverlock$indexOfIp(existing, entry.ip());
            if (found == -1) {
                continue;
            }
            if (found != i) {
                ServerData moved = existing.remove(found);
                existing.add(Math.min(i, existing.size()), moved);
                changed = true;
            }
        }

        // 再补齐缺失项
        for (int i = 0; i < required.size(); i++) {
            ServerLockRules.ServerEntry entry = required.get(i);
            if (serverlock$indexOfIp(existing, entry.ip()) == -1) {
                existing.add(Math.min(i, existing.size()), serverlock$create(entry));
                changed = true;
            }
        }

        // 最后：纠正名称（玩家可能把名字改了，名字也属于锁定范围）
        for (int i = 0; i < required.size() && i < existing.size(); i++) {
            ServerLockRules.ServerEntry entry = required.get(i);
            ServerData data = existing.get(i);
            if (!entry.name().equals(data.name)) {
                data.name = entry.name();
                changed = true;
            }
        }

        return changed;
    }

    /** 构造一个新的 ServerData 条目。 */
    private static ServerData serverlock$create(ServerLockRules.ServerEntry entry) {
        return new ServerData(entry.name(), entry.ip(), ServerData.Type.OTHER);
    }

    /** 查找指定 ip 的下标，找不到返回 -1。 */
    private static int serverlock$indexOfIp(List<ServerData> list, String ip) {
        for (int i = 0; i < list.size(); i++) {
            ServerData data = list.get(i);
            if (data != null && ServerLockRules.normalize(data.ip).equals(ServerLockRules.normalize(ip))) {
                return i;
            }
        }
        return -1;
    }

    /** 去重：同一规范化地址只保留第一条。 */
    private static boolean serverlock$dedupe(List<ServerData> list) {
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            ServerData cur = list.get(i);
            if (cur == null) {
                continue;
            }
            for (int j = list.size() - 1; j > i; j--) {
                ServerData other = list.get(j);
                if (other != null
                        && ServerLockRules.normalize(cur.ip).equals(ServerLockRules.normalize(other.ip))) {
                    list.remove(j);
                    changed = true;
                }
            }
        }
        return changed;
    }
}
