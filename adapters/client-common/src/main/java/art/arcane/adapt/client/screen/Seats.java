package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.state.ClientState;
import art.arcane.adapt.client.ui.kit.LegacyText;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import art.arcane.adapt.client.view.XpGuideView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The seats of the skills page: one for every enabled skill on the server the player may open or is shown, in name order,
 * whatever the player holds. A mutation sits in its lead's seat and its partner has none, so the seats after it close up.
 */
final class Seats {
    private Seats() {
    }

    /**
     * One seat: {@code skill} is the skill on the server that owns it, {@code shown} what it shows (a mutation in its
     * lead's seat), and whether the player has started it.
     */
    record Seat(String seatId, SkillView skill, SkillView shown, boolean started) {
    }

    static List<Seat> of(CatalogIndex catalog, PlayerView player, ClientState state) {
        List<SkillView> roster = new ArrayList<>();
        Map<String, SkillView> leading = new HashMap<>();
        Set<String> partners = new HashSet<>();
        for (SkillView skill : catalog.catalog().skills()) {
            XpGuideView guide = state.xpGuide(skill.id());
            if (guide != null && guide.mutated()) {
                if (guide.parents().size() == 2) {
                    leading.put(guide.parents().get(0), skill);
                    partners.add(guide.parents().get(1));
                }
            } else if (skill.enabled() && (player.access().canOpen(skill.id()) || player.access().skills().contains(skill.id()))) {
                roster.add(skill);
            }
        }
        roster.sort(Comparator.comparing((SkillView skill) -> LegacyText.strip(skill.name()).toLowerCase(Locale.ROOT))
                .thenComparing(SkillView::id));
        List<Seat> seats = new ArrayList<>();
        for (SkillView skill : roster) {
            if (partners.contains(skill.id())) {
                continue;
            }
            SkillView shown = leading.getOrDefault(skill.id(), skill);
            seats.add(new Seat(skill.id(), skill, shown, shown != skill || started(player, shown)));
        }
        return seats;
    }

    /** Whether the player has started {@code skill}: earned experience or knowledge in it, or learned something there. */
    static boolean started(PlayerView player, SkillView skill) {
        SkillLineView line = CatalogIndex.line(player, skill.id());
        return line != null && (line.xp() > 0.0D || line.knowledge() > 0L) || CatalogIndex.powerUsed(player, skill) > 0;
    }
}
