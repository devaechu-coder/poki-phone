# S13 核实记录：§16.3 的沙箱配方有洞，§16.4 的三张表要改

> 这是一份核实记录，不是设计。**施工方案本身不在本仓库里**，文中的 §编号指它。

对应施工方案 §16.3–§16.7、§15.2、附录 E-5。日期 2026-09-17，分支 `feature/script-engine`。
所有"实测"都是本轮在 `org.mozilla:rhino:1.9.1` 上跑出来的，探针可复现。

## 一、§16.3 的配方照抄之后还有三处漏

§16.3 写着"照抄"。照抄的结果：

```
typeof Script          → function
new Script('40+2')()   → 42        ← 第三个运行时编译入口还在
typeof ArrayBuffer     → function
typeof Int8Array       → function
Map.prototype.zz = 1   → 污染成功
```

`Script` **拿不到 Java**（`new Script('typeof Packages')()` → `undefined`），所以沙箱没破。
但它击穿 §16.3 自己写的那条理由——"运行的代码可以和 OP 审的源码不是一回事，静态审查全部失效"。
§14 的服主审批流建立在"服务端跑的就是审过的那份 `server.js`"上。

### 改法：三个点名名单全部反过来做成枚举

| §16.3 原文 | 改成 |
|---|---|
| `BANNED` 点名删 6 个 | **白名单**：只留脚本用得上的 34 个全局，其余 `getAllIds()` 逐个删 |
| `SEAL` 点名封 10 个 | **枚举**：留下来的每个全局，只要是 `ScriptableObject`，连 `prototype` 一起封 |
| §16.4 ② 点名拦 5 个方法 | **枚举**：`String.prototype`（50 个方法）、`Array.prototype`（40 个）、`JSON` 整个包一层尺寸闸 |

点名的名单每次 Rhino 升版都会漏一批。`Proxy` / `Reflect` 实测本来就不存在，`BANNED` 里那两条是空转。

**两个实现上的坑**（照抄 §16.3 的伪代码会踩）：

- **必须 `getAllIds()` 不是 `getIds()`**：标准全局都是 DONTENUM 的，`getIds()` 一个都看不见，
  照它写等于删除与密封两步整个空转。
- **白名单里放了 `globalThis` 就不能对它 `sealObject()`**：它指的就是顶层 scope 自己，
  封了脚本连 `var` 都声明不了 —— 那正是 §16.3 自己提醒过的那一条。

改完之后实测：八条逃逸路径全关、`Script` / TypedArray / `Promise` 全没了、`Map.prototype` 封住，
而变量 / 箭头函数 / map / join / BigInt / JSON / Math / `actions` 注入 / 正常 `repeat` 一条没坏。

## 二、中断信号必须是 `java.lang.Error`

§16.4 那句"宿主 Error 没被脚本吞掉"只在字面上成立。实测
（`function g(){ try { boom(); } finally { return 'FINALLY_WINS'; } } g()`）：

```
宿主抛 Error            → Error: HOST_ABORT     （吞不掉）
宿主抛 RuntimeException → FINALLY_WINS          ← 中断被吞了
```

Rhino 对 `Error` 给 `EX_NO_JS_STATE`（catch 与 finally 都轮不到），对 `RuntimeException` 给
`EX_FINALLY_STATE`。而"§16.4 三件事 ①"的边界尺寸检查，最自然的写法正好是 `IllegalStateException`。

**改法**：§16.4 加一条纪律——宿主桥里任何"立即中断本次调用"一律抛 `ScriptAbort extends Error`；
业务失败走 `ctx.fail()` 的返回值，不走异常。另：宿主函数不许用 `FunctionObject` 定义
（它把 RuntimeException 转成脚本 catch 得到的 `InternalError`），一律 `LambdaFunction`。

## 三、§16.4 的预算表要改三处

### ① 「50 万指令」要写清单位

`setInstructionObserverThreshold(N)` 的语义是"每累计 N 个**指令单位**回调一次，然后清零"，
回调收到的是**增量**；Rhino 数的是字节码偏移量的差，不是 JS 语句。实测折算：

```
一次最简循环迭代 ≈ 15 个指令单位  →  50 万 ≈ 3.3 万次迭代
```

墙钟精度受 threshold 制约：threshold=10000 时 20 ms 的闸停在 20.2 ms，
threshold=1000000 时停在 27.2 ms。

### ② 「调用栈深度 64 → 中断」是错的

`setMaximumInterpreterStackDepth(64)` 抛的是 `EvaluatorException`，**脚本一 `catch` 就继续跑**，
不是中断。而且它对宿主桥的重入完全无效：宿主里做强制转换会回调脚本的 `valueOf` / `toString`，
每次重入是一次新的解释器调用、深度重新起算，实测能重入到 852 层才 `StackOverflowError`。

**改法**：表里改成"抛 JS 错误，脚本可捕获；真正的硬中断由指令预算兜底"，
并加一条——**宿主桥不做强制转换，只收原语**（给对象直接拒），外加一个宿主重入计数兜底。

### ③ 表里缺一行：跨调用驻留

两道预算都是"每次调用"的，而 App 的 scope 跨调用复用（`actions` 表必须活着）。
实测脚本 `g = g + g;`，每次调用只有个位数条指令、一次都没超预算，**第 26 次调用 OutOfMemoryError**。

