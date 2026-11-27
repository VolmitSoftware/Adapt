package art.arcane.adapt.pack.raster;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiSprites;
import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.pack.text.PackFontMetrics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * {@link UiDraw} onto an ARGB raster in GUI units, one pixel per unit: fills, gradients and nearest-neighbour blits
 * composited source-over, a scissor stack and a pose stack of translations and scales. Text and items are not drawn;
 * each call is kept as a {@link TextCall} or {@link ItemCall} with its place on the raster, while the text metrics
 * answer as the vanilla font does. A call budget stops drawing after that many fill and blit calls, which lets a caller
 * paint only the recorded first part of a frame.
 */
public final class RasterDraw implements UiDraw {
    private final Raster raster;
    private final UiSprites sprites;
    private final RasterTextures textures;
    private final PackFontMetrics font = PackFontMetrics.vanilla();
    private final Deque<Clip> scissors = new ArrayDeque<>();
    private final Deque<Pose> poses = new ArrayDeque<>();
    private final List<TextCall> texts = new ArrayList<>();
    private final List<ItemCall> items = new ArrayList<>();
    private Pose pose = Pose.IDENTITY;
    private long budget = Long.MAX_VALUE;
    private long skip;
    private long calls;

    /** A scissor box in raster pixels, right and bottom exclusive. */
    public record Clip(int left, int top, int right, int bottom) {
        Clip and(Clip other) {
            return new Clip(Math.max(left, other.left), Math.max(top, other.top), Math.min(right, other.right), Math.min(bottom, other.bottom));
        }
    }

    private record Pose(double tx, double ty, double sx, double sy) {
        static final Pose IDENTITY = new Pose(0, 0, 1, 1);

        double x(double x) {
            return tx + x * sx;
        }

        double y(double y) {
            return ty + y * sy;
        }
    }

    /** One text call: where it would draw on the raster, its colour, shadow, scale and the clip in force. */
    public record TextCall(String text, int x, int y, int color, boolean shadow, double scale, Clip clip) {
    }

    /** One item call: the item, its model and custom model data, glint, and its top left on the raster. */
    public record ItemCall(String itemId, String model, int customModelData, boolean glint, int x, int y, double scale, Clip clip) {
    }

    public RasterDraw(Raster raster, UiSprites sprites, RasterTextures textures) {
        this.raster = Objects.requireNonNull(raster, "raster");
        this.sprites = Objects.requireNonNull(sprites, "sprites");
        this.textures = Objects.requireNonNull(textures, "textures");
    }

    public Raster raster() {
        return raster;
    }

    public List<TextCall> texts() {
        return texts;
    }

    public List<ItemCall> items() {
        return items;
    }

    /** Draws only the first {@code count} fill and blit calls from now on; later ones are counted and dropped. */
    public void budget(long count) {
        budget = count;
        calls = 0;
        skip = 0;
    }

    /** Drops the first {@code count} fill and blit calls from now on and draws the rest. */
    public void skip(long count) {
        skip = count;
        budget = Long.MAX_VALUE;
        calls = 0;
    }

    private boolean spend() {
        long index = calls++;
        return index >= skip && index - skip < budget;
    }

    @Override
    public int width() {
        return raster.width();
    }

    @Override
    public int height() {
        return raster.height();
    }

    @Override
    public double guiScale() {
        return 1.0;
    }

