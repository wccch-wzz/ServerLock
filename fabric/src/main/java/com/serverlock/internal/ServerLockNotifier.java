package com.serverlock.internal;

import com.serverlock.fabric.ServerLockRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * 纠正发生时的用户提示。
 *
 * <p>需求是「强制纠正并弹提示」，所以这里必须让玩家确实看到。
 *
 * <p>为什么用 Toast 而不是聊天栏消息：聊天栏只在进服后可见，而玩家是在标题界面或
 * 服务器列表界面触发纠正的，此时聊天栏不在屏幕上。Toast 是屏幕右上角的系统通知，
 * 任何界面下都能显示，是这个场景唯一合适的选择。
 *
 * <p>{@code SystemToastId.PERIODIC_NOTIFICATION} 带较长显示时长，适合这种需要被注意到的告知。
 *
 * <p>整个方法包在 try/catch 里：Toast 失败不影响纠正本身 —— 纠正此时已经落盘，
 * 提示只是告知，绝不能因为提示失败而回滚或抛异常中断主流程。
 */
public final class ServerLockNotifier {

    private ServerLockNotifier() {
    }

    /** 提示服务器列表已被重置。 */
    public static void notifyCorrected(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        try {
            int count = ServerLockRules.requiredServers().size();
            Component title = Component.literal("ServerLock");
            Component body = Component.literal("服务器列表已被重置为指定的 " + count + " 个服务器");

            SystemToast toast = new SystemToast(
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, body);
            // 26.2 起 ToastManager 从 Minecraft 移到了 Gui：
            //   旧: minecraft.getToastManager()
            //   新: minecraft.gui.toastManager()
            // （Minecraft.gui 是 public 字段，Gui.toastManager() 是 getter；
            //   Minecraft 里已无任何 ToastManager 字段与 getter。）
            minecraft.gui.toastManager().addToast(toast);
        } catch (Throwable ignored) {
            // 提示失败不影响锁定效果
        }
    }
}
