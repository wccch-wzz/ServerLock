package com.serverlock.mixin;

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
 *   <li>移除“添加服务器”按钮，玩家无法自行新增条目。</li>
 *   <li>进入界面时把服务器列表强制纠正为指定的两个（主服在前）。</li>
 * </ol>
 *
 * <p>为什么还要在这里纠正一次：{@code servers.dat} 可能被外部客户端、文件管理器或其它
 * 启动器改写，仅靠启动时纠正不足以覆盖「游戏运行中被改」的情况。每次打开这个界面都对齐一次，
 * 是成本最低的兜底。
 */
@Mixin(value = JoinMultiplayerScreen.class, remap = false)
public abstract class JoinMultiplayerScreenMixin {

    @Shadow
    private ServerList servers;

    @Inject(method = "init", at = @At("TAIL"))
    private void serverlock$onInit(CallbackInfo ci) {
        // 1) 强制纠正服务器列表
        serverlock$enforceServerList();

        // 2) 移除“添加服务器”按钮
        ScreenAccessor accessor = (ScreenAccessor) (Object) this;
        List<Object> renderables = accessor.serverlock$getRenderables();
        List<Object> children = accessor.serverlock$getChildren();
        List<Object> narratables = accessor.serverlock$getNarratables();

        List<Object> snapshot = new ArrayList<>(renderables);
        for (Object element : snapshot) {
            if (!(element instanceof AbstractWidget widget) || !serverlock$isAddServerButton(widget)) {
                continue;
            }
            renderables.remove(element);
            children.remove(element);
            narratables.remove(element);
        }
    }

    /** 把服务器列表纠正为“恰好这两个”。 */
    @Unique
    private void serverlock$enforceServerList() {
        ServerList list = this.servers;
        if (list == null) {
            return;
        }
        if (ServerLockEnforcer.enforce(list)) {
            // 有改动 → 落盘 + 提示
            list.save();
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
