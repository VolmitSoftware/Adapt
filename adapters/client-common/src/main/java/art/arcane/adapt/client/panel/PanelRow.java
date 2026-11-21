package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.TileAction;
import art.arcane.adapt.client.protocol.TileHint;
import art.arcane.adapt.client.protocol.TileRole;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.NumberStepper;
import art.arcane.adapt.client.ui.kit.TextField;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiEase;
import art.arcane.adapt.client.ui.kit.UiFade;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.platform.menu.ClickKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One body tile of a LIST or FORM panel as a two-column row: icon and coloured name on the left, wrapping onto more
 * lines when long, and on the right the row's control (switch, stepper, field, choice or button), its value or an ink
 * chevron when it opens another menu. Controls end on the row's right edge and typed ones share {@link #FIELD_WIDTH},
 * so they form one column down the list; the button for clicks a plain click cannot reach sits left of the control.
 * A row without a control shows its description after the name when the whole of it fits; the lore is always in the
 * tooltip. Mouse buttons on the row itself send the chest's clicks.
 */
final class PanelRow extends UiNode {
    static final int PAD = 3;
    static final int ICON_GAP = 4;
    static final int MIN_HEIGHT = 18;
    static final int NAME_GAP = 6;
    static final int CHEVRON = UiTome.Glyph.FORWARD.width();
    static final int MORE = 14;
    static final int MAX_INPUT = 256;
    /** Typed values, steppers and choices share one width, so their left edges line up down the list. */
    static final int FIELD_WIDTH = 110;
    static final int MIN_BUTTON = 44;
    private static final int LINE_PAD = 3;
    private static final int DIVIDER = 0x305A3E26;
    private static final int CHIP = 0x1E5A3E26;

    private final int slot;
    private final Host owner;
    private final Label label;
    private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
    private PanelTile data;
    private UiNode control;
    private ControlKind controlKind = ControlKind.NONE;
    private Button more;
    private List<TileAction> alternates = List.of();
    private boolean ruled = true;

    PanelRow(int slot, Host owner) {
        super("row:" + slot);
        this.slot = slot;
        this.owner = Objects.requireNonNull(owner, "owner");
        label = add(new Label(slot));
    }

    /** What a row needs from its panel. */
    interface Host extends PanelControls.Sink {
        ClickKind press(int slot, UiMouseEvent event);

        ClickKind release(int slot, UiMouseEvent event);

        void openActions(PanelRow row);

        void openOptions(PanelRow row);

        UiHost host();
    }

    int slot() {
        return slot;
    }

    /** Whether the row draws the rule under it; the list leaves it out under its last row and above a heading. */
    void setRuled(boolean rule) {
        ruled = rule;
    }

    /**
     * The width the row needs to show its name, description and value on one line, or 0 for a row with a control,
     * whose width follows the panel's.
     */
    int naturalWidth(UiDraw metrics) {
        if (control != null) {
            return 0;
        }
        String description = description();
        int text = metrics.textWidth(data.name()) + (description.isEmpty() ? 0 : NAME_GAP + metrics.textWidth(description));
        int right = (more == null ? 0 : MORE + services().theme().gap())
                + (data.value().isEmpty() ? 0 : metrics.textWidth(data.value()) + UiControl.TEXT_INSET * 2 + NAME_GAP)
                + (navigates() ? CHEVRON + NAME_GAP : 0);
        return PAD * 2 + UiIcons.SIZE + ICON_GAP + text + right;
    }

    PanelTile data() {
        return data;
    }

    UiNode control() {
        return control;
    }

    Button moreButton() {
        return more;
    }

    List<TileAction> alternates() {
        return alternates;
    }

    @Override
    public String kind() {
        return "row";
    }

    @Override
    public String label() {
        return LegacyText.strip(data.name());
    }

    @Override
    public void describe(Map<String, String> state) {
        state.put("slot", Integer.toString(slot));
        state.put("role", data.role().name());
        if (!data.section().isEmpty()) {
            state.put("section", data.section());
        }
        if (!data.value().isEmpty()) {
            state.put("value", data.value());
        }
        if (data.hint() != null) {
            state.put("hint", data.hint().kind().name());
            state.put("hintValue", data.hint().value());
        }
        if (!alternates.isEmpty()) {
            List<String> kinds = new ArrayList<>();
            for (TileAction action : alternates) {
                kinds.add(action.click().name());
            }
            state.put("actions", String.join("|", kinds));
        }
    }

    void update(PanelTile tile) {
        data = tile;
        alternates = alternates(tile);
        syncControl();
        syncMore();
        label.setTooltip(tooltip(tile, alternates, owner.host()));
    }

