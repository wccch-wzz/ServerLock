package com.serverlock.internal;

import net.minecraft.client.multiplayer.ServerData;

import java.util.List;

/**
 * {@link net.minecraft.client.multiplayer.ServerList} 私有字段的读写抽象。
 *
 * <p><b>为什么不直接写成 {@code @Accessor} 接口：</b>
 * Mixin 规定「被某个 mixin config 声明为 mixin 的包，其下的类不得被普通类直接引用」
 * （运行时抛 {@code IllegalClassLoadError}）。若把 {@code @Accessor} 接口放在
 * {@code com.serverlock.internal} 并由 config 声明，那么同包内的
 * {@code ServerLockEnforcer} 引用它就等于「普通类引用 mixin 包内类」。
 * 即便两个类同包，只要该接口被登记为 mixin，这个引用依然非法。
 *
 * <p>因此这里退化成<b>普通 Java 接口</b>（不含任何 Mixin 注解），
 * 由 {@code mixin} 包下的 {@code ServerListMixin} 用 {@code @Shadow} 实现它。
 * 这样 {@code internal} 包内只有纯业务逻辑，对 Mixin 零依赖：
 * 可以独立编译、独立单测，也不会踩包访问限制。
 */
public interface ServerListAccessor {

    /** 在线服务器列表（即 servers.dat 的内容）。 */
    List<ServerData> serverlock$getServerList();

    /**
     * 覆写在线服务器列表。
     *
     * <p>仅在原值为 {@code null} 时使用（类初始化异常场景），不用于常规改写——
     * 常规改写直接操作 getter 返回的列表即可，那样改动会反映到原对象上。
     */
    void serverlock$setServerList(List<ServerData> list);

    /** 被隐藏的服务器列表，同样需要清空以免绕过限制。 */
    List<ServerData> serverlock$getHiddenServerList();
}
