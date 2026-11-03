# 音乐 APP 自动停播：诊断、修复与设计检查

日期：2026-10-22 至 23 日（香港时间）。工作分支 `codex/music-context-isolation`，基线为 `main@665f331e`，与聊天工作区分开。

## 直接故障证据

提供的 Forge 1.20.1 / Forge 47.4.23 实例运行 MCPhone 1.11.2。`latest.log` 的两次本地 WAV 停播均为：

- 23:11:36.448：第二个 OpenAL Library 初始化，即 MCPhone 独立音乐设备；原版声音设备已于 23:10:52 初始化。
- 23:11:37.886、23:11:45.080：`LocalStreamChannel.OpenAlDriver.processed` 查询缓冲时抛出 `OpenAL 0xa001`，路径为 `updateStream → LocalPlayback.tick`。

`0xa001` 为 `AL_INVALID_NAME`。这里是 `alGetSourcei` 使用的声源句柄在当前上下文中无效。异常路径调用 stop，并将状态设为 IDLE；播放器保留当前队列位置，UI 因此表现为“自动暂停”。日志没有显示用户点击暂停，也不是歌曲正常 EOF、网络地址过期或付费试听时限。

## 根因及原生复现

旧播放器虽然创建了独立设备，却在初始化和每次 tick 用 `alcMakeContextCurrent` / `AL.setCurrentProcess` 切换全进程上下文。Minecraft 1.20.1 的 Library 初始化同样设置进程上下文，原版后台声音线程会并行管理自己的 Channel。

两个设备的声源编号可以相同。音乐作用域切换全局上下文后，后台线程按原版编号停止/删除声源，可能实际操作音乐设备中的同编号声源。下一次音乐查询就收到日志中的无效句柄。只给本模组加 synchronized 无法约束原版和其他模组的线程；只捕获错误或加长缓冲也不能消除这处竞态。

手动原生对照使用与 Forge 同版的 LWJGL 3.3.1：两个独立上下文分配同编号声源，在旧式全局切换期间让后台线程删除“原版”声源，稳定得到相同的 `0xa001`。改用新的实际 `LocalAudioDevice` 后，两线程分别执行一万次原生操作，音乐声源持续有效，后台线程仍访问原版上下文，关闭音乐也不销毁原版设备。LWJGL 3.3.3、3.4.1 上重复通过同一组 12 条原生断言。

