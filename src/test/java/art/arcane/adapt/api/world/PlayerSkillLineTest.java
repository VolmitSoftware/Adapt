package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

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
}
