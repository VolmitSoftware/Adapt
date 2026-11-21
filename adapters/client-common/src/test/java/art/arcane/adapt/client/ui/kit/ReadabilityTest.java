package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.FakeResources;
import art.arcane.adapt.client.ui.PngLuma;
import art.arcane.adapt.client.ui.UiSpriteCache;
import art.arcane.adapt.client.view.IconRef;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tooltips, field values and icons stay readable over whatever they are drawn on. */
class ReadabilityTest {
    private static final int TILE_BACKDROP = UiContrast.over(UiTheme.DEFAULT.tile(), UiTheme.DEFAULT.backdrop());

    @Test
    void tooltipBackgroundsAreOpaque() {
        KitDraw draw = new KitDraw();
        new Tooltip("Adaptation enabled", List.of("Current: Off")).render(draw, UiTheme.DEFAULT, 100, 100, 427, 240);
        List<String> calls = draw.calls();
        int border = calls.indexOf(calls.stream().filter((String call) -> call.startsWith("gradient ")).findFirst().orElseThrow());
        List<String> body = calls.subList(0, border).stream().filter((String call) -> call.startsWith("fill ")).toList();
        assertEquals(5, body.size(), calls.toString());
        for (String fill : body) {
            String color = fill.substring(fill.lastIndexOf(' ') + 1);
            assertTrue(color.length() == 8 && color.startsWith("ff"), "see-through tooltip fill: " + fill);
        }
    }

    @Test
    void longFieldValuesStopShortOfTheEdgeAndShowTheyAreCut() {
        FakeHost host = new FakeHost(427, 240);
        TextField field = new TextField("field", 256);
        UiRect box = new UiRect(10, 10, 108, 14);
        TestScreen screen = new TestScreen(new ManualClock().motion(), (UiRect area, art.arcane.adapt.client.ui.UiDraw metrics) ->
                field.place(box, metrics));
        screen.root().add(field);
        host.open(screen);
        field.setText("jdbc:mysql://database.example.org:3306/adapt_profiles?useSSL=false");
        KitDraw draw = new KitDraw();
        screen.render(draw, 0, 0, 0.0F);
        String text = draw.calls("text ").get(0);
        String shown = text.substring(text.indexOf(' ', 5) + 1);
        assertTrue(shown.endsWith(LegacyText.ELLIPSIS), text);
        int x = Integer.parseInt(text.substring(5, text.indexOf(',')));
        assertTrue(x >= box.x() + 3, text);
        assertTrue(x + draw.textWidth(shown) <= box.right() - 3, text + " reaches the edge of " + box);
        screen.focus(field);
        draw.calls().clear();
        screen.render(draw, 0, 0, 0.0F);
        String scissor = draw.calls("pushScissor ").get(0);
        int right = Integer.parseInt(scissor.substring("pushScissor ".length()).split(",")[2]);
        assertTrue(right <= box.right() - 3, scissor + " lets typed text reach the edge of " + box);
    }

    @Test
    void paleIconsGetAShadeThatReachesGraphicContrastAndDarkIconsDoNot() {
        double ground = UiContrast.luminance(TILE_BACKDROP);
        for (double icon : new double[] {0.75, 0.8, 0.9, 1.0}) {
            if (UiContrast.ratio(icon, ground) >= UiContrast.GRAPHIC) {
                continue;
            }
            int plate = UiContrast.iconPlate(icon, TILE_BACKDROP);
            assertTrue(plate != 0, "no plate for luminance " + icon);
            double onPlate = UiContrast.ratio(icon, UiContrast.luminance(plate));
            assertTrue(onPlate >= UiContrast.GRAPHIC, icon + " on " + Integer.toHexString(plate) + ": " + onPlate);
        }
        for (double icon : new double[] {0.0, 0.05, 0.2, 0.4}) {
            assertEquals(0, UiContrast.iconPlate(icon, TILE_BACKDROP), "plate behind a dark icon " + icon);
        }
        assertEquals(0, UiContrast.iconPlate(PngLuma.UNKNOWN, TILE_BACKDROP));
    }

