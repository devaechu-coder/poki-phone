package com.november.mcphone.core.script.server.economy;

/**
 * 这一次货币调用是哪个 App 发起的，给流水的 appId 一格用。<b>由宿主盖章，不采信调用方</b>（§22.10）。
 *
 * <p>章盖在发起调用的线程上，而调用实际在主线程执行（{@link CurrencyGateway}）——
 * 所以章不能放在各 provider 自己的 ThreadLocal 里，网关要能在 worker 上取下、到主线程再盖回去。
 */
public final class CallingApp {

    private static final ThreadLocal<String> CURRENT = ThreadLocal.withInitial(() -> "-");

    private CallingApp() {
    }

    public static String current() {
        return CURRENT.get();
    }

    public static void enter(String appId) {
        CURRENT.set(appId == null ? "-" : appId);
    }

    public static void leave() {
        CURRENT.remove();
    }
}
