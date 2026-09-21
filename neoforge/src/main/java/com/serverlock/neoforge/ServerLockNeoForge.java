package com.serverlock.neoforge;

import com.serverlock.mixin.ServerLockEnforcer;
import com.serverlock.mixin.ServerLockNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * NeoForge 客户端入口。
 *
 * <p>职责：在客户端启动后、玩家点进多人游戏之前，先把 {@code servers.dat} 纠正到位。
 *
 * <p>为什么需要这一层（而不是只靠 {@code ServerListMixin}）：{@code ServerList.load()}
 * 只在“打开多人游戏界面”或“启动时”被调用，而 {@code ServerList} 不提供公开的实例获取方式
 * （原版通过 {@code JoinMultiplayerScreen} 内部持有）。这里在客户端 tick 的第一次主动构造
 * 一个 {@code ServerList} 并触发加载与纠正，确保即使用户从不打开服务器界面，磁盘上的文件
 * 也已经被修正过。
 *
 * <p>只在第一 tick 执行一次，之后直接短路返回，不产生持续开销。
 */
@Mod(value = "serverlock", dist = Dist.CLIENT)
public class ServerLockNeoForge {

    /** 保证只跑一次。 */
    private static boolean applied = false;

    public ServerLockNeoForge() {
        // 构造阶段不做重活：此时 Minecraft 实例尚未就绪，
        // 纠正动作推迟到 tick 事件里执行。
    }

    /**
     * 客户端 tick 事件。
     *
     * <p>用 {@code ClientTickEvent.Post} 而不是 setup 阶段：setup 阶段
     * {@code Minecraft.getInstance()} 可能还是 null，或文件系统尚未就绪。
     */
    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (applied) {
            return;
        }
        applied = true;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        try {
            // ServerList 构造时会自行 load()，Mixin 在 load 的 TAIL 完成纠正并回写
            ServerList list = new ServerList(minecraft);
            if (ServerLockEnforcer.enforce(list)) {
                list.save();
                ServerLockNotifier.notifyCorrected(minecraft);
            }
        } catch (Throwable ignored) {
            // 启动期纠正失败不阻断游戏；玩家打开服务器界面时 Mixin 仍会再纠正一次
        }
    }

    /** NeoForge 的 mod 事件总线注册（保留空实现以说明事件监听方式）。 */
    @SubscribeEvent
    public void onClientSetup(FMLClientSetupEvent event) {
        // 无需在此做任何事，见 onClientTick 的说明
    }
}
