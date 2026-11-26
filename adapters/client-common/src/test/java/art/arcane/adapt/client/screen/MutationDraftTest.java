package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.view.MutationCandidate;
import art.arcane.adapt.client.view.MutationSide;
import art.arcane.adapt.client.view.MutationSpreadView;
import art.arcane.adapt.client.view.MutationWords;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationDraftTest {
    private static final MutationWords WORDS = new MutationWords("Mutate", "Lead", "Partner", "Gains", "Loses", "Hold", "Permanent", "Swap",
            "{level}");

    @Test
    void onlyAnEligibleSkillCanBePickedAndThePairIsConfirmableOnceItsAnswerIsIn() {
        MutationDraft draft = new MutationDraft("swords");
        draft.requested();
        draft.accept(view("swords", ""));

        assertFalse(draft.pick("axes"));
        assertTrue(draft.pick("stealth"));
        assertFalse(draft.confirmable());
        draft.requested();
        draft.accept(view("swords", "stealth"));
        assertTrue(draft.confirmable());
    }

    @Test
    void aPartnerTheServerRefusedIsDroppedOnceEveryRequestIsAnswered() {
        MutationDraft draft = new MutationDraft("swords");
        draft.requested();
        draft.accept(view("swords", ""));
        draft.pick("stealth");
        draft.requested();

        draft.accept(view("swords", ""));

        assertEquals("", draft.partner());
        assertFalse(draft.paired());
    }

    @Test
    void anAnswerSentBeforeASwapIsCountedButNotShownAsCurrent() {
        MutationDraft draft = new MutationDraft("swords");
        draft.requested();
        draft.accept(view("swords", ""));
        draft.pick("stealth");
        draft.requested();
        assertTrue(draft.swap());
        draft.requested();

        MutationSpreadView stale = view("swords", "stealth");
        draft.accept(stale);
        assertEquals("stealth", draft.lead());
        assertEquals("swords", draft.partner());
        assertFalse(draft.current());
        assertSame(stale.partnerSide(), draft.side("stealth"));

        draft.accept(view("stealth", "swords"));
        assertTrue(draft.confirmable());
    }

    @Test
    void theHoldCountsOnlyAfterItsWholeTimeAndDrainsWhenLetGo() {
        HoldToConfirm hold = new HoldToConfirm();
        hold.press(0L);
        assertFalse(hold.poll(HoldToConfirm.HOLD_MILLIS - 1L));
        hold.release(750L);
        assertEquals(0.5F, hold.shown(750L), 0.001F);
        assertEquals(0.25F, hold.shown(750L + HoldToConfirm.DRAIN_MILLIS / 2), 0.001F);
        assertFalse(hold.poll(5_000L));

        hold.press(10_000L);
        hold.press(10_500L);
        assertTrue(hold.poll(10_000L + HoldToConfirm.HOLD_MILLIS));
        assertFalse(hold.poll(20_000L));
        assertEquals(1.0F, hold.shown(20_000L));
        hold.release(20_000L);
        assertTrue(hold.fired());
        hold.reset();
        assertFalse(hold.fired());
        assertEquals(0.0F, hold.shown(20_000L));
    }

    private static MutationSpreadView view(String lead, String partner) {
        List<MutationCandidate> candidates = List.of(
                new MutationCandidate(lead.equals("swords") ? "stealth" : "swords", 20, MutationCandidate.State.ELIGIBLE, ""),
                new MutationCandidate("axes", 3, MutationCandidate.State.LEVEL_TOO_LOW, "Needs level 15"));
        MutationSide partnerSide = partner.isEmpty() ? MutationSide.NONE
                : new MutationSide(partner, 20, List.of(), List.of(), List.of(), List.of(), "");
        return new MutationSpreadView(lead, partner, partner.isEmpty() ? "" : "Name", "", List.of(),
                new MutationSide(lead, 20, List.of(), List.of(), List.of(), List.of(), ""), partnerSide, candidates, WORDS, "");
    }
}
