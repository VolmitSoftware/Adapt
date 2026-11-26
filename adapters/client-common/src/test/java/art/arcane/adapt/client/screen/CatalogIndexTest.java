package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.PlayerAccess;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.PreferenceOwner;
import art.arcane.adapt.client.view.SkillView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogIndexTest {
    private final CatalogIndex catalog = new CatalogIndex(PreviewSamples.catalog(), Map.of());

    static PlayerView withAccess(PlayerAccess access) {
        PlayerView player = PreviewSamples.player();
        return new PlayerView(player.header(), player.skills(), player.adaptationLevels(), player.preferences(), access);
    }

    private static List<String> ids(List<SkillView> skills) {
        List<String> ids = new ArrayList<>();
        for (SkillView skill : skills) {
            ids.add(skill.id());
        }
        return ids;
    }

    private static List<List<String>> groupIds(List<List<AdaptationView>> groups) {
        List<List<String>> ids = new ArrayList<>();
        for (List<AdaptationView> group : groups) {
            List<String> row = new ArrayList<>();
            for (AdaptationView adaptation : group) {
                row.add(adaptation.id());
            }
            ids.add(row);
        }
        return ids;
    }

    @Test
    void skillsListFollowsTheServerOrderExactly() {
        PlayerView player = withAccess(new PlayerAccess(false, true, true, List.of("stealth", "agility", "rift"), Map.of()));
        assertEquals(List.of("stealth", "agility", "rift"), ids(catalog.visibleSkills(player)));
    }

    @Test
    void skillsTheServerHidesOrTheCatalogLacksAreLeftOut() {
        PlayerView player = withAccess(new PlayerAccess(false, true, true, List.of("rift", "unknown", "hunter"), Map.of()));
        assertEquals(List.of("rift", "hunter"), ids(catalog.visibleSkills(player)));
        assertEquals(List.of(), catalog.visibleSkills(null));
        assertEquals(List.of(), catalog.visibleSkills(withAccess(PlayerAccess.NONE)));
    }

    @Test
    void adaptationRowsFollowTheServerRowsAndSkipUnknownIds() {
        PlayerView player = withAccess(new PlayerAccess(false, true, true, List.of("agility"), Map.of("agility",
                List.of(List.of("agility-ladder-slide", "gone"), List.of("gone-too"), List.of("agility-armor-up", "agility-wall-jump")))));
        assertEquals(List.of(List.of("agility-ladder-slide"), List.of("agility-armor-up", "agility-wall-jump")),
                groupIds(catalog.groups(player, "agility")));
    }

    @Test
    void deniedSkillsShowNoAdaptationsAndCannotBeLearned() {
        PlayerView player = withAccess(new PlayerAccess(false, true, true, List.of("agility", "rift"),
                Map.of("agility", List.of(List.of("agility-super-jump")))));
        assertFalse(player.access().canOpen("rift"));
        assertEquals(List.of(), catalog.groups(player, "rift"));
        assertEquals(LearnRules.LearnBlock.DENIED, LearnRules.learn(catalog, player, catalog.adaptation("rift-gate"), 2).block());
        assertEquals(LearnRules.LearnBlock.DENIED, LearnRules.learn(catalog, player, catalog.adaptation("agility-armor-up"), 1).block());
        assertEquals(LearnRules.LearnBlock.NONE, LearnRules.learn(catalog, player, catalog.adaptation("agility-super-jump"), 1).block());
        assertEquals(1, LearnRules.affordable(catalog, player, catalog.skill("agility")));
    }

    @Test
    void aSkillUsesOnePowerPerHeldAdaptationWhateverItsLevel() {
        assertEquals(2, CatalogIndex.powerUsed(PreviewSamples.player(), catalog.skill("agility")));
    }

    @Test
    void noRefundServersRefundNothing() {
        PlayerAccess base = PreviewSamples.access();
        PlayerView player = withAccess(new PlayerAccess(false, false, true, base.skills(), base.adaptationRows()));
        LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, catalog.adaptation("agility-wall-jump"), 0);
        assertTrue(check.allowed());
        assertFalse(check.refunded());
        assertEquals(0, check.refund());
        assertEquals("Unlearning returns no knowledge", LearnText.refund(check));
    }

    @Test
    void debugMakesLearningFreeAndSkipsPowerAndPermanence() {
        PlayerAccess base = PreviewSamples.access();
        PlayerView player = withAccess(new PlayerAccess(true, true, true, base.skills(), base.adaptationRows()));
        LearnRules.LearnCheck armor = LearnRules.learn(catalog, player, catalog.adaptation("agility-armor-up"), 1);
        assertTrue(armor.allowed());
        assertTrue(armor.free());
        assertEquals(0, armor.knowledgeCost());
        assertEquals("Learn I for free", LearnText.learnBlock(catalog, armor));
        assertTrue(LearnRules.learn(catalog, player, catalog.adaptation("stealth-vision"), 1).allowed());
        assertEquals(LearnRules.LearnBlock.REQUIREMENTS, LearnRules.learn(catalog, player, catalog.adaptation("rift-blink"), 1).block());
        LearnRules.UnlearnCheck unlearn = LearnRules.unlearn(catalog, player, catalog.adaptation("agility-wall-jump"), 0);
        assertFalse(unlearn.refunded());
    }

    @Test
    void preferencesResolveToThePlayerValueOrTheDefault() {
        AdaptationView gate = catalog.adaptation("rift-gate");
        assertEquals("OFF", CatalogIndex.preference(PreviewSamples.player(), PreferenceOwner.ADAPTATION, "rift-gate",
                gate.preferences().get(2)));
        assertEquals("ON", CatalogIndex.preference(null, PreferenceOwner.ADAPTATION, "rift-gate", gate.preferences().get(2)));
    }
}
