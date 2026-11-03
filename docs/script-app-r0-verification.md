# R0 预研核实记录：V-3 两支签名对照 / V-4 Rhino 与 KubeJS 共存

> 这是一份核实记录，不是设计。**施工方案本身不在本仓库里**，文中的 §编号指它。

对应施工方案 §10.4.1（V-3 剩余）、§16.4.1（V-4）、§27.2 的 R0 → P0 闸。
日期 2026-09-10。全部命令可复现，原始输出随表附在下面。

## 0. 执行环境

| | 值 |
|---|---|
| 机器 | Linux aarch64 |
| Gradle 守护进程 JDK | Temurin 25.0.4（`java -version`） |
| 编译工具链 | 由 `foojay-resolver-convention` 自动取：1.20.1 支用 JDK 17（`build.gradle:18` 钉死），1.21.1 支用 JDK 21 |
| V-3 工作树 | `/root/projects/mcphone-1.20.1`，分支 `1.20.1-forge`，`090db92` |
| V-4 工作树 | `/root/projects/mcphone-v4`，`origin/main` 的 `2e56034`（独立 worktree，不动别的分支） |

任务书写"JDK 21"，而 1.20.1 支的 `java.toolchain.languageVersion` 是 17。两者不冲突：
21 是 1.21.1 支的要求，1.20.1 支跑在 17 上，CI 也是同时装 17 与 21
（`.github/workflows/build.yml:72-78`）。

---

# V-3 两支签名对照表

## V-3.1 构建（前置：本机缓存里原先只有 1.21.1 的反混淆产物）

```bash
cd /root/projects/mcphone-1.20.1        # worktree，分支 1.20.1-forge
git rev-parse HEAD                      # 090db92...
./gradlew build --console=plain
```

```
断言测试 10 份跑完：BookSearchTest、ChatImagePacketTest、ChatMessageCodecTest、
ConversationKeyTest、GifDecodeTest、GreetingTest、ImageEncodeTest、
WallpaperPacketTest、WeatherTest、WorldClockTest
> Task :verifyDistIsolation
dist 隔离校验通过：170 个非 client 类，无一引用客户端类型
> Task :verifyServiceFiles
SPI 服务文件校验通过：17 个类全部存在
BUILD SUCCESSFUL in 28s
24 actionable tasks: 19 executed, 5 up-to-date
```

产出的映射产物（下面所有 `$J20` 指它）：

```bash
J20=/root/.gradle/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/\
1.20.1-47.4.23_mapped_official_1.20.1/forge-1.20.1-47.4.23_mapped_official_1.20.1.jar
# 19,237,052 字节
```

1.21.1 侧沿用 §附录 B 的 neoform 产物（下面所有 `$J21` 指它）：

```bash
J21=$(find ~/.gradle/caches/neoformruntime/intermediate_results \
        -name "sourcesAndCompiledWithNeoForge_*.jar" | head -1)
```

## V-3.2 对照表

