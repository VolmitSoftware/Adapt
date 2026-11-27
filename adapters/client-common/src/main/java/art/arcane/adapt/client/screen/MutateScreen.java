package art.arcane.adapt.client.screen;


import art.arcane.adapt.client.protocol.FeedbackKind;
import art.arcane.adapt.client.protocol.Route;
import art.arcane.adapt.client.state.ClientSubscription;
import art.arcane.adapt.client.ui.UiDraw;
import art.arcane.adapt.client.ui.UiKey;
import art.arcane.adapt.client.ui.UiKeyEvent;
import art.arcane.adapt.client.ui.kit.Label;
import art.arcane.adapt.client.ui.kit.UiIcons;
import art.arcane.adapt.client.ui.kit.UiRect;
import art.arcane.adapt.client.view.MutationEntry;
import art.arcane.adapt.client.view.MutationSide;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationWords;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.ToolView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The mutation spread: the lead on the left page and the partner on the right, each under the name of what they become
 * (large, across the fold, with everything the mutation gives under it) and above the hold control, centred on the
 * fold at the foot and saying the mutation cannot be undone. The lead swap sits on the spine just above it, between the
 * two parents; on a single page it sits beside the hold. The book wears the mutated skill's blended theme once a partner is picked. Each page lists
 * what its parent brings (the originals it keeps, and before a partner is picked its upgrades and tools) and what it
 * loses (adaptations unlearned and originals that go away, and for the higher parent its
 * surplus levels and knowledge). The partner page holds the skills that could join; on a page too short for both the
 * list folds away behind the partner's line once one is picked. On a single page, page 1 is the lead and page 2 the
 * partner, with the name and the controls on both. Holding the control for {@link HoldToConfirm#HOLD_MILLIS} asks the
 * server to merge; when it answers that it did, the halves merge into the spine and the new skill's page opens.
 */
public final class MutateScreen extends AdaptScreen {
    /** Flow densities: everything on the pages, the partner list folded away once picked, and a compact head. */
    private static final int ROOMY = 0;
    private static final int FOLDED = 1;
    private static final int COMPACT = 2;
    private static final int SWAP_GAP = 2;

    private final MutationDraft draft;
    private final HoldToConfirm gesture = new HoldToConfirm();
    private final SpreadParts.Spacer leadTop = new SpreadParts.Spacer("top:lead", 0);
    private final SpreadParts.Spacer partnerTop = new SpreadParts.Spacer("top:partner", 0);
    private final SpreadParts.Spacer leadFoot = new SpreadParts.Spacer("foot:lead", 0);
    private final SpreadParts.Spacer partnerFoot = new SpreadParts.Spacer("foot:partner", 0);
    private final SpreadParts.ParentRow leadRow = new SpreadParts.ParentRow("parent:lead");
    private final SpreadParts.ParentRow partnerRow = new SpreadParts.ParentRow("parent:partner");
    private final SpreadParts.TokenRow leadGains = new SpreadParts.TokenRow("gains:lead");
    private final SpreadParts.TokenRow leadLoses = new SpreadParts.TokenRow("loses:lead");
    private final SpreadParts.TokenRow partnerGains = new SpreadParts.TokenRow("gains:partner");
    private final SpreadParts.TokenRow partnerLoses = new SpreadParts.TokenRow("loses:partner");
    private final Label leadSurplus = new Label("surplus:lead", "").setWrap(true).setColor(Palette.LOCK);
    private final Label partnerSurplus = new Label("surplus:partner", "").setWrap(true).setColor(Palette.LOCK);
    private final SpreadParts.CandidateGrid candidates;
    private final SpreadBanner banner = new SpreadBanner("banner");
    private final SpreadSwap swap = new SpreadSwap("swap", this::swap);
    private final SpreadSeal seal;
    private final SpreadVeil veil = new SpreadVeil("veil");
    private final ClientSubscription spreadSubscription;
    private boolean pickerOpen;
    private boolean folding;
    private String merged = "";

    MutateScreen(ScreenRouter router, Route.Mutate route) {
        super(ScreenText.MUTATE.text(), route, router);
        draft = new MutationDraft(route.skillId());
        candidates = new SpreadParts.CandidateGrid("candidates", this::pick);
        SpreadSeal.Hold hold = new SpreadSeal.Hold("hold", gesture, () -> motion().now());
        seal = new SpreadSeal("seal", hold);
        root().add(banner);
        root().add(swap);
        root().add(seal);
        root().add(veil);
        spreadSubscription = router.state().onSpread(this::arrived);
        ask();
        refresh();
    }

    @Override
    protected String themeSkill() {
        return draft.paired() ? draft.lead() + '+' + draft.partner() : draft.lead();
    }

    @Override
    protected String probeKind() {
        return "adapt-mutate";
    }

    @Override
    protected int densities() {
        return 3;
    }

    @Override
    protected void describe(Map<String, String> state) {
        super.describe(state);
        state.put("lead", draft.lead());
        state.put("partner", draft.partner());
        state.put("confirmable", Boolean.toString(draft.confirmable()));
        state.put("picker", Boolean.toString(pickerShown()));
        state.put("merging", Boolean.toString(veil.merging()));
    }

    private void ask() {
        draft.requested();
        if (!router().actions().previewMutation(draft.lead(), draft.partner())) {
            draft.accept(new MutationSpreadView(draft.lead(), "", "", "", List.of(), MutationSide.NONE, MutationSide.NONE,
                    List.of(), words(), ""));
        }
    }

    /** Picks {@code skillId} as the partner and asks for the pair's preview; the partner's line fills at once. */
    void pick(String skillId) {
        if (veil.merging() || gesture.holding() || !draft.pick(skillId)) {
            return;
        }
        pickerOpen = false;
        gesture.reset();
        ask();
        refresh();
        requestLayout();
    }

    /** Exchanges lead and partner with a wisp across the fold each way. */
    void swap() {
        if (veil.merging() || gesture.holding() || !draft.swap()) {
            return;
        }
        gesture.reset();
        if (!still()) {
            veil.swap(motion().now());
        }
        ask();
        refresh();
        requestLayout();
    }

    /** Takes the server's answer; one that says the merge went through keeps the pair showing while the halves merge. */
    private void arrived(MutationSpreadView view) {
        if (view.mutatedNow()) {
            if (merged.isEmpty()) {
                merged = view.mutatedSkillId();
                if (still()) {
                    router().open(new Route.Skill(merged));
                    return;
                }
                veil.merge(motion().now());
                refresh();
            }
            return;
        }
        draft.accept(view);
        refresh();
        requestLayout();
    }

    @Override
    protected void answered(String action, FeedbackKind kind) {
        if (kind != FeedbackKind.SUCCESS) {
            gesture.reset();
        }
    }

    private boolean still() {
        return router().themes().still(motion());
    }

    private boolean pickerShown() {
        return !folding || !draft.paired() || pickerOpen;
    }

    /** Fires the merge once the hold has lasted, drops a mouse hold the pointer left, and opens the new page after the merge. */
    private void advance() {
        long now = motion().now();
        SpreadSeal.Hold hold = seal.hold();
        if (gesture.holding() && hold.byMouse() && hovered() != hold) {
            gesture.release(now);
        }
        if (gesture.poll(now)) {
            confirm();
        }
        if (veil.merged(now) && !merged.isEmpty()) {
            router().open(new Route.Skill(merged));
        }
    }

    private void confirm() {
        String lead = draft.lead();
        String partner = draft.partner();
        boolean sent = send("mutate", words().title(), () -> {
            draft.requested();
            return router().actions().confirmMutation(lead, partner);
        });
        if (!sent) {
            gesture.reset();
        }
    }

    @Override
    public void tick() {
        super.tick();
        advance();
    }

    @Override
    public void render(UiDraw draw, int mouseX, int mouseY, float delta) {
        advance();
        super.render(draw, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyReleased(UiKeyEvent event) {
        if ((event.key() == UiKey.ENTER || event.key() == UiKey.SPACE) && gesture.holding() && !seal.hold().byMouse()) {
            gesture.release(motion().now());
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public void removed() {
        super.removed();
        spreadSubscription.cancel();
    }

    private MutationWords words() {
        MutationSpreadView view = draft.view();
        if (view != null) {
            return view.words();
        }
        String level = ScreenText.LEVEL.text("{level}");
        return new MutationWords(ScreenText.MUTATE.text(), "", "", "", "", ScreenText.MUTATE.text(), "", "", level);
    }

    @Override
    protected void compose(Flow flow, UiDraw metrics, int width) {
        folding = flow.density() >= FOLDED;
        boolean compact = flow.density() >= COMPACT;
        banner.setCompact(compact);
        int head = SpreadBanner.height(compact, metrics.lineHeight());
        int foot = swapRoom(flow.spread()) + SpreadSeal.height(theme());
        for (SpreadParts.Spacer top : List.of(leadTop, partnerTop)) {
            top.setHeight(head);
        }
        for (SpreadParts.Spacer bottom : List.of(leadFoot, partnerFoot)) {
            bottom.setHeight(foot);
        }
        int gap = flow.density() == ROOMY ? PAGE_GAP + 1 : PAGE_GAP;
        flow.add(leadTop);
        flow.add(leadRow, gap);
        side(flow, draft.side(draft.lead()), leadGains, leadLoses, leadSurplus, gap);
        flow.foot(leadFoot);
        flow.nextPage();
        flow.add(partnerTop);
        flow.add(partnerRow, gap);
        boolean picker = pickerShown();
        if (picker) {
            flow.add(candidates, gap);
        }
        if (draft.paired() && (!folding || !pickerOpen)) {
            side(flow, draft.side(draft.partner()), partnerGains, partnerLoses, partnerSurplus, gap);
        }
        flow.foot(partnerFoot);
        partnerRow.setOpener(folding && draft.paired() && !veil.merging() ? this::togglePicker : null);
    }

    private static void side(Flow flow, MutationSide side, SpreadParts.TokenRow gains, SpreadParts.TokenRow loses, Label surplus, int gap) {
        if (side == null) {
            return;
        }
        if (!gains.empty()) {
            flow.add(gains, gap);
        }
        if (!loses.empty()) {
            flow.add(loses, gap);
        }
        if (!side.surplus().isEmpty()) {
            flow.add(surplus, 1);
        }
    }

    /** The height the swap takes above the hold on an open spread, its plate and the gap under it included. */
    private int swapRoom(boolean open) {
        return open ? theme().controlHeight() + SpreadSwap.PLATE * 2 + SWAP_GAP : 0;
    }

    private void togglePicker() {
        pickerOpen = !pickerOpen;
        requestLayout();
    }

    @Override
    protected void layoutScreen(UiRect screen, UiDraw metrics) {
        super.layoutScreen(screen, metrics);
        UiRect left = leadTop.bounds();
        UiRect right = partnerTop.bounds().isEmpty() ? left : partnerTop.bounds();
        boolean open = spread();
        int fold = open ? (left.right() + right.x()) / 2 : left.centerX();
        int from = left.x();
        int to = open ? right.right() : left.right();
        banner.place(UiRect.of(from, left.y(), to, left.bottom()), fold, metrics);
        UiRect footLeft = leadFoot.bounds();
        int faceHeight = theme().controlHeight();
        int beside = SpreadSwap.WIDTH + SpreadSwap.PLATE * 2 + SWAP_GAP;
        seal.place(UiRect.of(from, footLeft.y() + swapRoom(open), to, footLeft.bottom()), open ? fold : fold + beside / 2, metrics);
        UiRect hold = seal.hold().bounds();
        int swapX = open ? fold - SpreadSwap.WIDTH / 2 : hold.x() - beside + SpreadSwap.PLATE;
        int swapY = open ? footLeft.y() + SpreadSwap.PLATE : hold.centerY() - faceHeight / 2;
        swap.place(new UiRect(swapX, swapY, SpreadSwap.WIDTH, faceHeight), metrics);
        veil.place(UiRect.of(from, left.y(), to, footLeft.bottom()), fold, metrics);
    }

    @Override
    protected void refresh() {
        updatePath();
        CatalogIndex catalog = router().index();
        PlayerView player = router().state().player();
        MutationWords words = words();
        SkillView lead = catalog == null ? null : catalog.skill(draft.lead());
        SkillView partner = catalog == null || !draft.paired() ? null : catalog.skill(draft.partner());
        int leadAccent = lead == null ? theme().accent() : Palette.accent(lead.color());
        int partnerAccent = partner == null ? leadAccent : Palette.accent(partner.color());
        setTheme(router().theme().withAccent(leadAccent));
        leadRow.set(lead == null ? null : UiIcons.skill(lead), lead == null ? draft.lead() : lead.name(), leadAccent, words.lead(),
                words.level(level(player, draft.lead())));
        if (partner == null) {
            partnerRow.set(null, "", partnerAccent, words.partner(), "");
        } else {
            partnerRow.set(UiIcons.skill(partner), partner.name(), partnerAccent, words.partner(),
                    words.level(level(player, draft.partner())));
        }
        MutationSpreadView view = draft.view();
        boolean pair = view != null && draft.paired() && view.partner().equals(draft.partner()) && view.lead().equals(draft.lead());
        MutationSide leadSide = draft.side(draft.lead());
        MutationSide partnerSide = draft.paired() ? draft.side(draft.partner()) : null;
        fill(leadSide, leadSide == null ? List.of() : withRecipes(leadSide.tools()), pair, words, leadGains, leadLoses, leadSurplus);
        fill(partnerSide, partnerSide == null ? List.of() : withRecipes(partnerSide.tools()), pair, words, partnerGains,
                partnerLoses, partnerSurplus);
        candidates.set(view == null ? List.of() : view.candidates(), catalog, draft.partner(), words.level());
        if (pair) {
            List<MutationEntry> tools = withRecipes(view.leadSide().tools());
            tools.addAll(withRecipes(view.partnerSide().tools()));
            banner.set(words.title(), view.name(), view.summary(), view.mutated(), view.leadSide().upgrades(), tools,
                    view.partnerSide().upgrades());
        } else {
            banner.set(words.title(), "", "", List.of(), List.of(), List.of(), List.of());
        }
        swap.setName(words.swap());
        swap.setEnabled(draft.paired() && !veil.merging());
        seal.hold().set(words.hold(), leadAccent, partnerAccent);
        seal.hold().setWarning(words.permanent());
        seal.hold().setEnabled(gesture.fired() || draft.confirmable() && !veil.merging() && !pending());
        veil.colors(leadAccent, partnerAccent, theme().backdrop());
    }

    /**
     * Fills a parent's rows. Once the pair's result shows the upgrades and tools in the head, Gains keeps only the
     * originals this parent keeps; before that it lists all three.
     */
    private static void fill(MutationSide side, List<MutationEntry> tools, boolean paired, MutationWords words,
                             SpreadParts.TokenRow gains, SpreadParts.TokenRow loses, Label surplus) {
        gains.clear();
        loses.clear();
        surplus.setText(side == null ? "" : side.surplus());
        if (side == null) {
            return;
        }
        gains.setLabel(words.gains(), Palette.MUTED);
        if (!paired) {
            gains.add(side.upgrades(), SpreadToken.Look.GAIN);
        }
        gains.add(side.kept(), SpreadToken.Look.KEPT);
        if (!paired) {
            gains.add(tools, SpreadToken.Look.TOOL);
        }
        loses.setLabel(words.loses(), Palette.LOCK);
        loses.add(side.lost(), SpreadToken.Look.LOST);
    }

    /** {@code tools} with each one's recipe after its detail, once the server has sent that tool. */
    private List<MutationEntry> withRecipes(List<MutationEntry> tools) {
        List<MutationEntry> shown = new ArrayList<>(tools.size());
        for (MutationEntry entry : tools) {
            ToolView tool = router().state().tool(entry.id());
            if (tool == null) {
                shown.add(entry);
                continue;
            }
            List<String> lines = new ArrayList<>();
            lines.add(entry.detail());
            lines.addAll(ToolsSection.ingredients(tool));
            shown.add(new MutationEntry(entry.id(), entry.icon(), entry.name(), String.join("\n", lines), entry.level()));
        }
        return shown;
    }

    /** The parent's level as the server's last answer gave it, which a merge in progress keeps, else the player's. */
    private int level(PlayerView player, String skillId) {
        MutationSide side = draft.side(skillId);
        if (side != null) {
            return side.level();
        }
        SkillLineView line = player == null ? null : CatalogIndex.line(player, skillId);
        return line == null ? 0 : Math.max(0, line.level());
    }
}
