package art.arcane.adapt.api.mutation;

import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MutationConfigTest {
  @Test
  void nullProfilesRestoreDefaultsDuringLegacyNormalization() {
    MutationConfig config = Json.fromJson("{\"deepblood\":null,\"galeLung\":null}", MutationConfig.class);

    config.normalize();

    assertThat(config.getDeepblood()).isNotNull();
    assertThat(config.getGaleLung()).isNotNull();
    assertThat(config.getDeepblood().getMaximumIchor()).isEqualTo(100D);
  }

  @Test
  void explicitEmptyDomainMembershipRemainsEmpty() {
    MutationConfig config = Json.fromJson("{\"domainMembership\":{\"body\":[]}}", MutationConfig.class);

    config.normalize();

    assertThat(config.skills(MutationDomain.BODY)).isEmpty();
    assertThat(config.skills(MutationDomain.HUNT)).isNotEmpty();
  }

  @Test
  void runtimeWorkAndDurableResourceCeilingsCannotBeRaisedPastThePlanCaps() {
    MutationConfig config = Json.fromJson("""
        {
          "bastionSpine":{"maximumTargets":999},
          "packmind":{"maximumTempo":999,"maximumMembers":999},
          "umbralEcho":{"maximumTargetMemories":999},
          "livingLattice":{"maximumRootCharge":999,"maximumStructures":999},
          "deepblood":{"maximumIchor":999},
          "mycelialNerve":{"maximumRecipients":999},
          "gravebloom":{"maximumBlooms":999,"maximumAnimals":999}
        }
        """, MutationConfig.class);

    config.normalize();

    assertThat(config.getBastionSpine().getMaximumTargets()).isEqualTo(12);
    assertThat(config.getPackmind().getMaximumTempo()).isEqualTo(6);
    assertThat(config.getPackmind().getMaximumMembers()).isEqualTo(8);
    assertThat(config.getUmbralEcho().getMaximumTargetMemories()).isEqualTo(8);
    assertThat(config.getLivingLattice().getMaximumRootCharge()).isEqualTo(12D);
    assertThat(config.getLivingLattice().getMaximumStructures()).isEqualTo(3);
    assertThat(config.getDeepblood().getMaximumIchor()).isEqualTo(100D);
    assertThat(config.getMycelialNerve().getMaximumRecipients()).isEqualTo(8);
    assertThat(config.getGravebloom().getMaximumBlooms()).isEqualTo(3);
    assertThat(config.getGravebloom().getMaximumAnimals()).isEqualTo(8);
  }

  @Test
  void extremeDurationsAndDelaysNormalizeBelowOverflowBoundaries() {
    MutationConfig config = Json.fromJson("""
        {
          "switchCooldownMillis":9223372036854775807,
          "paradoxScar":{"echoLifetimeMillis":9223372036854775807,"hostileCollapseTicks":2147483647},
          "deepblood":{"maximumDepthY":2147483647,"aboveGroundHalfLifeMillis":9223372036854775807},
          "gravebloom":{"lifetimeMillis":9223372036854775807,"pulseTicks":2147483647},
          "resonantFormula":{"sigilLifetimeMillis":9223372036854775807,"echoDelayTicks":2147483647}
        }
        """, MutationConfig.class);

    config.normalize();

    assertThat(config.getSwitchCooldownMillis()).isEqualTo(MutationLimits.MAX_DURATION_MILLIS);
    assertThat(config.getParadoxScar().getEchoLifetimeMillis()).isEqualTo(MutationLimits.MAX_DURATION_MILLIS);
    assertThat(config.getParadoxScar().getHostileCollapseTicks()).isEqualTo(MutationLimits.MAX_DELAY_TICKS);
    assertThat(config.getDeepblood().getMaximumDepthY()).isEqualTo(2_048);
    assertThat(config.getDeepblood().getAboveGroundHalfLifeMillis()).isEqualTo(MutationLimits.MAX_DURATION_MILLIS);
    assertThat(config.getGravebloom().getLifetimeMillis()).isEqualTo(MutationLimits.MAX_DURATION_MILLIS);
    assertThat(config.getGravebloom().getPulseTicks()).isEqualTo(MutationLimits.MAX_DELAY_TICKS);
    assertThat(config.getResonantFormula().getSigilLifetimeMillis()).isEqualTo(MutationLimits.MAX_DURATION_MILLIS);
    assertThat(config.getResonantFormula().getEchoDelayTicks()).isEqualTo(MutationLimits.MAX_DELAY_TICKS);
    assertThat(config.getSwitchCooldownMillis()).isLessThan(Long.MAX_VALUE - System.currentTimeMillis());
  }

  @Test
  void completeDefaultCatalogRoundTripsThroughTheCanonicalTomlShape() throws Exception {
    String toml = TomlCodec.toToml(MutationConfig.defaults(), "mutations");

    MutationConfig parsed = TomlCodec.fromToml(toml, MutationConfig.class);
    parsed.normalize();

    assertThat(parsed.isEnabled()).isFalse();
    assertThat(parsed.getCooperativeConsentMode()).isEqualTo(MutationConfig.ConsentMode.EXPLICIT);
    assertThat(parsed.getDomainMembership()).hasSize(MutationDomain.values().length);
    for (MutationType type : MutationType.values()) {
      assertThat(parsed.profile(type)).isNotNull();
      assertThat(parsed.profile(type).isEnabled()).isTrue();
    }
  }

  @Test
  void explicitTomlOptInRemainsEnabled() throws Exception {
    MutationConfig enabled = Json.fromJson("{\"enabled\":true}", MutationConfig.class);
    enabled.normalize();
    String toml = TomlCodec.toToml(enabled, "mutations");

    MutationConfig parsed = TomlCodec.fromToml(toml, MutationConfig.class);
    parsed.normalize();

    assertThat(parsed.isEnabled()).isTrue();
  }

  @Test
  void worldBlacklistsRequireCanonicalNamespacedKeys() {
    MutationConfig valid = Json.fromJson(
        "{\"worldBlacklist\":[\" minecraft:overworld \",\"iris:floating_islands\"]}",
        MutationConfig.class
    );

    valid.normalize();

    assertThat(valid.getWorldBlacklist()).containsExactly("minecraft:overworld", "iris:floating_islands");

    MutationConfig invalid = Json.fromJson("{\"worldBlacklist\":[\"world\"]}", MutationConfig.class);
    assertThatThrownBy(invalid::normalize)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fully qualified namespaced key");
  }
}
