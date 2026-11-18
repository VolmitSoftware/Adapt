package art.arcane.adapt.mod.feature.display;

import art.arcane.adapt.mod.feature.mixin.TextDisplayDataAccess;
import art.arcane.adapt.mod.platform.ModText;
import art.arcane.adapt.platform.entity.display.TextAlignment;
import art.arcane.adapt.platform.entity.display.TextDisplayRef;
import net.minecraft.world.entity.Display;

final class ModTextDisplayRef extends ModDisplayRef<Display.TextDisplay> implements TextDisplayRef {
    private static final byte ALIGN_MASK = Display.TextDisplay.FLAG_ALIGN_LEFT | Display.TextDisplay.FLAG_ALIGN_RIGHT;

    ModTextDisplayRef(Display.TextDisplay display) {
        super(display);
    }

    @Override
    public String text() {
        return ModText.markup(synced().get(TextDisplayDataAccess.adapt$text()));
    }

    @Override
    public void setText(String markup) {
        synced().set(TextDisplayDataAccess.adapt$text(), ModText.component(markup == null ? "" : markup));
    }

    @Override
    public int backgroundArgb() {
        return synced().get(TextDisplayDataAccess.adapt$background());
    }

    @Override
    public void setBackgroundArgb(int argb) {
        synced().set(TextDisplayDataAccess.adapt$background(), argb);
        setFlag(Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND, false);
    }

    @Override
    public int lineWidth() {
        return synced().get(TextDisplayDataAccess.adapt$lineWidth());
    }

    @Override
    public void setLineWidth(int width) {
        synced().set(TextDisplayDataAccess.adapt$lineWidth(), width);
    }

    @Override
    public boolean isSeeThrough() {
        return (flags() & Display.TextDisplay.FLAG_SEE_THROUGH) != 0;
    }

    @Override
    public void setSeeThrough(boolean seeThrough) {
        setFlag(Display.TextDisplay.FLAG_SEE_THROUGH, seeThrough);
    }

    @Override
    public boolean isShadowed() {
        return (flags() & Display.TextDisplay.FLAG_SHADOW) != 0;
    }

    @Override
    public void setShadowed(boolean shadowed) {
        setFlag(Display.TextDisplay.FLAG_SHADOW, shadowed);
    }

    @Override
    public TextAlignment alignment() {
        byte flags = flags();
        if ((flags & Display.TextDisplay.FLAG_ALIGN_LEFT) != 0) {
            return TextAlignment.LEFT;
        }
        if ((flags & Display.TextDisplay.FLAG_ALIGN_RIGHT) != 0) {
            return TextAlignment.RIGHT;
        }
        return TextAlignment.CENTER;
    }

    @Override
    public void setAlignment(TextAlignment alignment) {
        byte flags = (byte) (flags() & ~ALIGN_MASK);
        if (alignment == TextAlignment.LEFT) {
            flags |= Display.TextDisplay.FLAG_ALIGN_LEFT;
        } else if (alignment == TextAlignment.RIGHT) {
            flags |= Display.TextDisplay.FLAG_ALIGN_RIGHT;
        }
        synced().set(TextDisplayDataAccess.adapt$styleFlags(), flags);
    }

    private byte flags() {
        return synced().get(TextDisplayDataAccess.adapt$styleFlags());
    }

    private void setFlag(byte flag, boolean enabled) {
        byte flags = flags();
        byte next = enabled ? (byte) (flags | flag) : (byte) (flags & ~flag);
        if (next != flags) {
            synced().set(TextDisplayDataAccess.adapt$styleFlags(), next);
        }
    }
}
