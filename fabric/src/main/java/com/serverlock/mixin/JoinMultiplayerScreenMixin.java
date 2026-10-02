package com.serverlock.mixin;

import com.serverlock.internal.DisabledButtonMarker;
import com.serverlock.internal.ServerLockNotifier;
import com.serverlock.internal.ServerLockEnforcer;
import com.serverlock.fabric.ServerLockRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 多人游戏界面（服务器列表）改造：
 *
 * <ol>
 *   <li>禁用“添加服务器”按钮（灰态 + 红叉），玩家无法自行新增条目。</li>
 *   <li>进入界面时把服务器列表强制纠正为指定的两个（主服在前）。</li>
 * </ol>
 *
 * <p>为什么还要在这里纠正一次：{@code servers.dat} 可能被外部客户端、文件管理器或其它
 * 启动器改写，仅靠启动时纠正不足以覆盖「游戏运行中被改」的情况。每次打开这个界面都对齐一次，
 * 是成本最低的兜底。
 *
 * <p>注意注入点分成两处、时机不同，不要合并：
 * <ul>
 *   <li>{@code updateOnlineServers} 调用<b>之前</b> —— 纠正列表，保证界面拿到正确数据；</li>
 *   <li>{@code init} 的 TAIL —— 禁用按钮，此时控件才全部创建完毕。</li>
 * </ul>
 */
@Mixin(value = JoinMultiplayerScreen.class, remap = false)
public abstract class JoinMultiplayerScreenMixin {

    @Shadow
    private ServerList servers;

    /**
     * 在 {@code ServerSelectionList.updateOnlineServers(servers)} 被调用<b>之前</b>
     * 纠正列表内容。
     *
     * <p>为什么注入点选在 {@code init()} 的这里而不是 TAIL：
     * {@code JoinMultiplayerScreen.init()} 的执行顺序是
     * <pre>
     *   servers = new ServerList(minecraft);
     *   servers.load();                                  ← 读盘
     *   ...
     *   serverSelectionList.updateOnlineServers(servers); ← 界面据此填充条目
     *   ... 创建按钮等
     * </pre>
     * 如果放在 TAIL，纠正发生时界面<b>已经</b>按旧内容（可能是空的 servers.dat）
     * 渲染完了，改内存不会触发重新填充，玩家看到的仍是空列表。
     * <p>把注入点放在 {@code updateOnlineServers} 调用之前，界面拿到的就是纠正后的列表。
     *
     * <p>用 {@code INVOKE} + {@code shift = BEFORE} 定位到该次调用之前，
     * 这样即使原版在 init 里调整了前后代码顺序，定位点依然跟随目标方法调用。
     */
    @Inject(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList;updateOnlineServers(Lnet/minecraft/client/multiplayer/ServerList;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void serverlock$enforceBeforeListFill(CallbackInfo ci) {
        serverlock$enforceServerList();
    }

    /**
     * 禁用“添加服务器”按钮。
     *
     * <p>为什么是禁用而不是移除：移除后玩家只会看到一个空位，不知道原本有什么、
     * 也不知道是被限制还是界面出 bug 了。保留按钮（灰态）并在其上叠加红叉，
     * 是更明确的“此功能被禁用”表达。
     *
     * <p>禁用方式用 {@code active = false}（{@code AbstractWidget} 的 public 字段），
     * 这是原版自身的机制：置为 false 后 {@code mouseClicked}/{@code keyPressed} 都会被短路，
     * 按钮渲染为灰态且不响应任何交互，不需要我们再拦截事件。
     *
     * <p>红叉的绘制不在本类：它需要每帧执行、且要精确落在按钮自身绘制之后，
     * 因此放在 {@link AbstractButtonMixin} 里注入 {@code AbstractButton} 的渲染方法。
     * 本方法只负责把按钮标为禁用，并把「哪些按钮需要画叉」记到按钮自己的字段上。
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void serverlock$onInit(CallbackInfo ci) {
        ScreenAccessor accessor = (ScreenAccessor) (Object) this;
        for (Object element : new ArrayList<>(accessor.serverlock$getRenderables())) {
            if (element instanceof AbstractWidget widget && serverlock$isAddServerButton(widget)) {
                // 原版机制：active = false 即彻底禁用（灰态 + 不响应点击/键盘）
                widget.active = false;
                // 标记该按钮需要叠加红叉，由 AbstractButtonMixin 在渲染时读取
                ((DisabledButtonMarker) widget).serverlock$markDisabled();
            }
        }
    }

    /**
     * 把服务器列表纠正为“恰好这两个”。
     *
     * <p>这里<b>只纠正内存，不写盘</b>。此方法运行在 {@code init()} 中
     * （即玩家点击「多人游戏」的同一帧），而 {@code save()} 是同步文件 I/O，
     * 在此处写盘会造成可感知的卡顿。
     * <p>界面展示只读内存列表，纠正内存已足以让玩家看到正确的两个服务器；
     * 落盘由 {@code ServerLockFabric} 的启动期 tick 与 {@code save()} 的 HEAD 注入共同保证，
     * 都不在这条关键路径上。
     */
    @Unique
    private void serverlock$enforceServerList() {
        ServerList list = this.servers;
        if (list == null) {
            return;
        }
        if (ServerLockEnforcer.enforce(list)) {
            // 有改动 → 只提示，不写盘（写盘时机见上）
            ServerLockNotifier.notifyCorrected(Minecraft.getInstance());
        }
    }

    /** 判断是否为“添加服务器”按钮。 */
    @Unique
    private static boolean serverlock$isAddServerButton(AbstractWidget widget) {
        if (!(widget instanceof Button)) {
            return false;
        }
        Component message = widget.getMessage();
        if (message == null) {
            return false;
        }
        if (String.valueOf(message.getContents()).contains("selectServer.add")) {
            return true;
        }
        return ServerLockRules.matchesAddServerLabel(message.getString());
    }
}
