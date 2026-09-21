package com.serverlock.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * {@link Screen} 私有字段访问器。
 *
 * <p>{@code renderables} 是 private 且元素类型 {@code Renderable} 为包级可见，
 * 无法在 mixin 包里用 {@code @Shadow} 声明（类型对不上、也 import 不到）。
 * 用 {@code @Accessor} 让 Mixin 在编译期生成 getter，类型退化为原始 {@code List}，
 * 我们只做增删不做元素读取，因此不损失类型安全。
 *
 * <p>{@code children} / {@code narratables} 同理由访问器提供：修改控件时必须三者同步，
 * 否则会出现「按钮看不见但仍响应点击」或「按钮没渲染但有旁白」这类半失效状态。
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    /** 渲染列表，等价于屏幕上看得到的控件集合。 */
    @Accessor("renderables")
    List<Object> serverlock$getRenderables();

    /** 子控件列表，负责事件分发。 */
    @Accessor("children")
    List<Object> serverlock$getChildren();

    /** 旁白列表，负责无障碍朗读。 */
    @Accessor("narratables")
    List<Object> serverlock$getNarratables();
}
