package com.serverlock.mixin;

import com.serverlock.neoforge.ServerLockRules;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 移除标题界面的“单人游戏”按钮。
 *
 * <p>做法：在 {@code TitleScreen.init()} 返回后（TAIL）遍历已注册的控件，把“单人游戏”
 * 按钮从渲染列表、子控件列表和旁白列表中同时摘掉。
 *
 * <p>为什么不在创建点拦截：1.21.1 的 {@code createNormalMenuOptions} 返回 void，
 * 1.21.11 返回 int，且按钮创建在 1.21.11 里被抽成独立方法再组装，直接注入创建点会把代码
 * 绑死在某一版的内部结构上。在 init 结束后统一清理，两版行为一致。
 *
 * <p>识别方式：优先用翻译键 {@code menu.singleplayer}（与语言无关），兜底用本地化文字。
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void serverlock$removeSingleplayerButton(CallbackInfo ci) {
        ScreenAccessor accessor = (ScreenAccessor) (Object) this;

        List<Object> renderables = accessor.serverlock$getRenderables();
        List<Object> children = accessor.serverlock$getChildren();
        List<Object> narratables = accessor.serverlock$getNarratables();

        // 快照后再删，避免在遍历 renderables 时改动它导致迭代器失效
        List<Object> snapshot = new ArrayList<>(renderables);

        for (Object element : snapshot) {
            if (!(element instanceof AbstractWidget widget) || !isSingleplayerButton(widget)) {
                continue;
            }
            renderables.remove(element);
            children.remove(element);
            narratables.remove(element);
        }
    }

    /** 判断该控件是否为“单人游戏”按钮。 */
    @Unique
    private static boolean isSingleplayerButton(AbstractWidget widget) {
        if (!(widget instanceof Button)) {
            return false;
        }
        Component message = widget.getMessage();
        if (message == null) {
            return false;
        }
        // 翻译键形式：TranslatableContents 的 toString 即 key
        String contents = String.valueOf(message.getContents());
        if (contents.contains("menu.singleplayer")) {
            return true;
        }
        return ServerLockRules.matchesSingleplayerLabel(message.getString());
    }
}
