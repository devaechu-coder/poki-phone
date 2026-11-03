# MCPhone 附属 UI API 修复报告

日期：2026-10-19（Asia/Hong_Kong）。修改基线：`67640ead07bb0bb33c3dee2a1ed7b3a02b34e97d`。

## 1. 交付范围与结论

本报告已按 2026-10-19 二次复查更新；累计修改 30 个实现及测试文件，另新增本报告。修复此前审查中的输入分发与失败处理问题，并补齐页面布局、更新周期和可选控件宿主。API 代号从 6 升为 7；既有方法和包名保留，新页面能力通过 default 方法追加。

代码及四目标测试已完成。没有运行实际 Minecraft 客户端、专用服务器或 HowToGo 联调，所以不能把编译和无窗口断言结果等同于游戏内验收。本次仅提交 PR，尚未发布；模组发行版本仍为仓库现有的 1.11.2，不手改由发布流程管理的版本号。

## 2. 对应此前 F01–F12 的处理结果

| 编号 | 处理结果 | 具体做法 |
|---|---|---|
| F01 非左键过滤 | 已修复 | 在内建页面的非左键过滤之前，为附属内容区分发右键、中键及其他按钮。内建页面原有规则保留。 |
| F02 缺少拖动/释放 | 已补齐 | 新增 mouseDragged、mouseReleased、mouseMoved 默认回调。按按钮建立捕获；通过 mouseMoved 产生所有捕获按钮的逻辑拖动，不依赖原版单一 activeButton/左键组件拖动状态。 |
| F03 消费语义不一致 | 已统一 | mouseClicked 的 true 用于处理与捕获，false 不捕获；宿主仍屏蔽内容区空点击。文档明确系统区域先处理，不再声称 false 会导致默认关机。 |
| F04 打开失败仍激活 | 已修复 | onOpen 或初始 onResize 失败后取消输入、调用 onClose、清除活动页面并返回主屏。 |
| F05 渲染状态泄漏 | 已修复 GUI 矩阵隔离，并明确边界 | 保存/恢复 GUI 矩阵栈，覆盖少 pop、多 pop 至栈底、修改矩阵和异常退出；法线矩阵恢复后采用保守归一化，避免新版 trustedNormals 缓存不匹配。页面内容强制裁剪；保留裁剪泄漏兜底。不承诺修复任意原生 GPU 状态破坏。 |
| F06 字符窄化/文档 | 已修复 | 26.1.2 完整码点直接进入 int 回调；默认桥拆为 UTF-16 单元兼容旧 char 页面。明确粘贴由按键及文本控件处理，不把输入法预编辑与剪贴板混为字符回调。 |
| F07 输入归属/暂停 | 已补齐 | 鼠标及键盘按下会话、对应释放、onInputCancelled；失焦、切页、关闭、退出 HUD 操作时清理。页面可继续显示/更新而停止接收输入。 |
| F08 布局通知 | 已补齐 | 新增可保存的 PhoneViewport；打开后初始布局、后续布局变化通知 onResize；输入和绘制前更新同一套布局。布局改变时取消正在进行的输入，避免坐标跳变。 |
| F09 内容区边界 | 已统一 | 页面绘制包在内容区裁剪内；按下和普通滚轮须命中内容区，捕获中的拖动和释放允许越界。系统导航、HUD 机身拖动和 Ctrl+滚轮优先。 |
| F10 更新周期 | 已补齐 | 新增 tick。当前 Screen 由原版驱动，后台 HUD 由各平台 PhoneHud 驱动，两条路径互斥。关闭后停止回调。 |
| F11 横向滚轮丢失 | 已修复 | 新增四参数滚轮回调，默认只调用一次旧纵向入口；1.20.1 的横向量仍为 0。 |
| F12 控件宿主缺失 | 已提供可选实现 | 新增 PhoneWidget、PhoneWidgetPage，统一子控件生命周期、命中、焦点、Tab/Shift+Tab、逐按钮拖动及按键释放归属。不强制现有手绘页面迁移。 |

## 3. 修改了哪些文件、哪些方法

### 3.1 稳定 API

