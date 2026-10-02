package com.serverlock.mixin;

import com.serverlock.fabric.ServerLockRules;
import com.serverlock.internal.DisabledButtonMarker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在已被禁用的按钮上叠加红叉。
 *
 * <p><b>为什么注入 {@code AbstractButton} 而不是 {@code JoinMultiplayerScreen}：</b>
 * MC 26.2 的 {@code JoinMultiplayerScreen} <b>没有</b> {@code extractRenderState} 方法
 * （渲染由父类 {@code Screen} 提供），对它注入渲染方法会因「找不到目标方法」而
 * 导致整个 Mixin 应用失败（表现为打开界面报错并卡顿）。
 * <p>而按钮自身的绘制最终都汇聚到 {@code AbstractButton.extractWidgetRenderState}——
 * 这是本类自己的方法，注入稳定，且天然精确到「按钮」这一层级，
 * 不会像注入 {@code Screen} 那样影响全部界面。
 *
 * <p>注入 TAIL：此时灰态底图与文字已由原版记录完毕，我们补两笔对角线，
 * 得到「灰按钮 + 红叉」的效果。顺序即层级，TAIL 保证红叉画在按钮之上。
 *
 * <p>用 {@code fill()} 画而非贴图或字体字符：不依赖资源包与字体，
 * 不会被材质包替换，也不存在字符缺字变方框的问题。
 */
@Mixin(value = AbstractButton.class, remap = false)
public abstract class AbstractButtonMixin implements DisabledButtonMarker {

    /** 是否已被 ServerLock 标记为禁用。 */
    @Unique
    private boolean serverlock$disabled = false;

    @Override
    public void serverlock$markDisabled() {
        this.serverlock$disabled = true;
    }

    @Override
    public boolean serverlock$isDisabled() {
        return this.serverlock$disabled;
    }

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void serverlock$drawDisabledCross(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!this.serverlock$disabled) {
            return;
        }

        // 通过接口拿几何信息：AbstractButton 继承自 AbstractWidget，
        // 这些 getter 都是 public，可直接调用。
        AbstractButton self = (AbstractButton) (Object) this;
        if (!self.visible) {
            return;
        }

        int x = self.getX();
        int y = self.getY();
        int w = self.getWidth();
        int h = self.getHeight();

        // 内缩 2px，避免红叉压到按钮边框上
        int left = x + 2;
        int top = y + 2;
        int right = x + w - 2;
        int bottom = y + h - 2;
        int span = right - left;
        if (span > bottom - top) {
            span = bottom - top;
        }

        // 两条对角线：逐列推进，每列画一个 2px 高的实心块
        for (int i = 0; i < span; i++) {
            // 主对角线（左上 → 右下）
            graphics.fill(left + i, top + i, left + i + 1, top + i + 2, ServerLockRules.CROSS_COLOR);
            // 副对角线（右上 → 左下）
            graphics.fill(right - i - 1, top + i, right - i, top + i + 2, ServerLockRules.CROSS_COLOR);
        }
    }
}
