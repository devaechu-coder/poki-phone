# 美西螈引用回复：开发方案与实施报告

初稿：2026-10-19；审查更新：2026-10-22。分支 `codex/chat-replies`，当前基线 `main@cd1a9ece`，已包含已合并的 PR #72，之前“API 分支尚未整合”的说明不再适用。

## 功能与边界

私聊/群聊右键文字或图片选择“引用”，输入栏上方显示可取消的作者/摘要；发送的文字气泡包含单层原文快照。引用回复仅取本次正文，图片引用只显示类型摘要，不额外读取或复制像素。复制和收藏仍取本次正文，保持既有收藏格式。未实现引用跳转、分页、撤回传播、重启恢复草稿。

图标采用用户选定的圆环左引号，基于 Lucide 0.468.0 原稿组合，quote 旋转 180°、缩放 0.6 并填实，保留原路径、源码和 ISC 许可；菜单共用图标槽与视觉中线，随主题着色。

数据使用 TextBody(text, Optional<ChatQuote>)，保留 TextBody(text)。请求只携原文 ID 和提交票据，作者、时间、内容快照均由服务端生成；私聊验证好友与本人可见记录，群聊验证成员、入群历史和隐藏记录。引用是新的独立转述快照，不继承额外历史/图片读取权限。

## 审查后强化的发送流程

1. Composer 提交原始草稿与编辑次数；引用状态保存独立票据。
2. 服务端从“真实作者＋会话＋群/私聊域＋票据”生成消息 ID；首次发送验证原文并存消息，相同票据重试核对已存内容，不重复落库。
3. 私聊补发回声只发给提交者；群聊重试只返回同票据确认，不向全群重新广播旧摘要。
4. 客户端按精确身份确认，不按相同正文猜测；只有未经编辑的原草稿可清空。6 秒超时使用单调时钟，保留票据用于手动重试；改变正文或重新选引用才创建新提交。关闭会话取消本页确认状态。
5. 去重保证限定于保留的 100 条历史，没有新增永久提交日志或可靠离线 outbox。

详细缺陷、修复证据、硬编码分析及后续维护风险见 [工程审查报告](CHAT-ENGINEERING-AUDIT-2026-10-20.md)。同时修复了补发重复气泡、图片零像素元数据无限积累与被逐出解码结果复活。

## 兼容性与验证

旧消息的缺省引用为空；普通文字继续写旧格式，引用文字写既有现代 body 格式。旧消息迁移 UUID 使用冻结的 1.11.2 种子，新增字段不改变删除标记对应关系。坏引用字段返回 Codec 错误，不靠构造异常中止加载。

网络须同步更新两端：Forge 通道 13，两个 NeoForge 通道 10；现代发送 send_chat_message_reply_v2、摘要 sync_conversations_reply_v1、历史 sync_messages_v3、推送 new_chat_message_v3、群 group_request_v5 / group_response_v5。Fabric 双向检查通道能力。旧开发预览包也须一起替换。模组版本仍为 1.11.2，正式发版另行授权。

重点回归：引用六类专项合计 104 条/目标，图片缓存上限 8 条；私聊缓存 10、群缓存 46、图标 50，及其余既有断言。2026-10-22 已核实四个平台完整 build --continue 均只有既有 EconomyDataTest 的 Windows POSIX 权限异常；最终序列化规则已在两个 NeoForge 目标补作专项复核并通过。Linux 最终状态以 PR 当前提交检查为准。没有改测试跳过规则。自动化不能代替游戏截图与多人交互实测。

## 全部改动文件

以下路径相对仓库根目录，平台同名文件分别列出；构建日志、预览包和本机辅助脚本不入仓库。

