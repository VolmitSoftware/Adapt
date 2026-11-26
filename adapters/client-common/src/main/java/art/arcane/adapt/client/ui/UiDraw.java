package art.arcane.adapt.client.ui;

/**
 * Draws Adapt client UI in scaled GUI units. Colors are ARGB; a color with zero alpha draws nothing on every version.
 * Item ids, models and textures are namespaced ids such as {@code minecraft:diamond} or
 * {@code adapt:textures/gui/panel.png}.
 */
public interface UiDraw {
    int width();

    int height();

    double guiScale();

    int lineHeight();

    int textWidth(String text);

    void fill(int left, int top, int right, int bottom, int color);

    void gradient(int left, int top, int right, int bottom, int topColor, int bottomColor);

    void outline(int x, int y, int width, int height, int color);

    void text(String text, int x, int y, int color, boolean shadow);

    /** Draws text wrapped to {@code maxWidth} and returns the height it took. */
    int wrappedText(String text, int x, int y, int maxWidth, int color, boolean shadow);

    int wrappedHeight(String text, int maxWidth);

    /** Draws a 16x16 item icon; {@code model} is an item model id or null. Unknown items draw nothing. */
    void item(String itemId, String model, int x, int y);

    /**
     * Draws a 16x16 item icon with custom model data (0 = none) and, when {@code glint} is set, the enchantment
     * glint.
     */
    default void item(String itemId, String model, int customModelData, boolean glint, int x, int y) {
        item(itemId, model, x, y);
    }

    /** Starts a new layer: everything drawn after this, text included, appears above everything drawn before. */
    default void nextLayer() {
    }

    /**
     * Raises later draws by {@code z} until the pose is popped, so fills and text drawn over an item icon stay on top
     * on versions that depth-sort GUI elements. Elsewhere draw order decides and this does nothing.
     */
    default void depth(float z) {
    }

    /**
     * Recolours legacy section-sign colour codes in later text with {@code palette}, 16 ARGB colours indexed by code
     * ({@code 0}-{@code 9}, {@code a}-{@code f}), and leaves the shadow off dark text; null restores the vanilla look.
     */
    default void legacyPalette(int[] palette) {
    }

    /** Textures the client's resource packs provide, for drawing art only where it exists. */
    default UiSprites sprites() {
        return UiSprites.NONE;
    }

    /** Draws the texture region at (u, v) of size regionWidth x regionHeight stretched to width x height. */
    void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight);

    /**
     * Draws the texture region as {@link #blit(UiTexture, int, int, int, int, int, int, int, int)} does with every pixel
     * multiplied by {@code color} (ARGB): its alpha fades the region and its RGB tints it. Zero alpha draws nothing.
     */
    default void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight,
                      int color) {
        if ((color >>> 24) != 0) {
            blit(texture, x, y, width, height, u, v, regionWidth, regionHeight);
        }
    }

    void pushScissor(int left, int top, int right, int bottom);

    void popScissor();

    void pushPose();

    void popPose();

    void translate(float x, float y);

    void scale(float x, float y);

    default void blit(UiTexture texture, int x, int y, int width, int height) {
        blit(texture, x, y, width, height, 0, 0, texture.width(), texture.height());
    }

    default void centeredText(String text, int centerX, int y, int color, boolean shadow) {
        text(text, centerX - textWidth(text) / 2, y, color, shadow);
    }

    /**
     * Draws a texture region as a nine-slice: corners of {@code border} units keep their size, edges and centre
     * stretch.
     */
    default void nineSlice(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight,
                           int border) {
        int edge = Math.max(0, Math.min(border, Math.min(Math.min(width, height), Math.min(regionWidth, regionHeight)) / 2));
        int[] targetX = {x, x + edge, x + width - edge};
        int[] targetWidth = {edge, width - edge * 2, edge};
        int[] sourceX = {u, u + edge, u + regionWidth - edge};
        int[] sourceWidth = {edge, regionWidth - edge * 2, edge};
        int[] targetY = {y, y + edge, y + height - edge};
        int[] targetHeight = {edge, height - edge * 2, edge};
        int[] sourceY = {v, v + edge, v + regionHeight - edge};
        int[] sourceHeight = {edge, regionHeight - edge * 2, edge};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (targetWidth[column] > 0 && targetHeight[row] > 0 && sourceWidth[column] > 0 && sourceHeight[row] > 0) {
                    blit(texture, targetX[column], targetY[row], targetWidth[column], targetHeight[row], sourceX[column], sourceY[row],
                            sourceWidth[column], sourceHeight[row]);
                }
            }
        }
    }
}
