package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.LegacyText;
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
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.MutationCandidate;
import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.SkillView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The blocks each page of the mutation spread is made of. */
final class SpreadParts {
    /** Space between medallions in a row. */
    static final int TOKEN_GAP = 2;
    /** The candidates' plates: an icon with a unit of plate round it. */
    static final int PLATE = UiIcons.SIZE + 2;
    static final int PLATE_GAP = 1;

    private SpreadParts() {
    }

    /** Holds a page's place for a block drawn across both pages, at a fixed height. */
    static final class Spacer extends UiNode {
        private int height;

        Spacer(String id, int height) {
            super(id);
            this.height = height;
        }

        void setHeight(int newHeight) {
            height = newHeight;
        }

        @Override
        public String kind() {
            return "spacer";
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return height;
        }
    }

    /**
     * A label and one line of medallions after it. Entries the line has no room for collapse into a last medallion
     * that counts them and names them in its tooltip, so the row never wraps.
     */
    static final class TokenRow extends UiNode {
        private static final int LABEL_GAP = 4;

        private String label = "";
        private int labelColor;
        private final List<Item> items = new ArrayList<>();

        private record Item(MutationEntry entry, SpreadToken.Look look) {
        }

        TokenRow(String id) {
            super(id);
        }

        void setLabel(String text, int color) {
            label = text;
            labelColor = color;
        }

        void clear() {
            items.clear();
        }

        void add(List<MutationEntry> entries, SpreadToken.Look look) {
            for (MutationEntry entry : entries) {
                items.add(new Item(entry, look));
            }
        }

        boolean empty() {
            return items.isEmpty();
        }

        @Override
        public String kind() {
            return "token-row";
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return UiTome.TOKEN;
        }

        private int labelWidth(UiDraw metrics) {
            return label.isEmpty() ? 0 : metrics.textWidth(label) + LABEL_GAP;
        }

        @Override
        protected void layout(UiDraw metrics) {
            clearChildren();
            UiRect box = bounds();
            int from = box.x() + labelWidth(metrics);
            int room = Math.max(0, (box.right() - from + TOKEN_GAP) / (UiTome.TOKEN + TOKEN_GAP));
            int shown = items.size() <= room ? items.size() : Math.max(0, room - 1);
            for (int index = 0; index < shown; index++) {
                Item item = items.get(index);
                SpreadToken token = add(new SpreadToken(id() + ":" + item.entry().id()).show(item.entry(), item.look()));
                token.place(new UiRect(from + index * (UiTome.TOKEN + TOKEN_GAP), box.y(), UiTome.TOKEN, UiTome.TOKEN), metrics);
            }
            if (shown < items.size() && room > 0) {
                List<String> names = new ArrayList<>();
                for (Item item : items.subList(shown, items.size())) {
                    names.add(item.entry().name());
                }
                SpreadToken more = add(new SpreadToken(id() + ":more").more(names));
                more.place(new UiRect(from + shown * (UiTome.TOKEN + TOKEN_GAP), box.y(), UiTome.TOKEN, UiTome.TOKEN), metrics);
            }
        }

        @Override
        protected void paint(UiFrame frame) {
            if (label.isEmpty()) {
                return;
            }
            UiDraw draw = frame.draw();
            UiRect box = bounds();
            draw.text(label, box.x(), box.y() + (box.height() - draw.lineHeight()) / 2 + 1, labelColor, false);
        }
    }

    /**
     * A parent's line: its icon, its name in its colour, its role, and its level against the right edge; an empty
     * medallion and the role alone before a partner is picked. When {@code onOpen} is set the line opens the partner
     * list and shows a mark that it does.
     */
    static final class ParentRow extends UiNode {
        private static final int GAP = 4;

        private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
        private IconRef icon;
        private String name = "";
        private int color;
        private String role = "";
        private String level = "";
        private Runnable onOpen;

        ParentRow(String id) {
            super(id);
        }

        void set(IconRef newIcon, String newName, int newColor, String newRole, String newLevel) {
            icon = newIcon;
            name = newName;
            color = newColor;
            role = newRole;
            level = newLevel;
        }

        void setOpener(Runnable opener) {
            onOpen = opener;
        }

        @Override
        public String kind() {
            return "parent";
        }

        @Override
        public String label() {
            return LegacyText.strip(name.isEmpty() ? role : name);
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("role", role);
            state.put("level", level);
            state.put("opens", Boolean.toString(onOpen != null));
        }

        @Override
        public boolean focusable() {
            return onOpen != null;
        }

        @Override
        public boolean hittable() {
            return onOpen != null;
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return UiIcons.SIZE + 2;
        }

        @Override
        public boolean mousePressed(UiMouseEvent event) {
            return activate();
        }