    @Test
    void aPaleItemTextureGetsAShadeBehindItsIcon() throws IOException {
        FakeResources resources = new FakeResources()
                .put("minecraft:textures/item/snowball.png", solid(0xFFF8F8F8))
                .put("minecraft:textures/block/wither_rose.png", solid(0xFF262626));
        KitDraw draw = new KitDraw().withSprites(new UiSpriteCache(resources));
        UiIcons.paintOn(draw, IconRef.item("minecraft:snowball"), 20, 20, 16, false, TILE_BACKDROP);
        UiIcons.paintOn(draw, IconRef.item("minecraft:wither_rose"), 60, 20, 16, false, TILE_BACKDROP);
        List<String> fills = draw.calls("fill ");
        assertEquals(3, fills.size(), draw.calls().toString());
        assertTrue(fills.get(1).startsWith("fill 19,20,37,36 "), fills.toString());
        assertTrue(draw.calls().indexOf(fills.get(2)) < draw.calls().indexOf("item minecraft:snowball cmd=0 glint=false 20,20"));
    }

    @Test
    void legacyInkKeepsTextContrastOnEveryPaperSurface() {
        UiTheme theme = UiTheme.DEFAULT;
        int[] papers = {theme.backdrop(), TILE_BACKDROP, theme.tooltip(), theme.surface(), theme.surfaceHover(), theme.surfacePressed(),
                UiContrast.over(UiTome.HOVER, theme.backdrop())};
        for (int index = 0; index < theme.legacyColours().length; index++) {
            for (int paper : papers) {
                double ratio = UiContrast.ratio(theme.legacyColours()[index], paper);
                assertTrue(ratio >= UiContrast.TEXT, "code " + Integer.toHexString(index) + " on " + Integer.toHexString(paper) + ": " + ratio);
            }
        }
        for (int text : new int[] {theme.text(), theme.textMuted()}) {
            for (int paper : papers) {
                assertTrue(UiContrast.ratio(text, paper) >= UiContrast.TEXT, Integer.toHexString(text) + " on " + Integer.toHexString(paper));
            }
        }
        assertTrue(UiContrast.ratio(UiTome.GILT, theme.ribbon()) >= UiContrast.TEXT);
        assertTrue(UiContrast.ratio(UiEase.mix(theme.textInverse(), theme.ribbon(), 0.25F), theme.ribbon()) >= UiContrast.TEXT);
    }

    @Test
    void edgesThatMarkAControlOrAStateReachGraphicContrastOnThePage() {
        UiTheme theme = UiTheme.DEFAULT;
        int page = theme.backdrop();
        int[] edges = {theme.surfaceBorder(), theme.focus(), theme.destructive(), UiRamp.PAGE[6], UiRamp.GOLD[1], UiRamp.GOLD[0],
                UiRamp.MOSS[1], UiRamp.WINE[2], UiRamp.LEATHER[1], UiRamp.LEATHER[4]};
        for (int edge : edges) {
            assertTrue(UiContrast.ratio(edge, page) >= UiContrast.GRAPHIC, Integer.toHexString(edge) + " on the page: "
                    + UiContrast.ratio(edge, page));
        }
    }

    @Test
    void buttonLabelsReadOnEveryButtonFill() {
        UiTheme theme = UiTheme.DEFAULT;
        int[] fills = {UiRamp.PAGE[2], UiRamp.PAGE[3], UiRamp.PAGE[4], UiRamp.MOSS[3], UiRamp.MOSS[4], UiRamp.MOSS[5], UiRamp.WINE[4],
                UiRamp.WINE[5], UiRamp.WINE[6]};
        for (int fill : fills) {
            assertTrue(UiContrast.ratio(theme.text(), fill) >= UiContrast.TEXT, "text on " + Integer.toHexString(fill));
        }
    }

    private static byte[] solid(int argb) throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
