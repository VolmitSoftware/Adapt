package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerSkillLineTest extends AdaptTestBase {

    @Test
    @DisplayName("granted xp is realized after the pool is flushed")
    void grantedXpRealizedAfterFlush() {
        PlayerSkillLine line = new PlayerSkillLine();
        line.giveXP(null, 100.0);
        line.flushXpPool(null);
        assertThat(line.getXp()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("knowledge can be granted and spent")
    void knowledgeGrantAndSpend() {
        PlayerSkillLine line = new PlayerSkillLine();
        line.giveKnowledge(100L);
        assertThat(line.spendKnowledge(30)).isTrue();
        assertThat(line.getKnowledge()).isEqualTo(70L);
    }

    @Test
    @DisplayName("spending more knowledge than available fails and leaves the balance intact")
    void overspendingKnowledgeFails() {
        PlayerSkillLine line = new PlayerSkillLine();
        line.giveKnowledge(10L);
        assertThat(line.spendKnowledge(1000)).isFalse();
        assertThat(line.getKnowledge()).isEqualTo(10L);
    }

    @Test
    @DisplayName("adaptation levels are stored and cleared by level zero")
    void adaptationLevelStoredAndCleared() {
        Adaptation<?> a = mock(Adaptation.class);
        lenient().when(a.getName()).thenReturn("testadapt");
        lenient().when(a.getMaxLevel()).thenReturn(10);
        PlayerSkillLine line = new PlayerSkillLine();
        line.setAdaptation(a, 5);
        assertThat(line.getAdaptationLevel("testadapt")).isEqualTo(5);
        line.setAdaptation(a, 0);
        assertThat(line.getAdaptationLevel("testadapt")).isEqualTo(0);
    }

    @Test
    @DisplayName("unlearning an adaptation clears its player attribute modifiers")
    void unlearningClearsAttributeModifiers() {
        Adaptation<?> adaptation = mock(Adaptation.class);
        when(adaptation.getName()).thenReturn("testadapt");
        when(adaptation.getMaxLevel()).thenReturn(10);
        AdaptPlayer owner = mock(AdaptPlayer.class);
        Player player = mock(Player.class);
        when(owner.getPlayer()).thenReturn(player);
        AdaptAttributeService service = mock(AdaptAttributeService.class);
        PlayerSkillLine line = new PlayerSkillLine();
        line.bindRuntimeOwner(owner);

        try (MockedStatic<AdaptAttributeService> attributes = mockStatic(AdaptAttributeService.class)) {
            attributes.when(AdaptAttributeService::get).thenReturn(service);
            line.setAdaptation(adaptation, 5);
            line.setAdaptation(adaptation, 0);
        }

        assertThat(line.getAdaptationLevel("testadapt")).isEqualTo(0);
        verify(service).removeAll(player, "testadapt");
    }

    @Test
    @DisplayName("lowering an adaptation level clears stale attribute strengths")
    void loweringLevelClearsAttributeModifiers() {
        Adaptation<?> adaptation = mock(Adaptation.class);
        when(adaptation.getName()).thenReturn("testadapt");
        when(adaptation.getMaxLevel()).thenReturn(10);
        AdaptPlayer owner = mock(AdaptPlayer.class);
        Player player = mock(Player.class);
        when(owner.getPlayer()).thenReturn(player);
        AdaptAttributeService service = mock(AdaptAttributeService.class);
        PlayerSkillLine line = new PlayerSkillLine();
        line.bindRuntimeOwner(owner);

        try (MockedStatic<AdaptAttributeService> attributes = mockStatic(AdaptAttributeService.class)) {
            attributes.when(AdaptAttributeService::get).thenReturn(service);
            line.setAdaptation(adaptation, 5);
            verify(service, never()).removeAll(player, "testadapt");
            line.setAdaptation(adaptation, 3);
            verify(service).removeAll(player, "testadapt");
            reset(service);
            line.setAdaptation(adaptation, 4);
            verify(service, never()).removeAll(player, "testadapt");
        }

        assertThat(line.getAdaptationLevel("testadapt")).isEqualTo(4);
    }
}
