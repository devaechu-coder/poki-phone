# S15b 核实记录：scoreboard 货币 provider

施工方案 §22.7 的 scoreboard 一行、§18.6。**这一档的卖点不在技术，在互通**：
服主不装任何经济模组就有一本账，而且用原版 `/scoreboard` 就能看、能改，
积分与签到天数这些他本来就想用计分板管的东西因此不会出现两个真相。

## 一、两版计分板 API 是查出来的，不是照文档写的

用 `javap` 在两个目标实际编译所对的 jar 上核过：

| | 1.20.1 | 1.20.3+ |
|---|---|---|
| 取分值 | `getOrCreatePlayerScore(String, Objective)` → `Score.getScore/setScore` | `getOrCreatePlayerScore(ScoreHolder, Objective)` → `ScoreAccess.get/set` |
| 建目标 | `addObjective(…, RenderType)` 四参 | `addObjective(…, RenderType, boolean, NumberFormat)` 六参 |
| 有没有 | `hasObjective(String)` | **没有**，只能 `getObjective(name) != null` |
| 列全部 | `getPlayerScores(Objective)` → `Collection<Score>` | `listPlayerScores(Objective)` → `Collection<PlayerScoreEntry>` |
| 只读检查 | `setScore` **一句都没有** | `set` 第一句就抛 `IllegalStateException` |

objective 名没有长度限制（两版 `addObjective` 除「重名就抛」外无校验），
但 `/scoreboard` 的参数是不带引号的 word，所以名字仍然限在 `[_a-z0-9]`。

## 二、分层

新建版本层 `1.20.3+`，里面只有 `Scores` 一个门面。三个 1.21.1 目标挂它，
1.20.1-forge 在 `platforms/` 下自带一份 —— **三个平台类型，两份代码**。

不并进现有 `1.20.5+` 的理由：这条边界比那条早两个版本，并进去的话将来一个
1.20.3/1.20.4 目标就拿不到这批代码，而它那儿这套 API 是有的。
依据是 `PLATFORM-SEAMS.md` 的原话：「宁可层多：层多不产生成本，复制才产生成本」。

**门面不把 `ScoreAccess` / `ScoreHolder` 透出去**：透出去就把版本差异漏进调用方，
共用代码一旦 import 到它们，1.20.1 当场编不过。对外只有字符串、int 与 UUID。

四份要与代码同步的东西都已同步：`versions/layers.json`、`versions/targets.json`（手写），
`shared/PLATFORM-SEAMS.md`、`versions/platform-twins.json`（生成器重写，没手改一个字）。

## 三、对抗查出来的九条（本轮已修）

派了两路对抗，**在开 PR 之前**。九条里有三条是这一档独有、两条连 `BuiltinProvider` 一起中招。

### 1. `transfer(X, X, n)` 凭空造币

两端的余额先读成两个快照再分别写回。`from == to` 时第二笔写用的是没扣过款的快照，
把扣款整个覆盖掉 —— 净效果是余额翻倍。脚本侧一句
`ctx.currency.pay(id, ctx.player.uuid, ctx.currency.balance(id))` 就能反复调用。

**改法**：`from.equals(to)` 当场 INVALID。

### 2. 两个 UUID 落到同一个持有者名

这一档按玩家名记账、判定按 UUID。改过名之后档案缓存里的旧 UUID 仍指着现在被别人占用的名字，
于是 `from != to` 但 `nameOf(from) == nameOf(to)` —— 与上一条同一个洞，不必自己转自己。

**改法**：解析出名字之后再比一次，撞了就 INVALID。

### 3. 两种货币混成一本账

`objectiveFor` 原本只取 `currency.id().getPath()`，于是 `server:coin` 与 `shop:coin`
落到同一个 objective：在便宜的那种上 mint、在贵的那种上花掉。
把 namespace 拼进来还不够 —— 字符集只有 `[_a-z0-9]`，`a:b_c` 与 `a_b:c` 归一后仍然一样。

**改法**：名字带 namespace，末尾再缀四位完整 id 的 SHA-256。仍然是能敲的 word。

### 4. `settle` 不比对货币

一本 `EscrowLedger` 可以管多种货币（`Entry` 带着 `currencyId`、`held(currencyId)` 按货币分开算）。
不比对的话，把 A 币的托管号递给 B 币的 provider 就是 **A 币销毁、等额铸出 B 币**。

**改法**：`id().equals(e.currencyId())` 不成立就 UNKNOWN_ESCROW。

### 5. 只读 objective：两个版本行为相反

服主先敲过 `/scoreboard objectives add mcphone_eco_… health` 的话，这个 objective
存在但改不动。1.20.1 的 `setScore` **静默写进去**（而原版每 tick 会用血量覆盖它 ——
一笔交易都没有，余额自己在变）；1.20.3+ 的 `set` 抛异常，而当时一个 `try` 都没有。

**改法**：门面加 `writable()`，`ensureObjective` 判 criteria 是不是只读，
两边一致地报「用不了」。

### 6. `settle` 先标结算、后写分值

`Scores.set` 一抛（第 5 条正是触发点），托管已经标成结算、钱一分没到账，
异常还穿过 `synchronized` 逃出去 —— **连流水都不进**，那笔钱查无对证；重试只会拿到
`ALREADY_SETTLED`。

**改法**：先写分值、后标结算，整段在同一把锁里；写失败就什么都没动，这一笔可以重来。
另外加了 `write()` 包一层，把写不进去变成 `false` 而不是让异常逃出去 ——
`transfer` 的第二笔写失败还会把第一笔按读到的原值退回去。