    /** The clicks a row offers besides the plain click (and besides the control, for typed values). */
    static List<TileAction> alternates(PanelTile tile) {
        List<TileAction> list = new ArrayList<>();
        for (TileAction action : tile.actions()) {
            if (action.click() != ClickKind.LEFT && action.click() != ClickKind.OTHER && action.click() != ClickKind.DOUBLE_CLICK) {
                list.add(action);
            }
        }
        return list;
    }

    static String actionLabel(TileAction action, UiHost host) {
        return action.label().isBlank() ? PanelText.click(action.click()).text(host) : action.label();
    }

    static Tooltip tooltip(PanelTile tile, List<TileAction> alternates, UiHost host) {
        List<String> lines = new ArrayList<>(tile.lore());
        if (!alternates.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            for (TileAction action : alternates) {
                String text = actionLabel(action, host);
                lines.add(action.label().isBlank() ? "§8" + text
                        : "§8" + PanelText.click(action.click()).text(host) + ": §7" + LegacyText.strip(text));
            }
        }
        return new Tooltip(tile.name(), lines);
    }

    private void syncControl() {
        TileHint hint = data.hint();
        ControlKind wanted = controlKind(data);
        if (wanted != controlKind) {
            if (control != null) {
                remove(control);
            }
            control = create(wanted);
            controlKind = wanted;
            if (control != null) {
                add(control);
            }
        }
        switch (controlKind) {
            case SWITCH -> ((PanelControls.Switch) control).setOn(NativePanelScreen.toggleOn(hint.value()));
            case NUMBER -> ((NumberStepper) control).setValue(hint.value());
            case TEXT -> {
                TextField field = (TextField) control;
                if (!field.isFocused() || !field.dirty()) {
                    field.setText(hint.value());
                }
            }
            case CHOICE -> {
                PanelControls.Choice choice = (PanelControls.Choice) control;
                choice.setHint(hint);
                choice.setBackward(answers(data, ClickKind.RIGHT) || !answers(data, ClickKind.LEFT) ? ClickKind.RIGHT : ClickKind.LEFT);
            }
            case BUTTON -> ((Button) control).setText(buttonLabel(data));
            case NONE -> {
            }
        }
    }

    static boolean answers(PanelTile tile, ClickKind click) {
        for (TileAction action : tile.actions()) {
            if (action.click() == click) {
                return true;
            }
        }
        return false;
    }

    static ControlKind controlKind(PanelTile tile) {
        TileHint hint = tile.hint();
        if (hint != null) {
            return switch (hint.kind()) {
                case TOGGLE -> ControlKind.SWITCH;
                case NUMBER -> ControlKind.NUMBER;
                case TEXT -> ControlKind.TEXT;
                case CHOICE -> ControlKind.CHOICE;
                case DESTRUCTIVE -> ControlKind.BUTTON;
                case NAVIGATION -> ControlKind.NONE;
            };
        }
        return tile.role() == TileRole.ACTION && !buttonLabel(tile).isBlank() ? ControlKind.BUTTON : ControlKind.NONE;
    }

    private static String buttonLabel(PanelTile tile) {
        if (tile.hint() != null && tile.hint().kind() == HintKind.DESTRUCTIVE && !tile.hint().value().isBlank()) {
            return tile.hint().value();
        }
        for (TileAction action : tile.actions()) {
            if (action.click() == ClickKind.LEFT) {
                return action.label();
            }
        }
        return "";
    }

    private UiNode create(ControlKind kind) {
        String id = "setting:" + slot;
        return switch (kind) {
            case SWITCH -> new PanelControls.Switch(id, slot, owner);
            case NUMBER -> new NumberStepper(id, data.hint().value(), PanelControls.number(slot, owner, () -> data.hint()));
            case TEXT -> {
                TextField field = new TextField(id, MAX_INPUT);
                field.setOnCommit((String text) -> owner.input(slot, text));
                yield field;
            }
            case CHOICE -> new PanelControls.Choice(id, slot, owner, () -> owner.openOptions(this));
            case BUTTON -> new Button(id, buttonLabel(data),
                    data.hint() != null && data.hint().kind() == HintKind.DESTRUCTIVE ? Button.Style.DESTRUCTIVE : Button.Style.NORMAL,
                    () -> owner.click(slot, ClickKind.LEFT));
            case NONE -> null;
        };
    }

    /** The row menu appears when the server named at least one extra click; unnamed ones stay in the tooltip and on the mouse. */
    private void syncMore() {
        boolean named = alternates.stream().anyMatch((TileAction action) -> !action.label().isBlank());
        if (!named) {
            if (more != null) {
                remove(more);
                more = null;
            }
            return;
        }
        if (more == null) {
            more = add(new Button("more:" + slot, UiTome.Glyph.MORE, () -> owner.openActions(this)));
        }
        more.setTooltip(Tooltip.of(PanelText.MORE.text(owner.host())));
    }

