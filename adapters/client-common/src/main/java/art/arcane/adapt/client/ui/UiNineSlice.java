package art.arcane.adapt.client.ui;

import java.util.Objects;

/**
 * A texture drawn as nine slices: corners keep their size, edges and the centre stretch, or repeat at their own size
 * when {@code tiled} so pixel art and paper grain never smear. {@code border} is in texture pixels and {@code scale} is
 * texture pixels per GUI unit, so a 2x texture with {@code scale} 2 draws as crisp as a 1x one.
 */
public record UiNineSlice(UiTexture texture, int border, int scale, boolean tiled) {
    public UiNineSlice {
        Objects.requireNonNull(texture, "texture");
        if (border < 0 || scale < 1) {
            throw new IllegalArgumentException("border must be >= 0 and scale >= 1: " + border + ", " + scale);
        }
    }

    public void paint(UiDraw draw, int x, int y, int width, int height) {
        int edge = Math.max(0, Math.min(border / scale, Math.min(width, height) / 2));
        int source = edge * scale;
        int[] targetX = {x, x + edge, x + width - edge};
        int[] targetWidth = {edge, width - edge * 2, edge};
        int[] targetY = {y, y + edge, y + height - edge};
        int[] targetHeight = {edge, height - edge * 2, edge};
        int[] sourceX = {0, source, texture.width() - source};
        int[] sourceWidth = {source, texture.width() - source * 2, source};
        int[] sourceY = {0, source, texture.height() - source};
        int[] sourceHeight = {source, texture.height() - source * 2, source};
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                if (targetWidth[column] <= 0 || targetHeight[row] <= 0 || sourceWidth[column] <= 0 || sourceHeight[row] <= 0) {
                    continue;
                }
                if (tiled && (column == 1 || row == 1)) {
                    repeat(draw, targetX[column], targetY[row], targetWidth[column], targetHeight[row], sourceX[column], sourceY[row],
                            sourceWidth[column], sourceHeight[row]);
                } else {
                    draw.blit(texture, targetX[column], targetY[row], targetWidth[column], targetHeight[row], sourceX[column],
                            sourceY[row], sourceWidth[column], sourceHeight[row]);
                }
            }
        }
    }

    private void repeat(UiDraw draw, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
        int stepX = Math.max(1, regionWidth / scale);
        int stepY = Math.max(1, regionHeight / scale);
        for (int top = 0; top < height; top += stepY) {
            int shownHeight = Math.min(stepY, height - top);
            for (int left = 0; left < width; left += stepX) {
                int shownWidth = Math.min(stepX, width - left);
                draw.blit(texture, x + left, y + top, shownWidth, shownHeight, u, v, shownWidth * scale, shownHeight * scale);
            }
        }
    }
}
