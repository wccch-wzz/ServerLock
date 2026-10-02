package com.serverlock.mixin;

import com.serverlock.internal.ServerLockNotifier;
import com.serverlock.internal.ServerLockEnforcer;
import com.serverlock.fabric.ServerLockRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    /**
     * 已被禁用的“添加服务器”按钮，供渲染阶段画红叉。
     *
     * <p>用实例字段而非静态字段：每个屏幕实例持有自己的按钮集合，
     * 屏幕销毁后随实例一起回收，不会残留旧引用导致画在别的界面上。
     */
    @Unique
    private final List<AbstractWidget> serverlock$disabledAddServerButtons = new ArrayList<>();

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
     * 禁用“添加服务器”按钮（保留可见，并在其上叠加一个红叉）。
     *
     * <p>为什么是禁用而不是移除：移除后玩家只会看到一个空位，不知道原本有什么、
     * 也不知道是被限制还是界面出 bug 了。保留按钮并叠加红叉，是更明确的“此功能被禁用”表达。
     *
     * <p>禁用方式用 {@code active = false}（{@code AbstractWidget} 的 public 字段），
     * 这是原版自身的机制：置为 false 后 {@code mouseClicked}/{@code keyPressed} 都会被短路，
     * 按钮渲染为灰态且不响应任何交互，不需要我们再拦截事件。
     *
     * <p>同时把它加入 {@link #serverlock$disabledAddServerButtons}，
     * 供渲染阶段画红叉使用 —— 注入点必须回归 {@code init}，
     * 因为按钮实例要等 {@code init} 跑完才创建出来。
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void serverlock$onInit(CallbackInfo ci) {
        serverlock$disabledAddServerButtons.clear();

        ScreenAccessor accessor = (ScreenAccessor) (Object) this;
        for (Object element : new ArrayList<>(accessor.serverlock$getRenderables())) {
            if (element instanceof AbstractWidget widget && serverlock$isAddServerButton(widget)) {
                // 原版机制：active = false 即彻底禁用（灰态 + 不响应点击/键盘）
                widget.active = false;
                serverlock$disabledAddServerButtons.add(widget);
            }
        }
    }

    /**
     * 在被禁用的“添加服务器”按钮上叠加一个红叉。
     *
     * <p><b>注入的方法是 {@code extractRenderState} 而不是 {@code render}：</b>
     * MC 26.2 重构了 GUI 渲染管线，{@code Screen} 上已不存在
     * {@code render(GuiGraphics, int, int, float)}，取而代之的是
     * {@code extractRenderState(GuiGraphicsExtractor, int, int, float)}——
     * 绘制指令先写入渲染状态对象，再由渲染线程统一提交。
     * 注入不存在的 {@code render} 会让 Mixin 应用失败（与 saveSingleServer 那次同类问题）。
     *
     * <p>注入 TAIL：此时按钮已由原版逻辑记录完毕（灰态底图 + 文字），
     * 我们在同一批绘制指令末尾补两笔对角线，得到“灰按钮 + 红叉”的视觉效果。
     * 注意顺序即层级，放在 TAIL 保证红叉画在按钮之上。
     *
     * <p>用 {@code fill()} 画而非贴图/字体字符：不依赖任何资源包与字体，
     * 不会被材质包替换，也不存在字符缺字变方框的问题。两条对角线由若干细实心矩形拼出，
     * 每条线按高度逐行推进，线宽 2px，视觉上足够清晰。
     *
     * <p>坐标取自 {@code getX()/getY()/getWidth()/getHeight()}，与按钮实际位置一致；
     * 按钮支持布局重排，用 getter 而非缓存值能保证窗口缩放后红叉仍对准按钮。
     */
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void serverlock$drawDisabledCross(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        for (AbstractWidget widget : serverlock$disabledAddServerButtons) {
            if (!widget.visible) {
                continue;
            }
            int x = widget.getX();
            int y = widget.getY();
            int w = widget.getWidth();
            int h = widget.getHeight();

            // 内缩 2px，避免红叉压到按钮边框上
            int left = x + 2;
            int top = y + 2;
            int right = x + w - 2;
            int bottom = y + h - 2;
            int span = Math.min(right - left, bottom - top);

            // 两条对角线：从左到右逐列推进，每列画一个 2px 高的实心块
            for (int i = 0; i < span; i++) {
                // 主对角线（左上 → 右下）
                graphics.fill(left + i, top + i, left + i + 1, top + i + 2, ServerLockRules.CROSS_COLOR);
                // 副对角线（右上 → 左下）
                graphics.fill(right - i - 1, top + i, right - i, top + i + 2, ServerLockRules.CROSS_COLOR);
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