### 7. `amount <= 0` 不是「一律 INVALID」

可用性检查排在金额检查之前，于是服务器没起来时一笔 `amount = -1` 返回 UNAVAILABLE ——
调用方以为重试就能过，而它永远过不了。

**改法**：金额判定提到最前面，四条路都是。

### 8. 读余额有副作用

`getOrCreatePlayerScore` 顾名思义会把这个人建进计分板。于是「查一次余额」就把从没交易过的
玩家写进存档、出现在 `/scoreboard players list` 里；1.20.1 那边还会因为新建时的
`setScore(0)` + `forceUpdate` 给全服发一个同步包并把存档标脏。

**改法**：1.20.3+ 走 `getPlayerScoreInfo`（只读），1.20.1 先问 `hasPlayerScore`。

### 9. `displayAutoUpdate` 传错

我传了 `true`，而原版 `/scoreboard objectives add` 传的是 `false`（其字节码是 `iconst_0`）。
传 true 的话每次写分值都会把这一条的显示名强行写成朴素玩家名，
**覆盖服主用 `/scoreboard players display name` 设过的文本** —— 而这一档的全部卖点
就是不跟服主的操作打架。

**改法**：传 `false`。`NumberFormat` 传 `null` 是原版常态，这一条没问题。

### 顺带：`all()` 的次序

1.20.1 的 `getPlayerScores` 末尾按分值排过，1.20.3+ 的 `listPlayerScores` 直接给哈希序，
而门面返回 `LinkedHashMap`。两边都改成按持有者名排。
**这个方法眼下没有调用方**：守恒对账（§22.10 / §22.12）要用它，但那条路还没接。

## 四、对抗查了但**没有**问题的

- **int 边界**：`checkCreditInt` / `checkDebitInt` 穷举 + 随机 600 万组，违例 0。
  返回 OK 时 `(int)` 写回一定精确，即使调用方把 `Long.MAX_VALUE` 当 maxBalance 传进来也不漏。
- **`scriptWritable` 的绕法**：15 种命名空间（含 `mcphone`、`MCPHONE`、带土耳其语 İ/ı 的变体、
  前后带空格、空串）去写货币 objective，一个都没返回 true。
- **重复放款**：`release` 两次、`release` 后 `refund`、未知托管号、null 托管号 —— 全部正确。
- **`max = 0` 的语义**：`clampMax` 与 `effectiveMax` 两处一致（都是「用这一档自己的上限」）。

## 五、并发守恒这条判据在这一档退化了

`onServerThread` 那道闸要求调用发生在服务端主线程上。所以 §22.12 那种
**多线程 1000 次 transfer 在真服上什么也不会发生** —— 实测收到一千个 `UNAVAILABLE`，总额不变。

把线程闸拆掉之后，同一个 provider 实例的 1000 次并发 transfer 守恒；
但**两个 provider 实例各拿各的锁写同一个 objective，1000 次里丢了 11 单位** ——
那把 `lock` 是实例字段，只在单个实例内成立。

**所以这一档真正保证守恒的是 `onServerThread`，不是那把锁。** 写在这里，
免得将来有人看到 `synchronized` 就以为它管跨实例的并发。

## 六、这一步我做不到的

| 判据 | 状态 |
|---|---|
| `provider = "scoreboard"` 从 `mcphone-server.toml` 真的读出来 | ❌ **`[[economy.currency]]` 这一段根本还没有** —— S15 的四种 provider 全是代码里 `register` 的。七个字段的解析与分派已经做成 `CurrencySpec`（纯函数、可测），但把这一段接进各加载器的 ServerConfig 没做：那是 Forge/NeoForge 的 `ForgeConfigSpec` 加 array-of-tables，Fabric 那边连 ServerConfig 都没有，而且这件事对 builtin 与 scoreboard 是同一件事 |
| 命名空间隔离的「脚本尝试写 → 被拒」 | ❌ **§18.6 的脚本侧计分板 API 还不存在**，没有东西在调 `scriptWritable`。这条目前是一条命名不变量，断言钉住了货币 objective 过不了那个判据 |
| `/scoreboard objectives list` 看得到、`players get` 随扣款变、服主手改后 App 读到新值 | ❌ **要起服务器** |
| 1000 次并发 transfer 总额不变 | ❌ 要起服务器；而且见第五节，这一档上它测不出东西 |
| 越界 LIMIT / 负数 INVALID 的**真服**复现 | ❌ 要起服务器。纯函数那一半已在 `docs/CurrencyTest.java` 上钉住 |

## 七、发现的、不属于本步的

**`BuiltinProvider.transfer` 有与第 1 条完全相同的洞**
（`balances.get(from)` / `balances.get(to)` 两个快照，再分别 `set` 回去）。
`from == to` 时同样凭空造币，而且 builtin 档**没有 int 上限**兜着。
它在 PR #28 里已经合进 main。任务书写着「不得改动别的 provider」，所以本步没动它。

`settle` 不比对 `currencyId`（第 4 条）同样在 `BuiltinProvider` 里。

## 怎么复现

```
cd platforms/1.20.1-forge   && sh gradlew check        # JAVA_HOME=…java-17
cd platforms/1.21.1-neoforge && sh gradlew check        # JAVA_HOME=…java-21
cd platforms/1.21.1-fabric  && sh gradlew check        # JAVA_HOME=…java-21
```

`docs/CurrencyTest.java` 从 208 条加到 **289 条**，只加没减。
