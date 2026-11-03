# S16b 核实记录：安装界面与签名工具

> 这是一份核实记录，不是设计。**施工方案本身不在本仓库里**，文中的 §编号指它。

对应 §12.4 / §12.5 / §12.6 的可见性那一半。日期 2026-09-17，分支 `feature/sign-ui`。

## 一、这一轮真跑起来的（不只是编过）

### `./gradlew signApp` 端到端

```
$ ./gradlew signApp -PappDir=<样例> -PgameDir=<run> -Pauthor=yumeka -PoutZip=<out.zip>
[signApp] 读到 3 个文件
[signApp] 摘要 0dbf631ccdbaeb4d9feff22de1160ee5558b2f1569c51cdb779f697f6a0bf1b2
[signApp] 作者指纹 ad88-d3ca-61de-73ee
[signApp] 签好了：…/gradle-signed.zip
[signApp] 提醒：签名确认的是「谁做的、有没有被改过」，不是「内容安不安全」。
BUILD SUCCESSFUL in 44s
```

四条失败路径各自报清是哪一步：

```
失败于「读私钥」：还没有作者密钥。先在游戏里「设置 → 开发者 → 我的签名密钥」生成一对…
失败于「读包」：包根缺 manifest.json —— zip 形态的包必须有它（§11.2）
失败于「读包」：<路径>（目录不存在）
```

### 五档在一个真签出来的包上各走一遍

```
① 签名包、空信任库   UNKNOWN_AUTHOR  指纹 ad88-…  能继续 true
② 同一个包、已信任   TRUSTED                      能继续 true
③ 改一字节重压       INVALID         指纹 ad88-…  能继续 false   ← 唯一的硬拒绝
④ 只换 sig.json      KEY_CHANGED     旧 ad88-… → 新 080e-…
     点一下就走？ false      抄对新指纹？ true
⑤ 未签名             UNSIGNED        指纹 null    能继续 true
```

③ 那一条**按判据原文的做法做的**：解开 zip、改内容、压回去，`sig.json` 原样带着。
（第一次我直接在 zip 字节里找 `hi` 去翻转 —— 那找不到，因为 zip 是 DEFLATE 压过的，
字面不在里面。这类"测试看起来通过其实什么都没测"的写法值得记一笔。）

## 二、文案

`SigCopy` 把五条 + `INSTALL_NOTE` 集中一处，`docs/SignCopyTest.java` **49 条断言**里有两组是硬的：

- **逐字比对**：读 `assets/mcphone/lang/zh_cn.json`，与 §12.5 原文逐字对
- **不许出现「安全」**：五条文案里中文查「安全」、英文查 `safe` / `secure`。
  `INSTALL_NOTE` 是例外 —— 它正是用来说"不是内容安不安全"的

确认短语的形态定成**抄新指纹本身**，不是打"确认"两个字。理由：打两个字谁都会打，
而抄一串十六进制必须先把目光挪到指纹那一行 —— 这一档存在的全部意义就是让玩家注意到
"这次不是上次那个人"。断言里钉住了：`"确认"` 不放行、抄成旧指纹不放行、
省掉连字符不放行、大小写与首尾空白不拦人。

## 三、判定只有一处实现

界面**不重判签名**。`LocalScriptSource` 把 §12.4 判好的结果整理成
`AppInfo.Signature`（四格纯字符串）带给 `AppDetail`，界面只渲染。
UI 里再判一遍就会有两份判据，而它们迟早对不上 —— 表现是"界面说能装，装下去被拒"，
或者更糟，反过来。

`AppInfo.Signature` 用纯字符串而不是 `TrustState.Verdict`：`api/` 不该依赖 `core.script.pkg`。

## 四、`signApp` 为什么落在 `gradle/`

`guard-version` 那道闸查的是 `platforms/*/gradle.properties` 里有没有出现
`mod_id|mod_name|mod_license|mod_group_id|mod_version` 五个仓库级的键（核实过它的正文）。
新任务定义在 `gradle/mcphone-sign.gradle`，三个平台各 `apply from` 一行，
**没有往平台的 gradle.properties 里加任何东西**，那道闸碰不到。

**不挂 check / build**：签名要私钥，挂上去等于每次构建都要有一把私钥在场，而 CI 上没有、
也不该有。签名是命令式操作（§12.6）。

