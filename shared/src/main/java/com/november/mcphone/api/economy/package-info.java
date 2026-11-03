/**
 * 通用货币 SDK（施工方案 §22）。A 档六项之一，判据与版本契约见
 * {@link com.november.mcphone.api.sdk}（那份 package-info 里的跨版本写法约束<b>同样适用于本包</b>）。
 *
 * <p><b>为什么它不在 {@code api.sdk.economy} 下</b>：§22.4 与 §23.4 的示例都写的是
 * {@code api/economy/}，而包名也是 API —— 挪一个类的包等于删了它再新建一个
 * （{@link com.november.mcphone.api.MCphoneApi} 第四条）。所以它留在这里，
 * 是"manifest 的 sdk 键 ↔ 包名"那条对应关系的<b>唯一例外</b>。
 *
 * <p>实现由 S15 交付，本包只有契约。
 */
package com.november.mcphone.api.economy;