    boolean destructive() {
        return data.hint() != null && data.hint().kind() == HintKind.DESTRUCTIVE;
    }

    boolean navigates() {
        return data.role() == TileRole.NAVIGATION || data.hint() != null && data.hint().kind() == HintKind.NAVIGATION;
    }

    /** Whether a plain click on the row goes to its control instead of the server. */
    boolean controlTakesClick() {
        return controlKind == ControlKind.NUMBER || controlKind == ControlKind.TEXT
                || controlKind == ControlKind.CHOICE && data.hint().typed();
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public boolean hittable() {
        return true;
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        int controlHeight = control == null ? 0 : services().theme().controlHeight() + 4;
        int nameHeight = metrics.wrappedHeight(data.name(), textWidth(width, metrics)) + LINE_PAD * 2;
        return Math.max(MIN_HEIGHT, Math.max(controlHeight, nameHeight));
    }

    /** The description on one line: its lines joined, colour codes dropped. */
    private String description() {
        StringBuilder joined = new StringBuilder();
        for (String line : data.shownDescription()) {
            if (!LegacyText.isBlank(line)) {
                if (!joined.isEmpty()) {
                    joined.append(' ');
                }
                joined.append(LegacyText.strip(line).strip());
            }
        }
        return joined.toString();
    }

    private int rightWidth(int width, UiDraw metrics) {
        UiTheme theme = services().theme();
        int used = 0;
        if (more != null) {
            used += MORE + theme.gap();
        }
        if (control != null) {
            used += controlWidth(width, metrics) + NAME_GAP;
        } else if (!data.value().isEmpty()) {
            used += chipWidth(width, metrics) + NAME_GAP;
        }
        if (navigates()) {
            used += CHEVRON + NAME_GAP;
        }
        return used;
    }

    /** The width of the control column at a row {@code width} wide. */
    static int columnWidth(int width) {
        return Math.min(width * 45 / 100, FIELD_WIDTH);
    }

    private int controlWidth(int width, UiDraw metrics) {
        int column = columnWidth(width);
        return switch (controlKind) {
            case SWITCH -> control.preferredWidth(metrics);
            case NUMBER, TEXT, CHOICE -> column;
            case BUTTON -> Math.min(Math.max(column, width / 2), Math.max(MIN_BUTTON, control.preferredWidth(metrics)));
            case NONE -> 0;
        };
    }

    private int chipWidth(int width, UiDraw metrics) {
        return Math.min(width / 2, metrics.textWidth(data.value()) + UiControl.TEXT_INSET * 2);
    }

    private int textLeft() {
        return bounds().x() + PAD + UiIcons.SIZE + ICON_GAP;
    }

    private int textWidth(int width, UiDraw metrics) {
        return Math.max(20, width - PAD * 2 - UiIcons.SIZE - ICON_GAP - rightWidth(width, metrics));
    }

    @Override
    protected void layout(UiDraw metrics) {
        UiRect box = bounds();
        UiTheme theme = services().theme();
        int right = box.right() - PAD;
        if (navigates()) {
            right -= CHEVRON + NAME_GAP;
        }
        if (control != null) {
            int width = controlWidth(box.width(), metrics);
            int height = controlKind == ControlKind.SWITCH ? UiTome.CHECK + 2 : theme.controlHeight();
            control.place(new UiRect(right - width, box.centerY() - height / 2, width, height), metrics);
            right -= width + theme.gap();
        } else if (!data.value().isEmpty()) {
            right -= chipWidth(box.width(), metrics) + theme.gap();
        }
        if (more != null) {
            more.place(new UiRect(right - MORE, box.centerY() - MORE / 2, MORE, MORE), metrics);
        }
        int nameWidth = Math.min(textWidth(box.width(), metrics), metrics.textWidth(data.name()));
        label.place(UiRect.of(box.x(), box.y(), textLeft() + nameWidth, box.bottom()), metrics);
    }

    @Override
    public boolean mousePressed(UiMouseEvent event) {
        ClickKind kind = owner.press(slot, event);
        if (kind == null) {
            return true;
        }
        if (kind == ClickKind.LEFT && controlTakesClick()) {
            if (control instanceof NumberStepper stepper) {
                services().focus(stepper.field());
            } else if (control instanceof PanelControls.Choice choice) {
                choice.activate();
            } else {
                services().focus(control);
            }
            return true;
        }
        services().playClick();
        owner.click(slot, kind);
        return true;
    }

    @Override
    public boolean mouseReleased(UiMouseEvent event) {
        ClickKind kind = owner.release(slot, event);
        if (kind != null && !controlTakesClick()) {
            owner.click(slot, kind);
        }
        return true;
    }

    @Override
    public boolean activate() {
        if (controlTakesClick()) {
            if (control instanceof NumberStepper stepper) {
                services().focus(stepper.field());
            } else if (control instanceof PanelControls.Choice choice) {
                choice.activate();
            } else {
                services().focus(control);
            }
            return true;
        }
        services().playClick();
        owner.click(slot, ClickKind.LEFT);
        return true;
    }

    @Override
    protected void paint(UiFrame frame) {
        UiDraw draw = frame.draw();
        UiTheme theme = frame.theme();
        UiRect box = bounds();
        UiNode hovered = frame.hovered();
        boolean over = hovered == this || hovered == label;
        float lift = hover.update(frame.motion(), over || frame.showsFocus(this));
        if (lift > 0.0F) {
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom() - 1, UiEase.fade(UiTome.HOVER, lift));
        }
        if (frame.isPressed(this)) {
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom() - 1, CHIP);
        }
        if (ruled) {
            draw.fill(box.x() + PAD, box.bottom() - 1, box.right() - PAD, box.bottom(), DIVIDER);
        }
        if (frame.showsFocus(this)) {
            UiControl.focusRing(draw, box.x() + 1, box.y() + 1, box.width() - 2, box.height() - 3, theme);
        }
        int iconY = box.y() + (box.height() - UiIcons.SIZE) / 2;
        UiIcons.paintOn(draw, data.icon(), box.x() + PAD, iconY, UiIcons.SIZE, data.glint(), theme.backdrop());
        int textLeft = textLeft();
        int text = textWidth(box.width(), draw);
        int nameColor = destructive() ? theme.destructive() : theme.text();
        int nameWidth = draw.textWidth(data.name());
        if (nameWidth > text) {
            int height = draw.wrappedHeight(data.name(), text);
            draw.wrappedText(data.name(), textLeft, box.y() + (box.height() - height) / 2 + 1, text, nameColor, false);
        } else {
            int textY = UiControl.textY(box);
            draw.text(data.name(), textLeft, textY, nameColor, false);
            String description = control == null ? description() : "";
            int descriptionLeft = textLeft + nameWidth + NAME_GAP;
            if (!description.isEmpty() && descriptionLeft + draw.textWidth(description) <= textLeft + text) {
                draw.text(description, descriptionLeft, textY, theme.textMuted(), false);
            }
        }
        paintRight(draw, theme, box);
    }

    @Override
    protected void paintOver(UiFrame frame) {
        if (data.amount() > 1) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            String count = Integer.toString(data.amount());
            int right = box.x() + PAD + UiIcons.SIZE + 1;
            int top = box.y() + (box.height() - UiIcons.SIZE) / 2 + UiIcons.SIZE - UiControl.CAP_HEIGHT;
            draw.pushPose();
            draw.depth(200.0F);
            UiTome.soft(draw, right - draw.textWidth(count) - 1, top - 1, right, top + UiControl.CAP_HEIGHT + 1, theme.backdrop());
            draw.text(count, right - draw.textWidth(count), top, theme.text(), false);
            draw.popPose();
        }
    }

    private void paintRight(UiDraw draw, UiTheme theme, UiRect box) {
        int right = box.right() - PAD;
        if (navigates()) {
            UiTome.glyph(draw, UiTome.Glyph.FORWARD, right - CHEVRON, box.centerY() - UiTome.Glyph.FORWARD.height() / 2, theme.text());
            right -= CHEVRON + NAME_GAP;
        }
        if (control == null && !data.value().isEmpty()) {
            int width = chipWidth(box.width(), draw);
            String value = LegacyText.truncate(draw, data.value(), width - UiControl.TEXT_INSET * 2);
            int height = theme.controlHeight() - 2;
            int top = box.centerY() - height / 2;
            UiTome.soft(draw, right - width, top, right, top + height, CHIP);
            draw.text(value, right - UiControl.TEXT_INSET - draw.textWidth(value), UiControl.textY(top, height), theme.text(), false);
        }
    }

    enum ControlKind {
        NONE,
        SWITCH,
        NUMBER,
        TEXT,
        CHOICE,
        BUTTON
    }

    /** The icon and name, which carry the row's tooltip so the controls on the right stay clear of it. */
    private static final class Label extends UiNode {
        private Label(int slot) {
            super("label:" + slot);
        }

        @Override
        public String kind() {
            return "label";
        }

        @Override
        public boolean hittable() {
            return true;
        }
    }
}