**改法**：加一行 `驻留（每 App，跨调用）| 1 MiB | 超出 → 重建 scope + 审计`，
并在 §16.4 ③ 的"剩余风险必须公开"那句里点名"单次调用的预算拦不住跨调用累积"。

### 顺带：§16.4 的"拦不住什么"那张表要补两行

点名的 5 个方法挡不住这些（实测，全程观察器 0 次回调）：

```
new ArrayBuffer(2e8)      19 ms 分配 200 MB
new Int8Array(2e8)        94 ms
JSON.stringify(16M 串)   419 ms
```

前两个按白名单整个删掉（§32.7 那七个 App 一个都用不上），`JSON` 走尺寸闸。

## 四、§16.6 的"禁 5 分钟"主键要改

§16.6 与 §16.7 都写"禁用该 App 的后端 5 分钟"。**按 App 计数的话，任何玩家挑一个吃 CPU 的动作
连调 3 次，就把这个 App 对所有人关 5 分钟** —— §32.7 的"限量抢购"正是最值得这么打的场景。

**改法**：主键改成 `(appId, 玩家)`，谁超预算禁谁；App 级熔断单列一条，
要**不同玩家**各自触发才累计（本步取 5 个不同玩家 / 60 秒窗口）。两处文字都要跟着改。

## 五、`ctx` 怎么注入（§16.5 / §16.7）

实测三种注入方式：

| 做法 | 结果 |
|---|---|
| `NativeJavaObject` + `setClassShutter(n -> false)` | ctx **整个不可用**（Access to Java class prohibited） |
| `NativeJavaObject` + shutter 放行自己的包 | `ctx.player.getClass().getClassLoader()` → **AppClassLoader，完整逃逸** |
| 手工 `NativeObject` + `LambdaFunction`，`setPrototype(null)` + `sealObject()` | 只枚举得出定义过的名字，`getPrototypeOf` 为 null |

危险在于第一种当场不可用会把人推向第二种。**`setClassShutter(n -> false)` 里的 false 是无条件的**，
这句要写进 §16.5。

**§16.7 那条判据要收紧**：不是"ctx 上枚举不出表外方法"，而是
"**`ctx` 与其每一级子对象**，`for..in` / `getOwnPropertyNames` / `getPrototypeOf` 三种枚举
都只给表内名字，且 `getPrototypeOf` 恒为 `null`"。

**还有一条**：后端没到货的 `ctx.*` **不挂属性**，不挂一个返回 `UNAVAILABLE` 的壳。
§16.7 判的是负向判据（枚举不出表外的），少挂不违反它；而多挂一个空壳会让 S14/S15 的实现者
以为授权审查在接口定下来时做过了。**每一个暴露出去的方法都是一次要重做的授权判定。**

## 六、V-4 拆成两半

§16.4.1 那句"必须真做，不能推理代替"只约束下半。

- **V-4a（静态状态互不干扰）—— 已关闭，靠结构论证**：KubeJS 用 `dev.latvian.mods.rhino`，
  与 `org.mozilla.javascript` 是两个包 → 两个 `Context` 类 → 静态字段与 `ThreadLocal` 各自独立，
  `ContextFactory.getGlobal()` 不可能互踩。我们这边全程用自己的 `ContextFactory` 实例，
  一次都不调 `Context.enter()`（它走全局 factory）。
- **V-4b（模块层与加载期共存）—— 未关闭**：NeoForge 的模块层对 jar-in-jar 里带 `module-info` 的库
  怎么处理、两个引擎在真实 mod 加载顺序下各自能否初始化。**只能靠真装 KubeJS 跑 runClient/runServer。**

`module-info` 的内容已读出来：`module org.mozilla.rhino`，requires
`java.base` / `java.compiler` / `jdk.dynalink` / `java.desktop`。
⚠ `java.desktop` 那一条意味着**裁剪过 runtime image 的专用服务器会模块解析失败**。

## 七、本步的验收

| §16.7 + 任务书 | 本步 |
|---|---|
| V-2a/b/c 有结论，语法统一 | ✅ E-5 探针本轮复跑 |
| 后端死循环 → 预算中断 | ✅ 断言测试里有 |
| 后端死循环 → TPS 不掉到 19 以下 | ❌ 要跑服务器。求值在 worker 上不在主线程，所以主线程只花 O(1)，但真 TPS 数字要实测 |
| 连续 3 次超预算 → 禁 5 分钟 | ✅ 逻辑判得了（主键已改成 App×玩家）；"管理界面可见"是 S14 那一步 |
| `typeof java` / `Packages` / `JavaImporter` / `getClass` 全 undefined | ✅ |
| 两个 App 的 scope 互相看不见 | ✅ |
| KubeJS 双端 runClient / runServer | ❌ **V-4b 未关闭** |
| ctx 枚举不出表外方法 | ✅（按收紧后的判据） |
| `ActionEvaluator.submit` 接上 S12 管线 | ✅ 求值线程选择写在类注释里 |
| （E2）ctx.item 枚举：opaque 不可解析不可构造 | ✅ |
| （E2）9007199254740993 精度无损 | ✅ `+1n` → `9007199254740994` |

`docs/ScriptEngineTest.java` 127 条断言，三个目标各跑一遍。

## 怎么复现

```bash
cd platforms/1.20.1-forge && ./gradlew assertTests    # 或 1.21.1-neoforge / 1.21.1-fabric
```