    @Override
    public int lineHeight() {
        return font.lineHeight();
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public UiSprites sprites() {
        return sprites;
    }

    @Override
    public void fill(int left, int top, int right, int bottom, int color) {
        if (!spend() || (color >>> 24) == 0) {
            return;
        }
        Clip box = box(left, top, right, bottom);
        for (int y = box.top; y < box.bottom; y++) {
            for (int x = box.left; x < box.right; x++) {
                raster.blend(x, y, color);
            }
        }
    }

    @Override
    public void gradient(int left, int top, int right, int bottom, int topColor, int bottomColor) {
        if (!spend()) {
            return;
        }
        Clip box = box(left, top, right, bottom);
        double y0 = pose.y(top);
        double span = Math.max(1e-9, pose.y(bottom) - y0);
        for (int y = box.top; y < box.bottom; y++) {
            int color = lerp(topColor, bottomColor, (y + 0.5 - y0) / span);
            for (int x = box.left; x < box.right; x++) {
                raster.blend(x, y, color);
            }
        }
    }

    @Override
    public void outline(int x, int y, int width, int height, int color) {
        fill(x, y, x + width, y + 1, color);
        fill(x, y + height - 1, x + width, y + height, color);
        fill(x, y + 1, x + 1, y + height - 1, color);
        fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    @Override
    public void text(String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty() || (color >>> 24) == 0) {
            return;
        }
        texts.add(new TextCall(text, (int) Math.floor(pose.x(x)), (int) Math.floor(pose.y(y)), color, shadow, pose.sx(), clip()));
    }

    @Override
    public int wrappedText(String text, int x, int y, int maxWidth, int color, boolean shadow) {
        List<String> lines = font.wrap(text, maxWidth);
        for (int index = 0; index < lines.size(); index++) {
            text(lines.get(index), x, y + index * font.lineHeight(), color, shadow);
        }
        return lines.size() * font.lineHeight();
    }

    @Override
    public int wrappedHeight(String text, int maxWidth) {
        return font.wrap(text, maxWidth).size() * font.lineHeight();
    }

    @Override
    public void item(String itemId, String model, int x, int y) {
        item(itemId, model, 0, false, x, y);
    }

    @Override
    public void item(String itemId, String model, int customModelData, boolean glint, int x, int y) {
        items.add(new ItemCall(itemId, model, customModelData, glint, (int) Math.floor(pose.x(x)), (int) Math.floor(pose.y(y)), pose.sx(), clip()));
    }

    @Override
    public void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight) {
        blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, 0xFFFFFFFF);
    }

    @Override
    public void blit(UiTexture texture, int x, int y, int width, int height, int u, int v, int regionWidth, int regionHeight, int color) {
        if (!spend() || (color >>> 24) == 0 || width <= 0 || height <= 0 || regionWidth <= 0 || regionHeight <= 0) {
            return;
        }
        Raster source = textures.get(texture);
        if (source == null) {
            return;
        }
        double x0 = pose.x(x);
        double y0 = pose.y(y);
        double drawnWidth = width * pose.sx();
        double drawnHeight = height * pose.sy();
        Clip box = box(x, y, x + width, y + height);
        boolean tinted = color != 0xFFFFFFFF;
        for (int py = box.top; py < box.bottom; py++) {
            int sy = v + (int) Math.floor((py + 0.5 - y0) * regionHeight / drawnHeight);
            for (int px = box.left; px < box.right; px++) {
                int sx = u + (int) Math.floor((px + 0.5 - x0) * regionWidth / drawnWidth);
                int texel = source.get(sx, sy);
                if ((texel >>> 24) == 0) {
                    continue;
                }
                raster.blend(px, py, tinted ? multiply(texel, color) : texel);
            }
        }
    }

    @Override
    public void pushScissor(int left, int top, int right, int bottom) {
        Clip box = new Clip((int) Math.floor(pose.x(left)), (int) Math.floor(pose.y(top)), (int) Math.ceil(pose.x(right)),
                (int) Math.ceil(pose.y(bottom)));
        scissors.push(scissors.isEmpty() ? box : scissors.peek().and(box));
    }

    @Override
    public void popScissor() {
        scissors.pop();
    }

    @Override
    public void pushPose() {
        poses.push(pose);
    }

    @Override
    public void popPose() {
        pose = poses.pop();
    }

    @Override
    public void translate(float x, float y) {
        pose = new Pose(pose.tx() + x * pose.sx(), pose.ty() + y * pose.sy(), pose.sx(), pose.sy());
    }

    @Override
    public void scale(float x, float y) {
        pose = new Pose(pose.tx(), pose.ty(), pose.sx() * x, pose.sy() * y);
    }

    private Clip clip() {
        return scissors.isEmpty() ? null : scissors.peek();
    }

    /** The raster pixels whose centres fall inside the posed box, cut to the scissor and the raster. */
    private Clip box(int left, int top, int right, int bottom) {
        int x0 = (int) Math.ceil(pose.x(left) - 0.5);
        int y0 = (int) Math.ceil(pose.y(top) - 0.5);
        int x1 = (int) Math.ceil(pose.x(right) - 0.5);
        int y1 = (int) Math.ceil(pose.y(bottom) - 0.5);
        Clip box = new Clip(Math.max(0, x0), Math.max(0, y0), Math.min(raster.width(), x1), Math.min(raster.height(), y1));
        return scissors.isEmpty() ? box : box.and(scissors.peek());
    }

    static int multiply(int texel, int color) {
        int a = ((texel >>> 24) * (color >>> 24) + 127) / 255;
        int r = (((texel >> 16) & 0xFF) * ((color >> 16) & 0xFF) + 127) / 255;
        int g = (((texel >> 8) & 0xFF) * ((color >> 8) & 0xFF) + 127) / 255;
        int b = ((texel & 0xFF) * (color & 0xFF) + 127) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    static int lerp(int from, int to, double t) {
        double k = Math.max(0, Math.min(1, t));
        int a = (int) Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * k);
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
