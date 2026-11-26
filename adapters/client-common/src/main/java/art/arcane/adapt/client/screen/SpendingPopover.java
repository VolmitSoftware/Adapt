package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiMouseButton;
import art.arcane.adapt.client.ui.UiMouseEvent;
import art.arcane.adapt.client.ui.kit.Button;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.ui.kit.Popover;
import art.arcane.adapt.client.ui.kit.ProgressBar;
import art.arcane.adapt.client.ui.kit.ScrollArea;
import art.arcane.adapt.client.ui.kit.Tooltip;
import art.arcane.adapt.client.ui.kit.UiControl;
import art.arcane.adapt.client.ui.kit.UiFade;
import art.arcane.adapt.client.ui.kit.UiFrame;
import art.arcane.adapt.client.ui.kit.UiGroup;
import art.arcane.adapt.client.ui.kit.UiLayoutPolicy;
import art.arcane.adapt.client.ui.kit.UiNode;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.ui.kit.UiTheme;
import art.arcane.adapt.client.ui.kit.UiTome;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.SpendView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Where the player's knowledge and power go, in popovers under the header chips. The knowledge popover shows earned,
 * spent and unspent knowledge, then a row for every seated skill with what it has spent and still has, sorted by seat or
 * by spent; a row's +/- lists each adaptation with its level and the knowledge an unlearn would give back, kept and lent
 * originals marked as free. The power popover lists the adaptations that take a slot. Every row opens its page.
 */
final class SpendingPopover {
    static final String KNOWLEDGE_ID = "knowledge.popover";
    static final String POWER_ID = "power.popover";
    private static final int WIDTH = 230;
    private static final int ROW_GAP = 2;

    private SpendingPopover() {
    }

    /** What the knowledge popover remembers between openings: its sort and which skills are expanded. */
    static final class Order {
        private boolean bySpent;
        private final Set<String> expanded = new HashSet<>();

        boolean bySpent() {
            return bySpent;
        }

        void toggleSort() {
            bySpent = !bySpent;
        }

        boolean expanded(String skillId) {
            return expanded.contains(skillId);
        }

        void toggle(String skillId) {
            if (!expanded.remove(skillId)) {
                expanded.add(skillId);
            }
        }
    }

    /** A skill's line in the breakdown: what it has spent, what it still has, and the adaptations it spent on. */
    record Ledger(SkillView skill, long spent, long available, List<SpendView> spends) {
    }

    /** One ledger per seated skill, in {@code skills} order or, when {@code bySpent}, largest spender first. */
    static List<Ledger> ledgers(List<SkillView> skills, PlayerView player, List<SpendView> spends, boolean bySpent) {
        Map<String, List<SpendView>> bySkill = new LinkedHashMap<>();
        for (SpendView spend : spends) {
            bySkill.computeIfAbsent(spend.skillId(), (String ignored) -> new ArrayList<>()).add(spend);
        }
        List<Ledger> ledgers = new ArrayList<>();
        for (SkillView skill : skills) {
            List<SpendView> own = bySkill.getOrDefault(skill.id(), List.of());
            long spent = 0L;
            for (SpendView spend : own) {
                spent += spend.knowledge();
            }
            SkillLineView line = CatalogIndex.line(player, skill.id());
            ledgers.add(new Ledger(skill, spent, line == null ? 0L : Math.max(0L, line.knowledge()), own));
        }
        if (bySpent) {
            ledgers.sort(Comparator.comparingLong(Ledger::spent).reversed());
        }
        return ledgers;
    }

