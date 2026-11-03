# S11 核实记录：A 档 SDK 定型的完成判据怎么判

> 这是一份核实记录，不是设计。**施工方案本身不在本仓库里**，文中的 §编号指它。

对应施工方案 §23（SDK 清单）、§27.2 的 P0 → P1 闸。日期 2026-09-17，分支 `feature/sdk-a1`。

## 为什么要写这一份

§23.6 列了八条完成判据。**其中七条在 S11 这一步判不了** —— 它们要脚本引擎、要服务端配置、
要 runServer，那些是 S12 / S13 / S14 的交付物。而附录 A 给 S11 标的是"✅ 可 docs 单测"。
两边对不上，照原样打勾就是打一个假的勾。

所以把 §23.6 拆成两段：**契约层**由 S11 判，**运行时层**改挂后面的步骤。
S11 交付的是契约定型，不是契约被正确实现。

## 判不了的根因：docs 测试里没有注册表

`docs/` 下带 `main()` 的断言测试由每个目标编译并运行，跑法是裸 `JavaExec`
（见 `gradle/mcphone-checks.gradle` 的 `assertTests`）。没有 `Bootstrap.bootStrap()`，
于是：

| 造不出来的东西 | 连带判不了的判据 |
|---|---|
| `BuiltInRegistries` | 任何要真物品的判据 |
| `ItemStack` | ItemRef 还原成物品 |
| `ServerPlayer`（要 `MinecraftServer` + `ServerLevel`） | 货币的转账、余额 |
| Rhino 引擎 | 脚本侧的一切 |

全仓现有的断言测试无一例外都是纯计算，这不是本步的特殊情况。

## §23.6-A：S11 判这些（全部已通过）

判据按"这一步真的能证"重写过，不是原文照抄。

| # | 判据 | 落在哪份测试 | 断言数 |
|---|---|---|---|
| A-1 | 版本表 ↔ 11 个 `XxxApi.VERSION` 双射，值都 ≥ 1 | `SdkGateTest` | 62 |
| A-2 | manifest 的 `sdk` 段：段可省、认不出的键照收判给门控、≤0 拒、非整数拒、超 32 项拒 | `SdkGateTest` | 同上 |
| A-3 | 门控：本机低于声明 → 拦；高于声明 → 放行；认不出的键 → 拦 | `SdkGateTest` | 同上 |
| A-4 | 周期标签的纯计算：`daily_at` 分界、跨年周、跨时区、夏令时不存在的时刻 | `TimeCycleTest` | 34 |
| A-5 | 金额：`amount ≤ 0` 拒、溢出返 `LIMIT` 不回绕、format/parse 往返、守恒 | `CurrencyTest` | 85 |
| A-6 | 错误码不复用：`INVALID` / `ALREADY_SETTLED` / `UNKNOWN_ESCROW` / `NOT_AUTHORIZED` 各自存在 | `CurrencyTest`、`MailboxTest` | 85 + 25 |
| A-7 | 句柄形状：32 位小写十六进制，大小写与长度都不放过；27 件一批塞得进 `PARAMS_MAX` | `ItemRefTest` | 32 |
| A-8 | 值类型的不变量：长度、范围、null、字段集合 | 七份都有 | — |
| A-9 | 收件箱要么全成要么全不成 | `MailboxTest` | 25 |
| A-10 | 通知的形状按 §33.2，`read` 不在值类型里，`dedupeKey` 在 | `NotificationTest` | 23 |
| A-11 | `PlayerRef` 只有 `uuid` 与 `name` 两格 | `PlayerRefTest` | 10 |

合计 **271 条**，三个目标各跑一遍（`1.20.1-forge` / `1.21.1-neoforge` / `1.21.1-fabric`）。

## §23.6-B：改挂后面的步骤

| §23.6 原文 | 改挂 | 为什么 |
|---|---|---|
| `opaque` 在脚本侧无法解析也无法构造 | **S13** | 要枚举 `ctx.item`，脚本桥还不存在 |
| 同一个 ItemRef 在两支上都能还原成物品 | **S13** | 要 runServer；且 opaque 改成句柄之后，这条的内容变成"句柄表在各目标上行为一致" |
| 两个不同作者的 App 一个存 Mailbox 一个读 count | **S13** | 契约层已证（一个箱子、全成或全不成），"两个 App" 要脚本运行时 |
| 两个 App 的 `label('weekly')` 落在同一周 | **S13** | 算法层已证，"两个 App" 同上 |
| 9007199254740993 精度无损 | **S13** | Rhino 的 BigInt |
| 改服务器时区 → 分界点同步改变 | **S14** | 算法层已证（A-4 有跨时区断言），"改配置"要服务端配置 |
| `timezone` 留空 → 拒绝启动 | **S14** | 服务端配置与启动流程 |

## 附录 A 那一行

```
| S11 | §23 | api/economy/** api/sdk/** | ✅ |
```

改成：

```
| S11 | §23 | api/economy/** api/sdk/** | ✅（仅契约层，运行时判据见 §23.6-B） |
```

## P0 → P1 闸没有因此放松

§27.2 的 P1 → P2 闸写着"§23.6 A 档六个 SDK 全部定型且有测试"。
**定型与有测试这两件事本步都做到了**，拆的是"判据挂在哪一步"，不是"判据可以不过"。
§23.6-B 那七条一条都没删，只是换了判它的人。

## 怎么复现

```bash
cd platforms/1.20.1-forge && ./gradlew assertTests    # 或 1.21.1-neoforge / 1.21.1-fabric
```

七份测试的类名：`ItemRefTest` `PlayerRefTest` `CurrencyTest` `MailboxTest`
`NotificationTest` `TimeCycleTest` `SdkGateTest`，都在 `docs/` 下。
