package art.arcane.adapt.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link UiDraw} over the frame's {@link GuiGraphics}. Used on the render thread only. These versions have no item
 * model component, so the model id is ignored.
 */
public final class MinecraftUiDraw implements UiDraw {
    private static final int INKED_LIMIT = 512;
    private static final float LAYER_DEPTH = 400.0F;
    private final Map<String, ResourceLocation> textures = new HashMap<>();
    private final Map<String, ItemStack> items = new HashMap<>();
    private final Map<String, Component> inked = new HashMap<>();
    private int[] palette;
    private GuiGraphics graphics;

    public MinecraftUiDraw bind(GuiGraphics frame) {
        graphics = frame;
        return this;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private static boolean visible(int color) {
        return (color >>> 24) != 0;
    }

    @Override
    public int width() {
        return graphics.guiWidth();
    }

    @Override
    public int height() {
        return graphics.guiHeight();
    }

    @Override
    public double guiScale() {
        return Minecraft.getInstance().getWindow().getGuiScale();
    }

    @Override
    public int lineHeight() {
        return font().lineHeight;
    }

    @Override
    public int textWidth(String text) {
        return font().width(text);
    }

    @Override
    public void fill(int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, bottom, color);
    }

    @Override
    public void gradient(int left, int top, int right, int bottom, int topColor, int bottomColor) {
        graphics.fillGradient(left, top, right, bottom, topColor, bottomColor);
    }

    @Override
    public void outline(int x, int y, int width, int height, int color) {
        graphics.renderOutline(x, y, width, height, color);
    }

    @Override
    public void text(String text, int x, int y, int color, boolean shadow) {
        if (!visible(color)) {
            return;
        }
        boolean dropShadow = shadow && (palette == null || LegacyRun.light(color));
        if (palette == null || text.indexOf(LegacyRun.SECTION) < 0) {
            graphics.drawString(font(), text, x, y, color, dropShadow);
        } else {
            graphics.drawString(font(), component(text).getVisualOrderText(), x, y, color, dropShadow);
        }
    }

    @Override
    public int wrappedText(String text, int x, int y, int maxWidth, int color, boolean shadow) {
        Font font = font();
        List<FormattedCharSequence> lines = font.split(component(text), maxWidth);
        if (visible(color)) {
            boolean dropShadow = shadow && (palette == null || LegacyRun.light(color));
            int lineY = y;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, x, lineY, color, dropShadow);
                lineY += font.lineHeight;
            }
        }
        return lines.size() * font.lineHeight;
    }

    @Override
    public int wrappedHeight(String text, int maxWidth) {
        Font font = font();
        return font.split(Component.literal(text), maxWidth).size() * font.lineHeight;
    }

    @Override
    public void legacyPalette(int[] colours) {
        if (colours != palette) {
            palette = colours;
            inked.clear();
        }
    }

    /** {@code text} with its legacy codes in the current palette, or as a plain literal when there is none. */
    private Component component(String text) {
        if (palette == null || text.indexOf(LegacyRun.SECTION) < 0) {
            return Component.literal(text);
        }
        Component known = inked.get(text);
        if (known != null) {
            return known;
        }
        MutableComponent built = Component.empty();
        for (LegacyRun run : LegacyRun.parse(text, palette)) {
            Style style = Style.EMPTY.withBold(run.bold()).withItalic(run.italic()).withUnderlined(run.underlined())
                    .withStrikethrough(run.strikethrough()).withObfuscated(run.obfuscated());
            built.append(Component.literal(run.text()).withStyle(run.color() == 0 ? style : style.withColor(run.color() & 0xFFFFFF)));
        }
        if (inked.size() >= INKED_LIMIT) {
            inked.clear();
        }
        inked.put(text, built);
        return built;
    }

    @Override
    public void item(String itemId, String model, int x, int y) {
        item(itemId, model, 0, false, x, y);
    }

    @Override
    public void item(String itemId, String model, int customModelData, boolean glint, int x, int y) {
        ItemStack stack = items.computeIfAbsent(itemId + '|' + customModelData + '|' + glint,
                (String key) -> MinecraftItemStacks.create(itemId, customModelData, glint));
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
        }
    }

    @Override
    public void nextLayer() {
        graphics.pose().translate(0.0F, 0.0F, LAYER_DEPTH);
    }

    @Override
    public void depth(float z) {
        graphics.pose().translate(0.0F, 0.0F, z);
    }

    @Override
    public UiSprites sprites() {
        return MinecraftUiResources.SPRITES;
    }

    @Override
    public void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
        ResourceLocation id = textures.computeIfAbsent(texture.id(), ResourceLocation::tryParse);
        if (id != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.blit(id, x, y, width, height, u, v, regionWidth, regionHeight, texture.width(), texture.height());
            RenderSystem.disableBlend();
        }
    }

    @Override
    public void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight, int color) {
        if (!visible(color)) {
            return;
        }
        ResourceLocation id = textures.computeIfAbsent(texture.id(), ResourceLocation::tryParse);
        if (id != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.setColor((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, (color >>> 24) / 255.0F);
            graphics.blit(id, x, y, width, height, u, v, regionWidth, regionHeight, texture.width(), texture.height());
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    @Override
    public void pushScissor(int left, int top, int right, int bottom) {
        graphics.enableScissor(left, top, right, bottom);
    }

    @Override
    public void popScissor() {
        graphics.disableScissor();
    }

    @Override
    public void pushPose() {
        graphics.pose().pushPose();
    }

    @Override
    public void popPose() {
        graphics.pose().popPose();
    }

    @Override
    public void translate(float x, float y) {
        graphics.pose().translate(x, y, 0.0F);
    }

    @Override
    public void scale(float x, float y) {
        graphics.pose().scale(x, y, 1.0F);
    }
}