    static Popover knowledge(ScreenRouter router, List<SkillView> skills, Order order, UiRect anchor, Runnable reopen) {
        PlayerView player = router.state().player();
        List<Ledger> ledgers = ledgers(skills, player, router.state().spends(), order.bySpent());
        long spent = 0L;
        long unspent = 0L;
        long most = 1L;
        for (Ledger ledger : ledgers) {
            spent += ledger.spent();
            unspent += ledger.available();
            most = Math.max(most, ledger.spent());
        }
        UiGroup list = new UiGroup("knowledge.list", UiLayoutPolicy.column(ROW_GAP));
        list.add(new Label("knowledge.totals", ScreenText.EARNED.text(ScreenText.number(spent + unspent)) + "  "
                + ScreenText.SPENT.text(ScreenText.number(spent)) + "  " + ScreenText.UNSPENT.text(ScreenText.number(unspent))).setWrap(true));
        list.add(new Button("knowledge.sort", order.bySpent() ? ScreenText.BY_SPENT.text() : ScreenText.BY_SEAT.text(), Button.Style.QUIET,
                () -> {
                    order.toggleSort();
                    reopen.run();
                }));
        CatalogIndex catalog = router.index();
        for (Ledger ledger : ledgers) {
            String skillId = ledger.skill().id();
            boolean open = order.expanded(skillId);
            UiGroup row = list.add(new UiGroup("ledger:" + skillId, UiLayoutPolicy.row(2)));
            Button expand = row.add(new Button("ledger.expand:" + skillId, open ? UiTome.Glyph.MINUS : UiTome.Glyph.PLUS, () -> {
                order.toggle(skillId);
                reopen.run();
            }));
            expand.setEnabled(!ledger.spends().isEmpty());
            row.add(new LedgerRow("ledger.row:" + skillId, ledger, most, catalog, () -> router.open(new Route.Skill(skillId))));
            if (open) {
                Set<String> listed = new HashSet<>();
                for (SpendView spend : ledger.spends()) {
                    String id = "ledger.spend:" + spend.adaptationId();
                    list.add(new SpendRow(listed.add(id) ? id : id + ":" + spend.source().name().toLowerCase(Locale.ROOT), spend, catalog, false,
                            () -> router.open(new Route.Adaptation(spend.adaptationId()))));
                }
            }
        }
        return new Popover(KNOWLEDGE_ID, ScreenText.KNOWLEDGE_TITLE.text(), anchor, WIDTH, new ScrollArea("knowledge.scroll", list));
    }

    static Popover power(ScreenRouter router, List<SkillView> skills, UiRect anchor) {
        PlayerView player = router.state().player();
        CatalogIndex catalog = router.index();
        int max = player.header().powerMax();
        int used = Math.max(0, max - LearnRules.freePower(player));
        UiGroup list = new UiGroup("power.list", UiLayoutPolicy.column(ROW_GAP));
        list.add(new Label("power.totals", ScreenText.POWER_SLOTS.text(used, max)));
        for (Ledger ledger : ledgers(skills, player, router.state().spends(), false)) {
            for (SpendView spend : ledger.spends()) {
                if (spend.power() > 0) {
                    list.add(new SpendRow("power.spend:" + spend.adaptationId(), spend, catalog, true,
                            () -> router.open(new Route.Adaptation(spend.adaptationId()))));
                }
            }
        }
        return new Popover(POWER_ID, ScreenText.POWER_TITLE.text(), anchor, WIDTH, new ScrollArea("power.scroll", list));
    }

    /** A clickable line that opens a page: hover inks it, focus rings it. */
    private abstract static class OpenRow extends UiNode {
        private final Runnable open;
        private final UiFade hover = new UiFade(UiControl.HOVER_MILLIS);

        OpenRow(String id, Runnable open) {
            super(id);
            this.open = Objects.requireNonNull(open, "open");
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
            return event.button() == UiMouseButton.LEFT && activate();
        }

        @Override
        public boolean activate() {
            services().playClick();
            open.run();
            return true;
        }

        @Override
        protected void paint(UiFrame frame) {
            float lift = hover.update(frame.motion(), frame.isHovered(this) || frame.showsFocus(this));
            UiRect box = bounds();
            if (lift > 0.0F) {
                UiTome.filigree(frame.draw(), box, lift);
            }
            if (frame.showsFocus(this)) {
                UiControl.focusRing(frame.draw(), box, frame.theme());
            }
            paintRow(frame.draw(), frame.theme(), box);
        }

        abstract void paintRow(UiDraw draw, UiTheme theme, UiRect box);
    }

    /** A skill's line: its name and spent knowledge, then a bar of its share of the largest spender and what it still has. */
    private static final class LedgerRow extends OpenRow {
        private static final int HEIGHT = 20;
        private static final int BAR_TOP = 11;
        private static final int AVAILABLE_ROOM = 40;
        private final Ledger ledger;
        private final long most;
        private final int accent;

