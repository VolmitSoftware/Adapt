package art.arcane.adapt.gameplay;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixturesSpawnTest {
    @Test
    void zombieFamilyMobsAreForcedAdult() {
        assertThat(Fixtures.rollsBaby("husk")).isTrue();
        assertThat(Fixtures.rollsBaby("minecraft:zombie")).isTrue();
        assertThat(Fixtures.rollsBaby("drowned")).isTrue();
        assertThat(Fixtures.rollsBaby("zombie_villager")).isTrue();
        assertThat(Fixtures.rollsBaby("zombified_piglin")).isTrue();
    }

    @Test
    void otherMobsKeepTheirSpawnData() {
        assertThat(Fixtures.rollsBaby("cow")).isFalse();
        assertThat(Fixtures.rollsBaby("skeleton")).isFalse();
        assertThat(Fixtures.rollsBaby("piglin")).isFalse();
    }
}
