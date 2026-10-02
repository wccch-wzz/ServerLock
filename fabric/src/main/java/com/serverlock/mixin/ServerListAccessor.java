package com.serverlock.mixin;

import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * {@link ServerList} 私有字段访问器。
 *
 * <p>为什么需要它：{@code serverList} 是 private 且没有公开的“整体替换”方法
 * （{@code set}/{@code replace} 只处理单个条目），要做全量纠正就必须直接操作该列表。
 * 相比反射，Mixin accessor 在编译期生成字节码，无运行时查找开销，也不受模块化限制。
 *
 * <p>用法：{@code ServerListAccessor acc = (ServerListAccessor) (Object) serverList;}
 * 然后调用无参方法。注意 accessor 方法必须是实例方法且无参数 —— 这是 Mixin 的约定。
 */
@Mixin(value = ServerList.class, remap = false)
public interface ServerListAccessor {

    /** 在线服务器列表（即 servers.dat 的内容）。 */
    @Accessor("serverList")
    List<ServerData> serverlock$getServerList();

    /** 被隐藏的服务器列表，同样需要清空以免绕过限制。 */
    @Accessor("hiddenServerList")
    List<ServerData> serverlock$getHiddenServerList();
}