| API | 1.20.1 签名 | 1.21.1 签名 | 门面 |
|---|---|---|---|
| 修饰符构造 | `AttributeModifier(UUID, String, double, Operation)`（另有 `(String,double,Op)` 与 `(UUID,Supplier<String>,double,Op)`） | `AttributeModifier(ResourceLocation, double, Operation)`，且是 `record` | `PlayerAbilities` |
| Operation 取值 | `ADDITION` / `MULTIPLY_BASE` / `MULTIPLY_TOTAL` | `ADD_VALUE` / `ADD_MULTIPLIED_BASE` / `ADD_MULTIPLIED_TOTAL` | `PlayerAbilities` |
| 修饰符标识 | `getId()` → `UUID`；`getName()` → `String` | `id()` → `ResourceLocation`；`is(ResourceLocation)` | `PlayerAbilities` |
| 删修饰符 | `removeModifier(UUID)` → **void**；`removePermanentModifier(UUID)` → `boolean` | `removeModifier(ResourceLocation)` → **boolean** | `PlayerAbilities` |
| 查修饰符 | `getModifier(UUID)`；`hasModifier(AttributeModifier)` | `getModifier(ResourceLocation)`；`hasModifier(ResourceLocation)` | `PlayerAbilities` |
| 取属性实例 | `LivingEntity.getAttribute(Attribute)` | `LivingEntity.getAttribute(Holder<Attribute>)` | `PlayerAbilities` |
| 战利品表入口 | `MinecraftServer.getLootData()` → `LootDataManager` | `MinecraftServer.reloadableRegistries()` → `ReloadableServerRegistries$Holder` | `LootAccess` |
| 按标识取表 | `LootDataResolver.getLootTable(ResourceLocation)` | `Holder.getLootTable(ResourceKey<LootTable>)` | `LootAccess` |
| 掷一次 | `LootTable.getRandomItems(LootParams)` → `ObjectArrayList<ItemStack>` | **同左，逐字相同** | `LootAccess` |
| 掷参构造 | `LootParams$Builder(ServerLevel)` + `create(LootContextParamSet)` | **同左，逐字相同** | `LootAccess` |
| 取分入口 | `Scoreboard.getOrCreatePlayerScore(String, Objective)` → `Score` | `Scoreboard.getOrCreatePlayerScore(ScoreHolder, Objective)` → `ScoreAccess` | `Scores` |
| 分数读写 | `Score`：`getScore()` / `setScore(int)` / `add(int)`→**void** / `increment()` / `reset()` / `isLocked()` / `setLocked(boolean)` | `ScoreAccess`：`get()` / `set(int)` / `add(int)`→**int** / `increment()`→int / `reset()` / `locked()` / `lock()` / `unlock()` | `Scores` |
| 分数持有者 | `String`（玩家名） | `ScoreHolder`（1.20.3+ 才有，1.20.1 **查不到此类**） | `Scores` |
| 物品能力键 | `ForgeCapabilities.ITEM_HANDLER`（`Capability<net.minecraftforge.items.IItemHandler>`） | `Capabilities$ItemHandler.BLOCK` / `ENTITY` / `ENTITY_AUTOMATION` / `ITEM` | `ItemCaps` |
| 能力查询 | `ICapabilityProvider.getCapability(Capability<T>, Direction)` → `LazyOptional<T>` | `BlockCapability.getCapability(Level, BlockPos, BlockState, BlockEntity, C)` → `T`（可空） | `ItemCaps` |
| 处理器接口 | `net.minecraftforge.items.IItemHandler` | `net.neoforged.neoforge.items.IItemHandler` | `ItemCaps` |

**没有"没查"项。** 唯一查不到的是 `net.minecraft.world.scores.ScoreAccess` 与
`ScoreHolder` 在 1.20.1 上不存在 —— 原因是它们是 1.20.3+ 引入的，原始输出见 V-3.3。

## V-3.3 原始输出

### 属性修饰符

```
$ javap -cp "$J20" net.minecraft.world.entity.ai.attributes.AttributeModifier
public class net.minecraft.world.entity.ai.attributes.AttributeModifier {
  public net.minecraft.world.entity.ai.attributes.AttributeModifier(java.lang.String, double, net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation);
  public net.minecraft.world.entity.ai.attributes.AttributeModifier(java.util.UUID, java.lang.String, double, net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation);
  public net.minecraft.world.entity.ai.attributes.AttributeModifier(java.util.UUID, java.util.function.Supplier<java.lang.String>, double, net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation);
  public java.util.UUID getId();
  public java.lang.String getName();
  public net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation getOperation();
  public double getAmount();
  public net.minecraft.nbt.CompoundTag save();
  public static net.minecraft.world.entity.ai.attributes.AttributeModifier load(net.minecraft.nbt.CompoundTag);
}

$ javap -cp "$J20" 'net.minecraft.world.entity.ai.attributes.AttributeModifier$Operation'
public final class ...$Operation extends java.lang.Enum<...$Operation> {
  public static final ...$Operation ADDITION;
  public static final ...$Operation MULTIPLY_BASE;
  public static final ...$Operation MULTIPLY_TOTAL;
  public int toValue();
  public static ...$Operation fromValue(int);
}

$ javap -cp "$J20" net.minecraft.world.entity.ai.attributes.AttributeInstance | grep -i modifier
  public java.util.Set<AttributeModifier> getModifiers(AttributeModifier$Operation);
  public java.util.Set<AttributeModifier> getModifiers();
  public AttributeModifier getModifier(java.util.UUID);
  public boolean hasModifier(AttributeModifier);
  public void addTransientModifier(AttributeModifier);
  public void addPermanentModifier(AttributeModifier);
  public void removeModifier(AttributeModifier);
  public void removeModifier(java.util.UUID);
  public boolean removePermanentModifier(java.util.UUID);
  public void removeModifiers();

$ javap -cp "$J20" net.minecraft.world.entity.LivingEntity | grep -E "getAttribute\("
  public AttributeInstance getAttribute(net.minecraft.world.entity.ai.attributes.Attribute);
```

