package art.arcane.adapt.client.hud;

import art.arcane.adapt.client.protocol.ToastKind;
import art.arcane.adapt.client.theme.PageTheme;
import art.arcane.adapt.client.theme.ThemeKit;
import art.arcane.adapt.client.ui.kit.UiContrast;
import art.arcane.adapt.client.ui.kit.UiRamp;
import art.arcane.adapt.client.ui.kit.UiTome;

/**
 * How a toast card is dressed: parchment washed toward its skill's page colour, an edge and corner studs in the skill's
 * frame colours (a mutated skill's lead on the left, partner on the right), its well behind the medallion and its accent
 * on the time line. Without a skill the card wears the book's leather and gold. The kind picks the medallion rim and a
 * restrained mark: a level-up's gold sheen as it lands, a discovery's ruled divider, a signature's gilt frame with
 * flourishes, a challenge's plum slip. Colours are opaque ARGB except {@link #paper}, which keeps the slip's see-through
 * alpha.
 */
final class ToastLook {
    static final int CHALLENGE_PAPER = 0xE6EAD6E0;
    static final int CHALLENGE_TITLE = 0xFF763078;
    /** How far a themed card's parchment moves toward the skill's page colour, before contrast pulls it back. */
    static final double WASH = 0.35;
    private static final double WASH_STEP = 0.05;
    private static final int[] PLAIN_STUD = {UiRamp.LEATHER[1], UiRamp.LEATHER[3], UiRamp.LEATHER[5], UiRamp.GOLD[3]};

    final int paper;
    final int edge;
    /** Outline, fill, highlight and centre of the left and the right corner studs. */
    final int[] leftStud;
    final int[] rightStud;
    final int well;
    final int leftAccent;
    final int rightAccent;
    final int title;
    final int body;
    final UiTome.Token token;
    final boolean gilt;
    final boolean sheen;
    final boolean divider;

    private ToastLook(int paper, int edge, int[] leftStud, int[] rightStud, int well, int leftAccent, int rightAccent,
                      int title, UiTome.Token token, ToastKind kind) {
        this.paper = paper;
        this.edge = edge;
        this.leftStud = leftStud;
        this.rightStud = rightStud;
        this.well = well;
        this.leftAccent = leftAccent;
        this.rightAccent = rightAccent;
        this.title = title;
        this.body = HudPaint.INK_MUTED;
        this.token = token;
        this.gilt = kind == ToastKind.SIGNATURE;
        this.sheen = kind == ToastKind.LEVEL_UP || kind == ToastKind.CHALLENGE;
        this.divider = kind == ToastKind.DISCOVERY;
    }

    /** The look of a toast of {@code kind} in {@code theme}, null for a toast without a themed skill. */
    static ToastLook of(ToastKind kind, PageTheme theme) {
        UiTome.Token token = switch (kind) {
            case LEVEL_UP, UNLOCK -> UiTome.Token.LEARNED;
            case SIGNATURE, CHALLENGE -> UiTome.Token.MAXED;
            case KNOWLEDGE, DISCOVERY, GENERIC -> UiTome.Token.PLAIN;
        };
        int paper = kind == ToastKind.CHALLENGE ? CHALLENGE_PAPER : HudPaint.PAPER;
        int title = kind == ToastKind.CHALLENGE ? CHALLENGE_TITLE : kind == ToastKind.SIGNATURE ? UiRamp.GOLD[0] : HudPaint.INK;
        if (theme == null) {
            int edge = kind == ToastKind.SIGNATURE ? UiRamp.GOLD[0] : HudPaint.PAPER_EDGE;
            int accent = kind == ToastKind.SIGNATURE ? UiRamp.GOLD[1] : HudPaint.INK_MUTED;
            return new ToastLook(paper, edge, PLAIN_STUD, PLAIN_STUD, UiRamp.PAGE[2], accent, accent, title, token, kind);
        }
        ThemeKit lead = theme.lead();
        ThemeKit partner = theme.mutated() ? theme.partner() : lead;
        if (kind != ToastKind.CHALLENGE) {
            paper = washed(HudPaint.mix(lead.color('4'), partner.color('4'), 0.5));
        }
        int edge = kind == ToastKind.SIGNATURE ? UiRamp.GOLD[0] : lead.color('k');
        int leftAccent = kind == ToastKind.SIGNATURE ? UiRamp.GOLD[1] : accent(lead, paper);
        int rightAccent = kind == ToastKind.SIGNATURE ? UiRamp.GOLD[1] : accent(partner, paper);
        return new ToastLook(paper, edge, stud(lead), stud(partner), lead.color('w'), leftAccent, rightAccent, title, token, kind);
    }

    /** Parchment moved toward {@code page} as far as {@link #WASH}, but no further than body ink still reads on it. */
    static int washed(int page) {
        for (double wash = WASH; wash > 0.0; wash -= WASH_STEP) {
            int paper = HudPaint.mix(HudPaint.PAPER, HudPaint.opaque(page) & 0xFFFFFF | HudPaint.PAPER & 0xFF000000, wash);
            if (UiContrast.ratio(HudPaint.INK_MUTED, HudPaint.opaque(paper)) >= UiContrast.TEXT) {
                return paper;
            }
        }
        return HudPaint.PAPER;
    }

    private static int accent(ThemeKit kit, int paper) {
        return UiContrast.darkenFor(HudPaint.opaque(kit.color('p')), HudPaint.opaque(paper), UiContrast.GRAPHIC);
    }

    private static int[] stud(ThemeKit kit) {
        return new int[] {kit.color('k'), kit.color('b'), kit.color('c'), kit.color('p')};
    }
}
