package com.november.mcphone.api.client.ui;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

/** 真正编出旧接口及旧附属字节码，再用当前接口加载，验证新增 default 方法的二进制兼容。 */
public final class PhonePageBinaryCompatibilityTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("mcphone-api6-compat-");
        try {
            Path api = root.resolve("com/november/mcphone/api/client/ui/IPhonePage.java");
            Files.createDirectories(api.getParent());
            Files.writeString(api, """
                    package com.november.mcphone.api.client.ui;
                    public interface IPhonePage {
                        void render(PhoneCanvas canvas);
                        default boolean mouseClicked(double x,double y,int b) { return false; }
                        default boolean mouseScrolled(double x,double y,double amount) { return false; }
                        default boolean keyPressed(int k,int s,int m) { return false; }
                        default boolean charTyped(char c,int m) { return false; }
                        default boolean capturesKeyboard() { return false; }
                        default boolean onBack() { return false; }
                        default void onOpen() {}
                        default void onClose() {}
                    }
                    """);
            Path addon = root.resolve("legacy/OldPage.java");
            Files.createDirectories(addon.getParent());
            Files.writeString(addon, """
                    package legacy;
                    import com.november.mcphone.api.client.ui.*;
                    public class OldPage implements IPhonePage {
                        public void render(PhoneCanvas canvas) {}
                        public boolean charTyped(char c,int modifiers) { return true; }
                        public boolean mouseScrolled(double x,double y,double amount) { return amount == 2; }
                    }
                    """);
            var compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) throw new AssertionError("Compatibility check requires a JDK");
            String classes = Path.of(IPhonePage.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            String classpath = classes + java.io.File.pathSeparator + System.getProperty("java.class.path");
            int result = compiler.run(null, null, null, "--release", "17", "-classpath", classpath,
                    "-d", root.toString(), api.toString(), addon.toString());
            if (result != 0) throw new AssertionError("Legacy fixture compilation failed: " + result);
            try (var loader = new URLClassLoader(new java.net.URL[]{root.toUri().toURL()}, IPhonePage.class.getClassLoader())) {
                IPhonePage page = (IPhonePage) loader.loadClass("legacy.OldPage").getConstructor().newInstance();
                if (page.mouseDragged(0,0,1,2,3) || page.mouseReleased(0,0,1)
                        || page.keyReleased(0,0,0)) throw new AssertionError("Unexpected default consumption");
                page.mouseMoved(0,0); page.onInputCancelled(); page.tick();
                page.onResize(new PhoneViewport(0,0,100,200,1));
                if (!page.charTyped(0x1f680,0) || !page.mouseScrolled(0,0,3,2))
                    throw new AssertionError("Legacy callback bridge failed");
            }
            System.out.println("PhonePageBinaryCompatibilityTest passed: API 6 bytecode on API 7");
        } finally {
            // 只删除本测试创建的临时目录，路径直接来自 createTempDirectory。
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
