package com.serverlock.internal;

/**
 * 标记「该按钮已被 ServerLock 禁用，渲染时应叠加红叉」。
 *
 * <p>为什么用接口而不是在渲染 mixin 里重新判断按钮身份：
 * {@code AbstractButtonMixin} 注入的是<b>所有</b>按钮的渲染路径，
 * 若每次都靠文字内容去识别「是不是添加服务器按钮」，等于每帧对每个按钮做一次
 * 本地化字符串比较，属于无谓开销。
 * <p>改为在界面 {@code init} 时识别一次并打标记，渲染期只做一次布尔判断。
 *
 * <p>接口定义在 {@code internal} 包（纯 Java，无 Mixin 依赖），
 * 由 {@code AbstractButtonMixin} 实现 —— 这样 {@code JoinMultiplayerScreenMixin}
 * 可以安全地引用它，不会触发 Mixin 包隔离限制。
 */
public interface DisabledButtonMarker {

    /** 标记为已禁用，渲染时叠加红叉。 */
    void serverlock$markDisabled();

    /** 是否为已禁用状态。 */
    boolean serverlock$isDisabled();
}