1.21.1 侧（同样命令换 `$J21`）：

```
public final class ...AttributeModifier extends java.lang.Record {
  public ...AttributeModifier(net.minecraft.resources.ResourceLocation, double, ...$Operation);
  public boolean is(net.minecraft.resources.ResourceLocation);
  public net.minecraft.resources.ResourceLocation id();
  public double amount();
  public ...$Operation operation();
}
  public static final ...$Operation ADD_VALUE;
  public static final ...$Operation ADD_MULTIPLIED_BASE;
  public static final ...$Operation ADD_MULTIPLIED_TOTAL;

  public AttributeModifier getModifier(net.minecraft.resources.ResourceLocation);
  public boolean hasModifier(net.minecraft.resources.ResourceLocation);
  public void addOrUpdateTransientModifier(AttributeModifier);
  public void addOrReplacePermanentModifier(AttributeModifier);
  public boolean removeModifier(net.minecraft.resources.ResourceLocation);

  public AttributeInstance getAttribute(net.minecraft.core.Holder<...Attribute>);
```

### 战利品表

```
$ javap -cp "$J20" net.minecraft.server.MinecraftServer | grep -i loot
  public net.minecraft.world.level.storage.loot.LootDataManager getLootData();

$ javap -cp "$J20" net.minecraft.world.level.storage.loot.LootDataResolver
public interface net.minecraft.world.level.storage.loot.LootDataResolver {
  public abstract <T> T getElement(LootDataId<T>);
  public default <T> T getElement(LootDataType<T>, net.minecraft.resources.ResourceLocation);
  public default <T> java.util.Optional<T> getElementOptional(LootDataId<T>);
  public default net.minecraft.world.level.storage.loot.LootTable getLootTable(net.minecraft.resources.ResourceLocation);
}

$ javap -cp "$J20" net.minecraft.world.level.storage.loot.LootTable | grep getRandomItems
  public it.unimi.dsi.fastutil.objects.ObjectArrayList<ItemStack> getRandomItems(LootParams);
  public it.unimi.dsi.fastutil.objects.ObjectArrayList<ItemStack> getRandomItems(LootParams, long);

$ javap -cp "$J20" 'net.minecraft.world.level.storage.loot.LootParams$Builder'
  public LootParams$Builder(net.minecraft.server.level.ServerLevel);
  public LootParams create(...parameters.LootContextParamSet);
```

1.21.1 侧：

```
  public net.minecraft.server.ReloadableServerRegistries$Holder reloadableRegistries();
  public LootTable getLootTable(net.minecraft.resources.ResourceKey<LootTable>);
  # getRandomItems 与 LootParams$Builder 与 1.20.1 逐字相同
```

### 计分板

