/**
 * A 档与 B 档 SDK（施工方案 §23）：<b>两个不同作者的 App 必须就此事达成一致</b>的那些东西。
 *
 * <p>判据只有一条（§23.1）：需要达成一致 → 必须 SDK 化，而且要在<b>第一个 App 发布之前</b>定型。
 * 一旦有 App 在用就改不动了 —— 等生态长出来再统一，等于要求所有人同时改代码，那不会发生。
 * 反面教材是 {@code api.cost.IEmcWallet}：名字写进了类型、单例、没有转账，现在只能留着当兼容层。
 *
 * <p>版本契约见 {@link com.november.mcphone.api.sdk.SdkVersions}，门控见
 * {@link com.november.mcphone.api.sdk.SdkGate}，总的兼容承诺见
 * {@link com.november.mcphone.api.MCphoneApi}。
 *
 * <h2>这个包里只许出现两个版本都成立的写法</h2>
 *
 * {@code api/} 在 {@code shared/} 下，要在每个目标上各编一遍。构建那道
 * {@code verifySharedIsTargetNeutral} 扫的是四条判据，但<b>它有一个已知盲区：签名漂移</b> ——
 * 类名与 import 都一样、方法签名换了的那种，扫不出来。眼下踩得着的是这一条：
 *
 * <pre>
 * ✅ ResourceLocation.tryParse(s)          两个版本都有
 * ❌ new ResourceLocation(s)               1.21 起构造器私有
 * ❌ ResourceLocation.parse(s)             1.20.1 没有
 * ❌ ResourceLocation.fromNamespaceAndPath 1.20.1 没有
 * </pre>
 *
 * 写错了 CI 会在 1.20.1-forge 上红，但那时是一大片编译错。先照着写，别让它红。
 */
package com.november.mcphone.api.sdk;