        @Override
        public boolean activate() {
            if (onOpen == null) {
                return false;
            }
            onOpen.run();
            return true;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            float lift = hover.update(frame.motion(), onOpen != null && (frame.isHovered(this) || frame.showsFocus(this)));
            if (lift > 0.0F) {
                UiTome.soft(draw, box.x() - 1, box.y(), box.right() + 1, box.bottom(), UiTome.HOVER);
            }
            int textY = box.y() + (box.height() - draw.lineHeight()) / 2 + 1;
            int x = box.x() + 1;
            if (icon != null) {
                UiIcons.paintOn(draw, icon, x, box.y() + 1, UiIcons.SIZE, false, theme.backdrop());
            } else {
                UiTome.soft(draw, x, box.y() + 1, x + UiIcons.SIZE, box.y() + 1 + UiIcons.SIZE, theme.tileBorder());
            }
            x += UiIcons.SIZE + GAP;
            int right = box.right() - 1;
            if (onOpen != null) {
                UiTome.glyph(draw, UiTome.Glyph.MORE, right - 8, textY + 2, theme.textMuted());
                right -= 8 + GAP;
            }
            int levelWidth = draw.textWidth(level);
            if (!level.isEmpty()) {
                draw.text(level, right - levelWidth, textY, theme.text(), false);
                right -= levelWidth + GAP * 2;
            }
            if (!name.isEmpty()) {
                String shownName = LegacyText.truncate(draw, name, Math.max(0, right - x - draw.textWidth(role) - GAP));
                draw.text(shownName, x, textY, color, false);
                x += draw.textWidth(shownName) + GAP;
            }
            draw.text(LegacyText.truncate(draw, role, Math.max(0, right - x)), x, textY, theme.textMuted(), false);
            if (frame.showsFocus(this)) {
                UiControl.focusRing(draw, box, theme);
            }
        }
    }

    /** The skills that could join the lead, as a grid of icon plates; the picked one sits on a gilt medallion. */
    static final class CandidateGrid extends UiNode {
        private final Consumer<String> pick;
        private List<CandidateTile> tiles = List.of();

        CandidateGrid(String id, Consumer<String> pick) {
            super(id);
            this.pick = pick;
        }

        /** Shows {@code candidates}, each with its icon and name from {@code index}, {@code picked} marked. */
        void set(List<MutationCandidate> candidates, CatalogIndex index, String picked, String levelFormat) {
            List<CandidateTile> next = new ArrayList<>();
            for (MutationCandidate candidate : candidates) {
                CandidateTile tile = (CandidateTile) find(id() + ":" + candidate.skillId());
                if (tile == null) {
                    tile = new CandidateTile(id() + ":" + candidate.skillId(), pick);
                }
                tile.update(candidate, index, candidate.skillId().equals(picked), levelFormat);
                next.add(tile);
            }
            clearChildren();
            for (CandidateTile tile : next) {
                add(tile);
            }
            tiles = next;
        }

        private static int perRow(int width) {
            return Math.max(1, (width + PLATE_GAP) / (PLATE + PLATE_GAP));
        }

        @Override
        public String kind() {
            return "candidates";
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            int rows = Math.max(1, (tiles.size() + perRow(width) - 1) / perRow(width));
            return rows * PLATE + (rows - 1) * PLATE_GAP;
        }

        @Override
        protected void layout(UiDraw metrics) {
            UiRect box = bounds();
            int perRow = perRow(box.width());
            for (int index = 0; index < tiles.size(); index++) {
                int column = index % perRow;
                int row = index / perRow;
                tiles.get(index).place(new UiRect(box.x() + column * (PLATE + PLATE_GAP), box.y() + row * (PLATE + PLATE_GAP), PLATE, PLATE),
                        metrics);
            }
        }
    }

    /** One skill in the partner grid: dimmed with its reason in the tooltip when it cannot join. */
    static final class CandidateTile extends UiNode {
        private static final int DIM = 0xA8E8DCC0;

        private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);
        private final Consumer<String> pick;
        private String skillId = "";
        private IconRef icon;
        private boolean eligible;
        private boolean picked;

        CandidateTile(String id, Consumer<String> pick) {
            super(id);
            this.pick = pick;
        }

        void update(MutationCandidate candidate, CatalogIndex index, boolean chosen, String levelFormat) {
            skillId = candidate.skillId();
            eligible = candidate.eligible();
            picked = chosen;
            SkillView skill = index == null ? null : index.skill(skillId);
            icon = skill == null ? null : UiIcons.skill(skill);
            List<String> lines = new ArrayList<>();
            lines.add("§7" + levelFormat.replace("{level}", Integer.toString(candidate.level())));
            if (!candidate.reason().isEmpty()) {
                lines.add("§c" + candidate.reason());
            }
            setTooltip(new Tooltip(skill == null ? skillId : skill.name(), lines));
        }

        @Override
        public String kind() {
            return "candidate";
        }

        @Override
        public String label() {
            return tooltip() == null ? skillId : LegacyText.strip(tooltip().title());
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("skill", skillId);
            state.put("eligible", Boolean.toString(eligible));
            state.put("picked", Boolean.toString(picked));
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
        public boolean mousePressed(UiMouseEvent event) {
            return activate();
        }

        @Override
        public boolean activate() {
            if (!eligible || picked) {
                return false;
            }
            services().playClick();
            pick.accept(skillId);
            return true;
        }

        @Override
        protected void paint(UiFrame frame) {
            UiDraw draw = frame.draw();
            UiTheme theme = frame.theme();
            UiRect box = bounds();
            float lift = hover.update(frame.motion(), eligible && (frame.isHovered(this) || frame.showsFocus(this)));
            if (picked) {
                UiTome.soft(draw, box.x() - 1, box.y() - 1, box.right() + 1, box.bottom() + 1, UiTome.GILT);
            }
            UiTome.soft(draw, box.x(), box.y(), box.right(), box.bottom(), picked ? theme.tile() : UiEase.mix(theme.surface(), theme.surfaceHover(), lift));
            if (icon != null) {
                UiIcons.paint(draw, icon, box.x() + 1, box.y() + 1, UiIcons.SIZE, false);
            }
            if (!eligible) {
                draw.fill(box.x() + 1, box.y() + 1, box.right() - 1, box.bottom() - 1, DIM);
            }
            if (frame.showsFocus(this)) {
                UiControl.focusRing(draw, box, theme);
            }
        }
    }
}
