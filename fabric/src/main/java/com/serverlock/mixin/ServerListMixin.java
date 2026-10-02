package com.serverlock.mixin;

import com.serverlock.fabric.ServerLockRules;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 从文件层锁死 {@code servers.dat}。
 *
 * <p>这是最底层的一道防线，覆盖所有绕过 UI 的路径：
 * <ul>
 *   <li>直接编辑 servers.dat 文件后启动游戏 → {@code load()} 注入点纠正。</li>
 *   <li>其它 mod / 启动器在运行期写入 → {@code save()} 注入点纠正。</li>
 *   <li>隐藏服务器（hiddenServerList）也是绕过方式之一 → 一并清空。</li>
 * </ul>
 *
 * <p>注入点都在方法 HEAD（读之前先补齐、写之前先纠正），保证无论调用方是谁，
 * 看到的和落盘的都是同一份合规数据。
 */
@Mixin(value = ServerList.class, remap = false)
public abstract class ServerListMixin {

    /**
     * 在 {@code load()} 读取文件之前，先确保目标条目存在。
     *
     * <p>为什么要在读取前做：{@code load()} 会清空内存列表再从 NBT 填充，
     * 如果只在这之后纠正，会有一次“短暂出现非法列表”的窗口，且需要额外一次 save。
     * 这里先预置，读取时 NBT 里的同名条目会覆盖它，效果等价但更省事。
     *
     * <p>真正的纠正放在 {@code load()} 的 TAIL，见下一个方法。
     */
    @Inject(method = "load", at = @At("HEAD"))
    private void serverlock$beforeLoad(CallbackInfo ci) {
        ServerList self = (ServerList) (Object) this;
        List<ServerData> hidden =
                ((ServerListAccessor) (Object) self).serverlock$getHiddenServerList();
        if (hidden != null && !hidden.isEmpty()) {
            hidden.clear();
        }
    }

    /**
     * 在 {@code load()} 读取完文件之后纠正内存列表。
     *
     * <p>此时列表已完全由 servers.dat 内容填充，是纠正的最佳时机。
     * 若发生纠正，立即回写文件，让磁盘与内存保持一致 —— 否则玩家关掉游戏后
     * 下一次启动仍会看到被篡改的内容。
     */
    @Inject(method = "load", at = @At("TAIL"))
    private void serverlock$afterLoad(CallbackInfo ci) {
        ServerList self = (ServerList) (Object) this;
        if (ServerLockEnforcer.enforce(self)) {
            // 磁盘内容非法 → 立刻写回合规版本
            self.save();
        }
    }

    /**
     * 在 {@code save()} 落盘之前纠正内存列表。
     *
     * <p>兜底所有写入路径：无论是本 mod 之外的代码、还是原版逻辑自身，
     * 只要走到保存这一步，写出的就一定是合规内容。
     */
    @Inject(method = "save", at = @At("HEAD"))
    private void serverlock$beforeSave(CallbackInfo ci) {
        ServerList self = (ServerList) (Object) this;
        ServerLockEnforcer.enforce(self);
    }

    /**
     * 阻止 {@code saveSingleServer} 这条单条写入路径写出非法数据。
     *
     * <p>{@code saveSingleServer} 用于 ping 完成后回写服务器信息（如 MOTD、图标），
     * 它会直接改写文件里对应条目。这里校验地址，非白名单则整体纠正后放弃该次写入。
     */
    @Inject(method = "saveSingleServer", at = @At("HEAD"), cancellable = true)
    private void serverlock$guardSingleServer(ServerData data, CallbackInfo ci) {
        if (data == null || !ServerLockRules.isAllowedAddress(data.ip)) {
            ServerList self = (ServerList) (Object) this;
            ServerLockEnforcer.enforce(self);
            self.save();
            ci.cancel();
        }
    }
}