```
$ javap -cp "$J20" net.minecraft.world.scores.Scoreboard | grep -i playerscore
  public boolean hasPlayerScore(java.lang.String, Objective);
  public net.minecraft.world.scores.Score getOrCreatePlayerScore(java.lang.String, Objective);
  public java.util.Collection<Score> getPlayerScores(Objective);
  public void resetPlayerScore(java.lang.String, Objective);

$ javap -cp "$J20" net.minecraft.world.scores.Score
public class net.minecraft.world.scores.Score {
  public net.minecraft.world.scores.Score(Scoreboard, Objective, java.lang.String);
  public void add(int);
  public void increment();
  public int getScore();
  public void reset();
  public void setScore(int);
  public java.lang.String getOwner();
  public boolean isLocked();
  public void setLocked(boolean);
}

$ javap -cp "$J20" net.minecraft.world.scores.ScoreAccess
Error: class not found: net.minecraft.world.scores.ScoreAccess
$ javap -cp "$J20" net.minecraft.world.scores.ScoreHolder
Error: class not found: net.minecraft.world.scores.ScoreHolder
```

1.21.1 侧：

```
  public ScoreAccess getOrCreatePlayerScore(ScoreHolder, Objective);
  public ScoreAccess getOrCreatePlayerScore(ScoreHolder, Objective, boolean);
  public ReadOnlyScoreInfo getPlayerScoreInfo(ScoreHolder, Objective);

public interface net.minecraft.world.scores.ScoreAccess {
  public abstract int get();
  public abstract void set(int);
  public default int add(int);
  public default int increment();
  public default void reset();
  public abstract boolean locked();
  public abstract void unlock();
  public abstract void lock();
  public abstract net.minecraft.network.chat.Component display();
  public abstract void numberFormatOverride(net.minecraft.network.chat.numbers.NumberFormat);
}
```

### 物品能力

```
$ javap -cp "$J20" net.minecraftforge.common.capabilities.ForgeCapabilities | grep ITEM_HANDLER
  public static final Capability<net.minecraftforge.items.IItemHandler> ITEM_HANDLER;

$ javap -cp "$J20" net.minecraftforge.common.capabilities.ICapabilityProvider
  public abstract <T> LazyOptional<T> getCapability(Capability<T>, net.minecraft.core.Direction);
  public default <T> LazyOptional<T> getCapability(Capability<T>);
```

1.21.1 侧：

```
public final class net.neoforged.neoforge.capabilities.Capabilities$ItemHandler {
  public static final BlockCapability<net.neoforged.neoforge.items.IItemHandler, Direction> BLOCK;
  public static final EntityCapability<net.neoforged.neoforge.items.IItemHandler, java.lang.Void> ENTITY;
  public static final EntityCapability<...IItemHandler, Direction> ENTITY_AUTOMATION;
  public static final ItemCapability<...IItemHandler, java.lang.Void> ITEM;
}
  public T getCapability(Level, BlockPos, BlockState, BlockEntity, C);
```

两支的 `IItemHandler` 方法名相同（`getSlots()` / `getStackInSlot(int)`），
**但类在不同的包里**：`net.minecraftforge.items` vs `net.neoforged.neoforge.items`。

## V-3.4 四个门面的统一签名建议

统一原则：**门面的参数与返回值里不许出现任何一支专有的类型**。
`ItemStack` / `BlockPos` / `Direction` / `ResourceLocation` / `ServerPlayer` / `ServerLevel`
两支同名同包，可以直接出现在签名里；`Holder<Attribute>`、`ResourceKey<LootTable>`、
`ScoreHolder`、`LazyOptional`、两个 `IItemHandler` 都不行。

### `PlayerAbilities`

```java
public final class PlayerAbilities {
    /** 三个取值与两支的枚举一一对应，映射表见实现。 */
    public enum Op { ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL }

    public static boolean grant(ServerPlayer p, ResourceLocation attributeId,
                                ResourceLocation modifierId, double amount, Op op, boolean permanent);
    public static boolean revoke(ServerPlayer p, ResourceLocation attributeId, ResourceLocation modifierId);
    public static boolean has(ServerPlayer p, ResourceLocation attributeId, ResourceLocation modifierId);
    public static void setMayFly(ServerPlayer p, boolean mayFly);
}
```

三处要点：

1. **标识统一用 `ResourceLocation`**。1.20.1 那侧把它降成 UUID：
   `UUID.nameUUIDFromBytes(modifierId.toString().getBytes(UTF_8))`，`name` 直接用
   `modifierId.toString()`。必须是确定性映射 —— 随机 UUID 会让重启后删不掉自己加的修饰符。
