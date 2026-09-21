package com.serverlock.mixin;

import com.serverlock.neoforge.ServerLockRules;
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
            // 注意：MC 1.21.1 的 API 是 Minecraft#getToasts() 返回 ToastComponent；
            // 1.21.11 才改名为 getToastManager() 返回 ToastManager。
            // 两端各自使用自己版本的写法，这也是两侧代码不能共用同一份实现的原因之一。
            minecraft.getToasts().addToast(toast);
        } catch (Throwable ignored) {
            // 提示失败不影响锁定效果
        }
    }
}
