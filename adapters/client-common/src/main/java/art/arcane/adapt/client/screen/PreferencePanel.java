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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The preferences of one skill or adaptation as an aligned grid. The {@code enabled} switch sits alone at the top
 * above a rule; the other on/off preferences form a checklist of equal columns as wide as the longest name, one row
 * height, name on the left and checkbox on the right of each cell; other preferences are a row of icon choices after
 * their name (choices above the player's level locked); toggle groups are their heading and a row of icon checkboxes.
 * Reset sits quietly at the right: on the main switch's line when nothing else follows it or the panel shares its
 * page with other content, and the line has room, else on a line of its own held against the foot of the page. Preferences the player cannot change show
 * read-only with a lock, under a note that says why when the whole panel is locked. Nothing changes on screen until the server's answer arrives; the owner
 * disables the panel while a request is out. Widget ids: {@code pref:<id>}, {@code pref:<id>:<value>},
 * {@code pref-group:<id>}, {@code pref-reset}.
 */
final class PreferencePanel extends UiGroup {
    static final String ON = "ON";
    static final String OFF = "OFF";
    static final String PRIMARY = "enabled";
    static final int CELL_HEIGHT = 12;
    static final int COLUMN_GAP = 10;
    static final int CELL_GAP = 6;
    static final int ROW_SPACING = 1;
    private static final int SECTION_GAP = 4;
    private static final int LEAD_GAP = 6;
    private static final int TILE_GAP = 2;
    private static final int MAX_LEAD = 150;
    private static final int MAX_NATURAL = 420;
    private static final int NATURAL_ROWS = 10;
    private static final int MAX_EXTRA_GAP = 16;
    /** The most the checklist's rows spread apart to take up a page taller than the panel needs. */
    private static final int MAX_ROW_STRETCH = 5;

    private final Actions actions;
    private final BooleanSupplier ownPage;
    private UiGroup checklist = new UiGroup("list");
    private UiNode primary;
    private Button reset;
    private UiGroup mainLine;
    private UiGroup foot;
    private int leadWidth;
    private int rowStretch;

    /** {@code ownPage} says whether the panel has a page to itself, where reset keeps to the foot. */
    PreferencePanel(String id, Actions actions, BooleanSupplier ownPage) {
        super(id, UiLayoutPolicy.column(SECTION_GAP));
        this.actions = Objects.requireNonNull(actions, "actions");
        this.ownPage = Objects.requireNonNull(ownPage, "ownPage");
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
        clearChildren();
        boolean locked = lockedText != null;
        if (locked) {
            add(new Label(id() + ".locked", lockedText).setColor(Palette.LOCK).setWrap(true));
        }
        Set<String> grouped = new HashSet<>();
        for (PreferenceGroupView group : groups) {
            for (PreferenceGroupView.Entry entry : group.entries()) {
                grouped.add(entry.preferenceId());
            }
        }
        primary = null;
        checklist = new UiGroup(id() + ".list", new Checklist());
        List<UiNode> rows = new ArrayList<>();
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
            if (primary == null && PRIMARY.equals(preference.id()) && isToggle(preference)) {
                primary = cell;
            } else {
                checklist.add(cell);
            }
        }
        for (PreferenceGroupView group : groups) {
            UiNode row = group(group, preferences, owner, ownerId, player, lockedText);
            if (row != null) {
                rows.add(row);
            }
        }
        boolean rest = !checklist.children().isEmpty() || !rows.isEmpty();
        boolean anyEditable = false;
        for (PreferenceView preference : preferences) {
            anyEditable |= editable(preference);
        }
        reset = null;
        mainLine = null;
        foot = null;
        if (!locked && anyEditable) {
            reset = new Button("pref-reset", ScreenText.RESET.text(), Button.Style.QUIET, actions::reset);
            reset.setTooltip(new Tooltip(ScreenText.RESET.text(), List.of("§7" + ScreenText.RESET_TIP.text())));
        }
        if (primary != null) {
            mainLine = add(new UiGroup(id() + ".main", new Ends(0)));
            mainLine.add(primary);
            if (rest) {
                add(new Rule(id() + ".rule"));
            }
        }
        if (!checklist.children().isEmpty()) {
            add(checklist);
        }
        for (UiNode row : rows) {
            add(row);
        }
        if (reset != null) {
            if (mainLine == null || rest) {
                foot = add(new UiGroup(id() + ".reset", new Ends(AdaptScreen.BAR)));
                foot.add(reset);
            } else {
                mainLine.add(reset);
            }
        }
        setLayout(foot == null ? UiLayoutPolicy.column(SECTION_GAP) : UiLayoutPolicy.footed(SECTION_GAP, foot));
        seatReset();
    }

    /**
     * Puts reset where the page has room for it: at the foot of a page the panel has to itself, and at the end of
     * the main switch's line when the panel follows other content down one page, which saves that page a row.
     */
    private void seatReset() {
        if (reset == null || mainLine == null || foot == null) {
            return;
        }
        UiGroup seat = ownPage.getAsBoolean() ? foot : mainLine;
        if (reset.parent() != seat) {
            (seat == foot ? mainLine : foot).remove(reset);
            seat.add(reset);
        }
        foot.setVisible(seat == foot);
    }

    /** The width the controls would like: the checklist in about {@value #NATURAL_ROWS} rows, capped. */
    int naturalWidth(UiDraw metrics) {
        return width(metrics, false);
    }

    /** The narrowest the controls stay uncramped: every row's tiles beside its name and the checklist in a single column. */
    int leastWidth(UiDraw metrics) {
        return width(metrics, true);
    }

    private int width(UiDraw metrics, boolean least) {
        int widest = 0;
        int lead = lead(metrics);
        for (UiNode child : children()) {
            if (child instanceof Row row) {
                int tail = row.tailWidth(metrics);
                widest = Math.max(widest, lead + LEAD_GAP + (least ? Math.min(tail, services().theme().tileSize() * 4) : tail));
            } else if (child == checklist) {
                List<UiNode> cells = checklist.children();
                int columns = least ? 1 : (cells.size() + NATURAL_ROWS - 1) / NATURAL_ROWS;
                widest = Math.max(widest, columns * Checklist.cellWidth(cells, metrics) + (columns - 1) * COLUMN_GAP);
            } else if (child instanceof UiGroup line && !(child instanceof Row)) {
                int width = 0;
                for (UiNode item : line.children()) {
                    int own = item.preferredWidth(metrics);
                    width = least ? Math.max(width, own) : width + (width == 0 ? 0 : LEAD_GAP * 2) + own;
                }
                widest = Math.max(widest, width);
            }
        }
        return Math.min(MAX_NATURAL, widest);
    }

    private int lead(UiDraw metrics) {
        int widest = 0;
        for (UiNode child : children()) {
            if (child instanceof Row row) {
                widest = Math.max(widest, row.lead.preferredWidth(metrics));
            }
        }
        return Math.min(MAX_LEAD, widest);
    }

    @Override
    public int preferredHeight(int width, UiDraw metrics) {
        leadWidth = lead(metrics);
        seatReset();
        int held = rowStretch;
        rowStretch = 0;
        int height = super.preferredHeight(width, metrics);
        rowStretch = held;
        return height;
    }

    /**
     * Lays the panel out with the checklist's rows spread to take up spare height, and the main switch as wide as a
     * checklist column so every checkbox stands in one line down the page.
     */
    @Override
    protected void layout(UiDraw metrics) {
        leadWidth = lead(metrics);
        seatReset();
        rowStretch = 0;
        List<UiNode> cells = checklist.children();
        int width = bounds().width();
        if (!cells.isEmpty()) {
            int columns = Checklist.columns(cells, width, metrics);
            int rows = (cells.size() + columns - 1) / columns;
            int spare = bounds().height() - super.preferredHeight(width, metrics);
            if (rows > 1 && spare > 0) {
                rowStretch = Math.min(MAX_ROW_STRETCH, spare / (rows - 1));
            }
        }
        super.layout(metrics);
        if (primary != null && !cells.isEmpty()) {
            int column = Checklist.columnWidth(cells, width, Checklist.columns(cells, width, metrics), metrics);
            UiRect box = primary.bounds();
            primary.place(new UiRect(box.x(), box.y(), Math.max(box.width(), column), box.height()), metrics);
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

    private UiNode choices(String ownerId, PreferenceView preference, String value, int level) {
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
        Label lead = new Label(id() + ".label." + preference.id(), preference.label()).setWrap(true);
        lead.setTooltip(new Tooltip(preference.label(), List.of("§7" + ScreenText.CURRENT.text(choiceLabel(preference, value)))));
        return new Row(id() + ".row." + preference.id(), lead, tiles);
    }

    private UiNode group(PreferenceGroupView group, List<PreferenceView> preferences, PreferenceOwner owner, String ownerId, PlayerView player,
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
        return new Row(id() + ".row.group." + group.id(), heading, tiles);
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

        RequestToggle(String id, String text, boolean on, Consumer<Boolean> request) {
            super(id, text, on, (Boolean ignored) -> {
            });
            this.request = Objects.requireNonNull(request, "request");
        }

        @Override
        public int preferredWidth(UiDraw metrics) {
            return metrics.textWidth(LegacyText.strip(label())) + CELL_GAP + UiTome.CHECK;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return Math.max(CELL_HEIGHT, wrappedLabelHeight(width, metrics));
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
     * Equal columns as wide as the widest cell, as many as fit side by side, filled row by row, with the room left over
     * shared out between them up to a limit; every row is as tall as the tallest cell, so names line up and no cell meets
     * another.
     */
    final class Checklist implements UiLayoutPolicy {
        static int cellWidth(List<UiNode> cells, UiDraw metrics) {
            int widest = 0;
            for (UiNode cell : cells) {
                widest = Math.max(widest, cell.preferredWidth(metrics));
            }
            return widest;
        }

        static int columns(List<UiNode> cells, int width, UiDraw metrics) {
            int cell = Math.max(1, cellWidth(cells, metrics));
            return Math.max(1, Math.min(cells.size(), (width + COLUMN_GAP) / (cell + COLUMN_GAP)));
        }

        static int columnWidth(List<UiNode> cells, int width, int columns, UiDraw metrics) {
            return Math.max(1, Math.min(cellWidth(cells, metrics), (width - (columns - 1) * COLUMN_GAP) / columns));
        }

        private static int gap(int width, int columns, int columnWidth) {
            if (columns < 2) {
                return COLUMN_GAP;
            }
            int left = width - columns * columnWidth - (columns - 1) * COLUMN_GAP;
            return COLUMN_GAP + Math.max(0, Math.min(MAX_EXTRA_GAP, left / (columns - 1)));
        }

        private static int rowHeight(List<UiNode> cells, int columnWidth, UiDraw metrics) {
            int tallest = CELL_HEIGHT;
            for (UiNode cell : cells) {
                tallest = Math.max(tallest, cell.preferredHeight(columnWidth, metrics));
            }
            return tallest;
        }

        @Override
        public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
            if (children.isEmpty()) {
                return;
            }
            int columns = columns(children, area.width(), metrics);
            int width = columnWidth(children, area.width(), columns, metrics);
            int height = rowHeight(children, width, metrics);
            int gap = gap(area.width(), columns, width);
            for (int index = 0; index < children.size(); index++) {
                int column = index % columns;
                int row = index / columns;
                children.get(index).place(new UiRect(area.x() + column * (width + gap),
                        area.y() + row * (height + ROW_SPACING + rowStretch), width, height), metrics);
            }
        }

        @Override
        public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
            if (children.isEmpty()) {
                return 0;
            }
            int columns = columns(children, width, metrics);
            int rows = (children.size() + columns - 1) / columns;
            return rows * rowHeight(children, columnWidth(children, width, columns, metrics), metrics)
                    + (rows - 1) * (ROW_SPACING + rowStretch);
        }
    }

    /**
     * The reset button against the right edge, anything before it at its own width from the left edge; the button
     * drops to a line of its own when the two do not fit side by side. A line at least {@code minHeight} tall centres
     * what it holds, so a reset at the foot of a page sits on the line of the action row across the spine.
     */
    private record Ends(int minHeight) implements UiLayoutPolicy {
        private static int lead(List<UiNode> children, int width, UiDraw metrics) {
            int tallest = 0;
            for (UiNode child : children) {
                if (!(child instanceof Button)) {
                    tallest = Math.max(tallest, child.preferredHeight(Math.min(width, child.preferredWidth(metrics)), metrics));
                }
            }
            return tallest;
        }

        private static boolean shared(List<UiNode> children, int width, UiDraw metrics) {
            int total = 0;
            for (UiNode child : children) {
                total += (total == 0 ? 0 : LEAD_GAP * 2) + child.preferredWidth(metrics);
            }
            return total <= width;
        }

        @Override
        public void layout(List<UiNode> children, UiRect area, UiDraw metrics) {
            boolean shared = shared(children, area.width(), metrics);
            int lead = lead(children, area.width(), metrics);
            int line = minHeight;
            for (UiNode child : children) {
                if (shared || !(child instanceof Button)) {
                    line = Math.max(line, child.preferredHeight(Math.min(area.width(), child.preferredWidth(metrics)), metrics));
                }
            }
            int x = area.x();
            for (UiNode child : children) {
                int width = Math.min(area.width(), child.preferredWidth(metrics));
                int height = child.preferredHeight(width, metrics);
                boolean end = child instanceof Button;
                int y = end && !shared && lead > 0 ? area.y() + lead + ROW_SPACING : area.y() + Math.max(0, (line - height) / 2);
                child.place(new UiRect(end ? area.right() - width : x, y, width, height), metrics);
                x += width + LEAD_GAP;
            }
        }

        @Override
        public int preferredHeight(List<UiNode> children, int width, UiDraw metrics) {
            int lead = lead(children, width, metrics);
            int end = 0;
            for (UiNode child : children) {
                if (child instanceof Button) {
                    end = Math.max(end, child.preferredHeight(width, metrics));
                }
            }
            return shared(children, width, metrics) || lead == 0 || end == 0 ? Math.max(minHeight, Math.max(lead, end)) : lead + ROW_SPACING + end;
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

    /** A lead (name or group heading) and a row of tiles; the tiles drop below the lead when the row is too narrow. */
    private final class Row extends UiGroup {
        private final UiNode lead;
        private final UiGroup tail;

        private Row(String id, UiNode lead, UiGroup tail) {
            super(id);
            this.lead = add(lead);
            this.tail = add(tail);
        }

        int tailWidth(UiDraw metrics) {
            int width = 0;
            for (UiNode tile : tail.children()) {
                width += (width == 0 ? 0 : TILE_GAP) + tile.preferredWidth(metrics);
            }
            return width;
        }

        private boolean stacked(int width, UiDraw metrics) {
            return leadWidth + LEAD_GAP + Math.min(tailWidth(metrics), services().theme().tileSize() * 4) > width;
        }

        private int tileHeight() {
            return services().theme().tileSize();
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            if (stacked(width, metrics)) {
                return lead.preferredHeight(width, metrics) + 2 + tail.preferredHeight(width, metrics);
            }
            return Math.max(Math.max(tileHeight(), lead.preferredHeight(leadWidth, metrics)),
                    tail.preferredHeight(width - leadWidth - LEAD_GAP, metrics));
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            if (stacked(box.width(), metrics)) {
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
            return metrics.textWidth(text) <= room ? CELL_HEIGHT : metrics.wrappedHeight(text, room) + 2;
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
            draw.wrappedText(group.label(), box.x() + UiIcons.SIZE + 4, textY, room, frame.theme().textMuted(), true);
        }
    }
}