2. **`revoke` 统一返回 `boolean`**。1.20.1 的 `removeModifier(UUID)` 返回 void，
   实现里先 `getModifier(uuid) != null` 取到结果再删。
3. **属性本身也要按标识取**。1.20.1 是 `getAttribute(Attribute)`，1.21.1 是
   `getAttribute(Holder<Attribute>)`，两支都从各自的注册表按 `ResourceLocation` 查出来再调。
   `setMayFly` 两支同形（`Player.getAbilities()` 逐字相同），放进来只是为了让"持续状态"
   这一类都走同一个门面。

### `LootAccess`

```java
public final class LootAccess {
    public static List<ItemStack> roll(ServerLevel level, ResourceLocation tableId);
}
```

只有查表那一步是缝：1.20.1 `server.getLootData().getLootTable(id)`，
1.21.1 `server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id))`。
掷的那一步（`LootParams$Builder(ServerLevel)` + `create(paramSet)` + `getRandomItems(params)`）
两支逐字相同，可以整段写在 `shared/`。返回 `List<ItemStack>` 而不是
`ObjectArrayList`：后者是 fastutil 的类型，虽然两支都有，但没有理由把第三方容器
写进门面签名。

### `Scores`

```java
public final class Scores {
    public static int  get(ServerPlayer p, String objective);
    public static void set(ServerPlayer p, String objective, int value);
    public static int  add(ServerPlayer p, String objective, int delta);
    public static void reset(ServerPlayer p, String objective);
}
```

两处要点：

1. **持有者统一传 `ServerPlayer`**。1.20.1 那侧取 `p.getScoreboardName()` 拿 String，
   1.21.1 那侧 `ServerPlayer` 本身就是 `ScoreHolder`。门面签名里不出现 `ScoreHolder`
   ——那个类在 1.20.1 上不存在。
2. **`add` 统一返回加完之后的值**。1.21.1 的 `ScoreAccess.add(int)` 直接返回 int，
   1.20.1 的 `Score.add(int)` 返回 void，实现里加完再 `getScore()`。
   `lock`/`display`/`numberFormatOverride` 是 1.20.3+ 才有的，**不进门面**。

### `ItemCaps`

```java
public final class ItemCaps {
    /** 只读视图。两支的 IItemHandler 在不同的包里，不能出现在签名上。 */
    public interface ItemView {
        int size();
        ItemStack get(int slot);
    }

    public static Optional<ItemView> at(ServerLevel level, BlockPos pos, @Nullable Direction side);
}
```

**这个门面的返回值不能是 `IItemHandler`** —— 1.20.1 是 `net.minecraftforge.items.IItemHandler`，
1.21.1 是 `net.neoforged.neoforge.items.IItemHandler`，同名不同包，共享代码里一提到就编不过。
`ItemView` 只暴露 `§16.5` 的 `ctx.container.read(pos)` 真正需要的两个方法。
返回 `Optional` 而不是可空值，是为了同时盖住 1.20.1 的 `LazyOptional`（`.resolve()`）
与 1.21.1 的可空返回。

---

# V-4 Mozilla Rhino 与 KubeJS 共存

## V-4.0 结论

**支持，条件有三条。**

| # | 条件 | 依据 |
|---|---|---|
| 1 | **Mozilla Rhino 必须以"模组可见"的方式随包分发（`jarJar`），只放进运行时 classpath 不够** | V-4.4 第三项：`rhino-1.9.1.jar` 确实在游戏 JVM 的 `-cp` 上，但模组侧类加载器看不见它 |
| 2 | **shade / jarJar 时剥掉 `rhino-1.9.1.jar` 里的 `module-info.class`** | V-4.4 第二项 |
| 3 | KubeJS 的脚本层看不到 Mozilla Rhino，也不需要看到 —— 我们的引擎在 Java 侧起，不经过 KubeJS | V-4.4 第三项 |

