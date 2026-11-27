package art.arcane.adapt.client.screen;

import art.arcane.adapt.client.view.LearnRules;
import art.arcane.adapt.client.view.CatalogIndex;
import art.arcane.adapt.client.view.AdaptationView;
import art.arcane.adapt.client.view.CatalogView;
import art.arcane.adapt.client.view.IconRef;
import art.arcane.adapt.client.view.PlayerHeader;
import art.arcane.adapt.client.view.PlayerView;
import art.arcane.adapt.client.view.RequirementView;
import art.arcane.adapt.client.view.SkillLineView;
import art.arcane.adapt.client.view.SkillView;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearnRulesTest {
    private final CatalogIndex catalog = new CatalogIndex(PreviewSamples.catalog(), Map.of());
    private final PlayerView player = PreviewSamples.player();

    private AdaptationView adaptation(String id) {
        return catalog.adaptation(id);
    }

    private static PlayerView withLevels(PlayerView base, Map<String, Integer> changes) {
        Map<String, Integer> levels = new HashMap<>(base.adaptationLevels());
        levels.putAll(changes);
        return new PlayerView(base.header(), base.skills(), levels, base.preferences(), base.access());
    }

    @Test
    void nextLevelWithinKnowledgeAndPowerIsAllowed() {
        LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation("agility-super-jump"), 1);
        assertTrue(check.allowed());
        assertEquals(8, check.knowledgeCost());
        assertEquals(1, check.powerCost());
        assertTrue(LearnRules.nextAffordable(catalog, player, adaptation("agility-super-jump")));
    }

    @Test
    void costsAddUpFromTheCurrentLevelLikeTheServer() {
        LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation("agility-wall-jump"), 4);
        assertEquals(2, check.from());
        assertEquals(8 + 10, check.knowledgeCost());
        assertEquals(0, check.powerCost());
        assertTrue(check.allowed());
        LearnRules.LearnCheck tooFar = LearnRules.learn(catalog, player, adaptation("agility-wall-jump"), 5);
        assertEquals(8 + 10 + 12, tooFar.knowledgeCost());
        assertEquals(LearnRules.LearnBlock.NONE, tooFar.block());
    }

    @Test
    void aHeldAdaptationLevelsUpWithoutPowerEvenWhenNoneIsFree() {
        PlayerHeader header = player.header();
        PlayerView full = new PlayerView(new PlayerHeader(header.masterLevel(), header.masterProgress(), header.powerMax(),
            header.powerMax(), header.wisdom(), header.effectsEnabled()), player.skills(), player.adaptationLevels(),
            player.preferences(), player.access());
        LearnRules.LearnCheck check = LearnRules.learn(catalog, full, adaptation("agility-wall-jump"), 3);
        assertEquals(0, check.powerCost());
        assertEquals(LearnRules.LearnBlock.NONE, check.block());
        LearnRules.LearnCheck fresh = LearnRules.learn(catalog, full, adaptation("agility-super-jump"), 1);
        assertEquals(LearnRules.LearnBlock.POWER, fresh.block());
    }

    @Test
    void knowledgeShortageNamesTheMissingAmount() {
        LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation("agility-armor-up"), 1);
        assertEquals(LearnRules.LearnBlock.KNOWLEDGE, check.block());
        assertEquals(10L, check.knowledgeShort());
        assertEquals("Needs 10 more knowledge", LearnText.learnBlock(catalog, check));
    }

    @Test
    void noKnowledgeAtAllReadsLikeTheChestMenu() {
        LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation("hunter-adrenaline"), 1);
        assertEquals(LearnRules.LearnBlock.KNOWLEDGE, check.block());
        assertEquals("No knowledge in this skill yet", LearnText.learnBlock(catalog, check));
    }

    @Test
    void powerIsCheckedBeforeKnowledge() {
        LearnRules.LearnCheck check = LearnRules.learn(catalog, player, adaptation("stealth-vision"), 1);
        assertEquals(LearnRules.LearnBlock.POWER, check.block());
        assertEquals(5, check.freePower());
        assertEquals(1, check.powerShort());
        assertEquals("Needs 6 power, 5 free", LearnText.learnBlock(catalog, check));
    }

    @Test
    void requirementsAreCheckedFirstAndNamed() {
        PlayerView broke = new PlayerView(new PlayerHeader(1, 0.0D, 8, 8, 0L, true),
                List.of(new SkillLineView("rift", 0.0D, 1, 0.0D, 10.0D, 0L)), Map.of(), List.of(), PreviewSamples.access());
        LearnRules.LearnCheck check = LearnRules.learn(catalog, broke, adaptation("rift-blink"), 1);
        assertEquals(LearnRules.LearnBlock.REQUIREMENTS, check.block());
        assertEquals(List.of(new RequirementView("rift-gate", 2)), check.missing());
        assertEquals("Requires Rift Gate II", LearnText.learnBlock(catalog, check));
        PlayerView met = withLevels(player, Map.of("rift-gate", 2));
        assertTrue(LearnRules.learn(catalog, met, adaptation("rift-blink"), 1).allowed());
    }

    @Test
    void disabledAdaptationsAndLearnedLevelsCannotBeLearned() {
        assertEquals(LearnRules.LearnBlock.DISABLED, LearnRules.learn(catalog, player, adaptation("rift-access"), 1).block());
        assertEquals(LearnRules.LearnBlock.LEARNED, LearnRules.learn(catalog, player, adaptation("agility-wall-jump"), 2).block());
        assertFalse(LearnRules.nextAffordable(catalog, player, adaptation("agility-ladder-slide")));
    }

    @Test
    void unlearnRefundsTheLevelsGivenUp() {
        LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, player, adaptation("agility-wall-jump"), 0);
        assertTrue(check.allowed());
        assertEquals(4 + 6, check.refund());
        assertEquals(6, LearnRules.unlearn(catalog, player, adaptation("agility-wall-jump"), 1).refund());
        assertEquals(LearnRules.UnlearnBlock.NOT_LEARNED, LearnRules.unlearn(catalog, player, adaptation("agility-super-jump"), 0).block());
    }

    @Test
    void permanentAdaptationsCannotBeUnlearned() {
        PlayerView learned = withLevels(player, Map.of("rift-resistance", 1));
        LearnRules.UnlearnCheck check = LearnRules.unlearn(catalog, learned, adaptation("rift-resistance"), 0);
        assertEquals(LearnRules.UnlearnBlock.PERMANENT, check.block());
        assertEquals("Cannot be unlearned", LearnText.unlearnBlock(check));
    }

    @Test
    void learnedDependentsBlockAnUnlearnBelowTheirRequirement() {
        PlayerView learned = withLevels(player, Map.of("rift-gate", 3, "rift-blink", 1));
        LearnRules.UnlearnCheck below = LearnRules.unlearn(catalog, learned, adaptation("rift-gate"), 1);
        assertEquals(LearnRules.UnlearnBlock.DEPENDENTS, below.block());
        assertEquals("Needed by Rift Blink", LearnText.unlearnBlock(below));
        assertTrue(LearnRules.unlearn(catalog, learned, adaptation("rift-gate"), 2).allowed());
    }

    @Test
    void skillsMarkWhenAnyNextLevelIsAffordable() {
        assertEquals(2, LearnRules.affordable(catalog, player, catalog.skill("agility")));
        assertEquals(0, LearnRules.affordable(catalog, player, catalog.skill("hunter")));
    }

    @Test
    void costsSaturateInsteadOfOverflowing() {
        AdaptationView huge = new AdaptationView("huge", "big", "Huge", "", IconRef.item("minecraft:stone"), true, 2, false,
                List.of(Integer.MAX_VALUE, Integer.MAX_VALUE), List.of(1, 1), List.of(1, 1), List.of(), List.of(List.of(), List.of()),
                List.of(), List.of());
        SkillView skill = new SkillView("big", "Big", "", IconRef.item("minecraft:stone"), 0, true, List.of(), List.of(),
                List.of(huge));
        CatalogIndex index = new CatalogIndex(new CatalogView(1L, "en_us", List.of(skill)), Map.of());
        assertEquals(Integer.MAX_VALUE, LearnRules.learn(index, player, huge, 2).knowledgeCost());
    }
}
