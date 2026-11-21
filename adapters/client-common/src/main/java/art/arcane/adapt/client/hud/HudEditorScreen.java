package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.ui.UiCharEvent;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.UiScreen;
import art.arcane.adapt.client.ui.UiScrollEvent;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Arranges the HUD over the game with sample content: drag to move with snap guides (Alt places freely), drag a
 * corner or scroll to resize, arrows to nudge, right-click for a widget's options. The centre bar holds the global
 * scale, profiles, clipboard export and import, undo, redo and reset. Every change is saved as it is made.
 */
public final class HudEditorScreen extends UiScreen {
    static final int ACCENT = 0xFF5FC8FF;
    static final int GUIDE = 0xFFFF5FD2;
    static final int HANDLE_HIT = 4;
    static final int BUTTON_HEIGHT = 14;
    static final int ROW_HEIGHT = 12;
    static final int POPOVER_WIDTH = 168;
    private static final int VALUE_GAP = 8;
    private static final int HINT_WIDTH = 360;
    private static final int HINT_GAP = 4;
    private static final long MESSAGE_MILLIS = 2500L;
    private static final double SCALE_STEP = 0.1;
    private static final int PROFILE_BUTTON = 4;

    private enum Mode { NONE, MOVE, SCALE }

    record Button(int x, int y, int width, int height, String label, boolean enabled, Runnable action) {
        boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }
    }

    private final HudController controller;
    private final HudText text;
    private final HudRenderer renderer;
    private final HudModel sample;
    private final HudHistory history;
    private final HudEditorOptions options;
    private final List<Button> buttons = new ArrayList<>();
    private HudLayout working;
    private String selected;
    private Mode mode = Mode.NONE;
    private double pressX;
    private double pressY;
    private HudRect startRect;
    private HudRect dragRect;
    private HudPlacement startPlacement;
    private int corner;
    private List<HudSnap.Guide> guides = List.of();
    private String popoverWidget;
    private int popoverScroll;
    private List<HudEditorOptions.Row> popoverRows = List.of();
    private int popoverX;
    private int popoverWidth = POPOVER_WIDTH;
    private int popoverY;
    private int popoverVisibleRows;
    private StringBuilder rename;
    private String message;
    private long messageAt;
    private HudRect hintBox;

    public HudEditorScreen(HudController controller) {
        super(controller.text().get("adapt.hud.editor.title"));
        this.controller = controller;
        this.text = controller.text();
        this.sample = HudSamples.editorModel();
        this.renderer = new HudRenderer(sample, text);
        this.working = controller.layout();
        this.history = new HudHistory(working);
        this.options = new HudEditorOptions(text, this::changeProfile);
    }

    public HudLayout working() {
        return working;
    }

    public String selected() {
        return selected;
    }

    public List<HudSnap.Guide> guides() {
        return guides;
    }

    public HudRenderer renderer() {
        return renderer;
    }

    public boolean popoverOpen() {
        return popoverWidget != null;
    }

    public String message() {
        return message;
    }

    /** The bar's buttons as last drawn; empty while dragging. */
    List<Button> buttons() {
        return buttons;
    }

    /** The hint text's box as last drawn, or null when it was not drawn. */
    HudRect hintBox() {
        return hintBox;
    }

    List<HudEditorOptions.Row> popoverRows() {
        return popoverRows;
    }

    int popoverX() {
        return popoverX;
    }

    int popoverWidth() {
        return popoverWidth;
    }

    int popoverY() {
        return popoverY;
    }

    int popoverVisibleRows() {
        return popoverVisibleRows;
    }

    int popoverScroll() {
        return popoverScroll;
    }

    @Override
    public boolean dimsBackground() {
        return false;
    }

    @Override
    public boolean closesOnEscape() {
        return rename == null && popoverWidget == null;
    }

    @Override
    public void removed() {
        controller.apply(working);
    }

    @Override
    public void render(UiDraw draw, int mouseX, int mouseY, float delta) {
        int width = draw.width();
        int height = draw.height();
        draw.fill(0, 0, width, height, 0x38000000);
        drawGrid(draw, width, height);
        hintBox = null;
        renderer.renderEditing(draw, working);
        drawOutlines(draw, mouseX, mouseY);
        for (HudSnap.Guide guide : guides) {
            int position = (int) Math.round(guide.position());
            if (guide.vertical()) {
                draw.fill(position, 0, position + 1, height, GUIDE);
            } else {
                draw.fill(0, position, width, position + 1, GUIDE);
            }
        }
        draw.nextLayer();
        if (mode == Mode.NONE) {
            drawBar(draw, mouseX, mouseY, width, height);
        } else {
            buttons.clear();
        }
        if (popoverOpen()) {
            draw.nextLayer();
        }
        drawPopover(draw, mouseX, mouseY, width, height);
        if (mode == Mode.NONE && !popoverOpen()) {
            draw.nextLayer();
            drawGlyphName(draw, mouseX, mouseY, width);
        }
    }

    /** The name of the glyph under the pointer, in a small tooltip; glyphs carry no text of their own. */
    private void drawGlyphName(UiDraw draw, int mouseX, int mouseY, int width) {
        for (int index = 0; index < renderer.placedCount(); index++) {
            HudRenderer.Placed placed = renderer.placed(index);
            for (int cell = 0; cell < placed.cellCount(); cell++) {
                HudRect rect = placed.cell(cell);
                if (mouseX < rect.x() || mouseX >= rect.right() || mouseY < rect.y() || mouseY >= rect.bottom()) {
                    continue;
                }
                GlyphLayout layout = renderer.glyphLayout(placed.config().id());
                if (cell >= layout.shown.size()) {
                    return;
                }
                String label = layout.shown.get(cell).label();
                int labelWidth = draw.textWidth(label);
                int x = Math.max(2, Math.min(width - labelWidth - 6, mouseX + 8));
                int y = Math.max(2, mouseY - 14);
                HudPaint.panel(draw, x, y, labelWidth + 6, draw.lineHeight() + 4, 1.0);
                HudPaint.text(draw, label, x + 3, y + 3, HudPaint.TEXT, 1.0);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(UiMouseEvent event) {
        double x = event.x();
        double y = event.y();
        if (rename != null) {
            finishRename(true);
        }
        if (popoverWidget != null) {
            int row = popoverRow(x, y);
            if (row >= 0) {
                HudEditorOptions.Row target = popoverRows.get(row);
                Runnable action = event.button() == UiMouseButton.RIGHT ? target.previous() : target.next();
                if (action != null) {
                    controller.host().playClick();
                    action.run();
                }
                return true;
            }
            if (insidePopover(x, y)) {
                return true;
            }
            popoverWidget = null;
        }
        if (event.button() == UiMouseButton.LEFT) {
            for (Button button : buttons) {
                if (button.contains(x, y)) {
                    if (button.enabled()) {
                        controller.host().playClick();
                        button.action().run();
                    }
                    return true;
                }
            }
        }
        if (selected != null && event.button() == UiMouseButton.LEFT) {
            HudRenderer.Placed current = renderer.placed(selected);
            int hitCorner = current == null ? -1 : corner(current.box(), x, y);
            if (hitCorner >= 0) {
                begin(Mode.SCALE, current, x, y);
                corner = hitCorner;
                return true;
            }
        }
        HudRenderer.Placed hit = hit(x, y);
        if (hit == null) {
            selected = null;
            return true;
        }
        selected = hit.config().id();
        if (event.button() == UiMouseButton.RIGHT) {
            openPopover(selected);
        } else if (event.button() == UiMouseButton.LEFT) {
            begin(Mode.MOVE, hit, x, y);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(UiMouseEvent event, double dragX, double dragY) {
        if (mode == Mode.NONE || selected == null) {
            return false;
        }
        double screenWidth = renderer.screenWidth();
        double screenHeight = renderer.screenHeight();
        if (mode == Mode.MOVE) {
            HudRect proposal = startRect.at(startRect.x() + event.x() - pressX, startRect.y() + event.y() - pressY);
            if (event.modifiers().alt()) {
                dragRect = HudGeometry.clamp(proposal, screenWidth, screenHeight);
                guides = List.of();
            } else {
                HudSnap.Result snapped = HudSnap.snap(proposal, screenWidth, screenHeight, otherRects(selected));
                dragRect = snapped.rect();
                guides = snapped.guides();
            }
            setPlacement(selected, HudGeometry.moveTo(startPlacement, dragRect, screenWidth, screenHeight), false, null);
            return true;
        }
        boolean left = corner == 0 || corner == 2;
        boolean top = corner == 0 || corner == 1;
        double fixedX = left ? startRect.right() : startRect.x();
        double fixedY = top ? startRect.bottom() : startRect.y();
        double cornerX = left ? startRect.x() : startRect.right();
        double cornerY = top ? startRect.y() : startRect.bottom();
        double scale = HudGeometry.cornerScale(startPlacement.scale(), fixedX, fixedY, cornerX, cornerY, event.x(), event.y());
        double ratio = scale / startPlacement.scale();
        double width = startRect.width() * ratio;
        double height = startRect.height() * ratio;
        HudRect resized = new HudRect(left ? fixedX - width : fixedX, top ? fixedY - height : fixedY, width, height);
        dragRect = HudGeometry.clamp(resized, screenWidth, screenHeight);
        setPlacement(selected, HudGeometry.moveTo(startPlacement.withScale(scale), dragRect, screenWidth, screenHeight), false, null);
        return true;
    }

    @Override
    public boolean mouseReleased(UiMouseEvent event) {
        if (mode == Mode.NONE) {
            return false;
        }
        Mode finished = mode;
        mode = Mode.NONE;
        guides = List.of();
        if (dragRect != null && selected != null) {
            HudPlacement current = working.activeProfile().widget(selected).placement();
            setPlacement(selected, HudGeometry.reanchor(current, dragRect, renderer.screenWidth(), renderer.screenHeight()), true,
                    null);
        }
        dragRect = null;
        return finished != Mode.NONE;
    }

    @Override
    public boolean mouseScrolled(UiScrollEvent event) {
        if (popoverWidget != null && insidePopover(event.x(), event.y())) {
            int maxScroll = Math.max(0, popoverRows.size() - popoverVisibleRows);
            popoverScroll = Math.max(0, Math.min(maxScroll, popoverScroll - (int) Math.signum(event.vertical())));
            return true;
        }
        HudRenderer.Placed target = selected == null ? null : renderer.placed(selected);
        if (target == null || !target.box().rect().contains(event.x(), event.y())) {
            target = hit(event.x(), event.y());
        }
        if (target == null || event.vertical() == 0.0) {
            return false;
        }
        selected = target.config().id();
        scaleBy(target, event.vertical() > 0.0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(UiKeyEvent event) {
        UiKey key = event.key();
        if (rename != null) {
            if (key == UiKey.ENTER) {
                finishRename(true);
            } else if (key == UiKey.ESCAPE) {
                finishRename(false);
            } else if (key == UiKey.BACKSPACE && rename.length() > 0) {
                rename.setLength(rename.length() - 1);
            }
            return true;
        }
        if (event.modifiers().shortcut() && key == UiKey.Z) {
            if (event.modifiers().shift()) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (event.modifiers().shortcut() && key == UiKey.Y) {
            redo();
            return true;
        }
        if (key == UiKey.ESCAPE && popoverWidget != null) {
            popoverWidget = null;
            return true;
        }
        if (selected == null) {
            return false;
        }
        int step = event.modifiers().shift() ? 10 : 1;
        return switch (key) {
            case LEFT -> nudge(-step, 0);
            case RIGHT -> nudge(step, 0);
            case UP -> nudge(0, -step);
            case DOWN -> nudge(0, step);
            case ENTER -> {
                if (popoverWidget == null) {
                    openPopover(selected);
                } else {
                    popoverWidget = null;
                }
                yield true;
            }
            case H -> {
                HudPlacement placement = working.activeProfile().widget(selected).placement();
                setPlacement(selected, placement.withVisible(!placement.visible()), true, null);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(UiCharEvent event) {
        if (rename == null) {
            return false;
        }
        String typed = event.text();
        boolean printable = typed.codePoints().allMatch((int codepoint) -> codepoint >= ' ' && codepoint != 0x7F && codepoint != 0xA7);
        if (printable && rename.length() + typed.length() <= HudProfile.MAX_NAME_LENGTH) {
            rename.append(typed);
        }
        return true;
    }

    public void undo() {
        working = history.undo();
        keepSelectionValid();
        save();
    }

    public void redo() {
        working = history.redo();
        keepSelectionValid();
        save();
    }

    /** Applies a change to the active profile as one undo step. */
    void changeProfile(UnaryOperator<HudProfile> change) {
        commit(working.withActiveProfile(change.apply(working.activeProfile())), null);
        keepSelectionValid();
    }

    void exportProfile() {
        controller.host().setClipboard(HudLayoutCodec.export(working.activeProfile()));
        say("adapt.hud.editor.exported");
    }

    void importProfile() {
        HudProfile imported = HudLayoutCodec.importCode(controller.host().clipboard()).orElse(null);
        if (imported == null) {
            say("adapt.hud.editor.import_failed");
            return;
        }
        commit(working.withAdded(imported.withName(working.freshName(imported.name()))), null);
        selected = null;
        say("adapt.hud.editor.imported");
    }

    private void begin(Mode next, HudRenderer.Placed placed, double x, double y) {
        mode = next;
        pressX = x;
        pressY = y;
        startRect = placed.box().rect();
        startPlacement = placed.config().placement();
        dragRect = null;
    }

    private boolean nudge(int dx, int dy) {
        HudRenderer.Placed placed = renderer.placed(selected);
        if (placed == null || !placed.drawn()) {
            return false;
        }
        HudRect rect = placed.box().rect();
        HudRect moved = HudGeometry.clamp(rect.at(rect.x() + dx, rect.y() + dy), renderer.screenWidth(), renderer.screenHeight());
        HudPlacement placement = working.activeProfile().widget(selected).placement();
        setPlacement(selected, HudGeometry.reanchor(placement, moved, renderer.screenWidth(), renderer.screenHeight()), true,
                "nudge:" + selected);
        return true;
    }

    private void scaleBy(HudRenderer.Placed placed, int direction) {
        HudPlacement placement = working.activeProfile().widget(placed.config().id()).placement();
        double scale = HudPlacement.clampScale(Math.round(placement.scale() * (direction > 0 ? 1.1 : 1.0 / 1.1) * 20.0) / 20.0);
        if (scale == placement.scale()) {
            scale = HudPlacement.clampScale(placement.scale() + direction * 0.05);
        }
        HudRect rect = placed.box().rect();
        double ratio = scale / placement.scale();
        double width = rect.width() * ratio;
        double height = rect.height() * ratio;
        HudRect resized = HudGeometry.clamp(new HudRect(rect.centerX() - width / 2.0, rect.centerY() - height / 2.0, width, height),
                renderer.screenWidth(), renderer.screenHeight());
        setPlacement(placed.config().id(), HudGeometry.reanchor(placement.withScale(scale), resized, renderer.screenWidth(),
                renderer.screenHeight()), true, "scale:" + placed.config().id());
    }

    private void setPlacement(String id, HudPlacement placement, boolean record, String key) {
        HudProfile profile = working.activeProfile();
        HudWidgetConfig config = profile.widget(id);
        if (config == null) {
            return;
        }
        HudLayout next = working.withActiveProfile(profile.with(config.withPlacement(placement)));
        if (record) {
            commit(next, key);
        } else {
            working = next;
        }
    }

    private void commit(HudLayout next, String key) {
        working = next;
        history.commit(next, key);
        save();
    }

    private void save() {
        if (!controller.apply(working)) {
            say("adapt.hud.editor.save_failed");
        }
    }

    private void keepSelectionValid() {
        if (selected != null && working.activeProfile().widget(selected) == null) {
            selected = null;
        }
        if (popoverWidget != null && working.activeProfile().widget(popoverWidget) == null) {
            popoverWidget = null;
        }
    }

    private void say(String key) {
        message = text.get(key);
        messageAt = controller.model().clock().millis();
    }

    private void openPopover(String id) {
        popoverWidget = id;
        popoverScroll = 0;
    }

    private void startRename() {
        popoverWidget = null;
        rename = new StringBuilder(working.activeProfile().name());
    }

    private void finishRename(boolean keep) {
        StringBuilder typed = rename;
        rename = null;
        if (!keep || typed == null) {
            return;
        }
        String name = HudLayoutCodec.sanitizeName(typed.toString());
        HudProfile active = working.activeProfile();
        if (name.isEmpty() || name.equals(active.name())) {
            return;
        }
        commit(working.withActiveProfile(active.withName(working.freshName(name))), null);
    }

    private HudRenderer.Placed hit(double x, double y) {
        for (int index = renderer.placedCount() - 1; index >= 0; index--) {
            HudRenderer.Placed placed = renderer.placed(index);
            if (placed.drawn() && placed.box().rect().inflate(1.0).contains(x, y)) {
                return placed;
            }
        }
        return null;
    }

    private List<HudRect> otherRects(String id) {
        List<HudRect> rects = new ArrayList<>();
        for (int index = 0; index < renderer.placedCount(); index++) {
            HudRenderer.Placed placed = renderer.placed(index);
            if (placed.drawn() && !placed.config().id().equals(id)) {
                rects.add(placed.box().rect());
            }
        }
        return rects;
    }

    /** 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right, or -1. */
    static int corner(HudBox box, double x, double y) {
        double[] cornersX = {box.x(), box.x() + box.width(), box.x(), box.x() + box.width()};
        double[] cornersY = {box.y(), box.y(), box.y() + box.height(), box.y() + box.height()};
        for (int index = 0; index < 4; index++) {
            if (Math.abs(x - cornersX[index]) <= HANDLE_HIT && Math.abs(y - cornersY[index]) <= HANDLE_HIT) {
                return index;
            }
        }
        return -1;
    }

    private String widgetName(HudWidgetConfig config) {
        String name = text.get(config.kind().translationKey());
        if (config.id().equals(config.kind().id())) {
            return name;
        }
        return name + " " + config.id().substring(config.kind().id().length() + 1);
    }

    private void drawGrid(UiDraw draw, int width, int height) {
        int faint = 0x10FFFFFF;
        draw.fill(width / 3, 0, width / 3 + 1, height, faint);
        draw.fill(width * 2 / 3, 0, width * 2 / 3 + 1, height, faint);
        draw.fill(0, height / 3, width, height / 3 + 1, faint);
        draw.fill(0, height * 2 / 3, width, height * 2 / 3 + 1, faint);
        int margin = (int) HudSnap.SAFE_MARGIN;
        draw.outline(margin, margin, width - margin * 2, height - margin * 2, 0x14FFFFFF);
    }

    private void drawOutlines(UiDraw draw, int mouseX, int mouseY) {
        HudRenderer.Placed hovered = mode == Mode.NONE ? hit(mouseX, mouseY) : null;
        HudRenderer.Placed chosen = null;
        for (int index = 0; index < renderer.placedCount(); index++) {
            HudRenderer.Placed placed = renderer.placed(index);
            if (!placed.drawn()) {
                continue;
            }
            if (placed.config().id().equals(selected)) {
                chosen = placed;
                continue;
            }
            HudBox box = placed.box();
            int color = placed == hovered ? 0xA0FFFFFF : placed.config().placement().visible() ? 0x40FFFFFF : 0x28FFFFFF;
            draw.outline(floor(box.x()) - 1, floor(box.y()) - 1, ceil(box.width()) + 2, ceil(box.height()) + 2, color);
        }
        if (chosen != null) {
            drawSelection(draw, chosen);
        }
    }

    private void drawSelection(UiDraw draw, HudRenderer.Placed placed) {
        HudBox box = placed.box();
        int left = floor(box.x()) - 1;
        int top = floor(box.y()) - 1;
        int right = left + ceil(box.width()) + 2;
        int bottom = top + ceil(box.height()) + 2;
        draw.outline(left, top, right - left, bottom - top, ACCENT);
        int[] cornersX = {left, right, left, right};
        int[] cornersY = {top, top, bottom, bottom};
        for (int index = 0; index < 4; index++) {
            draw.fill(cornersX[index] - 3, cornersY[index] - 3, cornersX[index] + 3, cornersY[index] + 3, ACCENT);
            draw.fill(cornersX[index] - 2, cornersY[index] - 2, cornersX[index] + 2, cornersY[index] + 2, 0xFFFFFFFF);
        }
        HudPlacement placement = placed.config().placement();
        HudAnchor anchor = placement.anchor();
        int screenX = (int) Math.round(renderer.screenWidth() * anchor.fractionX());
        int screenY = (int) Math.round(renderer.screenHeight() * anchor.fractionY());
        int pivotX = (int) Math.round(box.x() + box.width() * anchor.fractionX());
        int pivotY = (int) Math.round(box.y() + box.height() * anchor.fractionY());
        draw.fill(screenX - 2, screenY - 2, screenX + 2, screenY + 2, ACCENT);
        draw.fill(pivotX - 1, pivotY - 1, pivotX + 1, pivotY + 1, ACCENT);
        String tag = widgetName(placed.config()) + "  " + HudEditorOptions.percent(placement.scale());
        if (!placement.visible()) {
            tag += "  " + text.get("adapt.hud.editor.hidden");
        }
        int tagWidth = draw.textWidth(tag) + 8;
        int tagY = top - 14 >= 0 ? top - 14 : bottom + 2;
        int tagX = Math.max(0, Math.min(left, renderer.screenWidth() - tagWidth));
        draw.fill(tagX, tagY, tagX + tagWidth, tagY + 12, 0xE0101216);
        draw.fill(tagX, tagY + 11, tagX + tagWidth, tagY + 12, ACCENT);
        draw.text(tag, tagX + 4, tagY + 2, 0xFFFFFFFF, false);
    }

    private record Spec(String label, Runnable action, boolean enabled, boolean groupStart) {
    }

    private List<Spec> barSpecs() {
        HudProfile active = working.activeProfile();
        boolean several = working.profiles().size() > 1;
        String profileLabel = rename == null ? active.name() : rename + "_";
        String motion = text.get(working.reducedMotion() ? "adapt.hud.editor.motion.reduced" : "adapt.hud.editor.motion.full");
        return List.of(
                new Spec("-", () -> scaleProfile(-SCALE_STEP), active.scale() > HudProfile.MIN_SCALE + 1e-6, false),
                new Spec(text.get("adapt.hud.editor.scale") + " " + HudEditorOptions.percent(active.scale()),
                        () -> scaleProfile(1.0 - active.scale()), true, false),
                new Spec("+", () -> scaleProfile(SCALE_STEP), active.scale() < HudProfile.MAX_SCALE - 1e-6, false),
                new Spec("<", () -> commit(working.cycled(-1), null), several, true),
                new Spec(profileLabel, this::startRename, true, false),
                new Spec(">", () -> commit(working.cycled(1), null), several, false),
                new Spec(text.get("adapt.hud.editor.new"), () -> commit(working.withAdded(
                        HudDefaults.profile(working.freshName(text.get("adapt.hud.editor.new_profile")))), null), true, false),
                new Spec(text.get("adapt.hud.editor.duplicate"), () -> commit(working.withAdded(active.withName(
                        working.freshName(active.name() + " " + text.get("adapt.hud.editor.copy_suffix")))), null), true, false),
                new Spec(text.get("adapt.hud.editor.delete"), () -> {
                    commit(working.withoutActive(), null);
                    keepSelectionValid();
                }, several, false),
                new Spec(text.get("adapt.hud.editor.export"), this::exportProfile, true, true),
                new Spec(text.get("adapt.hud.editor.import"), this::importProfile, true, false),
                new Spec(text.get("adapt.hud.editor.undo"), this::undo, history.canUndo(), true),
                new Spec(text.get("adapt.hud.editor.redo"), this::redo, history.canRedo(), false),
                new Spec(text.get("adapt.hud.editor.motion") + ": " + motion,
                        () -> commit(working.withReducedMotion(!working.reducedMotion()), null), true, false),
                new Spec(text.get("adapt.hud.editor.reset_all"), () -> {
                    commit(working.withActiveProfile(HudDefaults.profile(active.name())), null);
                    keepSelectionValid();
                }, true, false),
                new Spec(text.get("adapt.hud.editor.done"), this::close, true, true));
    }

    /**
     * Lays the bar out in rows that fit the screen, with the hint under it, and puts the block where it covers the
     * least of the widgets and the protected view (never the selected widget when that can be helped): centred on the
     * crosshair, beside the zone or a widget, or at the top or bottom. Hidden while dragging.
     */
    private void drawBar(UiDraw draw, int mouseX, int mouseY, int width, int height) {
        buttons.clear();
        List<Spec> specs = barSpecs();
        int available = Math.max(80, width - 8);
        List<int[]> rows = new ArrayList<>();
        int rowStart = 0;
        int rowWidth = 0;
        for (int index = 0; index < specs.size(); index++) {
            int buttonWidth = draw.textWidth(specs.get(index).label()) + 10;
            int gap = index == rowStart ? 0 : specs.get(index).groupStart() ? 8 : 2;
            if (index > rowStart && rowWidth + gap + buttonWidth > available) {
                rows.add(new int[]{rowStart, index, rowWidth});
                rowStart = index;
                rowWidth = buttonWidth;
            } else {
                rowWidth += gap + buttonWidth;
            }
        }
        rows.add(new int[]{rowStart, specs.size(), rowWidth});
        String hint = rename != null ? text.get("adapt.hud.editor.rename_hint") : text.get("adapt.hud.editor.hint");
        int hintMax = Math.min(width - 16, HINT_WIDTH);
        int hintHeight = draw.wrappedHeight(hint, hintMax);
        boolean messageShown = message != null && controller.model().clock().millis() - messageAt < MESSAGE_MILLIS;
        int barHeight = rows.size() * (BUTTON_HEIGHT + 4) - 4;
        int blockHeight = 2 + barHeight + 2 + HINT_GAP + hintHeight + 3 + (messageShown ? 16 : 0);
        List<HudRect> parts = new ArrayList<>(rows.size() + 2);
        for (int index = 0; index < rows.size(); index++) {
            int span = rows.get(index)[2];
            parts.add(new HudRect((width - span) / 2 - 3, index * (BUTTON_HEIGHT + 4), span + 6, BUTTON_HEIGHT + 4));
        }
        int hintWidth = Math.min(hintMax, draw.textWidth(hint));
        int hintTop = 2 + rows.size() * (BUTTON_HEIGHT + 4) + HINT_GAP;
        parts.add(new HudRect((width - hintWidth) / 2 - 4, hintTop - 2, hintWidth + 8, hintHeight + 3));
        if (messageShown) {
            int messageWidth = draw.textWidth(message);
            parts.add(new HudRect((width - messageWidth) / 2 - 4, hintTop + hintHeight + 2, messageWidth + 8, 12));
        }
        int y = barTop(width, height, blockHeight, parts) + 2;
        for (int[] row : rows) {
            int x = (width - row[2]) / 2;
            draw.fill(x - 3, y - 2, x + row[2] + 3, y + BUTTON_HEIGHT + 2, 0xD0101216);
            for (int index = row[0]; index < row[1]; index++) {
                Spec spec = specs.get(index);
                if (index > row[0]) {
                    x += spec.groupStart() ? 8 : 2;
                }
                int buttonWidth = draw.textWidth(spec.label()) + 10;
                Button button = new Button(x, y, buttonWidth, BUTTON_HEIGHT, spec.label(), spec.enabled(), spec.action());
                buttons.add(button);
                drawButton(draw, button, button.contains(mouseX, mouseY), rename != null && index == PROFILE_BUTTON);
                x += buttonWidth;
            }
            y += BUTTON_HEIGHT + 4;
        }
        drawHint(draw, width, y - 2 + HINT_GAP + 2, hint, hintMax, hintHeight, messageShown);
    }

    /**
     * The block's top: the candidate spot where its rows and hint ({@code parts}, relative to the top) cover the least
     * widget area, the selected widget counting far more.
     */
    private int barTop(int width, int height, int blockHeight, List<HudRect> parts) {
        int lowest = Math.max(4, height - blockHeight - 4);
        List<Integer> candidates = new ArrayList<>();
        candidates.add(height / 2 - blockHeight / 2);
        HudBox zone = renderer.zone();
        candidates.add((int) Math.ceil(zone.y() + zone.height()) + 4);
        candidates.add((int) Math.floor(zone.y()) - 4 - blockHeight);
        for (int index = 0; index < renderer.placedCount(); index++) {
            HudRenderer.Placed placed = renderer.placed(index);
            if (placed.drawn()) {
                candidates.add((int) Math.ceil(placed.box().y() + placed.box().height()) + 4);
                candidates.add((int) Math.floor(placed.box().y()) - 4 - blockHeight);
            }
        }
        candidates.add(4);
        candidates.add(lowest);
        int best = Math.max(4, Math.min(lowest, candidates.get(0)));
        double bestCost = Double.MAX_VALUE;
        for (int candidate : candidates) {
            int top = Math.max(4, Math.min(lowest, candidate));
            double cost = 0.0;
            for (HudRect part : parts) {
                HudRect block = new HudRect(part.x(), top + part.y(), part.width(), part.height());
                cost += covered(block, zone.x(), zone.y(), zone.width(), zone.height());
                for (int index = 0; index < renderer.placedCount(); index++) {
                    HudRenderer.Placed placed = renderer.placed(index);
                    if (placed.drawn()) {
                        HudBox box = placed.box();
                        double area = covered(block, box.x() - 2, box.y() - 2, box.width() + 4, box.height() + 4);
                        cost += placed.config().id().equals(selected) ? area * 1000.0 : area;
                    }
                }
            }
            if (cost < bestCost - 1e-6) {
                bestCost = cost;
                best = top;
            }
        }
        return best;
    }

    private static double covered(HudRect block, double x, double y, double width, double height) {
        double across = Math.min(block.right(), x + width) - Math.max(block.x(), x);
        double down = Math.min(block.bottom(), y + height) - Math.max(block.y(), y);
        return across > 0.0 && down > 0.0 ? across * down : 0.0;
    }

    private static void drawButton(UiDraw draw, Button button, boolean hovered, boolean editing) {
        int background = !button.enabled() ? 0x60202428 : editing ? 0xFF1E3A4A : hovered ? 0xFF3A4350 : 0xFF262B33;
        draw.fill(button.x(), button.y(), button.x() + button.width(), button.y() + button.height(), background);
        draw.fill(button.x(), button.y() + button.height() - 1, button.x() + button.width(), button.y() + button.height(),
                editing || hovered && button.enabled() ? ACCENT : 0x40000000);
        int color = button.enabled() ? 0xFFF2F2F2 : 0xFF707780;
        draw.text(button.label(), button.x() + 5, button.y() + 3, color, false);
    }

    private void scaleProfile(double delta) {
        HudProfile active = working.activeProfile();
        double scale = Math.round((active.scale() + delta) * 10.0) / 10.0;
        commit(working.withActiveProfile(active.withScale(scale)), "profile-scale");
    }

    private void drawHint(UiDraw draw, int width, int top, String hint, int maxWidth, int hintHeight, boolean messageShown) {
        int hintWidth = Math.min(maxWidth, draw.textWidth(hint));
        int x = (width - hintWidth) / 2;
        draw.fill(x - 4, top - 2, x + hintWidth + 4, top + hintHeight + 1, 0x90000000);
        hintBox = new HudRect(x - 4, top - 2, hintWidth + 8, hintHeight + 3);
        draw.wrappedText(hint, x, top, maxWidth, 0xFFB8C0CA, false);
        if (messageShown) {
            int messageWidth = draw.textWidth(message);
            int messageY = top + hintHeight + 4;
            draw.fill((width - messageWidth) / 2 - 4, messageY - 2, (width + messageWidth) / 2 + 4, messageY + 10, 0xE0101216);
            draw.text(message, (width - messageWidth) / 2, messageY, ACCENT, false);
        }
    }

    private void drawPopover(UiDraw draw, int mouseX, int mouseY, int width, int height) {
        if (popoverWidget == null) {
            return;
        }
        HudWidgetConfig config = working.activeProfile().widget(popoverWidget);
        HudRenderer.Placed placed = renderer.placed(popoverWidget);
        if (config == null || placed == null) {
            popoverWidget = null;
            return;
        }
        List<SkillView> catalog = controller.model().active() && !controller.model().catalogSkills().isEmpty()
                ? controller.model().catalogSkills() : sample.catalogSkills();
        popoverRows = options.rows(config, working.activeProfile().autoFit(), working.activeProfile().protect(), catalog);
        int popoverWidth = POPOVER_WIDTH;
        for (HudEditorOptions.Row row : popoverRows) {
            int need = draw.textWidth(row.label()) + (row.value() == null ? 0 : VALUE_GAP + draw.textWidth(row.value())) + 10;
            popoverWidth = Math.max(popoverWidth, need);
        }
        popoverWidth = Math.min(popoverWidth, width - 4);
        HudBox box = placed.box();
        int x = (int) Math.round(box.x() + box.width()) + 6;
        if (x + popoverWidth > width - 2) {
            x = (int) Math.round(box.x()) - 6 - popoverWidth;
        }
        x = Math.max(2, Math.min(x, width - popoverWidth - 2));
        int totalHeight = ROW_HEIGHT * (popoverRows.size() + 1) + 4;
        int y = Math.max(2, Math.min((int) Math.round(box.y()), height - totalHeight - 2));
        popoverVisibleRows = Math.max(1, Math.min(popoverRows.size(), (height - y - 4) / ROW_HEIGHT - 1));
        popoverScroll = Math.max(0, Math.min(popoverScroll, popoverRows.size() - popoverVisibleRows));
        popoverX = x;
        popoverY = y;
        this.popoverWidth = popoverWidth;
        int bottom = y + ROW_HEIGHT * (popoverVisibleRows + 1) + 4;
        draw.fill(x, y, x + popoverWidth, bottom, 0xFF101216);
        draw.outline(x, y, popoverWidth, bottom - y, 0xFF3A4350);
        draw.fill(x, y, x + popoverWidth, y + ROW_HEIGHT + 1, 0xFF1A2028);
        draw.text(HudPaint.fit(draw, widgetName(config), popoverWidth - 10), x + 5, y + 2, ACCENT, false);
        for (int visible = 0; visible < popoverVisibleRows; visible++) {
            int index = visible + popoverScroll;
            HudEditorOptions.Row row = popoverRows.get(index);
            int rowY = y + ROW_HEIGHT * (visible + 1) + 2;
            boolean hovered = !row.heading() && mouseX >= x && mouseX < x + popoverWidth && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) {
                draw.fill(x + 1, rowY, x + popoverWidth - 1, rowY + ROW_HEIGHT, 0x30FFFFFF);
            }
            int labelColor = row.heading() ? 0xFFFFD45C : row.value() == null ? ACCENT : 0xFFB8C0CA;
            String value = row.value() == null ? null : HudPaint.fit(draw, row.value(),
                    popoverWidth - 10 - VALUE_GAP - Math.min(draw.textWidth(row.label()), popoverWidth / 3));
            int labelWidth = popoverWidth - 10 - (value == null ? 0 : VALUE_GAP + draw.textWidth(value));
            draw.text(HudPaint.fit(draw, row.label(), labelWidth), x + 5, rowY + 2, labelColor, false);
            if (value != null) {
                draw.text(value, x + popoverWidth - 5 - draw.textWidth(value), rowY + 2, 0xFFFFFFFF, false);
            }
        }
        if (popoverRows.size() > popoverVisibleRows) {
            int track = bottom - y - ROW_HEIGHT - 4;
            int thumb = Math.max(6, track * popoverVisibleRows / popoverRows.size());
            int thumbY = y + ROW_HEIGHT + 2 + (track - thumb) * popoverScroll / Math.max(1, popoverRows.size() - popoverVisibleRows);
            draw.fill(x + popoverWidth - 3, thumbY, x + popoverWidth - 1, thumbY + thumb, 0x80FFFFFF);
        }
    }

    private boolean insidePopover(double x, double y) {
        return popoverWidget != null && x >= popoverX && x < popoverX + popoverWidth && y >= popoverY
                && y < popoverY + ROW_HEIGHT * (popoverVisibleRows + 1) + 4;
    }

    private int popoverRow(double x, double y) {
        if (!insidePopover(x, y)) {
            return -1;
        }
        int visible = (int) Math.floor((y - popoverY - 2) / ROW_HEIGHT) - 1;
        if (visible < 0 || visible >= popoverVisibleRows) {
            return -1;
        }
        int index = visible + popoverScroll;
        return index < popoverRows.size() && !popoverRows.get(index).heading() ? index : -1;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }
}
