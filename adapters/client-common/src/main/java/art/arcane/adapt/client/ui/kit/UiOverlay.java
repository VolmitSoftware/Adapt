package art.arcane.adapt.client.ui.kit;

/**
 * A layer above the screen's tree, opened with {@link UiServices#openOverlay}. It is placed over the whole screen and
 * positions its own content. A modal overlay dims the screen and takes every pointer event; a non-modal one (a
 * popover) is dismissed by a click outside its content. Escape dismisses the top overlay.
 */
public abstract class UiOverlay extends UiNode {
    protected UiOverlay(String id) {
        super(id);
    }

    public abstract boolean modal();

    @Override
    public boolean hittable() {
        return modal();
    }

    /** The node focused when the overlay opens, or null. */
    public UiNode initialFocus() {
        return null;
    }

    /** Called when Escape or an outside click closes the overlay; not called for {@link #close()}. */
    protected void dismissed() {
    }

    public final void close() {
        services().closeOverlay(this);
    }
}
