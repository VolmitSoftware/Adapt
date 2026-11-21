package art.arcane.adapt.client.ui.kit;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiTexture;
import art.arcane.adapt.client.view.IconRef;

import java.util.Map;
import java.util.Objects;

/**
 * A square icon slot: an item or sprite icon with an amount or value in the corner, enchantment glint, selected,
 * disabled and locked states, a corner badge, and optional switch or text-field marks for tiles that stand for a
 * control. Every mouse button reaches the {@link Listener}, so screens can map clicks as they need.
 */
public class IconTile extends UiNode {
    private static final long HOVER_MILLIS = 80L;
    private static final int ICON = 16;
    private static final int LARGE_TILE = 34;
    private static final int MARK = 7;
    private static final int DECORATION_DEPTH = 200;

    private final UiFade hover = new UiFade(HOVER_MILLIS);
    private String text = "";
    private IconRef icon;
    private UiTexture sprite;
    private int amount = 1;
    private boolean glint;
    private boolean selected;
    private boolean locked;
    private Tone tone = Tone.NORMAL;
    private String badge;
    private int badgeColor;
    private String cornerText;
    private Boolean switchState;
    private boolean field;
    private Listener listener;

    public IconTile(String id) {
        super(id);
    }

    public IconTile(String id, IconRef icon, String text) {
        super(id);
        this.icon = icon;
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public String kind() {
        return "tile";
    }

    @Override
    public String label() {
        return LegacyText.strip(text);
    }

    @Override
    public void describe(Map<String, String> state) {
        if (icon != null) {
            state.put("item", icon.itemId());
        }
        state.put("amount", Integer.toString(amount));
        state.put("glint", Boolean.toString(glint));
        state.put("selected", Boolean.toString(selected));
        state.put("locked", Boolean.toString(locked));
        state.put("tone", tone.name());
        if (switchState != null) {
            state.put("switch", switchState ? "ON" : "OFF");
        }
        if (cornerText != null) {
            state.put("value", cornerText);
        }
        if (badge != null) {
            state.put("badge", badge);
        }
    }

    /** The name used by the QA probe and screen readers of the tree; drawing uses the tooltip. */
    public IconTile setText(String name) {
        text = Objects.requireNonNull(name, "text");
        return this;
    }

    public IconRef icon() {
        return icon;
    }

    public IconTile setIcon(IconRef newIcon) {
        icon = newIcon;
        return this;
    }

    /** A sprite drawn instead of the item when a resource pack provides it. */
    public IconTile setSprite(UiTexture texture) {
        sprite = texture;
        return this;
    }

    public int amount() {
        return amount;
    }

    public IconTile setAmount(int count) {
        amount = count;
        return this;
    }

    public IconTile setGlint(boolean enchanted) {
        glint = enchanted;
        return this;
    }

    public boolean selected() {
        return selected;
    }

    public IconTile setSelected(boolean isSelected) {
        selected = isSelected;
        return this;
    }

    public boolean locked() {
        return locked;
    }

    public IconTile setLocked(boolean isLocked) {
        locked = isLocked;
        return this;
    }

    public Tone tone() {
        return tone;
    }

    public IconTile setTone(Tone newTone) {
        tone = Objects.requireNonNull(newTone, "tone");
        return this;
    }

    /** A short label in a pill at the top-right corner; null removes it. Colour 0 means the accent. */
    public IconTile setBadge(String label, int argb) {
        badge = label;
        badgeColor = argb;
        return this;
    }

    /** Text shown in the amount corner instead of the amount, such as a number value; null shows the amount. */
    public IconTile setCornerText(String value) {
        cornerText = value;
        return this;
    }

    public String cornerText() {
        return cornerText;
    }

    /** Draws a small switch in the bottom-left corner reflecting {@code on}; null removes it. */
    public IconTile setSwitch(Boolean on) {
        switchState = on;
        return this;
    }

    public Boolean switchState() {
        return switchState;
    }

    /** Marks the tile as an editable text value with an underline. */
    public IconTile setField(boolean editable) {
        field = editable;
        return this;
    }

    public IconTile setListener(Listener newListener) {
        listener = newListener;
        return this;
    }

    @Override
    public boolean focusable() {
        return listener != null;
    }

    @Override
    public boolean hittable() {
        return true;
    }

    @Override
    public int preferredWidth(UiDraw metrics) {
        return services().theme().tileSize();
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        return services().theme().tileSize();
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        if (listener == null || locked) {
            return listener != null;
        }
        listener.pressed(this, event);
        return true;
    }

    @Override
    public boolean mouseReleased(UiMouseEvent event) {
        if (listener != null && !locked) {
            listener.released(this, event);
        }
        return listener != null;
    }

    @Override
    public boolean activate() {
        if (listener == null || locked) {
            return false;
        }
        listener.activated(this);
        return true;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        boolean active = enabled() && !locked;
        float lift = hover.update(frame.motion(), active && (frame.isHovered(this) || frame.showsFocus(this)));
        boolean textured = UiChrome.paint(draw, UiChrome.TILE, selected ? "selected" : lift >= 0.5F ? "hover" : null, box);
        if (!textured) {
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), theme.tile());
        }
        if (tone == Tone.DESTRUCTIVE) {
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), UiEase.fade(theme.destructive(), 0.12F + 0.14F * lift));
        }
        if (lift > 0.0F && !textured) {
            UiTome.soft(draw, box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, UiEase.fade(UiTome.HOVER, lift));
        }
        if (frame.isPressed(this)) {
            draw.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, 0x205A3E26);
        }
        int plate = UiContrast.over(theme.tile(), theme.backdrop());
        paintIcon(draw, frame.isPressed(this) ? box.offset(0, UiControl.SINK) : box, plate);
        int border = selected ? theme.text() : tone == Tone.DESTRUCTIVE ? theme.destructive() : theme.tileBorder();
        if (!textured || border != theme.tileBorder()) {
            UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), border);
        }
        if (selected) {
            UiTome.softEdge(draw, box.x() + 1, box.y() + 1, box.width() - 2, box.height() - 2, theme.text());
        }
        if (frame.showsFocus(this)) {
            UiControl.focusRing(draw, box, theme);
        }
        draw.pushPose();
        draw.depth(DECORATION_DEPTH);
        if (!active) {
            draw.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, UiEase.fade(theme.panel(), 0.62F));
        }
        if (locked) {
            UiTome.lock(draw, box.right() - 8, box.y() + 2);
        }
        if (field) {
            draw.fill(box.x() + 2, box.bottom() - 2, box.right() - 2, box.bottom() - 1, theme.accent());
        }
        if (switchState != null) {
            paintSwitch(draw, theme, box, switchState);
        }
        String corner = cornerText != null ? cornerText : amount > 1 ? Integer.toString(amount) : null;
        if (corner != null && draw.textWidth(corner) <= box.width() - 2) {
            int left = box.right() - 1 - draw.textWidth(corner);
            int top = box.bottom() - 1 - UiControl.CAP_HEIGHT;
            UiTome.soft(draw, left - 1, top - 1, box.right() - 1, box.bottom() - 1, plate);
            draw.text(corner, left, top, theme.text(), false);
        }
        if (badge != null) {
            int width = draw.textWidth(badge) + 4;
            int left = box.right() - width + 2;
            int top = box.y() - 2;
            UiTome.soft(draw, left, top, left + width, top + draw.lineHeight() + 1, badgeColor == 0 ? theme.accent() : badgeColor);
            draw.text(badge, left + 2, top + 1, theme.textInverse(), false);
        }
        draw.popPose();
    }

    private void paintIcon(UiDraw draw, UiRect box, int backdrop) {
        UiTexture texture = sprite == null ? null : draw.sprites().texture(sprite.id());
        if (texture == null && icon == null) {
            return;
        }
        boolean large = Math.min(box.width(), box.height()) >= LARGE_TILE;
        int size = large ? ICON * 2 : ICON;
        int x = box.x() + (box.width() - size) / 2;
        int y = box.y() + (box.height() - size) / 2;
        if (texture != null) {
            draw.blit(texture, x, y, size, size);
        } else {
            UiIcons.paintOn(draw, icon, x, y, size, glint, backdrop);
        }
    }

    /** A small checkbox in the bottom left corner: an inked box, filled with ink and a paper tick when on. */
    private static void paintSwitch(UiDraw draw, UiTheme theme, UiRect box, boolean on) {
        int left = box.x() + 1;
        int top = box.bottom() - 1 - MARK;
        UiTome.soft(draw, left, top, left + MARK, top + MARK, on ? theme.text() : theme.tooltip());
        UiTome.softEdge(draw, left, top, MARK, MARK, theme.text());
        if (on) {
            int paper = theme.tooltip();
            draw.fill(left + 2, top + 3, left + 3, top + 5, paper);
            draw.fill(left + 3, top + 4, left + 4, top + 5, paper);
            draw.fill(left + 4, top + 2, left + 5, top + 4, paper);
        }
    }

    public enum Tone {
        NORMAL,
        DESTRUCTIVE
    }

    /** Receives every mouse button on the tile and Enter/Space while it is focused. */
    public interface Listener {
        void pressed(IconTile tile, UiMouseEvent event);

        default void released(IconTile tile, UiMouseEvent event) {
        }

        void activated(IconTile tile);

        /** Runs {@code action} with the click sound on a left click or Enter/Space. */
        static Listener click(Runnable action) {
            Objects.requireNonNull(action, "action");
            return new Listener() {
                @Override
                public void pressed(IconTile tile, UiMouseEvent event) {
                    if (event.button() == UiMouseButton.LEFT) {
                        activated(tile);
                    }
                }

                @Override
                public void activated(IconTile tile) {
                    tile.services().playClick();
                    action.run();
                }
            };
        }
    }
}