**没有观察到任何重复类、split package 或模块层冲突。** 两支 Rhino 的包名不同
（`org.mozilla.javascript` vs `dev.latvian.mods.rhino`），两个 jar 同时在 `-cp` 上，
服务端与客户端都正常起到底。

⚠ **一项未能实证，原因明确**：见 V-4.5。

## V-4.1 环境（改了什么）

独立 worktree `/root/projects/mcphone-v4`（`origin/main` 的 `2e56034`），只改
`platforms/1.21.1-neoforge/build.gradle`，全部走 `localRuntime`——按任务书要求
不进发布产物的依赖表：

```groovy
repositories {
    maven { name = "saps.dev"; url = "https://maven.saps.dev/releases"
            content { includeGroup "dev.latvian.mods"; includeGroup "dev.latvian.apps" } }
    maven { name = "jitpack";  url = "https://jitpack.io"
            content { includeGroup "com.github.rtyley" } }
}

dependencies {
    localRuntime("dev.latvian.mods:kubejs-neoforge:2101.7.2-build.377") {
        exclude group: "dev.latvian.mods", module: "better-advanced-tooltips"
    }
    localRuntime "org.mozilla:rhino:1.9.1"
}
```

两处是环境适配，不是共存验证的一部分：

- `better-advanced-tooltips` 在 saps.dev 上是 404，排除掉，否则整个运行配置解析不出来。
- **本机是 aarch64**，而 Minecraft 1.21.1 的依赖表里只有 `lwjgl:3.3.3:natives-linux`（x86_64），
  且 `org.lwjgl` 被排他地路由到 `libraries.minecraft.net`，加 Maven Central 也取不到 arm64
  那一份。做法是把 8 个 `natives-linux-arm64` jar 从 Maven Central 取下来解出 `.so`，
  再给 client 运行配置加一行 `systemProperty 'org.lwjgl.librarypath', '<解出目录>'`。
  **这一条只对 arm64 核实机成立，x86_64 开发机不需要。**

两个最小脚本（`run/kubejs/`）：

```js
// startup_scripts/v4_probe.js —— 只用 console.info：KubeJS 会把 console.error
// 收进 "startup script errors" 并抛致命异常，服务端会直接在 mod loading 阶段崩掉。
console.info('V4-PROBE kubejs-startup-ran');
function probe(name) {
    try { Java.loadClass(name); console.info('V4-PROBE load-ok   ' + name); }
    catch (e) { console.info('V4-PROBE load-fail ' + name + ' :: ' + e); }
}
probe('dev.latvian.mods.rhino.Context');   // KubeJS 自己的 fork
probe('com.november.mcphone.MCphone');     // 本模组（走 fml.modFolders）
probe('fr.delthas.javamp3.Sound');         // 在 -cp 上的普通库（对照组）
probe('org.mozilla.javascript.Context');   // 正题
```

```js
// server_scripts/v4_server.js
console.info('V4-PROBE kubejs-server-script-ran');
```

`run/eula.txt` 置 `eula=true`（1.20.1 的 dev 运行目录里本来就是这个值）。

## V-4.2 runServer

```bash
cd /root/projects/mcphone-v4/platforms/1.21.1-neoforge
sh ./gradlew runServer --console=plain      # gradlew 在仓库里没有执行位，用 sh 调
```

```
[14:14:48] [modloading-worker-0/INFO] [KubeJS Startup/]: startup_scripts:v4_probe.js#4: V4-PROBE kubejs-startup-ran
[14:14:48] [modloading-worker-0/INFO] [KubeJS Startup/]: startup_scripts:v4_probe.js#9: V4-PROBE load-ok   dev.latvian.mods.rhino.Context
[14:14:48] [modloading-worker-0/INFO] [KubeJS Startup/]: startup_scripts:v4_probe.js#9: V4-PROBE load-ok   com.november.mcphone.MCphone
[14:14:48] [modloading-worker-0/INFO] [KubeJS Startup/]: v4_probe.js#8: V4-PROBE load-fail fr.delthas.javamp3.Sound :: InternalError: Failed to load Java class 'fr.delthas.javamp3.Sound': Class could not be found!
[14:14:48] [modloading-worker-0/INFO] [KubeJS Startup/]: v4_probe.js#8: V4-PROBE load-fail org.mozilla.javascript.Context :: InternalError: Failed to load Java class 'org.mozilla.javascript.Context': Class could not be found!
[14:14:59] [Server thread/INFO] [minecraft/DedicatedServer]: Done (10.860s)! For help, type "help"
[14:14:59] [Server thread/INFO] [KubeJS Server/]: server_scripts:v4_server.js#2: V4-PROBE kubejs-server-script-ran
```