        LedgerRow(String id, Ledger ledger, long most, CatalogIndex catalog, Runnable open) {
            super(id, open);
            this.ledger = ledger;
            this.most = most;
            accent = Palette.accent(ledger.skill().color());
            List<String> lines = new ArrayList<>();
            for (SpendView spend : ledger.spends()) {
                lines.add("§7" + spendText(catalog, spend) + " §8" + costText(spend));
            }
            lines.add("§8" + ScreenText.SPENT.text(ScreenText.number(ledger.spent())) + " · "
                    + ScreenText.UNSPENT.text(ScreenText.number(ledger.available())));
            setTooltip(new Tooltip(ledger.skill().name(), lines));
        }

        @Override
        public String kind() {
            return "ledger";
        }

        @Override
        public String label() {
            return LegacyText.strip(ledger.skill().name());
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("spent", Long.toString(ledger.spent()));
            state.put("available", Long.toString(ledger.available()));
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return HEIGHT;
        }

        @Override
        void paintRow(UiDraw draw, UiTheme theme, UiRect box) {
            String spent = ScreenText.number(ledger.spent());
            int right = box.right() - 2;
            draw.text(spent, right - draw.textWidth(spent), box.y() + 2, theme.text(), false);
            String name = LegacyText.truncate(draw, ledger.skill().name(), right - draw.textWidth(spent) - 4 - (box.x() + 2));
            draw.text(name, box.x() + 2, box.y() + 2, accent, false);
            String available = "+" + ScreenText.number(ledger.available());
            draw.text(available, right - draw.textWidth(available), box.y() + BAR_TOP, theme.textMuted(), false);
            ProgressBar.framed(draw, theme, box.x() + 2, box.y() + BAR_TOP + 1, right - AVAILABLE_ROOM, 5,
                    (float) ledger.spent() / (float) most, accent);
        }
    }

    /** One adaptation: its name and level, and the knowledge it cost, or that it is kept or lent for free. */
    private static final class SpendRow extends OpenRow {
        private static final int INDENT = 14;
        private final SpendView spend;
        private final String text;
        private final String cost;

        SpendRow(String id, SpendView spend, CatalogIndex catalog, boolean withSkill, Runnable open) {
            super(id, open);
            this.spend = spend;
            String name = spendText(catalog, spend);
            SkillView skill = catalog == null ? null : catalog.skill(spend.skillId());
            text = withSkill && skill != null ? name + " · " + skill.name() : name;
            cost = withSkill ? "" : costText(spend);
        }

        @Override
        public String kind() {
            return "spend";
        }

        @Override
        public String label() {
            return LegacyText.strip(text);
        }

        @Override
        public void describe(Map<String, String> state) {
            state.put("knowledge", Integer.toString(spend.knowledge()));
            state.put("source", spend.source().name());
        }

        @Override
        public int preferredHeight(int width, UiDraw metrics) {
            return metrics.lineHeight() + 3;
        }

        @Override
        void paintRow(UiDraw draw, UiTheme theme, UiRect box) {
            int right = box.right() - 2;
            int costWidth = draw.textWidth(cost);
            draw.text(cost, right - costWidth, box.y() + 2, spend.source() == SpendView.Source.LEARNED ? theme.text() : theme.textMuted(), false);
            int left = box.x() + INDENT;
            draw.text(LegacyText.truncate(draw, text, right - costWidth - 4 - left), left, box.y() + 2, theme.text(), false);
        }
    }

    private static String spendText(CatalogIndex catalog, SpendView spend) {
        String name = catalog == null ? spend.adaptationId() : catalog.adaptationName(spend.adaptationId());
        return name + " " + ScreenText.roman(spend.level());
    }

    private static String costText(SpendView spend) {
        return switch (spend.source()) {
            case LEARNED -> ScreenText.number(spend.knowledge());
            case KEPT -> ScreenText.KEPT_FREE.text();
            case LENT -> ScreenText.LENT_FREE.text();
        };
    }
}
