package art.arcane.adapt.client.panel;

import art.arcane.adapt.client.protocol.HintKind;
import art.arcane.adapt.client.protocol.PanelTile;
import art.arcane.adapt.client.protocol.TileAction;
import art.arcane.adapt.client.protocol.TileHint;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiHost;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.NumberStepper;
import art.arcane.adapt.client.ui.kit.Popover;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.TextField;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;

import java.util.List;

/**
 * The one popover a panel shows at a time: a value editor for a grid tile, the option list of a choice, or the extra
 * clicks of a row. It follows its slot across updates and closes when the slot no longer takes that kind of edit.
 */
final class PanelEditors {
    private static final int TEXT_EDITOR_WIDTH = 180;
    private static final int NUMBER_EDITOR_WIDTH = 140;
    private static final int MENU_WIDTH = 170;

    interface Host {
        UiHost host();

        void openOverlay(Popover popover);

        void closeOverlay(Popover popover);

        /** The current tile in {@code slot}, or null. */
        PanelTile tile(int slot);

        /** Where an editor for {@code slot} points, or null when the slot is not on screen. */
        UiRect anchor(int slot);
    }

    private final Host host;
    private final PanelControls.Sink sink;
    private Popover editor;
    private int editorSlot = -1;

    PanelEditors(Host host, PanelControls.Sink sink) {
        this.host = host;
        this.sink = sink;
    }

    Popover editor() {
        return editor;
    }

    void anchor() {
        if (editor == null) {
            return;
        }
        UiRect anchor = host.anchor(editorSlot);
        if (anchor != null) {
            editor.setAnchor(anchor);
        }
    }

    void sync() {
        if (editor == null) {
            return;
        }
        PanelTile tile = host.tile(editorSlot);
        UiNode content = editor.content();
        TileHint hint = tile == null ? null : tile.hint();
        boolean keep = tile != null && (content.id().startsWith("actions:") || hint != null && switch (hint.kind()) {
            case NUMBER -> content instanceof NumberStepper;
            case TEXT -> content instanceof TextField;
            case CHOICE -> content.id().startsWith("options.scroll:");
            default -> false;
        });
        if (!keep) {
            close();
            return;
        }
        if (content instanceof NumberStepper stepper) {
            stepper.setValue(hint.value());
        } else if (content instanceof TextField field && !field.dirty()) {
            field.setText(hint.value());
        }
    }

    void openGrid(PanelGrid.SlotTile tile) {
        close();
        int slot = tile.slot();
        TileHint hint = tile.data().hint();
        UiNode content;
        int width;
        if (hint.kind() == HintKind.NUMBER) {
            content = new NumberStepper("editor.value", hint.value(), PanelControls.number(slot, sink, () -> currentHint(slot)));
            width = NUMBER_EDITOR_WIDTH;
        } else if (hint.kind() == HintKind.CHOICE) {
            openOptions(slot, tile.bounds(), tile.data());
            return;
        } else {
            TextField field = new TextField("editor.value", PanelRow.MAX_INPUT);
            field.setText(hint.value());
            field.selectAll();
            field.setOnCommit((String text) -> {
                sink.input(slot, text);
                close();
            });
            content = field;
            width = TEXT_EDITOR_WIDTH;
        }
        show(slot, new Popover("editor", tile.data().name(), tile.bounds(), width, content));
    }

    void openOptions(int slot, UiRect anchor, PanelTile tile) {
        close();
        TileHint hint = tile.hint();
        UiGroup group = new UiGroup("options:" + slot, UiLayoutPolicy.column(2));
        List<String> options = hint.options();
        Button chosenButton = null;
        for (int index = 0; index < options.size(); index++) {
            String option = options.get(index);
            boolean chosen = option.equals(hint.value());
            Button button = group.add(new Button("option:" + slot + ":" + index, option, chosen ? Button.Style.PRIMARY : Button.Style.NORMAL,
                    () -> {
                        close();
                        if (!chosen) {
                            sink.input(slot, option);
                        }
                    }));
            if (chosen) {
                chosenButton = button;
            }
        }
        show(slot, new Popover("editor", PanelText.OPTIONS.text(host.host()), anchor, MENU_WIDTH,
                new OptionScroll("options.scroll:" + slot, group, chosenButton)));
    }

    void openActions(int slot, UiRect anchor, PanelTile tile, List<TileAction> alternates) {
        close();
        UiGroup group = new UiGroup("actions:" + slot, UiLayoutPolicy.column(2));
        for (TileAction action : alternates) {
            group.add(new Button("action:" + slot + ":" + action.click().name(), PanelRow.actionLabel(action, host.host()), () -> {
                close();
                sink.click(slot, action.click());
            }));
        }
        show(slot, new Popover("editor", tile.name(), anchor, MENU_WIDTH, group));
    }

    void close() {
        if (editor != null) {
            Popover open = editor;
            forget();
            host.closeOverlay(open);
        }
    }

    private TileHint currentHint(int slot) {
        PanelTile tile = host.tile(slot);
        return tile == null ? null : tile.hint();
    }

    private void show(int slot, Popover popover) {
        editor = popover;
        editorSlot = slot;
        popover.setOnDismiss(this::forget);
        host.openOverlay(popover);
    }

    private void forget() {
        editor = null;
        editorSlot = -1;
    }

    /** The option list of a choice: at most {@value #VISIBLE_OPTIONS} options tall, scrolled to the current one. */
    private static final class OptionScroll extends ScrollArea {
        private static final int VISIBLE_OPTIONS = 8;
        private final UiNode chosen;
        private boolean revealed;

        private OptionScroll(String id, UiNode content, UiNode chosen) {
            super(id, content);
            this.chosen = chosen;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            UiTheme theme = services().theme();
            return Math.min(super.preferredHeight(width, metrics), VISIBLE_OPTIONS * (theme.controlHeight() + 2) - 2);
        }

        @Override
        protected void layout(UiDraw metrics) {
            super.layout(metrics);
            if (!revealed && chosen != null) {
                revealed = true;
                reveal(chosen.bounds());
            }
        }
    }
}