**入口住在 `shared/`（第一版放错了）**：本来放在 `docs/` 下，想着开发期工具不必进 jar。
结果 CI 三个目标全红 —— `assertTests` 会自动发现 `docs/` 下每一个带 `main()` 的类并**无参跑一遍**，
它于是打印用法、退出码 2。挪进 `shared/` 成了 `AppSigner`，顺带解决一件事：
游戏里那个「打包并签名」按钮要做的事与它逐字相同，`readDir` / `zip` 两个方法直接用得上。

## 五、对抗查出来的六个洞（本轮补完）

这一节不是"改进"，是**修**。六条都拿探针复现过才动手。

### 1. 不持私钥就能造出「验签通过」的包

JDK 的 Ed25519 不查公钥落在哪个子群。拿**恒等点**当公钥（X.509 外壳 + `0x01||0x00*31`）、
签名取 `0x01||0x00*63`，对任意消息验签恒为 true：

```
JDK 17.0.20  恒等点公钥 + 全零签名，500 条随机消息验过: 500
JDK 21.0.11  同上: 500
```

它伪造不了某个已知作者的指纹，但把那个伪造指纹变成**谁都能签的公共身份**：
玩家对它点过一次确认，此后任何人的任意内容都走 TRUSTED 直装。同一把假钥匙还能伪造
`META/rotate.json`，把信任转到攻击者的真密钥上。

**改法**：`Signatures` 加小阶点判据。Ed25519 上阶整除 8 的点共 8 个，去掉 x 的符号位之后
只剩 5 个 y —— 比 y 而不是比 32 字节编码，因为同一个点有两种写法，比字节会漏掉一半。
`publicKey()` 抛、`verify()` 返 false，两处都拦。断言在 `PackageSignTest.smallOrderForgery`。

### 2. 信任库从来没落过盘

`TrustStore.load/save/record/trust/setBlocked/applyRotation` 在 `shared/` 下**零生产调用方**。
`LocalScriptSource` 那个 `TRUST` 永远是空的，于是 TRUSTED / KEY_CHANGED / 封禁**三档全是不可达分支**，
实际出货的只有 UNSIGNED / UNKNOWN_AUTHOR / INVALID，而前两档都是点一下就装。

**改法**：惰性从 `config/mcphone/authors.json` 读，装成功后 `record` + `trust` + `save`。

**顺带修的**：钉扎改成**单独一段 `pins` 落盘**。原先从 `authors[].apps` 重建，而那张列表有
64 条上限 —— 装到第 65 个 App 就会把第 1 个的钉扎挤掉，那个 App 换一把密钥重签就从
KEY_CHANGED（要抄指纹）掉回 UNKNOWN_AUTHOR（点一下就装）。不落盘的时候这个洞够不着，
一落盘就是真的。断言在 `pinsSurviveAppListCap`。

### 3. `zip -d pkg.zip META/sig.json` 通吃

`TrustState.of` 对未签名包**在查封禁与查钉扎之前**就 return。没有签名就没有指纹，
两条都查不了，于是「换钥要抄指纹」与 §12.7 的吊销一起降级成「点一下就装」。

**改法**：加第六档 `SIGNATURE_REMOVED`（**硬拒绝**）——钉扎过的 appId 拿来一份没签名的，
那是降级，不是「未签名」。没装过的包没签名仍然是普通 `UNSIGNED`，
§12.4 那句「把未签名做成硬拒绝会逼所有人去找绕过办法」仍然成立。

于是**硬拒绝从一档变成两档**。这是对 §12.4 的改动，不是实现细节。

### 4. `registerAll` 连硬拒绝都不查

启动恢复只看「这个 id 在存档的 installed 集合里」，没有 `trustOf`、没有 `blockedReason`。
装过一次之后把 `mcphone/apps/` 里的包换成同 id 的另一份，下次进世界直接跑；
或者开一次商店，`listAvailable` 里的 `adapter()` 直接热替换。

**改法**：两条路都判 `trustOf`，硬拒绝与「要抄指纹」的都不恢复/不替换，日志说明去商店重新确认。

### 5. 签名工具会把私钥签进待发布的包

