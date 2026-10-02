package com.serverlock.fabric;

import com.serverlock.internal.ServerLockNotifier;
import com.serverlock.internal.ServerLockEnforcer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerList;

/**
 * Fabric 客户端入口。
 *
 * <p>职责：在客户端启动后、玩家点进多人游戏之前，先把 {@code servers.dat} 纠正到位。
 *
 * <p>为什么需要这一层（而不是只靠 {@code ServerListMixin}）：{@code ServerList.load()}
 * 只在“打开多人游戏界面”或“启动时”被调用，而 {@code ServerList} 不提供公开的实例获取方式
 * （原版通过 {@code JoinMultiplayerScreen} 内部持有）。这里在客户端 tick 的第一次
 * 主动构造一个 {@code ServerList} 并触发加载与纠正，确保即使用户从不打开服务器界面，
 * 磁盘上的文件也已经被修正过。
 *
 * <p>只在第一 tick 执行一次，之后自动注销，不产生持续开销。
 */
public class ServerLockFabric implements ClientModInitializer {

    /** 保证只跑一次。 */
    private static boolean applied = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onEndTick(Minecraft minecraft) {
        if (applied) {
            return;
        }
        applied = true;

        if (minecraft == null) {
            return;
        }

        try {
            // MC 26.2 起 ServerList 的构造函数只初始化字段，不再自动 load()。
            // （已通过字节码确认：<init> 中只有 Lists.newArrayList 赋值 +
            //   minecraft 字段赋值，无 load 调用。1.21.x 及更早是构造即 load。）
            // 因此这里必须显式 load()，否则拿到的是两个空列表，
            // enforce() 补进去的条目只存在于内存，且随对象一起被丢弃。
            ServerList list = new ServerList(minecraft);

            // 先读盘，把 servers.dat 的现有内容加载到内存
            list.load();

            // 再把内存列表纠正为「恰好两个白名单服务器」
            if (ServerLockEnforcer.enforce(list)) {
                list.save();
                ServerLockNotifier.notifyCorrected(minecraft);
            }
        } catch (Throwable ignored) {
            // 启动期纠正失败不阻断游戏；玩家打开服务器界面时 Mixin 仍会再纠正一次
        }
    }
}
