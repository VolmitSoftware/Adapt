package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.engine.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TragoulTargetProtectionTest {
  private static final Set<String> ENEMY_TYPES = Set.of("ENDERMAN", "PIGLIN", "ZOMBIFIED_PIGLIN", "SPIDER", "CAVE_SPIDER",
      "ZOMBIE", "HUSK", "SKELETON", "CREEPER", "GHAST", "SLIME", "MAGMA_CUBE", "SHULKER", "PIGLIN_BRUTE", "HOGLIN",
      "ENDER_DRAGON", "WITHER", "WARDEN");
  private static final Loc ORIGIN = new Loc(new FakeWorld("world"), 0D, 64D, 0D);

  @Test
  void protectionDefaultsOffForEachAdaptation() {
    assertThat(new TragoulThorns.Config().ignorePassiveMobs).isFalse();
    assertThat(new TragoulGlobe.Config().ignorePassiveMobs).isFalse();
    assertThat(new TragoulLance.Config().ignorePassiveMobs).isFalse();
    assertThat(new TragoulCorpseExplosion.Config().ignorePassiveMobs).isFalse();
    assertThat(new TragoulPlagueBearer.Config().ignorePassiveMobs).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"COW", "SHEEP", "VILLAGER", "WANDERING_TRADER", "ALLAY",
      "WOLF", "BEE", "IRON_GOLEM", "SNOW_GOLEM", "DOLPHIN", "POLAR_BEAR", "PANDA", "LLAMA",
      "ENDERMAN", "PIGLIN", "ZOMBIFIED_PIGLIN", "SPIDER", "CAVE_SPIDER"})
  void passiveAndNeutralSpeciesAreExcludedOnlyWhenEnabled(String type) {
    EntityRef target = target(type);
    assertThat(AdaptationDamageTargets.allows(target, false)).isTrue();
    assertThat(AdaptationDamageTargets.allows(target, true)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"ZOMBIE", "HUSK", "SKELETON", "CREEPER", "GHAST",
      "SLIME", "MAGMA_CUBE", "SHULKER", "PIGLIN_BRUTE", "HOGLIN", "ENDER_DRAGON",
      "WITHER", "WARDEN", "PLAYER"})
  void hostileMobsAndPlayersRemainEligible(String type) {
    EntityRef target = target(type);
    assertThat(AdaptationDamageTargets.allows(target, false)).isTrue();
    assertThat(AdaptationDamageTargets.allows(target, true)).isTrue();
  }

  private EntityRef target(String type) {
    if (type.equals("PLAYER")) {
      return new FakePlayer("Target", ORIGIN);
    }
    FakeLiving target = new FakeLiving(Id.minecraft(type.toLowerCase(Locale.ROOT)), ORIGIN);
    if (ENEMY_TYPES.contains(type)) {
      target.trait(EntityTrait.ENEMY);
    }
    return target;
  }
}