| 文件 | 变更职责 |
| --- | --- |
| `docs/CHAT-ARCHITECTURE.md` | 累计架构文档新增本轮工程报告和实施清单索引 |
| `docs/CHAT-ENGINEERING-AUDIT-2026-10-20.md` | 本轮工程审查、修复证据和未完成风险 |
| `docs/CHAT-REPLY-IMPLEMENTATION-2026-10-19.md` | 本实施报告及逐文件清单 |
| `docs/ChatActionIconsTest.java` | 自动化回归：ChatActionIconsTest |
| `docs/ChatClientCacheTest.java` | 自动化回归：ChatClientCacheTest |
| `docs/ChatImageCacheBoundsTest.java` | 自动化回归：ChatImageCacheBoundsTest |
| `docs/ChatReplyActionsTest.java` | 自动化回归：ChatReplyActionsTest |
| `docs/ChatReplyCodecTest.java` | 自动化回归：ChatReplyCodecTest |
| `docs/ChatReplyIdentityTest.java` | 自动化回归：ChatReplyIdentityTest |
| `docs/ChatReplyLayoutTest.java` | 自动化回归：ChatReplyLayoutTest |
| `docs/ChatReplySubmissionTest.java` | 自动化回归：ChatReplySubmissionTest |
| `docs/GroupClientCacheTest.java` | 自动化回归：GroupClientCacheTest |
| `docs/assets/chat-ui/reply/LICENSE.txt` | 引用图标原稿、上游许可或可重建脚本 |
| `docs/assets/chat-ui/reply/README.md` | 引用图标原稿、上游许可或可重建脚本 |
| `docs/assets/chat-ui/reply/circle-original.svg` | 引用图标原稿、上游许可或可重建脚本 |
| `docs/assets/chat-ui/reply/quote-original.svg` | 引用图标原稿、上游许可或可重建脚本 |
| `docs/assets/chat-ui/reply/rasterize.cjs` | 引用图标原稿、上游许可或可重建脚本 |
| `docs/assets/chat-ui/reply/reply.svg` | 引用图标原稿、上游许可或可重建脚本 |
| `docs/release-notes/next.md` | 未发布版本的功能与兼容性说明 |
| `layers/version/1.20.5+/docs/ChatDeletionPacketTest.java` | 自动化回归：ChatDeletionPacketTest |
| `layers/version/1.20.5+/docs/ChatReplyPacketTest.java` | 自动化回归：ChatReplyPacketTest |
| `layers/version/1.20.5+/src/main/java/com/november/mcphone/feature/chat/ChatMessage.java` | 引用文本写现代 body 格式；普通文本仍写旧格式 |
| `layers/version/1.20.5+/src/main/java/com/november/mcphone/feature/chat/TextBody.java` | 可选引用字段，保留普通文本构造及旧存档读取 |
| `layers/version/1.20.5+/src/main/java/com/november/mcphone/feature/chat/group/net/GroupRequestPacket.java` | 群请求协议升级 v5 |
| `layers/version/1.20.5+/src/main/java/com/november/mcphone/feature/chat/group/net/GroupResponsePacket.java` | 群响应协议升级 v5 |
| `layers/version/1.20.5+/src/main/java/com/november/mcphone/feature/chat/net/NewMessagePacket.java` | 新消息推送正文协议 v3 |
| `layers/version/1.20.5+/src/main/java/com/november/mcphone/feature/chat/net/SendChatMessagePacket.java` | 原文 ID＋提交票据；普通请求兼容构造；更新线格式 |
| `layers/version/1.20.5+/src/main/java/com/november/mcphone/feature/chat/net/SyncConversationsPacket.java` | 摘要正文协议 reply_v1 |
| `layers/version/1.20.5+/src/main/java/com/november/mcphone/feature/chat/net/SyncMessagesPacket.java` | 历史消息正文协议 v3 |
| `platforms/1.20.1-forge/docs/ChatReplyPacketTest.java` | 自动化回归：ChatReplyPacketTest |
| `platforms/1.20.1-forge/src/main/java/com/november/mcphone/core/net/MCphoneNetwork.java` | Forge 通道 11→13，防止旧端误读新线格式 |
| `platforms/1.20.1-forge/src/main/java/com/november/mcphone/feature/chat/ChatMessage.java` | 引用文本写现代 body 格式；普通文本仍写旧格式 |
| `platforms/1.20.1-forge/src/main/java/com/november/mcphone/feature/chat/TextBody.java` | 可选引用字段，保留普通文本构造及旧存档读取 |
| `platforms/1.20.1-forge/src/main/java/com/november/mcphone/feature/chat/net/ChatNetworking.java` | 共享文本提交入口；Fabric 另检查新旧客户端通道 |
| `platforms/1.20.1-forge/src/main/java/com/november/mcphone/feature/chat/net/SendChatMessagePacket.java` | 原文 ID＋提交票据；普通请求兼容构造；更新线格式 |
| `platforms/1.21.1-fabric/src/main/java/com/november/mcphone/core/client/ClientNetworking.java` | Fabric 客户端拒绝不支持新聊天协议的旧服务端 |
| `platforms/1.21.1-fabric/src/main/java/com/november/mcphone/feature/chat/net/ChatNetworking.java` | 共享文本提交入口；Fabric 另检查新旧客户端通道 |
| `platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/core/net/NetworkHandler.java` | NeoForge 通道 8→10，防止旧端误读新线格式 |
| `platforms/1.21.1-neoforge/src/main/java/com/november/mcphone/feature/chat/net/ChatNetworking.java` | 共享文本提交入口；Fabric 另检查新旧客户端通道 |
| `platforms/26.1.2-neoforge/docs/PhonePayloadTypesTest.java` | 自动化回归：PhonePayloadTypesTest |
| `platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/core/net/NetworkHandler.java` | NeoForge 通道 8→10，防止旧端误读新线格式 |
| `platforms/26.1.2-neoforge/src/main/java/com/november/mcphone/feature/chat/net/ChatNetworking.java` | 共享文本提交入口；Fabric 另检查新旧客户端通道 |
| `shared/PLATFORM-SEAMS.md` | 任务生成；补 MessageBody 的共享引用边界 |
| `shared/src/main/java/com/november/mcphone/core/client/PhoneSkin.java` | 新增引用图标皮肤元素 |
| `shared/src/main/java/com/november/mcphone/feature/chat/ChatDelivery.java` | 共享文本提交协调，重试只补发送者回声 |
| `shared/src/main/java/com/november/mcphone/feature/chat/ChatMessageIdentity.java` | 冻结旧存档迁移种子，不受 record 新属性影响 |
| `shared/src/main/java/com/november/mcphone/feature/chat/ChatQuote.java` | 服务端单层快照、统一长度及可控解码错误 |
| `shared/src/main/java/com/november/mcphone/feature/chat/ChatReplyIdentity.java` | 提交票据绑定作者与会话，并验证已存内容 |
| `shared/src/main/java/com/november/mcphone/feature/chat/ChatService.java` | 私聊可见原文验证、服务端生成快照及票据去重 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/ChatComposer.java` | 发送守卫、原始草稿快照、编辑次数及精确清理 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/ChatConversation.java` | 协调引用预览、提交、确认和会话清理 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/ChatConversationChannel.java` | 私聊/群聊发送、确认与取消适配 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/ChatImageCache.java` | 元数据 LRU 上限及被逐出解码结果的所有权 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/ChatMessageLayout.java` | 引用气泡高度与正文/选区原点 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/ChatMessagePane.java` | 绘制引用摘要，正文选择范围独立 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/GroupTransport.java` | 满容量重试既有票据不逐出其他待确认请求 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/ChatReplyDraft.java` | 会话实例内的引用目标 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/ChatReplySubmission.java` | 精确票据确认、超时重试与原始草稿保护 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/ChatReplyView.java` | 预览及气泡内的两行摘要视图 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/contextmenu/ChatContextMenuIcon.java` | 映射引用 PNG 贴图 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/contextmenu/ChatContextMenuView.java` | 共用图标枚举与呈现 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/messageaction/ChatContextMenu.java` | 增加引用菜单项，冻结原消息身份 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/messageaction/ChatMessageAction.java` | 新增 REPLY 操作类型 |
| `shared/src/main/java/com/november/mcphone/feature/chat/client/messageaction/ChatMessageActions.java` | 引用回调注入，保留原构造入口 |
| `shared/src/main/java/com/november/mcphone/feature/chat/group/GroupService.java` | 群成员/历史可见性验证、服务端快照及去重 |
| `shared/src/main/java/com/november/mcphone/feature/chat/group/net/GroupClientCache.java` | 跟踪群消息推送确认，复用有界操作集合 |
| `shared/src/main/java/com/november/mcphone/feature/chat/group/net/GroupCommand.java` | 文字请求增加可选原消息 ID，复用 request 作提交票据 |
| `shared/src/main/java/com/november/mcphone/feature/chat/group/net/GroupServer.java` | 使用群请求票据发送；去重只返回确认 |
| `shared/src/main/java/com/november/mcphone/feature/chat/group/net/GroupWire.java` | 有界群引用读写 |
| `shared/src/main/java/com/november/mcphone/feature/chat/net/ChatClientCache.java` | 相同消息 ID 的补发不产生重复气泡 |
| `shared/src/main/java/com/november/mcphone/feature/chat/net/ChatReplyWire.java` | 两代正文共用的有界引用与 ID 读写 |
| `shared/src/main/resources/THIRD-PARTY.txt` | Lucide 引用图标固定版本来源与许可登记 |
| `shared/src/main/resources/assets/mcphone/lang/en_us.json` | 引用菜单、预览与错误提示翻译 |
| `shared/src/main/resources/assets/mcphone/lang/zh_cn.json` | 引用菜单、预览与错误提示翻译 |
| `shared/src/main/resources/assets/mcphone/textures/chat/ui/reply.png` | 运行时圆环左引号 PNG（透明白色蒙版） |
| `shared/src/main/resources/licenses/lucide-reply-ISC.txt` | 运行包随附的上游 ISC 许可 |
| `versions/platform-twins.json` | 任务生成的平台差异基线；两代 codec 和 Fabric 专属握手差异 |
