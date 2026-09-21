# ServerLock

锁定 Minecraft 客户端的服务器列表。移除「单人游戏」与「添加/编辑服务器」入口，并把服务器列表强制固定为指定的两个服务器。

支持 **Fabric (MC 1.21.11)** 和 **NeoForge (MC 1.21.1)**。

## 功能

| 功能 | 说明 |
|---|---|
| 移除「单人游戏」按钮 | 标题界面上不再出现该入口 |
| 移除「添加服务器」按钮 | 服务器列表界面无法自行新增条目 |
| 自动添加指定服务器 | 固定为「山之城」与「山之城备用ip」两个，主服在前 |
| 锁定 `servers.dat` | 直接改文件、用别的启动器、隐藏服务器等绕过方式全部被纠正 |
| 强制纠正 + 提示 | 检测到非法内容时重写文件并弹出 Toast 提示 |

锁定的两个服务器：

| 名称 | 地址 |
|---|---|
| 山之城 | `xiaohu1994.top` |
| 山之城备用ip | `by.xiaohu1994.top` |

## 下载

从 [Releases](../../releases) 获取对应版本的 jar：

| 加载器 | Minecraft | 文件 |
|---|---|---|
| Fabric | 1.21.11 | `serverlock-fabric-<版本>+mc1.21.11.jar` |
| NeoForge | 1.21.1 | `serverlock-neoforge-<版本>+mc1.21.1.jar` |

放进 `mods/` 目录即可，**仅客户端需要安装**（服务端无需、也不应安装）。

依赖：**Java 21**。Fabric 版本需要 [Fabric API](https://modrinth.com/mod/fabric-api)。

## 实现方式

全部通过 Mixin 在客户端侧实现，不修改任何原版文件。

### 移除按钮

`TitleScreen` 与 `JoinMultiplayerScreen` 的按钮创建逻辑在两个 MC 版本里结构不同（例如
`TitleScreen.createNormalMenuOptions` 在 1.21.1 返回 `void`，在 1.21.11 返回 `int`），
因此不在创建点注入，而是在 `init()` 返回后（`@At("TAIL")`）遍历已注册的控件，把目标按钮
从 **三个列表** 中同步摘除：

- `renderables` — 控制是否渲染
- `children` — 控制是否响应事件
- `narratables` — 控制无障碍旁白

只删其中一个会导致「按钮看不见但还能点」这类半失效状态。

识别按钮采用**翻译键**（`menu.singleplayer` / `selectServer.add`）作为主判据，与界面语言无关；
另附常见语言的文字兜底匹配，防止资源包改动翻译键导致漏删。

### 锁定 `servers.dat`

这是最关键的一层。`ServerList` 的所有读写路径都被拦截：

| 注入点 | 时机 | 作用 |
|---|---|---|
| `load()` HEAD | 读文件前 | 清空 `hiddenServerList`（隐藏服务器也是绕过手段） |
| `load()` TAIL | 读文件后 | 纠正内存列表，若非法则立即回写磁盘 |
| `save()` HEAD | 写文件前 | 兜底，保证落盘内容一定合规 |
| `saveSingleServer()` HEAD | 单条写入前 | 校验地址，非白名单则整体纠正并取消该次写入 |

纠正逻辑本身是**幂等**的：对一个已经合规的列表调用不会产生任何改动，因此可以放心地在每次
打开界面、每次读写时执行。清理顺序为「删非法条目 → 去重 → 按固定顺序补齐 → 校正名称」。

### 启动时纠正

`ServerList.load()` 只在打开多人界面或写文件时被调用，而 `ServerList` 没有公开的实例获取方式。
因此在客户端第一个 tick 主动构造一个 `ServerList` 触发加载与纠正，确保即使用户从不打开
服务器界面，磁盘上的文件也已被修正。

## 构建

需要 **JDK 21**。两端各自独立构建：

```bash
# Fabric (MC 1.21.11)
cd fabric
./gradlew build          # 产物在 build/libs/

# NeoForge (MC 1.21.1)
cd neoforge
./gradlew build          # 产物在 build/libs/
```

> NeoForge 构建使用 ModDevGradle，首次构建需要反编译 Minecraft，耗时较长且**需要充足内存**。
> 若在容器中构建，注意容器的内存上限（JVM 会按宿主机内存推算默认堆大小，可能远超容器限制）。
> 可通过 `JAVA_TOOL_OPTIONS="-Xmx3g"` 显式限制。

### 工具链版本

| 项 | Fabric | NeoForge |
|---|---|---|
| Minecraft | 1.21.11 | 1.21.1 |
| 加载器 | Fabric Loader 0.19.5 | NeoForge 21.1.233 |
| 构建插件 | Fabric Loom 1.17.21 | ModDevGradle 2.0.147 |
| Gradle | 9.5.0 | 9.5.0 |
| Java | 21 | 21 |
| Mappings | 官方 Mojang | 官方 Mojang |

> Loom 1.18.x 要求 **JVM 25**，1.17.21 是最后一个支持 JVM 21 的版本，故锁定 1.17.21。
> Loom 1.17.21 同时要求 Gradle ≥ 9.5.0。

## 项目结构

```
.
├── fabric/                    # Fabric (MC 1.21.11) 实现
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/serverlock/
│       │   ├── fabric/        # 入口与规则常量
│       │   └── mixin/         # Mixin 与纠错逻辑
│       └── resources/
└── neoforge/                  # NeoForge (MC 1.21.1) 实现
    ├── build.gradle
    └── src/main/
        ├── java/com/serverlock/
        │   ├── neoforge/      # 入口与规则常量
        │   └── mixin/         # Mixin 与纠错逻辑
        └── resources/
```

两端各自维护一份完整实现，不共享 common 模块。原因：两个 MC 版本的原版 GUI 类结构差异明显
（按钮创建方式、`Minecraft` 的 Toast 访问 API 等都不同，例如 1.21.1 是
`Minecraft#getToasts()` 返回 `ToastComponent`，1.21.11 是 `getToastManager()` 返回
`ToastManager`），强行抽公共层会让代码充满版本分支，收益低于维护成本。

## 许可

[LGPL-3.0-only](LICENSE)