`FATAL` 出现 0 次。

**服务端是我用 SIGTERM 停的，所以 gradle 报非零退出码** —— 不是启动失败。
NeoGradle 的 `runServer` 不转发 stdin，`stop` 送不进去；`Done (10.860s)` 与随后的
`V4-PROBE kubejs-server-script-ran` 是"起到底了"的判据。

## V-4.3 runClient

```bash
Xvfb :99 -screen 0 1280x720x24 &
cd /root/projects/mcphone-v4/platforms/1.21.1-neoforge
DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 sh ./gradlew runClient --console=plain
```

```
[14:22:59] [pool-2-thread-1/INFO] [EARLYDISPLAY/]: GL info: llvmpipe (LLVM 21.1.8, 128 bits)
                                   GL version 4.5 (Core Profile) Mesa 26.0.8-1ubuntu0.3, Mesa
[14:26:03] [Render thread/INFO] [minecraft/Minecraft]: Backend library: LWJGL version 3.3.3+5
[14:26:05] [modloading-worker-0/INFO] [KubeJS Startup/]: v4_probe.js#4: V4-PROBE kubejs-startup-ran
[14:26:05] [modloading-worker-0/INFO] [KubeJS Startup/]: v4_probe.js#9: V4-PROBE load-ok   dev.latvian.mods.rhino.Context
[14:26:05] [modloading-worker-0/INFO] [KubeJS Startup/]: v4_probe.js#9: V4-PROBE load-ok   com.november.mcphone.MCphone
```

客户端起到了界面渲染循环。判据是线程栈（`jcmd <game pid> Thread.print`）：

```
"Render thread" #1 prio=10 cpu=41288.49ms elapsed=437.38s runnable
   java.lang.Thread.State: RUNNABLE
	at org.lwjgl.opengl.GL11C.nglDrawElements(org.lwjgl.opengl@3.3.3+5/Native Method)
	at com.mojang.blaze3d.systems.RenderSystem.drawElements(minecraft@1.21.1/RenderSystem.java:440)
	at net.minecraft.client.renderer.PostPass.process(minecraft@1.21.1/PostPass.java:86)
	at net.minecraft.client.renderer.GameRenderer.processBlurEffect(minecraft@1.21.1/GameRenderer.java:359)
	at net.minecraft.client.gui.screens.Screen.renderBlurredBackground(minecraft@1.21.1/Screen.java:390)
	at net.minecraft.client.gui.screens.Screen.render(minecraft@1.21.1/Screen.java:133)
```

日志在资源加载后停止增长，看着像卡死，实际是 **1.21 菜单背景的模糊后处理在 llvmpipe
上极慢**（Render thread 41 秒 CPU 还在同一帧里）。这是软件渲染的代价，与 KubeJS / Rhino 无关。

一处噪声：`UnsatisfiedLinkError: Native library (linux-aarch64/libflite.so) not found` ——
旁白（`text2speech`）在 arm64 上没有 native，Minecraft 自己接住了，不影响启动。

## V-4.4 三项检查

**① 重复类 / split package —— 没有。**
两支 Rhino 的包名不同：Mozilla 是 `org.mozilla.javascript`，KubeJS 的 fork 是
`dev.latvian.mods.rhino`。两个 jar 同时在游戏 JVM 的 `-cp` 上（从 `/proc/<pid>/cmdline` 读出）：

