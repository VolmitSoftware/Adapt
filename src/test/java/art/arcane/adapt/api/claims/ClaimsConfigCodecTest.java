package art.arcane.adapt.api.claims;

import art.arcane.adapt.content.skill.SkillClaims;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimsConfigCodecTest {
  @Test
  void skillConfigurationRetainsEnabledAndOverridesAcrossRepeatedTomlLoads() throws IOException {
    SkillClaims.Config configured = new SkillClaims.Config();
    configured.enabled = true;
    configured.claims.worldAllowlist = List.of("claims_world");
    configured.claims.offlineGraceMillis = 123_000;
    configured.claims.resistanceRanks.rank1 = new ClaimResistance(7, 56, 28, 224);
    configured.claims.resistanceRanks.rank2 = new ClaimResistance(11, 88, 44, 352);
    configured.claims.resistanceRanks.rank3 = new ClaimResistance(13, 104, 52, 416);
    configured.claims.resistanceRanks.rank4 = new ClaimResistance(17, 136, 68, 544);

    String firstWrite = TomlCodec.toToml(configured, "skill-claims");
    SkillClaims.Config firstLoad = TomlCodec.fromToml(firstWrite, SkillClaims.Config.class);
    firstLoad.claims.validate();
    SkillClaims.Config secondLoad = TomlCodec.fromToml(TomlCodec.toToml(firstLoad, "skill-claims"), SkillClaims.Config.class);
    secondLoad.claims.validate();

    assertThat(secondLoad.enabled).isTrue();
    assertThat(secondLoad.claims.worldAllowlist).containsExactly("claims_world");
    assertThat(secondLoad.claims.offlineGraceMillis).isEqualTo(123_000);
    for (int rank = 1; rank <= 4; rank++) {
      assertThat(secondLoad.claims.resistanceRanks.forRank(rank)).isEqualTo(configured.claims.resistanceRanks.forRank(rank));
    }
    assertThat(secondLoad.claims.resistance(new ClaimRanks(true, 4, 0, 0, false), false, ClaimAction.PLACE)).isEqualTo(544);
  }

  @Test
  void defaultFirstWriteReloadsAndRejectsInvalidCurrentResistanceValues() throws IOException {
    String canonical = TomlCodec.toToml(new SkillClaims.Config(), "skill-claims");
    SkillClaims.Config defaults = TomlCodec.fromToml(canonical, SkillClaims.Config.class);
    defaults.claims.validate();
    assertThat(defaults.enabled).isFalse();
    assertThat(defaults.claims.resistanceRanks.rank1.onlineBreaks()).isEqualTo(16);

    String invalid = canonical.replace("onlineBreaks = 16", "onlineBreaks = 0");
    assertThat(invalid).isNotEqualTo(canonical);
    SkillClaims.Config rejected = TomlCodec.fromToml(invalid, SkillClaims.Config.class);
    assertThatThrownBy(rejected.claims::validate).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
  }
}