`AppSigner.readDir` 只跳 `META/`，不跑 §3.4 的路径判据。`gameDir` 默认 `${projectDir}/run`，
而私钥就在它下面的 `config/mcphone/keys/` —— `-PappDir=run` 就把私钥和界面导出的备份
一起签进了那个 zip，stdout 一个字不提。收包方会拒，但那是**别人装的时候**，密钥已经出门了。

**改法**：`readDir` 就地跑 `PathRules.require`。实测：

```
$ ./gradlew signApp -PappDir=<含 config/ 的目录> ...
[signApp] 失败于「读包」：…：E_PKG_BAD_EXT：路径 'config/mcphone/keys/author.key'
          的扩展名不在允许清单里，允许的是：.json .mss .png .txt .vue
（wide.zip 不存在）
```

### 6. 二次确认没有消费者，短语判据有两份

`needsConfirm()` 与 `SigCopy.canProceed` 的调用方都只有 `docs/*Test.java`。界面侧唯一的闸是
`AppDetail.btnEnabled`，而它自己又写了一份 `equalsIgnoreCase`。于是 UNSIGNED 与 UNKNOWN_AUTHOR
**一点就装**，跟已信任作者的包操作完全一样；而 `IAppSource` 是对外接口，
任何拿到 `AppInfo` 的调用方直接调 `install()` 就绕过了整个界面。

**改法**：

- 闸挪进 `LocalScriptSource.install()`：不是 TRUSTED 就必须有一条对得上的确认记录，否则拒。
  **失败的方向是"装不了"，不是"随便装"**。
- 确认这一步走 `IAppSource.confirmSignature`（default 方法，对附属纯加法），
  商店界面因此不必认识脚本子系统。
- `AppDetail` 不再自己比对短语，改问 `SigCopy.phraseAccepted` —— 判据只剩一份。
- UNSIGNED / UNKNOWN_AUTHOR 画一个确认框，不勾按钮不亮。

因为往 `api/` 加了东西，`MCphoneApi.VERSION` 从 4 提到 5 并补了账本行 ——
那是这个文件自己的契约，附属要靠它 feature-detect。

### 顺带：文案铁律原先只盯住 19 条里的 5 条

`noSafetyClaim` 只遍历 `EXPECTED_ZH` 那 5 个键，往 `confirm_prompt` 或任何一条 `key_*`
里写「安全」测试照样绿；lang 资源不在类路径时还会**静默少跑 25 条**、退出码仍是 0。
现在改成扫全部 `mcphone.sig.*`（`install_note` 是唯一例外，它正是用来说"不是内容安不安全"的），
读不到资源直接红，并断言中英条数一致。

## 六、这一步我做不到的

| 判据 | 状态 |
|---|---|
| 各档安装截图（含 INSTALL_NOTE 与指纹） | ❌ **要 runClient** |
| `LocalScriptSource` 那几条新闸的实走（确认框、install 拒绝、registerAll 不恢复） | ❌ **要 runClient**：它们依赖 Minecraft 实例，`docs/` 下测不了 |
| 信任库真的写进 `config/mcphone/authors.json` | ❌ **要 runClient**：落盘路径要 `mc.gameDirectory` |
| 「作者密钥变了」的确认短语输入截图 | ❌ 同上（逻辑已判：`"确认"` 进不去） |
| 「我的签名密钥」页截图 | ❌ 同上 |
| 「已签名」处没有暗示内容安全的图标 | ◐ 代码里确实没画对勾，但"看着像不像"要人看 |
| `./gradlew check` / `build` 行为不变 | ◐ 新任务没挂生命周期、没加仓库级键；**完整 build 我没跑** |
| 1.20.1 支线能签能装 | ◐ 签过了（上面那次就是在 1.20.1-forge 上跑的）；装要 runClient |

**界面代码一次都没渲染过。** 编得过、判定有断言、文案逐字比对过，
但像素、换行、点击热区没人看过 —— `AppDetail` 那一段插在描述与价格之间，
文字多的时候会不会挤掉价格行，只有开一次客户端才知道。

## 怎么复现

```bash
cd platforms/1.20.1-forge
./gradlew assertTests
./gradlew signApp -PappDir=<一个 App 目录> -PgameDir=run -Pauthor=<名字>
```
