import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** 依据首页菜单参考手绘两枚细线图标；同时保留 SVG 原稿与可着色的透明白色 PNG。 */
public class GenerateChatHomeIcons {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root.resolve("shared/src/main/resources"))) throw new IllegalArgumentException("需要仓库根目录");
        Path sources = root.resolve("docs/assets/chat-ui/home-menu");
        Path textures = root.resolve("shared/src/main/resources/assets/mcphone/textures/chat/ui");
        Files.createDirectories(sources); Files.createDirectories(textures);
        for (boolean group : new boolean[]{true, false}) {
            String name = group ? "create_group" : "add_contact";
            Path2D.Float path = new Path2D.Float();
            String shapes;
            if (group) {
                path.moveTo(12, 3); path.curveTo(6.9, 3, 3, 6.4, 3, 11);
                path.curveTo(3, 15.2, 6.6, 18.5, 11, 18.9); path.lineTo(15.1, 21);
                path.curveTo(15.7, 21.3, 16.3, 20.8, 16.2, 20.1); path.lineTo(15.7, 18.1);
                path.curveTo(18.9, 16.7, 21, 14.1, 21, 11); path.curveTo(21, 6.5, 17.1, 3, 12, 3); path.closePath();
                path.moveTo(9, 11); path.lineTo(15, 11); path.moveTo(12, 8); path.lineTo(12, 14);
                shapes = "<path d=\"M12 3C6.9 3 3 6.4 3 11c0 4.2 3.6 7.5 8 7.9l4.1 2.1c.6.3 1.2-.2 1.1-.9l-.5-2c3.2-1.4 5.3-4 5.3-7.1 0-4.5-3.9-8-9-8Z M9 11h6 M12 8v6\"/>";
            } else {
                path.append(new Ellipse2D.Float(6, 3, 7, 7), false);
                path.moveTo(12.6, 21); path.lineTo(3, 21); path.lineTo(3, 19);
                path.curveTo(3, 15.8, 5.8, 14, 9.5, 14); path.curveTo(10.7, 14, 11.8, 14.2, 12.8, 14.6);
                path.moveTo(15, 18); path.lineTo(21, 18); path.moveTo(18, 15); path.lineTo(18, 21);
                shapes = "<circle cx=\"9.5\" cy=\"6.5\" r=\"3.5\"/><path d=\"M12.6 21H3v-2c0-3.2 2.8-5 6.5-5 1.2 0 2.3.2 3.3.6 M15 18h6 M18 15v6\"/>";
            }
            String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"96\" height=\"96\" viewBox=\"0 0 24 24\"><g fill=\"none\" stroke=\"white\" stroke-width=\"1.7\" stroke-linecap=\"round\" stroke-linejoin=\"round\">" + shapes + "</g></svg>\n";
            Files.writeString(sources.resolve(name + ".svg"), svg, StandardCharsets.UTF_8);
            BufferedImage large = new BufferedImage(384, 384, BufferedImage.TYPE_INT_ARGB);
            var graphics = large.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.scale(16, 16); graphics.setColor(Color.WHITE);
                graphics.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); graphics.draw(path);
            } finally { graphics.dispose(); }
            BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
            // 只平均覆盖率，透明像素不混入黑色，图标在界面中可统一着色。
            for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) {
                int alpha = 0;
                for (int dy = 0; dy < 4; dy++) for (int dx = 0; dx < 4; dx++) alpha += large.getRGB(x * 4 + dx, y * 4 + dy) >>> 24;
                image.setRGB(x, y, ((alpha + 8) / 16) << 24 | 0xFFFFFF);
            }
            ImageIO.write(image, "png", textures.resolve(name + ".png").toFile());
        }
        System.out.println("已生成两枚首页菜单图标：96×96 透明白色 PNG 与 SVG 原稿");
    }
}
