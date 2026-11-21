package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.qa.UiProbe;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;

import java.io.IOException;
import java.util.Objects;

/**
 * The client HUD: the model the network handler feeds ({@link #model()}), the saved layout, the renderer and the
 * editor. The layout file is read on first use.
 */
public final class HudController {
    static final long PROBE_INTERVAL_MILLIS = 100L;
    static final long FILE_CHECK_MILLIS = 1000L;
    private final UiHost host;
    private final HudLayoutStore store;
    private final HudModel model;
    private final HudText text;
    private final HudRenderer renderer;
    private final HudSamples preview;
    private HudLayout layout;
    private long probedAt = Long.MIN_VALUE;
    private long knownModified = Long.MIN_VALUE;
    private long checkedAt = Long.MIN_VALUE;

    public HudController(UiHost host, HudLayoutStore store, HudClock clock, boolean preview) {
        this.host = Objects.requireNonNull(host, "host");
        this.store = Objects.requireNonNull(store, "store");
        this.model = new HudModel(clock);
        this.text = new HudText(host::translate);
        this.renderer = new HudRenderer(model, text);
        this.preview = preview ? new HudSamples() : null;
    }

    public HudModel model() {
        return model;
    }

    public HudText text() {
        return text;
    }

    public HudRenderer renderer() {
        return renderer;
    }

    public UiHost host() {
        return host;
    }

    public HudLayout layout() {
        if (layout == null) {
            layout = store.load();
            knownModified = store.modified();
        }
        return layout;
    }

    /** Uses {@code next} from now on and writes it to disk; returns false when the file could not be written. */
    public boolean apply(HudLayout next) {
        layout = Objects.requireNonNull(next, "next");
        try {
            store.save(next);
            knownModified = store.modified();
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /**
     * Draws the HUD when {@code visible}. Free when the server has no Adapt (the model stays inactive) and skipped while
     * any Adapt screen is open; the editor draws its own copy. While the test bridge watches, the layout is published
     * for {@link UiProbe#snapshot()} at most every {@value #PROBE_INTERVAL_MILLIS} ms.
     */
    public void render(UiDraw draw, boolean visible) {
        if (visible && model.active() && host.current() == null) {
            renderer.render(draw, layout());
        } else {
            renderer.clear();
        }
        if (UiProbe.watched()) {
            long now = model.clock().millis();
            if (probedAt == Long.MIN_VALUE || now - probedAt >= PROBE_INTERVAL_MILLIS) {
                probedAt = now;
                UiProbe.hud(HudQa.widgetsJson(this, visible));
            }
        }
    }

    /** Shows server feedback that arrived with no Adapt screen open to show it. */
    public void feedback(FeedbackKind kind, String text) {
        model.feedback(kind, text);
    }

    public void openEditor() {
        host.open(new HudEditorScreen(this));
    }

    /** Advances the preview, and picks up a layout file edited by hand (checked once a second, one file stat). */
    public void tick() {
        if (preview != null && model.active()) {
            preview.tick(model);
        }
        long now = model.clock().millis();
        if (layout != null && (checkedAt == Long.MIN_VALUE || now - checkedAt >= FILE_CHECK_MILLIS)) {
            checkedAt = now;
            long modified = store.modified();
            if (modified != knownModified) {
                knownModified = modified;
                layout = store.load();
            }
        }
    }

    public void connected() {
        text.refresh();
        if (preview != null) {
            preview.start(model);
        }
    }

    public void disconnected() {
        model.reset();
    }
}