```
/root/.gradle/caches/.../dev.latvian.mods/kubejs-neoforge/2101.7.2-build.377/.../kubejs-neoforge-2101.7.2-build.377.jar
/root/.gradle/caches/.../org.mozilla/rhino/1.9.1/.../rhino-1.9.1.jar
/root/.gradle/caches/.../dev.latvian.mods/rhino/2101.2.7-build.81/.../rhino-2101.2.7-build.81.jar
```

服务端与客户端的日志里都没有 duplicate / split package / 模块层相关的报错。

**② `module-info.class` —— 确实存在，dev 运行里不生效，产物里必须剥掉。**

```
$ python3 -c "import zipfile; z=zipfile.ZipFile('.../rhino-1.9.1.jar'); \
              print([n for n in z.namelist() if 'module-info' in n])"
['module-info.class']
```

dev 运行把它挂在 `-cp` 上（命令行里是 `-cp`，模块路径参数是 `ALL-MODULE-PATH`），
**类路径上的 `module-info.class` 会被 JVM 忽略**，所以这次一次冲突都没出现。

**处理方式写死：shade / jarJar 打包 Mozilla Rhino 时排除 `module-info.class`。**
理由是产物 jar 走的是另一条路——NeoForge 用 `securejarhandler` 把模组 jar 放进模块层，
带 `module-info` 的库会被当作具名模块，它的包只在被 `exports` 时对别的层可见。剥掉之后
按自动模块/类路径处理，与 KubeJS 的 fork（包名不同）不会撞。
⚠ 这一条是**依据机制作出的决定，不是本轮实测**：本轮只跑了 dev 运行，没有打产物 jar 验证。
S13 打第一个带 Rhino 的产物时必须复验一次。

**③ 两个引擎各自初始化 —— KubeJS ✅，Mozilla Rhino ❌ 未实证。**

KubeJS 的引擎确实起来了并执行了脚本：startup 与 server 两类脚本在服务端与客户端都跑到，
且 `dev.latvian.mods.rhino.Context` 能加载。

Mozilla Rhino **没能从 KubeJS 脚本里初始化**，原因是 KubeJS 的脚本类加载器看不见普通
classpath 库，不是 JVM 里没有这个类。对照组是决定性的：

| 类 | 来源 | KubeJS 脚本能否加载 |
|---|---|---|
| `dev.latvian.mods.rhino.Context` | KubeJS 自己的依赖 | ✅ |
| `com.november.mcphone.MCphone` | 本模组（`-Dfml.modFolders`） | ✅ |
| `fr.delthas.javamp3.Sound` | **在 `-cp` 上的普通库** | ❌ `Class could not be found!` |
| `org.mozilla.javascript.Context` | **在 `-cp` 上的普通库** | ❌ `Class could not be found!` |
| `java.lang.System` | JDK | ❌ `Class is not allowed by class filter!`（**报错文案不同**，说明上面两条是真找不到，不是被过滤） |

`javamp3` 是 `implementation` 依赖、确实在 `-cp` 上，却和 Mozilla Rhino 一样"找不到"——
**在 `-cp` 上 ≠ 模组侧类加载器看得见**。这正是结论里的条件 1。

## V-4.5 未实证的那一项

「Mozilla Rhino 在同一个 JVM 里初始化」**没有直接证据**，只证到了「它的 jar 在同一个
JVM 的 `-cp` 上且不与 KubeJS 冲突」。

原因是任务约束「不改任何产品代码」与这项验证互斥：JVM 内唯一不写 Java 就能执行代码的
入口是 KubeJS 脚本，而它恰恰看不见这个类。

**这一项并进 S13 补**（PM 2026-09-14 定）：打第一个带 Rhino 的产物 jar 时，连同
V-4.0 的条件 1、2 一起验 —— `Context.enter()` → `initStandardObjects()` →
`evaluateString`。那时 Rhino 本来就要以 `jarJar` 进产物，走的是真实分发路径，
比在 dev 运行里造一次性探针更接近实情。

**对 R0 → P0 闸（§27.2）**：本项已有明确结论（「支持 + 三个条件」），不是「没查」。
