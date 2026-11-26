package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.view.MutationCandidate;
import art.arcane.adapt.client.view.MutationSide;
import art.arcane.adapt.client.view.MutationSpreadView;

import java.util.Objects;

/**
 * What the mutation spread shows and asks for: the lead, the partner picked (empty for none) and the server's latest
 * answer. The server decides everything; the draft only lets a skill be picked that the latest answer lists as
 * eligible, and takes the server's partner once every request has its answer.
 */
final class MutationDraft {
    private String lead;
    private String partner = "";
    private MutationSpreadView view;
    private int outstanding;

    MutationDraft(String lead) {
        this.lead = Objects.requireNonNull(lead, "lead");
    }

    String lead() {
        return lead;
    }

    String partner() {
        return partner;
    }

    boolean paired() {
        return !partner.isEmpty();
    }

    MutationSpreadView view() {
        return view;
    }

    /** Notes a request sent for the current lead and partner. */
    void requested() {
        outstanding++;
    }

    /** Whether the latest answer is for exactly the lead and partner showing, with nothing still asked. */
    boolean current() {
        return view != null && outstanding == 0 && view.lead().equals(lead) && view.partner().equals(partner);
    }

    /** Whether the hold may start: a partner is picked and the answer for this pair is in. */
    boolean confirmable() {
        return paired() && current() && !view.mutatedNow();
    }

    /** Picks {@code skillId} as the partner when the latest answer lists it as eligible; true when the pick changed. */
    boolean pick(String skillId) {
        MutationCandidate candidate = view == null ? null : view.candidate(skillId);
        if (candidate == null || !candidate.eligible() || skillId.equals(partner) || skillId.equals(lead)) {
            return false;
        }
        partner = skillId;
        return true;
    }

    /** Exchanges lead and partner; false when no partner is picked. */
    boolean swap() {
        if (!paired()) {
            return false;
        }
        String former = lead;
        lead = partner;
        partner = former;
        return true;
    }

    /**
     * Takes the server's answer. Once every request is answered, the answer's partner is the truth: a partner the
     * rules refused is dropped. An answer for the same pair the other way round (one sent before a swap) still shows
     * both sides until the swapped answer comes; an answer for another lead only counts as answered.
     */
    void accept(MutationSpreadView answer) {
        outstanding = Math.max(0, outstanding - 1);
        boolean same = answer.lead().equals(lead);
        boolean swapped = paired() && answer.lead().equals(partner) && answer.partner().equals(lead);
        if (!same && !swapped) {
            return;
        }
        view = answer;
        if (same && outstanding == 0) {
            partner = answer.partner();
        }
    }

    /** What the server last said about {@code skillId}'s side, or null when no answer covers it. */
    MutationSide side(String skillId) {
        if (view == null || skillId.isEmpty()) {
            return null;
        }
        if (view.leadSide().skillId().equals(skillId)) {
            return view.leadSide();
        }
        return view.partnerSide().skillId().equals(skillId) ? view.partnerSide() : null;
    }
}
