/**
 * 聊天：查玩家、查好友、查会话、以玩家的名义发私信。
 *
 * <p><b>运行端</b>：类两端都能加载，但只有服务端有数据 —— 参数里的 {@code MinecraftServer} /
 * {@code ServerPlayer} 只在逻辑服务端拿得到。
 *
 * <p><b>线程</b>：全部方法只能在服务端主线程调，别的线程调直接抛 {@link java.lang.IllegalStateException}。
 * 聊天数据是不加锁的 SavedData，异步回调里要先 {@code server.execute(...)} 回到主线程，并在那里重新取玩家。
 *
 * <p><b>权限</b>：不查 OP 等级 —— 调用方是服务端代码，谁有资格触发由附属自己判。
 * 写操作守的规矩与手机界面相同：只能发给好友，发件人身上得有手机。这里不提供加好友：
 * 好友要对方同意，替玩家建立关系等于绕过对方。
 *
 * <p><b>冒名</b>：代发的私信在收件人那边与玩家亲手发的无从分辨，存档里也没有来源标记。
 * 只在玩家本人发起的操作里代他发，别用它发玩家不知情的内容。
 *
 * <p><b>兼容</b>：{@link com.november.mcphone.api.chat.SendResult} 与
 * {@link com.november.mcphone.api.chat.ContactRelation} 以后可能加取值，{@code switch} 要留 {@code default}。
 * 需要 {@code MCphoneApi.VERSION >= 3}。
 */
package com.november.mcphone.api.chat;
