package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TragoulTargetProtectionTest {
  @Test
  void protectionDefaultsOffForEachAdaptation() {
    assertThat(new TragoulThorns.Config().ignorePassiveMobs).isFalse();
    assertThat(new TragoulGlobe.Config().ignorePassiveMobs).isFalse();
    assertThat(new TragoulLance.Config().ignorePassiveMobs).isFalse();
    assertThat(new TragoulCorpseExplosion.Config().ignorePassiveMobs).isFalse();
    assertThat(new TragoulPlagueBearer.Config().ignorePassiveMobs).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = EntityType.class, names = {"COW", "SHEEP", "VILLAGER", "WANDERING_TRADER", "ALLAY",
      "WOLF", "BEE", "IRON_GOLEM", "SNOW_GOLEM", "DOLPHIN", "POLAR_BEAR", "PANDA", "LLAMA",
      "ENDERMAN", "PIGLIN", "ZOMBIFIED_PIGLIN", "SPIDER", "CAVE_SPIDER"})
  void passiveAndNeutralSpeciesAreExcludedOnlyWhenEnabled(EntityType type) {
    Entity target = target(type);
    assertThat(AdaptationDamageTargets.allows(target, false)).isTrue();
    assertThat(AdaptationDamageTargets.allows(target, true)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = EntityType.class, names = {"ZOMBIE", "HUSK", "SKELETON", "CREEPER", "GHAST",
      "SLIME", "MAGMA_CUBE", "SHULKER", "PIGLIN_BRUTE", "HOGLIN", "ENDER_DRAGON",
      "WITHER", "WARDEN", "PLAYER"})
  void hostileMobsAndPlayersRemainEligible(EntityType type) {
    Entity target = target(type);
    assertThat(AdaptationDamageTargets.allows(target, false)).isTrue();
    assertThat(AdaptationDamageTargets.allows(target, true)).isTrue();
  }

  private Entity target(EntityType type) {
    Entity target = mock(type.getEntityClass());
    when(target.getType()).thenReturn(type);
    return target;
  }
}
