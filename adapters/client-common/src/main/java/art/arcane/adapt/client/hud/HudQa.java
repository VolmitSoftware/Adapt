package art.arcane.adapt.client.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Layout of the HUD as last drawn, for the test bridge: widget ids with bounds in scaled GUI units. While the editor
 * is open the editor's layout is reported instead. Built on the render thread.
 */
public final class HudQa {
    private HudQa() {
    }

    public static String widgetsJson(HudController controller, boolean hudVisible) {
        HudRenderer renderer = controller.renderer();
        String source = "hud";
        if (controller.host().current() instanceof HudEditorScreen editor) {
            renderer = editor.renderer();
            source = "editor";
        }
        JsonObject root = describe(renderer, controller.layout());
        if (controller.host().current() instanceof HudEditorScreen editor) {
            root.add("editor", editor(editor));
        }
        root.addProperty("source", source);
        root.addProperty("active", controller.model().active());
        root.addProperty("hudVisible", hudVisible);
        root.addProperty("screenOpen", controller.host().current() != null);
        return root.toString();
    }

    /** The editor's bar buttons, hint box and open options popover, as last drawn. */
    static JsonObject editor(HudEditorScreen editor) {
        JsonObject root = new JsonObject();
        root.addProperty("selected", editor.selected());
        root.addProperty("message", editor.message());
        JsonArray buttons = new JsonArray();
        for (HudEditorScreen.Button button : editor.buttons()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("label", button.label());
            entry.addProperty("x", button.x());
            entry.addProperty("y", button.y());
            entry.addProperty("width", button.width());
            entry.addProperty("height", button.height());
            entry.addProperty("enabled", button.enabled());
            buttons.add(entry);
        }
        root.add("buttons", buttons);
        HudRect hint = editor.hintBox();
        if (hint != null) {
            JsonArray box = new JsonArray();
            box.add(hint.x());
            box.add(hint.y());
            box.add(hint.width());
            box.add(hint.height());
            root.add("hint", box);
        }
        if (editor.popoverOpen()) {
            JsonObject popover = new JsonObject();
            popover.addProperty("x", editor.popoverX());
            popover.addProperty("y", editor.popoverY());
            popover.addProperty("width", editor.popoverWidth());
            popover.addProperty("visibleRows", editor.popoverVisibleRows());
            popover.addProperty("scroll", editor.popoverScroll());
            JsonArray rows = new JsonArray();
            for (HudEditorOptions.Row row : editor.popoverRows()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("label", row.label());
                entry.addProperty("value", row.value());
                rows.add(entry);
            }
            popover.add("rows", rows);
            root.add("popover", popover);
        }
        return root;
    }

    static JsonObject describe(HudRenderer renderer, HudLayout layout) {
        JsonObject root = new JsonObject();
        root.addProperty("profile", layout.activeProfile().name());
        root.addProperty("scale", layout.activeProfile().scale());
        root.addProperty("autoFit", layout.activeProfile().autoFit());
        root.addProperty("screenWidth", renderer.screenWidth());
        root.addProperty("screenHeight", renderer.screenHeight());
        JsonArray widgets = new JsonArray();
        for (int index = 0; index < renderer.placedCount(); index++) {
            HudRenderer.Placed placed = renderer.placed(index);
            HudBox box = placed.box();
            JsonObject widget = new JsonObject();
            widget.addProperty("id", placed.config().id());
            widget.addProperty("kind", placed.config().kind().id());
            widget.addProperty("anchor", placed.config().placement().anchor().id());
            widget.addProperty("visible", placed.config().placement().visible());
            widget.addProperty("drawn", placed.drawn());
            widget.addProperty("x", box.x());
            widget.addProperty("y", box.y());
            widget.addProperty("width", box.width());
            widget.addProperty("height", box.height());
            widget.addProperty("scale", box.scale());
            widget.addProperty("form", placed.compact() ? "compact" : "full");
            widget.addProperty("crowded", placed.crowded());
            if (placed.cellCount() > 0) {
                JsonArray cells = new JsonArray();
                for (int cell = 0; cell < placed.cellCount(); cell++) {
                    HudRect rect = placed.cell(cell);
                    JsonArray entry = new JsonArray();
                    entry.add(rect.x());
                    entry.add(rect.y());
                    entry.add(rect.width());
                    entry.add(rect.height());
                    cells.add(entry);
                }
                widget.add("cells", cells);
                GlyphLayout glyphs = renderer.glyphLayout(placed.config().id());
                widget.addProperty("iconSize", glyphs.iconSize);
                widget.addProperty("hidden", glyphs.hidden);
            }
            widgets.add(widget);
        }
        root.add("widgets", widgets);
        HudBox zone = renderer.zone();
        JsonArray protectedZone = new JsonArray();
        protectedZone.add(zone.x());
        protectedZone.add(zone.y());
        protectedZone.add(zone.width());
        protectedZone.add(zone.height());
        root.add("protected", protectedZone);
        HudRenderer.Placed toasts = renderer.placed(HudWidgetKind.TOASTS.id());
        root.add("model", model(renderer.model(), toasts != null && toasts.compact() ? "compact" : "full"));
        return root;
    }

    /** What the HUD holds right now: skill activity, toasts, notices and glyphs. */
    static JsonObject model(HudModel model, String toastForm) {
        JsonObject root = new JsonObject();
        JsonArray xp = new JsonArray();
        for (HudXpLine line : model.xpLines()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("skill", line.skillId());
            entry.addProperty("amount", line.amount());
            entry.addProperty("level", line.level());
            entry.addProperty("progress", line.progress());
            entry.addProperty("text", line.label());
            xp.add(entry);
        }
        root.add("xp", xp);
        JsonArray skills = new JsonArray();
        for (HudXpLine line : model.xpLines()) {
            String skillId = line.skillId();
            HudSkillState state = model.skillState(skillId);
            if (state == null) {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("skill", skillId);
            entry.addProperty("level", state.level());
            entry.addProperty("progress", state.progress());
            entry.addProperty("xp", state.xp());
            skills.add(entry);
        }
        root.add("skills", skills);
        JsonArray toasts = new JsonArray();
        for (HudToast toast : model.toasts().shown()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("kind", toast.kind().name());
            entry.addProperty("title", toast.title());
            entry.addProperty("body", toast.body());
            entry.addProperty("form", toastForm);
            entry.addProperty("slot", toast.slot());
            entry.addProperty("leaving", model.toasts().leaving(toast, model.clock().millis()));
            toasts.add(entry);
        }
        root.add("toasts", toasts);
        root.addProperty("toastsQueued", model.toasts().queuedCount());
        JsonArray notices = new JsonArray();
        for (HudNotice notice : model.notices()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("purpose", notice.purpose());
            entry.addProperty("text", notice.text());
            notices.add(entry);
        }
        root.add("notices", notices);
        JsonArray statuses = new JsonArray();
        for (HudStatusEntry status : model.statuses()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("purpose", status.purpose());
            entry.addProperty("glyph", status.glyph().name());
            entry.addProperty("skill", status.skillId());
            entry.addProperty("lit", status.lit());
            entry.addProperty("label", status.label());
            entry.addProperty("stacks", status.stacks());
            entry.addProperty("maxStacks", status.maxStacks());
            statuses.add(entry);
        }
        root.add("statuses", statuses);
        return root;
    }
}
