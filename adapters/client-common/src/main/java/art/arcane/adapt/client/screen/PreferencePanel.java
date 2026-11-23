package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.IconTile;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.Toggle;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceChoiceView;
import art.arcane.adapt.client.view.PreferenceGroupView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.PreferenceView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The preferences of one skill or adaptation, as blocks for the book's flow so a page break falls between whole
 * preferences. The {@code enabled} switch sits alone at the top above a rule; the other on/off preferences form an even
 * grid of as many equal columns as the page holds cells of {@link #MIN_CELL} with every name on at most two lines,
 * filled row by row, one block per line of it, each cell its checkbox right before its name; other preferences are a row
 * of icon choices after their name (choices above the player's level locked); toggle groups are their heading and a
 * row of icon checkboxes. Every such row puts its tiles in one column after the same width of names, or every one puts
 * them under its name, whichever takes less height, so names and tiles line up down the page. Reset is the section's action: the owner sets it at the end of the section's heading.
 * Preferences the player cannot change show
 * read-only with a lock, under a note that says why when the whole panel is locked. Nothing changes on screen until the
 * server's answer arrives; the owner disables the panel while a request is out. Widget ids: {@code pref:<id>},
 * {@code pref:<id>:<value>}, {@code pref-group:<id>}, {@code pref-reset}.
 */
final class PreferencePanel {
    static final String ON = "ON";
    static final String OFF = "OFF";
    static final String PRIMARY = "enabled";
    static final int CELL_HEIGHT = 12;
    /** A checklist line when the page is short of room: the checkbox's height. */
    static final int TIGHT_CELL = UiTome.CHECK;
    static final int COLUMN_GAP = 10;
    static final int CELL_GAP = 6;
    static final int ROW_SPACING = 1;
    /** The narrowest a checklist cell gets before the grid drops a column. */
    static final int MIN_CELL = 96;
    private static final int MOST_COLUMNS = 3;
    private static final int SECTION_GAP = 4;
    private static final int TIGHT_GAP = 2;
    private static final int LEAD_GAP = 6;
    private static final int TILE_GAP = 2;

    private final String id;
    private final Actions actions;
    private final List<UiNode> cells = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private Label note;
    private RequestToggle primary;
    private UiGroup mainLine;
    private Rule rule;
    private Button reset;
    private boolean enabled = true;

    PreferencePanel(String id, Actions actions) {
        this.id = Objects.requireNonNull(id, "id");
        this.actions = Objects.requireNonNull(actions, "actions");
    }

    /** Whether a preference's choices are exactly on and off, so it shows as a switch. */
    static boolean isToggle(PreferenceView preference) {
        Set<String> values = new HashSet<>();
        for (PreferenceChoiceView choice : preference.choices()) {
            values.add(choice.value());
        }
        return values.equals(Set.of(ON, OFF)) || preference.choices().isEmpty() && (ON.equals(preference.defaultValue())
                || OFF.equals(preference.defaultValue()));
    }

    /** Whether the player can change this preference at all. */
    static boolean editable(PreferenceView preference) {
        return preference.playerEditable() && preference.choices().size() > 1;
    }

    /**
     * Rebuilds the controls. {@code level} is the owner's learned level (choices with a higher minimum are locked);
     * {@code locked} disables everything with {@code lockedText} as the reason, such as an unlearned adaptation.
     */
    void update(PreferenceOwner owner, String ownerId, List<PreferenceView> preferences, List<PreferenceGroupView> groups, PlayerView player,
                int level, String lockedText) {
        boolean locked = lockedText != null;
        note = locked ? new Label(id + ".locked", lockedText).setColor(Palette.LOCK).setWrap(true) : null;
        Set<String> grouped = new HashSet<>();
        for (PreferenceGroupView group : groups) {
            for (PreferenceGroupView.Entry entry : group.entries()) {
                grouped.add(entry.preferenceId());
            }
        }
        primary = null;
        mainLine = null;
        UiNode primaryCell = null;
        cells.clear();
        rows.clear();
        for (PreferenceView preference : preferences) {
            if (grouped.contains(preference.id())) {
                continue;
            }
            String value = CatalogIndex.preference(player, owner, ownerId, preference);
            UiNode cell;
            if (locked || !editable(preference)) {
                cell = readOnly(preference, value, lockedText);
            } else if (isToggle(preference)) {
                cell = toggle(preference, value);
            } else {
                rows.add(choices(ownerId, preference, value, level));
                continue;
            }
            if (primaryCell == null && PRIMARY.equals(preference.id()) && isToggle(preference)) {
                primaryCell = cell;
                primary = cell instanceof RequestToggle toggle ? toggle : null;
            } else {
                cells.add(cell);
            }
        }
        for (PreferenceGroupView group : groups) {
            Row row = group(group, preferences, owner, ownerId, player, lockedText);
            if (row != null) {
                rows.add(row);
            }
        }
        boolean rest = !cells.isEmpty() || !rows.isEmpty();
        boolean anyEditable = false;
        for (PreferenceView preference : preferences) {
            anyEditable |= editable(preference);
        }
        reset = null;
        if (!locked && anyEditable) {
            reset = new Button("pref-reset", ScreenText.RESET.text(), Button.Style.QUIET, actions::reset);
            reset.setTooltip(new Tooltip(ScreenText.RESET.text(), List.of("§7" + ScreenText.RESET_TIP.text())));
        }
        if (primaryCell != null) {
            mainLine = new UiGroup(id + ".main", new Leading());
            mainLine.add(primaryCell);
        }
        rule = primaryCell != null && rest ? new Rule(id + ".rule") : null;
    }

    /** The button that puts every preference back to the server default, or null when there is nothing to reset. */
    Button reset() {
        return reset;
    }

    /** Disables every control, as while a request waits for the server. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        for (UiNode node : blocks()) {
            node.setEnabled(enabled);
        }
    }

    private List<UiNode> blocks() {
        List<UiNode> blocks = new ArrayList<>();
        for (UiNode node : new UiNode[] {note, mainLine, rule, reset}) {
            if (node != null) {
                blocks.add(node);
            }
        }
        blocks.addAll(rows);
        return blocks;
    }

    /**
     * Adds the panel to {@code flow} as blocks for pages {@code width} wide; {@code tight} closes the checklist's lines
     * to the checkbox's height and narrows the space between sections, for a page short of room.
     */
    void compose(AdaptScreen.Flow flow, UiDraw metrics, int width, boolean tight) {
        int section = tight ? TIGHT_GAP : SECTION_GAP;
        int cell = tight ? TIGHT_CELL : CELL_HEIGHT;
        for (UiNode each : cells) {
            if (each instanceof RequestToggle toggle) {
                toggle.cell = cell;
            } else if (each instanceof ReadOnly shown) {
                shown.cell = cell;
            }
        }
        if (primary != null) {
            primary.cell = cell;
        }
        int gap = AdaptScreen.PAGE_GAP;
        if (note != null) {
            flow.add(note, gap);
            gap = section;
        }
        Checklist checklist = cells.isEmpty() ? null : new Checklist(cells, width, cell, metrics);
        if (mainLine != null) {
            flow.add(mainLine, gap);
            gap = section;
        }
        if (rule != null) {
            flow.rule(rule, section);
        }
        if (checklist != null) {
            for (int start = 0; start < cells.size(); start += checklist.columns) {
                UiGroup line = new UiGroup(id + ".list:" + start / checklist.columns, checklist);
                line.setEnabled(enabled);
                for (UiNode entry : cells.subList(start, Math.min(cells.size(), start + checklist.columns))) {
                    line.add(entry);
                }
                flow.add(line, start == 0 ? gap : tight ? 0 : ROW_SPACING);
            }
            gap = section;
        }
        int lead = 0;
        for (Row row : rows) {
            lead = Math.max(lead, row.lead.preferredWidth(metrics));
        }
        lead = Math.min(width * 2 / 5, lead);
        int beside = 0;
        int under = 0;
        for (Row row : rows) {
            row.leadWidth = lead;
            beside += row.besideHeight(width, metrics);
            under += row.underHeight(width, metrics);
        }
        boolean stacked = under < beside;
        for (Row row : rows) {
            stacked |= !row.fitsBeside(width);
        }
        for (Row row : rows) {
            row.stacked = stacked;
            flow.add(row, gap);
            gap = section;
        }
    }

    private UiNode readOnly(PreferenceView preference, String value, String lockedText) {
        ReadOnly shown = new ReadOnly("pref:" + preference.id(), preference.label(), choiceLabel(preference, value));
        shown.setEnabled(false);
        shown.setTooltip(new Tooltip(preference.label(), List.of("§7" + ScreenText.CURRENT.text(choiceLabel(preference, value)),
                "§6" + (lockedText != null ? lockedText : ScreenText.SERVER_CONTROLLED.text()))));
        return shown;
    }

    private UiNode toggle(PreferenceView preference, String value) {
        RequestToggle toggle = new RequestToggle("pref:" + preference.id(), preference.label(), ON.equals(value),
                (Boolean on) -> actions.set(preference.id(), on ? ON : OFF));
        toggle.setTooltip(new Tooltip(preference.label(), List.of("§7" + ScreenText.CURRENT.text(choiceLabel(preference, value)))));
        return toggle;
    }

    private Row choices(String ownerId, PreferenceView preference, String value, int level) {
        UiGroup tiles = new UiGroup("pref:" + preference.id(), UiLayoutPolicy.flow(TILE_GAP));
        for (PreferenceChoiceView choice : preference.choices()) {
            IconTile tile = tiles.add(new IconTile("pref:" + preference.id() + ":" + choice.value(), UiIcons.choice(choice),
                    choice.label()));
            tile.setSprite(UiIcons.choiceSprite(ownerId, preference.id(), choice));
            tile.setSelected(choice.value().equals(value));
            boolean reachable = choice.minimumLevel() <= level;
            tile.setLocked(!reachable);
            List<String> lines = new ArrayList<>();
            lines.add("§8" + LegacyText.strip(preference.label()));
            if (choice.value().equals(value)) {
                lines.add("§a" + ScreenText.CURRENT.text(LegacyText.strip(choice.label())));
            }
            if (!reachable) {
                lines.add("§c" + ScreenText.CHOICE_LEVEL.text(ScreenText.roman(choice.minimumLevel())));
            }
            tile.setTooltip(new Tooltip(choice.label(), lines));
            if (reachable && !choice.value().equals(value)) {
                tile.setListener(IconTile.Listener.click(() -> actions.set(preference.id(), choice.value())));
            }
        }
        Label lead = new Label(id + ".label." + preference.id(), preference.label()).setWrap(true);
        lead.setTooltip(new Tooltip(preference.label(), List.of("§7" + ScreenText.CURRENT.text(choiceLabel(preference, value)))));
        return new Row(id + ".row." + preference.id(), lead, tiles);
    }

    private Row group(PreferenceGroupView group, List<PreferenceView> preferences, PreferenceOwner owner, String ownerId, PlayerView player,
                         String lockedText) {
        UiGroup tiles = new UiGroup("pref-group:" + group.id() + ".switches", UiLayoutPolicy.flow(TILE_GAP));
        int on = 0;
        for (PreferenceGroupView.Entry entry : group.entries()) {
            PreferenceView member = find(preferences, entry.preferenceId());
            if (member == null) {
                continue;
            }
            String value = CatalogIndex.preference(player, owner, ownerId, member);
            boolean active = ON.equals(value);
            if (active) {
                on++;
            }
            IconSwitch tile = tiles.add(new IconSwitch("pref:" + member.id(), UiIcons.groupMember(ownerId, entry), member.label(), active,
                    (Boolean next) -> actions.set(member.id(), next ? ON : OFF)));
            tile.setValue(choiceLabel(member, value));
            boolean changeable = lockedText == null && editable(member);
            tile.setEnabled(changeable);
            List<String> lines = new ArrayList<>();
            lines.add("§7" + ScreenText.CURRENT.text(choiceLabel(member, value)));
            if (!changeable) {
                lines.add("§6" + (lockedText != null ? lockedText : ScreenText.SERVER_CONTROLLED.text()));
            }
            tile.setTooltip(new Tooltip(member.label(), lines));
        }
        if (tiles.children().isEmpty()) {
            return null;
        }
        GroupHeading heading = new GroupHeading("pref-group:" + group.id(), group, UiIcons.groupHeading(ownerId, group),
                ScreenText.GROUP_ON.text(on, tiles.children().size()));
        heading.setTooltip(new Tooltip(group.label(), List.of("§7" + ScreenText.GROUP_ON.text(on, tiles.children().size()))));
        return new Row(id + ".row.group." + group.id(), heading, tiles);
    }

    private static String choiceLabel(PreferenceView preference, String value) {
        for (PreferenceChoiceView choice : preference.choices()) {
            if (choice.value().equals(value)) {
                return LegacyText.strip(choice.label());
            }
        }
        return value;
    }

    private static PreferenceView find(List<PreferenceView> preferences, String id) {
        for (PreferenceView preference : preferences) {
            if (preference.id().equals(id)) {
                return preference;
            }
        }
        return null;
    }

    /** What the panel asks for; the owner sends it to the server. */
    interface Actions {
        void set(String preferenceId, String value);

        void reset();
    }

    /** A checkbox that asks for the new value instead of taking it, so it changes only when the server agrees. */
    static final class RequestToggle extends Toggle {
        private final Consumer<Boolean> request;
        private int cell = CELL_HEIGHT;

        RequestToggle(String id, String text, boolean on, Consumer<Boolean> request) {
            super(id, text, on, (Boolean ignored) -> {
            });
            this.request = Objects.requireNonNull(request, "request");
            setBoxFirst(true);
        }

        @Override
        public int preferredWidth(UiDraw metrics) {
            return metrics.textWidth(LegacyText.strip(label())) + CELL_GAP + UiTome.CHECK;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return Math.max(cell, wrappedLabelHeight(width, metrics));
        }

        @Override
        public void flip() {
            if (!enabled()) {
                return;
            }
            services().playClick();
            request.accept(!on());
        }
    }

    /** One member of a toggle group: its icon with a small switch, asking for the new value like {@link RequestToggle}. */
    static final class IconSwitch extends IconTile {
        private final Consumer<Boolean> request;
        private final boolean on;
        private String value = "";

        IconSwitch(String id, IconRef icon, String text, boolean on, Consumer<Boolean> request) {
            super(id, icon, text);
            this.on = on;
            this.request = Objects.requireNonNull(request, "request");
            setSprite(UiIcons.sprite(icon));
            setSwitch(on);
            setSelected(on);
            setListener(IconTile.Listener.click(this::flip));
        }

        boolean on() {
            return on;
        }

        void setValue(String shown) {
            value = Objects.requireNonNull(shown, "value");
        }

        @Override
        public String kind() {
            return "switch";
        }

        @Override
        public void describe(Map<String, String> state) {
            super.describe(state);
            state.put("on", Boolean.toString(on));
            state.put("value", value);
            state.put("locked", Boolean.toString(!enabled()));
        }

        void flip() {
            if (!enabled()) {
                return;
            }
            services().playClick();
            request.accept(!on);
        }
    }

    /**
     * Equal columns sharing the page's width, as many as hold a cell of {@link #MIN_CELL} with every name on at most two
     * lines; one line of the checklist is laid out with it, as tall as its tallest cell and never less than
     * {@code least}.
     */
    private static final class Checklist implements UiLayoutPolicy {
        private final int columns;
        private final int cellWidth;
        private final int least;

        private Checklist(List<UiNode> cells, int width, int least, UiDraw metrics) {
            int most = Math.max(1, Math.min(Math.min(MOST_COLUMNS, cells.size()), (width + COLUMN_GAP) / (MIN_CELL + COLUMN_GAP)));
            int chosen = 1;
            for (int count = most; count > 1 && chosen == 1; count--) {
                int cellWidth = (width - (count - 1) * COLUMN_GAP) / count;
                boolean fits = true;
                for (UiNode cell : cells) {
                    fits &= cell.preferredHeight(cellWidth, metrics) <= metrics.lineHeight() * 2 + 2;
                }
                chosen = fits ? count : 1;
            }
            columns = chosen;
            cellWidth = Math.max(1, (width - (columns - 1) * COLUMN_GAP) / columns);
            this.least = least;
        }

        @Override
        public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
            int height = preferredHeight(children, area.width(), metrics);
            for (int index = 0; index < children.size(); index++) {
                children.get(index).place(new UiRect(area.x() + index * (cellWidth + COLUMN_GAP), area.y(), cellWidth, height), metrics);
            }
        }

        @Override
        public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
            int tallest = 0;
            for (UiNode child : children) {
                tallest = Math.max(tallest, Math.max(least, child.preferredHeight(cellWidth, metrics)));
            }
            return tallest;
        }
    }

    /** One control at its own width from the left edge, such as the main switch. */
    private static final class Leading implements UiLayoutPolicy {
        @Override
        public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
            for (UiNode child : children) {
                int width = Math.min(area.width(), child.preferredWidth(metrics));
                child.place(new UiRect(area.x(), area.y(), width, child.preferredHeight(width, metrics)), metrics);
            }
        }

        @Override
        public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
            int tallest = 0;
            for (UiNode child : children) {
                tallest = Math.max(tallest, child.preferredHeight(Math.min(width, child.preferredWidth(metrics)), metrics));
            }
            return tallest;
        }
    }

    /** The thin rule under the main switch. */
    private static final class Rule extends UiNode {
        private Rule(String id) {
            super(id);
        }

        @Override
        public String kind() {
            return "rule";
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return 1;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiRect box = bounds();
            UiTome.rule(frame.draw(), box.x(), box.right(), box.y());
        }
    }

    /**
     * A lead (name or group heading) and a row of tiles: beside the lead, after the width every row's lead takes, or
     * under it, as the panel decides for all its rows at once.
     */
    private static final class Row extends UiGroup {
        private final UiNode lead;
        private final UiGroup tail;
        /** The width every row's lead takes, so the tiles of all rows start in one line. */
        private int leadWidth;
        private boolean stacked;

        private Row(String id, UiNode lead, UiGroup tail) {
            super(id);
            this.lead = add(lead);
            this.tail = add(tail);
        }

        private int tileHeight() {
            return services().theme().tileSize();
        }

        /** Whether one tile at least fits beside the lead. */
        boolean fitsBeside(int width) {
            return leadWidth + LEAD_GAP + tileHeight() <= width;
        }

        int besideHeight(int width, UiDraw metrics) {
            return Math.max(Math.max(tileHeight(), lead.preferredHeight(leadWidth, metrics)),
                    tail.preferredHeight(Math.max(1, width - leadWidth - LEAD_GAP), metrics));
        }

        int underHeight(int width, UiDraw metrics) {
            return lead.preferredHeight(width, metrics) + 2 + tail.preferredHeight(width, metrics);
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return stacked ? underHeight(width, metrics) : besideHeight(width, metrics);
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            if (stacked) {
                int leadHeight = lead.preferredHeight(box.width(), metrics);
                lead.place(new UiRect(box.x(), box.y(), box.width(), leadHeight), metrics);
                int top = box.y() + leadHeight + 2;
                tail.place(UiRect.of(box.x(), top, box.right(), box.bottom()), metrics);
                return;
            }
            int leadHeight = lead.preferredHeight(leadWidth, metrics);
            lead.place(new UiRect(box.x(), box.y() + Math.max(0, (tileHeight() - leadHeight) / 2), leadWidth, leadHeight), metrics);
            tail.place(UiRect.of(box.x() + leadWidth + LEAD_GAP, box.y(), box.right(), box.bottom()), metrics);
        }
    }

    /** A preference the player cannot change: its name, wrapping when long, then its value behind a lock. */
    private static final class ReadOnly extends UiNode {
        private static final int LOCK_WIDTH = 10;
        private static final int GAP = 4;

        private final String text;
        private final String value;
        private int cell = CELL_HEIGHT;

        private ReadOnly(String id, String text, String value) {
            super(id);
            this.text = text;
            this.value = value;
        }

        @Override
        public String kind() {
            return "setting";
        }

        @Override
        public String label() {
            return LegacyText.strip(text);
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("value", value);
            state.put("locked", "true");
        }

        @Override
        public boolean hittable() {
            return true;
        }

        @Override
        public int preferredWidth(UiDraw metrics) {
            return metrics.textWidth(text) + GAP + LOCK_WIDTH + metrics.textWidth(value);
        }

        private int labelRoom(int width, UiDraw metrics) {
            return Math.max(1, width - metrics.textWidth(value) - LOCK_WIDTH - GAP);
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            int room = labelRoom(width, metrics);
            return metrics.textWidth(text) <= room ? cell : metrics.wrappedHeight(text, room) + 2;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            int room = labelRoom(box.width(), draw);
            int textHeight = draw.wrappedHeight(text, room);
            int textY = box.y() + Math.max(0, (box.height() - textHeight) / 2) + 1;
            int valueX = box.right() - draw.textWidth(value);
            int valueY = box.y() + (box.height() - draw.lineHeight()) / 2 + 1;
            draw.text(value, valueX, valueY, frame.theme().textMuted(), false);
            UiTome.lock(draw, valueX - LOCK_WIDTH + 1, valueY);
            draw.wrappedText(text, box.x(), textY, room, frame.theme().textMuted(), false);
        }
    }

    /** A toggle group's icon, name and how many of its switches are on. */
    private static final class GroupHeading extends UiNode {
        private final PreferenceGroupView group;
        private final IconRef icon;
        private final String count;

        private GroupHeading(String id, PreferenceGroupView group, IconRef icon, String count) {
            super(id);
            this.group = group;
            this.icon = icon;
            this.count = count;
        }

        @Override
        public String kind() {
            return "heading";
        }

        @Override
        public String label() {
            return LegacyText.strip(group.label());
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("count", count);
        }

        @Override
        public boolean hittable() {
            return true;
        }

        @Override
        public int preferredWidth(UiDraw metrics) {
            return UiIcons.SIZE + 4 + metrics.textWidth(group.label());
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return Math.max(UiIcons.SIZE, metrics.wrappedHeight(group.label(), Math.max(1, width - UiIcons.SIZE - 4)));
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            UiIcons.paintOn(draw, icon, box.x(), box.y() + (box.height() - UiIcons.SIZE) / 2, UiIcons.SIZE, false,
                    frame.theme().backdrop());
            int room = Math.max(1, box.width() - UiIcons.SIZE - 4);
            int textY = box.y() + Math.max(0, (box.height() - draw.wrappedHeight(group.label(), room)) / 2) + 1;
            draw.wrappedText(group.label(), box.x() + UiIcons.SIZE + 4, textY, room, frame.theme().text(), false);
        }
    }
}
