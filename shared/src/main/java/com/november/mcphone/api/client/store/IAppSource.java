package com.november.mcphone.api.client.store;

import com.november.mcphone.api.client.app.IPhoneApp;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;

/**
 * 应用来源：商店的 App 从哪来。实现方不限定 App 实例的产生方式，
 * install() 只要求最终交出一个可用的 {@link IPhoneApp}。
 *
 * 通过 SPI 注册：META-INF/services/com.november.mcphone.api.client.store.IAppSource。
 *
 * listAvailable / install 可在后台线程干活，但回调必须切回客户端主线程
 * （回调里会碰注册表和 GUI 状态）：{@code Minecraft.getInstance().execute(...)}。
 */
public interface IAppSource {

    /** 来源唯一标识，形如 {@code mymod:official_repo}；命名空间用你自己的 modid */
    ResourceLocation getId();

    /** 商店分组标题上显示的名字 */
    Component getDisplayName();

    /**
     * 列出此来源当前可安装的 App，只返回尚未安装的。
     * 允许异步；callback 必须在客户端主线程执行。
     */
    void listAvailable(Consumer<List<AppInfo>> callback);

    /**
     * 安装指定 App（info 来自本来源的 listAvailable），实现方负责让它真正可用。
     * onSuccess 交出可用实例，onError 给一条可展示给玩家的错误信息。
     */
    void install(AppInfo info, Consumer<IPhoneApp> onSuccess, Consumer<Component> onError);

    /** 返回 false 时商店把此来源标记为不可用（离线、未登录、加载中）。默认 true */
    default boolean isReady() { return true; }

    /**
     * 玩家在安装界面上确认了一次签名（§12.4 的二次确认与确认短语）。
     *
     * <p>界面在调 {@link #install} 之前调它，把玩家输入的东西交过来；<b>放行与否由来源自己判</b>。
     * 界面那一层只负责收集输入 —— 判据写在界面里的话，任何不走界面的调用方都能绕开。
     *
     * <p>默认返回 true：没有签名这一说的来源（内建、附属自己的商店）不受影响。
     *
     * @param typedPhrase 玩家输入的东西；不需要短语的档位传什么都行
     * @return 确认成功了没有。false 时界面不该继续调 {@link #install}
     */
    default boolean confirmSignature(AppInfo info, String typedPhrase) { return true; }
}