这一隔离方式依据 LWJGL 的[上下文与能力表说明](https://javadoc.lwjgl.org/org/lwjgl/openal/AL.html)：进程状态与线程状态必须分别管理，OpenAL Soft 支持线程局部上下文。实际兼容性同时核对了本机 3.3.1 字节码和三个版本的原生行为，未只依赖最新文档。

## 本轮修复

1. 新增 `LocalAudioDevice`：独立持有设备、上下文和能力表；初始化、使用及释放均不切换进程上下文，只修改调用线程。恢复到无线程上下文时清除线程能力表，继续使用原版的实时进程回退；保留其他模组原先的线程绑定。校验线程归属、嵌套关闭顺序与部分初始化失败的资源回收。不支持线程局部扩展时明确拒绝本地设备，不冒险退回全局切换。
2. `LocalPlayback` 改为协调设备和音轨，不再持有原版 Library。停止时先清空播放状态，再尽力关闭资源；避免清理异常留下假 PLAYING。进度改用单调时钟，避免系统校时影响时长。退出世界仍释放自己的设备。
3. `LocalStreamChannel` 区分“用户要求播放”与 OpenAL 的瞬时状态。另一个可由代码复现的设计缺陷是：四秒缓冲耗尽后，原实现虽然补了数据，却没有重新启动停止的声源，导致永久停播。现在只在仍要求播放、有有效缓冲且声源 STOPPED 时恢复同一声源；用户暂停/停止及真正 EOF 不自动重启。补上“刚断流时暂停，再继续”的边界：先补流再恢复，不直接重播已消费的旧缓冲。
4. 删除四份内部 `PhoneSoundDevice` 接缝。它们只为调用各版本 Library.init 服务，已无调用者；没有修改稳定 API、网络、歌曲文件、播放列表或模组版本。

## 文件职责

| 文件 | 职责/变化 |
| --- | --- |
| `shared/.../music/client/playback/LocalAudioDevice.java` | 新增独立设备及线程局部作用域；原生 Driver 边界 |
| `shared/.../music/client/playback/LocalPlayback.java` | 播放状态、进度、设备/音轨生命周期协调 |
| `shared/.../music/client/playback/LocalStreamChannel.java` | 声源/缓冲所有权，增加断流恢复且保留用户意图 |
| `platforms/<四目标>/.../platform/client/PhoneSoundDevice.java` | 删除已失去用途的 Library 初始化接缝 |
| `docs/LocalAudioDeviceTest.java` | 新增设备初始化、作用域、线程归属、失败回收及禁止全局切换的 52 条断言 |
| `docs/LocalStreamChannelTest.java` | 增补断流恢复、暂停/停止约束、断流期间暂停再继续和 EOF 回归；含既有覆盖合计 433 条断言 |
| `tools/audio/NativeAudioProbe.java`、`validate.ps1` | 独立无声 JVM 原生对照，可重复运行 |
| `shared/PLATFORM-SEAMS.md`、`versions/platform-twins.json` | 任务生成；移除无用音频接缝，129 对差异合计 9621 行 |
| `docs/local-audio-compat.md`、`docs/PORT-26.1.2-NEOFORGE.md`、`docs/release-notes/next.md` | 同步兼容性、接缝与未发布说明 |

## 自动验证与实测边界

最终源码在四个平台均完成 `build --continue`。每个目标的两项音乐专项均通过：设备隔离 52 条断言，流式通道 433 条断言。四个完整构建都仅有既有 `EconomyDataTest` 失败：Windows 的 `Files.setPosixFilePermissions` 抛出 `UnsupportedOperationException`。未改动、跳过或伪装通过该测试，因此完整构建不能称为全绿。

| 目标 | JDK | 最终检查结果 |
| --- | --- | --- |
| Forge 1.20.1 | 17 | 打包及其余检查通过；仅上述权限测试失败 |
| NeoForge 1.21.1 | 21 | 打包及其余检查通过；仅上述权限测试失败 |
| Fabric 1.21.1 | 21 | 打包及其余检查通过；仅上述权限测试失败 |
| NeoForge 26.1.2 | 25 | 打包及其余检查通过；仅上述权限测试失败 |

接缝、平台孪生、第三方导入和最终私有引擎打包检查均通过。原生测试的三个日志与四份最终构建日志保存于 `C:\Users\31286\Desktop\MCP\music-validation`。Forge 依赖站曾出现临时证书握手失败，保留证书校验重试后完成构建。

交付包：`C:\Users\31286\Desktop\MCP\music-preview\mcphone-1.11.2-1.20.1-forge-music-context-preview.jar`。

SHA256：`B5E5C8056BC08DB71525A0177591A272544087E06FA73321F163274A3F0F1743`。已检查 JAR 中的实际字节码：线程局部上下文与能力表调用存在，未包含旧设备接缝，全局切换未重新引入，使用 Forge 正式运行映射，并包含最后补充的断流暂停恢复逻辑。逐项结果另存于同目录 `validation.json`；未由代理自动安装。

原生回归在独立 JVM 以 OpenAL Soft 的 null 输出运行，不启动或关闭游戏、不访问玩家存档。可在先构建相应目标后执行：

```powershell
.\tools\audio\validate.ps1 -JavaHome <JDK目录> -LwjglVersion 3.3.1
```

脚本读取本机 Gradle 缓存，输出到忽略的 build 目录，退出时恢复原有 ALSOFT_DRIVERS。3.3.3 / 3.4.1 可用相同命令替换版本号复核；原生测试不作为普通无声卡 CI 的前提。

2026-10-23 用户反馈“测试后没有发现问题”，记录为用户实测通过。该反馈未细分平台和逐项操作，不据此宣称四个平台及所有组合均已完成游戏验收。后续扩大覆盖可检查：同一 WAV 连续播放、走动/挖方块、切歌、暂停/继续、关 App、重新进世界及资源重载。原生竞态复现与编译通过不能代替听觉验收；此次代理未改动测试实例的 mods、音乐文件、配置或存档。

测试包基于已合并引用回复的 main，内部仍使用 1.11.2 版本号；若联机，须同时使用对应的新服务端构建。音乐修复本身未改网络协议，但不能因此认为整个主分支预览包与已发布 1.11.2 的聊天协议相同。

## 仍然存在的设计压力

现有职责划分可以保留：`MusicPage` 绘制与接收操作，`MusicController` 管队列及循环，`MusicSources` / `MusicSource` 管曲库与开流，`AudioDecoders` 管格式，`LocalPlayback` 管一条音轨。本轮将设备所有权独立到 `LocalAudioDevice`，流式声源与缓冲继续归 `LocalStreamChannel`；唱片/网络外放仍由 `DiscLoop`、`NetSongPlayback` 及原版声音系统承担。这些边界已有实际作用，不需要为了修停播整体重写 APP。

- **主线程解码和文件/联动音源打开**：当前读流在 tick 中完成，慢盘、较慢解码或在线联动会阻塞客户端。未来应引入有界 PCM 队列、后台解码、取消票据，并明确“暂时没数据”和 EOF 的区别；不应直接让后台线程操作当前设备。此次没有顺带重写整条媒体链。
- **设备热插拔恢复**：独立设备还没有默认设备切换和断连重建策略，不能承诺拔插耳机后无缝续播。
- **错误分类与 UI 提示**：中途读流错误和原生故障目前仍沿用停止/STARVED 协调，日志较明确但用户提示不足；应后续区分解码错误、设备丢失和真正的缓冲不足。
- **进度不是采样级位置**：单调时钟改进了稳定性，但展示的时间仍是播放经过时间，不等同于音频硬件采样位置；若增加拖动进度条，需要单独的 seek 契约。
- **状态为单客户端播放器单例**：对当前一首耳机音乐合理。将来增加多播放器、多个输出设备时，应先实例化播放会话；外放目前继续归原版声音系统，不能与本地声源混用句柄。

当前结论：独立耳机通道与菜单/队列的分层可以保留；有缺陷的是旧的全局上下文隔离方式及断流恢复策略。本轮针对这两点修复，未把后续媒体能力建设伪装成已完成。
