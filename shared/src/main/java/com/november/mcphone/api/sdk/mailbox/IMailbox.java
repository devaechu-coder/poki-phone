package com.november.mcphone.api.sdk.mailbox;

import com.november.mcphone.api.sdk.item.ItemRef;

import java.util.List;
import java.util.UUID;

/**
 * 全手机一个收件箱（施工方案 §23.3）。
 *
 * <p><b>为什么必须统一</b>：§20.9 的 {@code onFull: mailbox} 策略如果每个 App 各存一份，
 * 玩家要在十个 App 里各点一遍才知道有没有漏领。等十个 App 都发布了再统一就来不及了。
 *
 * <p><b>界面由宿主提供一个内建「收件箱」App</b>，不是每个 App 自己做。
 *
 * <p><b>存东西不等于造东西</b>：存自己的东西是 {@code plain} 档；凭空造出来的东西
 * 由 {@code item.give} / {@code loot.roll} 那一层管，不归这里。
 *
 * <p>只在服务端调用。
 */
public interface IMailbox {

    /** 每玩家的容量，一个箱子。 */
    int CAPACITY = 27;

    /** 一次最多存几件。与 {@link ItemRef#MAX_BATCH} 同源，理由见那里（RPC 参数上限）。 */
    int MAX_BATCH = ItemRef.MAX_BATCH;

    /** 默认保留天数。到期前 3 天通知，过期进审计并销毁（§23.3）。 */
    int RETENTION_DAYS = 30;

    /**
     * 存入。
     *
     * <p><b>要么全存进去要么一件都不存</b>。§23.3 只写了"满了返回 FULL"，没说 5 件里放得下 3 件怎么办 ——
     * 而"存了一半"这个答案是不能选的：调用方没法知道存进去了哪几件，只能整批重试，
     * 于是重试就是重复发放。这与 §22.3 ④ 拒绝 withdraw + deposit 两步 API 是同一条理由。
     */
    DepositResult deposit(UUID player, List<ItemRef> items, String reason);

    /** 待领取件数。角标与首页汇总读它。 */
    int count(UUID player);
}
