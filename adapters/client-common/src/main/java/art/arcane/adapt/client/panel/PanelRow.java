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
 * One body tile of a LIST or FORM panel as a single line: icon, coloured name and its description cut to what is left
 * of the line (the whole lore is in the tooltip), then on the right the row's control (switch, stepper, field, choice
 * or button), its value, an ink chevron when it opens another menu and a button for the clicks a plain click cannot reach.
 * Mouse buttons on the row itself send the chest's clicks.
 */
final class PanelRow extends UiNode {
    static final int PAD = 3;
    static final int ICON_GAP = 4;
    static final int MIN_HEIGHT = 18;
    static final int NAME_GAP = 6;
    static final int CHEVRON = UiTome.Glyph.FORWARD.width();
    static final int MORE = 14;
    static final int MAX_INPUT = 256;
    /** Typed values and steppers share one width, so their left edges line up down the list. */
    static final int FIELD_WIDTH = 110;
    private static final int DIVIDER = 0x305A3E26;
    private static final int CHIP = 0x1E5A3E26;

    private final int slot;
    private final Host owner;
    private final Label label;
    private final UiFade hover = new UiFade(90L);
    private PanelTile data;
    private UiNode control;
    private ControlKind controlKind = ControlKind.NONE;
    private Button more;
    private List<TileAction> alternates = List.of();

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
            more = add(new Button("more:" + slot, "...", () -> owner.openActions(this)));
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
        return Math.max(MIN_HEIGHT, controlHeight);
    }

    /** The description on one line: its lines joined, colour codes kept. */
    private String description() {
        StringBuilder joined = new StringBuilder();
        for (String line : data.shownDescription()) {
            if (!LegacyText.isBlank(line)) {
                if (!joined.isEmpty()) {
                    joined.append(' ');
                }
                joined.append(line.strip());
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
        if (navigates()) {
            used += CHEVRON + theme.gap() * 2;
        }
        if (control != null) {
            used += controlWidth(width, metrics) + theme.gap() * 2;
        } else if (!data.value().isEmpty()) {
            used += chipWidth(width, metrics) + theme.gap() * 2;
        }
        return used;
    }

    private int controlWidth(int width, UiDraw metrics) {
        int cap = width * 45 / 100;
        return switch (controlKind) {
            case SWITCH -> control.preferredWidth(metrics);
            case NUMBER, TEXT -> Math.min(cap, FIELD_WIDTH);
            case CHOICE, BUTTON -> Math.min(cap, control.preferredWidth(metrics));
            case NONE -> 0;
        };
    }

    private int chipWidth(int width, UiDraw metrics) {
        return Math.min(width / 3, metrics.textWidth(data.value()) + 8);
    }

    private int textWidth(int width, UiDraw metrics) {
        return Math.max(20, width - PAD * 2 - UiIcons.SIZE - ICON_GAP - rightWidth(width, metrics));
    }

    @Override
    protected void layout(UiDraw metrics) {
        UiRect box = bounds();
        UiTheme theme = services().theme();
        int right = box.right() - PAD;
        if (more != null) {
            more.place(new UiRect(right - MORE, box.centerY() - MORE / 2, MORE, MORE), metrics);
            right -= MORE + theme.gap();
        }
        if (navigates()) {
            right -= CHEVRON + theme.gap() * 2;
        }
        if (control != null) {
            int width = controlWidth(box.width(), metrics);
            int height = controlKind == ControlKind.SWITCH ? 12 : theme.controlHeight();
            control.place(new UiRect(right - width, box.centerY() - height / 2, width, height), metrics);
        }
        int textLeft = box.x() + PAD + UiIcons.SIZE + ICON_GAP;
        label.place(UiRect.of(box.x(), box.y(), Math.max(textLeft, box.x() + PAD + UiIcons.SIZE + ICON_GAP
                + Math.min(textWidth(box.width(), metrics), metrics.textWidth(data.name()))), box.bottom()), metrics);
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
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), UiEase.fade(UiTome.HOVER, lift));
        }
        if (frame.isPressed(this)) {
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), 0x1E5A3E26);
        }
        if (destructive()) {
            draw.fill(box.x(), box.y() + 2, box.x() + 2, box.bottom() - 2, theme.destructive());
        }
        draw.fill(box.x() + PAD, box.bottom() - 1, box.right() - PAD, box.bottom(), DIVIDER);
        if (frame.showsFocus(this)) {
            UiTome.softEdge(draw, box.x(), box.y(), box.width(), box.height(), theme.focus());
        }
        int iconY = box.y() + (box.height() - UiIcons.SIZE) / 2;
        UiIcons.paintOn(draw, data.icon(), box.x() + PAD, iconY, UiIcons.SIZE, data.glint(), theme.backdrop());
        int textLeft = box.x() + PAD + UiIcons.SIZE + ICON_GAP;
        int text = textWidth(box.width(), draw);
        int textY = box.y() + (box.height() - draw.lineHeight()) / 2 + 1;
        int nameColor = destructive() ? theme.destructive() : theme.text();
        String name = LegacyText.truncate(draw, data.name(), text);
        draw.text(name, textLeft, textY, nameColor, true);
        int descriptionLeft = textLeft + draw.textWidth(name) + NAME_GAP;
        int room = textLeft + text - descriptionLeft;
        String description = description();
        if (!description.isEmpty() && room > draw.textWidth(LegacyText.ELLIPSIS) * 4) {
            draw.text(LegacyText.truncate(draw, LegacyText.strip(description), room), descriptionLeft, textY, theme.textMuted(), false);
        }
        paintRight(frame, draw, theme, box);
    }

    @Override
    protected void paintOver(UiFrame frame) {
        if (data.amount() > 1) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            String count = Integer.toString(data.amount());
            int iconY = box.y() + (box.height() - UiIcons.SIZE) / 2;
            draw.pushPose();
            draw.depth(200.0F);
            draw.text(count, box.x() + PAD + UiIcons.SIZE + 1 - draw.textWidth(count), iconY + 9, 0xFFFFFFFF, true);
            draw.popPose();
        }
    }

    private void paintRight(UiFrame frame, UiDraw draw, UiTheme theme, UiRect box) {
        int right = box.right() - PAD;
        if (more != null) {
            right -= MORE + theme.gap();
        }
        int textY = box.centerY() - draw.lineHeight() / 2 + 1;
        if (navigates()) {
            UiTome.glyph(draw, UiTome.Glyph.FORWARD, right - CHEVRON, box.centerY() - UiTome.Glyph.FORWARD.height() / 2, theme.text());
            right -= CHEVRON + theme.gap() * 2;
        }
        if (control == null && !data.value().isEmpty()) {
            int width = chipWidth(box.width(), draw);
            String value = LegacyText.truncate(draw, data.value(), width - 8);
            int left = right - width;
            UiTome.soft(draw, left, box.centerY() - 6, right, box.centerY() + 6, CHIP);
            draw.text(value, left + 4, textY, theme.text(), false);
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
