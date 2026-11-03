# S15 核实记录：货币 SDK

> 这是一份核实记录，不是设计。**施工方案本身不在本仓库里**，文中的 §编号指它。

对应 §22.1–§22.12、§16.2 的 BigInt 修正、勘误 E13 / E15 / E18。日期 2026-09-17，分支 `feature/economy`。

## 一、⚠ 任务书的「API 形状照 §22.4 一字不改」与已冻结的实现冲突

S11（PR #23，已合并）交付的 `ICurrencyProvider` **不是** §22.4 的字面形状。
差异与理由在 `docs/script-app-s11-verification.md` 与 PR #23 正文里，这里只列结论：

| §22.4 / S15 任务书 | main 上实际 | 为什么不改回去 |
|---|---|---|
| `ServerPlayer` | `UUID` | 卖家下线市场就不成立；且 `ServerPlayer` 在裸 JavaExec 里造不出来，改回去 §22.12 第一条判据当场打不了勾 |
| `Component unavailableReason()` | `String unavailableReasonKey()` | 自由文本是往客户端推任意字符串的路（与 §23.3 对 titleKey 的要求同一条） |
| `EscrowId hold(...)` | `HoldResult hold(...)` | 返回值里放不下失败原因 |
| `TxnResult` 六个值 | 九个值 | 少了 `NOT_AUTHORIZED` 而 §22.5 自己在用它；删码正撞 §23.4 的「只增不减」 |
| `TxnReason(appId, kind, ref)` | `TxnReason(kind, ref)` | 让被监督的一方填写自己是谁，流水就不成其为安全网（§22.10） |

**本步按 main 上的形状实现。** 要回退得先撕掉 S11 的冻结，那是另一个决定。

## 二、勘误收口

| 判据 | 结果 |
|---|---|
| `grep -rn "Emc" core/script/` | **0 处** |
| `grep -rn "PlayerStore"` 全仓 | **0 处** |
| 脚本侧金额没有字符串算术、没有 Number 转换 | ✅ 进出都是 `BigInteger` |

⚠ 为了让第一条为空，provider 的类名从 `EmcLegacyProvider` 改成了 **`LegacyWalletProvider`**。
服主在 `mcphone-server.toml` 里写的 provider 名仍然是 `emc_legacy`（§22.7 那一行不变）——
判据管的是**代码里的旧命名**，不是服主看得见的配置键。

同样为了这条判据，类注释里那句引用 grep 命令的话也改了写法：
注释本身含有那个词就会让判据自噬（S14 那次也踩过同一个坑）。

## 三、E18：脚本侧金额是 BigInt —— 本轮实测确认桥走得通

```
脚本 BigInt('9007199254740993')  → Java java.math.BigInteger
Java 塞 BigInteger 回去          → typeof 是 'bigint'，能直接 + 1n
BigInt 与 Number 混算            → TypeError（引擎自己拦，不用我们挡）
Long.MAX_VALUE + 1n              → Java 侧仍拿得到 BigInteger
```

所以 `Amounts` 在边界切 `BigInteger ↔ long`，**宿主侧一次字符串算术都不做**。
最后一条意味着桥上必须查范围：不查的话 `longValueExact` 会抛，
而那是个宿主异常、会被当成内部错误 —— 它其实是个正常的"金额太大"。

线格式仍然是十进制字符串（JSON 没有 BigInt，§15.3）。三段各归各：
**脚本 BigInt、宿主 long、线上字符串。**

## 四、能纯逻辑判的与要跑服务器的

`docs/CurrencyTest.java` **174 条断言**，其中这几条是 §22.12 点名的：

| §22.12 | 本步 |
|---|---|
| `docs/CurrencyTest.java` 全绿 | ✅ 174 条 |
| **1000 次并发 transfer 后总额不变** | ✅ 8 线程 × 125 次，真起线程跑 |
| `amount ≤ 0` 一律 INVALID 且两侧余额不变 | ✅ 含 `Long.MIN_VALUE` |
| 超 maxBalance 与 `Long.MAX_VALUE` 返回 LIMIT 不回绕 | ✅ |
| 托管受益人定死 / 重复释放退款 | ✅ 重复结算回 `ALREADY_SETTLED` 不是 `FAILED` |
| 托管超时 7 天自动退 | ✅ 逻辑；真"启动时扫"要服务器 |
| audit 改存档后报不平 | ✅ 差额正好等于塞进去的数，且那一行带 ⚠ |
| `emc_legacy` 的 hold 返回 UNAVAILABLE | ✅ 并且 `supportsEscrow` 为假 |
| 没有默认货币时 `default()` 返回 null 不崩 | ✅ |
| `grep Emc` / `grep PlayerStore` 为空 | ✅ |
| 9007199254740993 精度无损 | ✅ BigInt 往返 |
| **transfer 中途强杀 → 重启后钱不消失不翻倍** | ❌ 要服务器 |
| **托管中途重启 → 钱还在且能 release** | ❌ 要服务器（`escrow.dat` 的落盘接到 SavedData 上是平台侧的事） |
| **同一市场 App 在两台配不同货币 id 的服务器上都能跑** | ❌ 要两台服务器 |
| **死亡保留：死后余额仍在** | ❌ 要服务器。代码侧三支都接好了 |

## 五、本步没交付的两种 provider

- **scoreboard**：要一个新的 `platform/Scores` 三平台接缝
  （`ScoreAccess` 是 1.20.3+ 才有，1.20.1 按玩家名取 `Score`）。三个新平台类型 +
  双胞胎基线与接缝清单重生成，而我没法验它在真服上读写记分板对不对。**没做，不是漏了。**
- **adapter**：按移交记录的口径"在真正的目标模组确定前只做接口与单测" ——
  `AdapterProvider` 与它的 `ExternalWallet` 接口都在，托管由我们自己的账兜
  （外部模组大多只有加钱/扣钱/查余额）。真机联调随那个模组的专项。

## 怎么复现

```bash
cd platforms/1.20.1-forge && ./gradlew assertTests    # 或 1.21.1-neoforge / 1.21.1-fabric
```