| 文件入口 | 修改位置 | 修改内容 |
|---|---|---|
| [IPhonePage.java](../shared/src/main/java/com/november/mcphone/api/client/ui/IPhonePage.java#L94) | 输入回调及类注释 | 增加拖动、释放、移动、取消、布局、tick、按键释放、两轴滚轮和完整码点入口；改正点击消费、异常隔离和粘贴说明。 |
| [PhoneViewport.java](../shared/src/main/java/com/november/mcphone/api/client/ui/PhoneViewport.java#L4) | 整个新增类型 | 内容区 x/y/宽高和当前手机到窗口 GUI 的缩放倍率；contains 使用半开边界。可保存，与帧内 PhoneCanvas 分离。 |
| [PhoneCanvas.java](../shared/src/main/java/com/november/mcphone/api/client/ui/PhoneCanvas.java#L121) | transformed(Runnable) | 提供有异常恢复能力的临时矩阵作用域。已有 graphics/font/style/clipped 和构造函数保持。 |
| [PhoneWidget.java](../shared/src/main/java/com/november/mcphone/api/client/ui/PhoneWidget.java#L4) | 整个新增接口 | 在 IPhonePage 上补充子控件 contains、focusable 和 onFocusChanged；作为包装原版/第三方控件的薄接口。 |
| [PhoneWidgetPage.java](../shared/src/main/java/com/november/mcphone/api/client/ui/PhoneWidgetPage.java#L14) | 整个新增宿主 | 维护固定子控件集合；后加入者优先命中；按按钮保留拖动目标；按键释放返回原接收控件；取消输入清焦点；清理某个控件失败仍继续清理其他控件。 |
| [MCphoneApi.java](../shared/src/main/java/com/november/mcphone/api/MCphoneApi.java#L57) | 版本历史与静态初始化 | API 6 → 7，保留非编译期常量的静态赋值方式。 |

### 3.2 页面宿主及事件路由

- [PhonePageHost.java](../shared/src/main/java/com/november/mcphone/core/client/PhonePageHost.java#L11)：**新增 PhonePageHost**。从 PhoneScreen 抽出可独立测试的页面状态管理；open/invoke/close 负责失败处理，layout/interactive/cancelInput 负责布局和交互状态，click/move/release/scroll/keyPressed/keyReleased 负责输入归属。关闭先清字段，再调用旧页回调；关闭回调重入打开的新页不会被外层 open 覆盖。
- [PhonePoseScope.java](../shared/src/main/java/com/november/mcphone/core/client/PhonePoseScope.java#L10)：**新增 PhonePoseScope**。快照和恢复 GUI 矩阵栈及法线矩阵；经公开 scale 使新版法线缓存采用保守归一化后恢复数值矩阵。由 renderAddonPage 和 PhoneCanvas.transformed 共用。

[PhoneScreen.java](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java) 的修改较多，按方法列出：

| 方法/字段 | 修改内容 |
|---|---|
| [addonHost 字段](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L110) | 替代散落的 addonPage 状态，统一事件和生命周期。 |
| [构造函数时间初始化](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L228) | 修正初次渲染前布局查询使用未初始化动画时间的问题。 |
| [addonViewport / addonInteractive](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L537) | 统一布局计算与当前窗口输入资格。 |
| [openAddonPage / closeAddonPage](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L554) | 委托页面宿主，打开失败不进入 ADDON_PAGE。 |
| [updateAddonContext / leaveFailedAddon / callPage](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L561) | 同步失焦与布局；所有页面回调经统一错误处理，失败后退出该页。 |
| [renderAddonPage](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L580) | 内容区裁剪和矩阵作用域，保留现有裁剪兜底。 |
| [tick](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L597) | 活动页面每客户端刻一次更新；后台 HUD 也可驱动。 |
| [mouseClicked](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L1200) | 附属非左键在全局左键过滤前分发；内容区点击通过宿主决定捕获；抓 HUD 机身先取消页面输入。 |
| [mouseMoved](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L1498) | 用反缩放后的 double 坐标产生所有捕获按钮的拖动，转发页面鼠标移动；挪动 HUD 机身期间不触发附属移动。 |
| [mouseDragged](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L1511) | 保留系统机身拖动优先级；附属由 mouseMoved 驱动，避免原版拖动再次派发。 |
| [mouseReleased](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L1550) | 系统拖动先收尾；附属按原捕获按钮收到释放，越界也有效。 |
| [onScroll](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L1588) | 保留系统 Ctrl+滚轮缩放，普通附属滚轮限制在内容区并保留两轴。 |
| [keyPressed / keyReleased](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L1654) | 记录被页面处理或键盘捕获的按下，释放与其配对；ESC 及现有背包键政策保留。 |
| [charTyped(int) / charTyped(char)](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L1742) | 新版走完整码点，旧平台字符继续兼容；不再截断新版附属码点。 |
| [removed](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java#L1777) | HUD 页面暂失交互也取消输入，清除机身拖动状态；HUD 是否真正关页仍按现有所有权处理。 |

### 3.3 四个平台的适配修改

| 文件组 | 改动 |
|---|---|
| [1.20.1-forge / PhoneHud](../platforms/1.20.1-forge/src/main/java/com/november/mcphone/core/client/PhoneHud.java#L180) | 当前手机不是 mc.screen 时调用 tick；作为当前 Screen 时由原版调用，避免双重更新。 |
| [1.21.1-neoforge / PhoneHud](../platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/core/client/PhoneHud.java#L172) | 当前手机不是 mc.screen 时调用 tick；作为当前 Screen 时由原版调用，避免双重更新。 |
| [1.21.1-fabric / PhoneHud](../platforms/1.21.1-fabric/src/main/java/com/november/mcphone/core/client/PhoneHud.java#L171) | 当前手机不是 mc.screen 时调用 tick；作为当前 Screen 时由原版调用，避免双重更新。 |
| [26.1.2-neoforge / PhoneHud](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/core/client/PhoneHud.java#L172) | 当前手机不是 mc.screen 时调用 tick；作为当前 Screen 时由原版调用，避免双重更新。 |
| [1.20.1-forge / Draw](../platforms/1.20.1-forge/src/main/java/com/november/mcphone/platform/client/Draw.java#L43) | 新增 poseAtBase，调用本版本 PoseStack.clear；把真实版本差异留在平台层。 |
| [1.21.1-neoforge / Draw](../platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/platform/client/Draw.java#L42) | 新增 poseAtBase，调用本版本 PoseStack.clear；把真实版本差异留在平台层。 |
| [1.21.1-fabric / Draw](../platforms/1.21.1-fabric/src/main/java/com/november/mcphone/platform/client/Draw.java#L42) | 新增 poseAtBase，调用本版本 PoseStack.clear；把真实版本差异留在平台层。 |
| [26.1.2-neoforge / Draw](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/Draw.java#L13) | 新增 poseAtBase，调用本版本 PoseStack.isEmpty；把真实版本差异留在平台层。 |
| [26.1.2 / PhoneScreenBase](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java#L32) | CharacterEvent 不再强转 char，先调用 int 重载；默认 int 桥仍保留其他旧页面的 char 处理。 |
| [platform-twins.json](../versions/platform-twins.json) | 由 updateTwinBaseline 生成：Draw 差异 86→88，PhoneScreenBase 差异 58→65。增加的差异分别来自已核实的栈底方法改名和完整码点桥，不是跳过一致性检查。 |

二次复查额外修改的平台文件如下：

| 文件 | 修改位置及目的 |
|---|---|
| [1.20.1-forge/PhoneScreenBase.java](../platforms/1.20.1-forge/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java) | 增加中立 charTyped(int) 回退入口，支持共享页面委托，并完整转发旧版 UTF-16 代理对。 |
| [1.21.1-neoforge/PhoneScreenBase.java](../platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java) | 增加中立 charTyped(int) 回退入口，支持共享页面委托，并完整转发旧版 UTF-16 代理对。 |
| [1.21.1-fabric/PhoneScreenBase.java](../platforms/1.21.1-fabric/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java) | 增加中立 charTyped(int) 回退入口，支持共享页面委托，并完整转发旧版 UTF-16 代理对。 |
| [PhoneInputs.java](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/port/PhoneInputs.java) | dispatchCharacter 区分原生事件和传统字符；submitCharacter 避免旧附属代理对被原生控件重复提交。 |
| [PhoneEditBox.java](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/port/PhoneEditBox.java) | charTyped(char) 改经 submitCharacter，单行输入框只收到一次完整原生事件。 |
| [PhoneMultiLineEditBox.java](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/api/client/ui/PhoneMultiLineEditBox.java) | 同样经 submitCharacter，修正多行输入框补充字符的重复提交。 |

### 3.4 新增测试与示例

- [PhonePageHostTest.java](PhonePageHostTest.java)：43 条无窗口断言：内容区边界、右键越界拖动、逻辑增量、多按钮释放、不消费不捕获、暂停交互、取消幂等、后台更新、两轴滚轮、布局变化、按键配对、打开/布局/更新/拖动/清理异常、重入跳转、UTF-16 桥、旧滚轮单次委托、矩阵栈恢复及子控件焦点/Tab/拖动归属。
- [PhonePageBinaryCompatibilityTest.java](PhonePageBinaryCompatibilityTest.java)：动态按旧 IPhonePage 签名编译 Java 17 附属字节码，再用当前接口加载，调用新增默认方法，并验证旧字符和纵向滚轮桥接；不是仅仅拿旧源码重编。
- [PhonePageApiExamples.java](PhonePageApiExamples.java)：只引用稳定 API 的最小地图交互示例：布局、右键拖动、释放/取消、两轴滚轮。背景只是示例绘制，不包含 HowToGo 地图实现。

## 4. 行号清单与完整补丁

以下均为当前二次复查后工作区的行号；删除项标在对应相邻位置。代码进一步修改后行号可能变化，方法名和补丁仍可用于定位。

- [docs/PhonePageApiExamples.java](PhonePageApiExamples.java): 新增文件，L1–38
- [docs/PhonePageBinaryCompatibilityTest.java](PhonePageBinaryCompatibilityTest.java): 新增文件，L1–64
- [docs/PhonePageHostTest.java](PhonePageHostTest.java): 新增文件，L1–161
- [docs/PhonePageReviewTest.java](PhonePageReviewTest.java): 新增文件，L1–127
- [platforms/1.20.1-forge/src/main/java/com/november/mcphone/core/client/PhoneHud.java](../platforms/1.20.1-forge/src/main/java/com/november/mcphone/core/client/PhoneHud.java): L180
- [platforms/1.20.1-forge/src/main/java/com/november/mcphone/platform/client/Draw.java](../platforms/1.20.1-forge/src/main/java/com/november/mcphone/platform/client/Draw.java): L41–45
- [platforms/1.20.1-forge/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java](../platforms/1.20.1-forge/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java): L50–57
- [platforms/1.21.1-fabric/src/main/java/com/november/mcphone/core/client/PhoneHud.java](../platforms/1.21.1-fabric/src/main/java/com/november/mcphone/core/client/PhoneHud.java): L171
- [platforms/1.21.1-fabric/src/main/java/com/november/mcphone/platform/client/Draw.java](../platforms/1.21.1-fabric/src/main/java/com/november/mcphone/platform/client/Draw.java): L40–44
- [platforms/1.21.1-fabric/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java](../platforms/1.21.1-fabric/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java): L50–57
- [platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/core/client/PhoneHud.java](../platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/core/client/PhoneHud.java): L172
- [platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/platform/client/Draw.java](../platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/platform/client/Draw.java): L40–44
- [platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java](../platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java): L50–57
- [platforms/26.1.2-neoforge/docs/PhoneNativeCharacterTest.java](../platforms/26.1.2-neoforge/docs/PhoneNativeCharacterTest.java): 新增文件，L1–50
- [platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/api/client/ui/PhoneMultiLineEditBox.java](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/api/client/ui/PhoneMultiLineEditBox.java): L14
- [platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/core/client/PhoneHud.java](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/core/client/PhoneHud.java): L172
- [platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/Draw.java](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/Draw.java): L11–15
- [platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/PhoneScreenBase.java): L32、L35–38
- [platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/port/PhoneEditBox.java](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/port/PhoneEditBox.java): L13
- [platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/port/PhoneInputs.java](../platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/platform/client/port/PhoneInputs.java): L29–49
- [shared/src/main/java/com/november/mcphone/api/MCphoneApi.java](../shared/src/main/java/com/november/mcphone/api/MCphoneApi.java): L29–30、L57
- [shared/src/main/java/com/november/mcphone/api/client/ui/IPhonePage.java](../shared/src/main/java/com/november/mcphone/api/client/ui/IPhonePage.java): L69–70、L85–87、L93–107、L111–121、L136–150
- [shared/src/main/java/com/november/mcphone/api/client/ui/PhoneCanvas.java](../shared/src/main/java/com/november/mcphone/api/client/ui/PhoneCanvas.java): L120–126
- [shared/src/main/java/com/november/mcphone/api/client/ui/PhoneViewport.java](../shared/src/main/java/com/november/mcphone/api/client/ui/PhoneViewport.java): 新增文件，L1–8
- [shared/src/main/java/com/november/mcphone/api/client/ui/PhoneWidget.java](../shared/src/main/java/com/november/mcphone/api/client/ui/PhoneWidget.java): 新增文件，L1–8
- [shared/src/main/java/com/november/mcphone/api/client/ui/PhoneWidgetPage.java](../shared/src/main/java/com/november/mcphone/api/client/ui/PhoneWidgetPage.java): 新增文件，L1–168
- [shared/src/main/java/com/november/mcphone/core/client/PhonePageHost.java](../shared/src/main/java/com/november/mcphone/core/client/PhonePageHost.java): 新增文件，L1–144
- [shared/src/main/java/com/november/mcphone/core/client/PhonePoseScope.java](../shared/src/main/java/com/november/mcphone/core/client/PhonePoseScope.java): 新增文件，L1–39
- [shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java](../shared/src/main/java/com/november/mcphone/core/client/PhoneScreen.java): L5、L109–111、L228、L265–270、L537–545、L547、L550–592、L596–600、L1212–1221、L1232、L1246、L1342–1344、L1498–1509、L1513、L1517–1521、L1557–1562、L1592、L1645–1649、L1655、L1708–1709、L1722–1726、L1731–1750、L1754–1757、L1778–1780
- [versions/platform-twins.json](../versions/platform-twins.json): L126、L132

完整代码补丁：`API-FIX-CHANGES-2026-10-19.patch`（本机留存，未提交）。该补丁包含上述 30 个实现及测试文件，不包含本报告与运行日志。

## 5. 验证结果

| 目标 | 编译与打包 | 本次 API 测试（含二次复查） | 其他验证 |
|---|---|---|---|
| 1.20.1 Forge / Java 17 | assemble 成功 | 56 条断言 + 旧字节码兼容通过 | dist、平台差异、接缝检查通过 |
| 1.21.1 NeoForge / Java 21 | build（排除下述 Windows 不兼容测试）成功 | 56 条断言 + 旧字节码兼容通过 | 全部其余断言与构建检查通过 |
| 1.21.1 Fabric / Java 21 | assemble / remapJar 成功 | 56 条断言 + 旧字节码兼容通过 | dist、平台差异、接缝检查通过 |
| 26.1.2 NeoForge / Java 25 | assemble 成功 | 64 条断言 + 旧字节码兼容通过 | dist、平台差异、接缝检查通过 |

1.21.1 NeoForge 的最终广泛回归使用 `build -x assertTestEconomyDataTest --max-workers=2`。二次复查后当前发现 114 份断言测试，实际排除 1 份后运行其余 113 份。Gradle 汇总仍会打印全部发现的名称，因此不能把其“114 份”文字当作 114 份均执行。

已记录的验证限制：

- 未排除任务的首次完整构建在既有 EconomyDataTest.java:936 的 Files.setPosixFilePermissions 失败（Windows 不支持 POSIX 权限），与本次 UI 源码无关；没有修改该经济测试或关闭其 CI 检查。
- 高并发同时验证四目标时，既有 LayoutEngineTest 的“2048 项列表 50ms 内完成”耗时断言发生一次超时。降低并发后的完整回归通过；没有放宽阈值或排除该布局测试。
- 初次生成平台差异基线与 verifyPlatformTwins 放在同次构建时，校验可能先于生成运行并报旧基线。生成完成后独立校验已通过，最终验证没有排除平台检查。
- 编译有现存的过时 API、可选注解缺失及 Gradle 弃用提示；没有影响本次编译结果。
- git diff --check 通过。

可复现命令（在相应 platforms/<目标> 目录，使用对应 JDK）：

```text
# 主力目标的广泛回归
./gradlew --offline build -x assertTestEconomyDataTest --max-workers=2

# 其余三目标的本次变更验证
./gradlew --offline assemble assertTestPhonePageHostTest \
  assertTestPhonePageBinaryCompatibilityTest assertTestPhonePageReviewTest verifyDistIsolation \
  verifyPlatformTwins verifySeamsDocCurrent
```

Windows 使用 gradlew.bat；以上的换行反斜线仅为阅读展示，PowerShell 执行时合并为一行。26.1.2 另外执行 assertTestPhoneNativeCharacterTest；执行结果记录见下方说明；原始日志仅本机留存，未提交。

最终日志：

- `api-review-verified-1.21.1-neoforge.log`（本机留存，未提交）
- `api-review-verified-1.20.1-forge.log`（本机留存，未提交）
- `api-review-verified-1.21.1-fabric.log`（本机留存，未提交）
- `api-review-verified-26.1.2-neoforge.log`（本机留存，未提交）

首次失败记录与中间验证日志均保留于 `API-validation-2026-10-19`（本机留存，未提交）。

## 6. 附属接入及兼容注意点

1. 原有 render、mouseClicked、三参数 mouseScrolled、charTyped(char)、onOpen/onClose 等签名和包名均保留；源码中没有给已发布接口增加抽象方法。旧字节码兼容测试已经验证这一条。
2. 新附属应声明支持 API 7 的最低 MCPhone 发行版本；本次尚未发布，不应把 API 代号 7 当作模组发行版本号。
3. mouseClicked 返回 true 才建立该按钮的拖动与释放会话。页面在 onInputCancelled 中结束拖动、清除按住键；该通知可重复，不能假定总有正常释放事件。
4. onResize 的 PhoneViewport 可以保存；PhoneCanvas 仍只在当前 render 中有效。onResize 应移动/调整现有控件，避免不必要地重建并丢失编辑内容。
5. 坐标继续使用反缩放后的屏幕绝对坐标，不是内容区相对坐标。dx/dy 是同一坐标系的差值。布局或缩放变化会结束正在进行的输入，不把不同变换下的位置相减。
6. 页面现在受内容区裁剪和命中约束。以往越出内容区绘制系统栏覆盖物的行为不再保留；这是有意收紧既有“画在内容区”的约定，需在发行说明中告知附属作者。
7. PhoneWidgetPage 是可选轻量宿主，不是自动把任意原版 Screen 缩进手机的通用包装器。原版/第三方控件仍需 PhoneWidget 包装，并适配其自身裁剪和版本差异。
8. 本次 GUI 矩阵隔离不承诺修复附属任意原生渲染调用、删除底层矩阵栈根节点或绕开宿主的裁剪栈破坏；正确代码应使用 PhoneCanvas.clipped/transformed。现有裁剪泄漏兜底仍是有限启发式，不是任意底层状态恢复器。
9. 没有实现或改写 HowToGo/Xaero 集成。示例说明新的宿主输入能力，不能当成真实地图联调成功的证据。

## 7. 打包产物与待游戏验收项

以下是本地测试构建，仍使用仓库原有 1.11.2 文件名，尚未发布：

- 1.20.1-forge / mcphone-1.11.2.jar（本地产物：`platforms/1.20.1-forge/build/libs/mcphone-1.11.2.jar`，未提交）
- 1.21.1-neoforge / mcphone-1.11.2.jar（本地产物：`platforms/1.21.1-neoforge/build/libs/mcphone-1.11.2.jar`，未提交）
- 1.21.1-fabric / mcphone-1.11.2.jar（本地产物：`platforms/1.21.1-fabric/build/libs/mcphone-1.11.2.jar`，未提交）
- 26.1.2-neoforge / mcphone-1.11.2.jar（本地产物：`platforms/26.1.2-neoforge/build/libs/mcphone-1.11.2.jar`，未提交）

仍需实际游戏验收的项目：不同 GUI 比例及 75%/100%/150%/300% 手机缩放下的命中、拖动、滚轮和裁剪；右键/中键与多按钮；越界松手；窗口失焦；HUD 开关交互和机身拖动；多行选区与中文输入/粘贴；初始化和渲染异常的画面恢复；导航、聊天右键菜单及内建页面回归；专用服务器启动；HowToGo 的实际应用接入。以上未运行，不记为通过。

## 8. 二次复查与优化（2026-10-19）

此次复查没有只停留在性能建议，修正了以下可以从调用链确认的边界问题。新测试先在优化前运行，复现了“旧回调异常关闭同实例新会话”的失败；修复后所有新增测试通过。

| 问题 | 优化前的行为 | 本次修改 |
|---|---|---|
| 会话重入 | 仅比较 page 对象引用；同一个实例关闭后重新打开，旧回调抛错仍可能关闭新会话 | PhonePageHost 增加 revision；open、invoke、layout 在回调返回后校验会话代次。PhoneScreen.navigateTo 尊重清理回调触发的后续导航。 |
| 取消后重建捕获 | mouseClicked/keyPressed 内部取消输入，回调返回 true 后宿主又记录按下 | 增加 inputRevision，只有页面和输入代次都未变化才建立捕获；PhoneWidgetPage 同样保护子控件捕获。 |
| 多按钮漏发 | 拖动中释放快照中的一个按钮，会 break 整个循环，其他有效按钮漏收；重新捕获的同按钮可能混入旧拖动 | 用带身份的 Press 对象记录会话；已释放/重建者 continue，只有整页或输入会话改变才停止旧批次。 |
| 重复控件清理 | 同一控件可重复注册；多个控件抛出同一个异常时 addSuppressed 自我抑制会中断清理；重复 onClose 会重复释放 | 拒绝同实例重复注册，汇总异常时排除自我抑制，关闭幂等并先清捕获和焦点，禁止关闭过程中注册/重开。 |
| 关闭后的子控件回调 | 一个控件在 tick/render/resize 中关闭页面，循环可能继续调用其余已关闭控件 | 子控件遍历中检查活动状态和输入代次，已结束的批次立即停止。 |
| 新版补充字符重复 | 非附属路径把一个 CharacterEvent 拆为两个 char，原生控件每次又读取完整事件；旧附属转发代理对也有同样风险 | 字符回退由平台层处理；26.1 原生事件仅提交一次，传统字符仍完整发送代理对。单行、多行控件及基类统一使用 submitCharacter。 |

### 8.1 每帧及输入路径的优化

- PhoneScreen.addonViewport：缓存不可变布局，坐标、尺寸、倍率不变时复用，移除稳定布局下重复 new PhoneViewport 的代码路径。
- PhonePageHost.move：没有捕获时直接返回；有捕获时只快照 Press 引用，不再复制整张 LinkedHashMap 及其节点。保留快照以支持回调中释放/重建捕获，不能为了省分配直接遍历可变表。
- PhoneWidgetPage 的 Tab 导航：用有界循环查找下一个可聚焦控件，不再每次构造 stream 和候选列表；仍实时查询 focusable，不缓存会过期的可用性。
- IPhonePage.charTyped(int)：BMP 字符直接调用旧回调；补充字符用 highSurrogate/lowSurrogate 依次传递，移除 Character.toChars 临时数组，并保持即使高位被消费也仍传递低位。
- PhonePoseScope 保留原有完整 GUI 矩阵恢复；没有为了减少分配而缩减异常隔离保证。

以上是代码路径层面的分配优化，没有进行 FPS、GC 或帧耗时基准测试，不能据此声称具体提速百分比。

### 8.2 新增测试与具体位置

- [docs/PhonePageReviewTest.java](PhonePageReviewTest.java#L20)：13 条断言：会话重入、取消后的捕获、布局换页、多按钮释放/重建、重复控件注册、共享异常、关闭幂等及 tick 中关闭。
- [platforms/26.1.2-neoforge/docs/PhoneNativeCharacterTest.java](../platforms/26.1.2-neoforge/docs/PhoneNativeCharacterTest.java#L11)：8 条断言：原生事件完整且仅提交一次、旧附属代理对转发、低 16 位落入代理区的特殊码点、传统字符回退、BMP 和非法码点。

二次复查共涉及 14 个实现及测试文件（累计修复范围为上文 30 个文件）；具体变动行号已合并到第 4 节。API 7 尚未发布，因此仍在同一代号下完善，没有再增加 API 代号。

测试构成：前三目标各 43+13=56 条本次断言，新版额外 8 条为 64 条；四目标另各运行旧附属字节码兼容测试。1.21.1 NeoForge 按第 5 节命令执行广泛回归。仍未进行实际游戏或 HowToGo 联调，原有 Windows 测试限制不变。

失败复现日志：`api-review-before.log`（本机留存，未提交）。
