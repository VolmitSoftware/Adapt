package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.claims.ClaimAction;
import art.arcane.adapt.api.claims.ClaimRanks;
import art.arcane.adapt.content.adaptation.claims.ClaimsCitizenship;
import art.arcane.adapt.content.adaptation.claims.ClaimsExpansion;
import art.arcane.adapt.content.adaptation.claims.ClaimsFortification;
import art.arcane.adapt.content.adaptation.claims.ClaimsFounding;
import art.arcane.adapt.content.adaptation.claims.ClaimsHomestead;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillClaimsTest {
  @Test
  void optionalSkillDefaultsToDisabledWithRequestedResistanceAndLimits() {
    SkillClaims.Config config = new SkillClaims.Config();
    ClaimRanks first = new ClaimRanks(true, 1, 0, 0, false);
    ClaimRanks last = new ClaimRanks(true, 4, 4, 4, true);

    assertThat(config.enabled).isFalse();
    assertThat(config.claims.chunkCapacity(first)).isEqualTo(9);
    assertThat(config.claims.memberCapacity(first)).isZero();
    assertThat(config.claims.chunkCapacity(last)).isEqualTo(121);
    assertThat(config.claims.memberCapacity(last)).isEqualTo(32);
    assertThat(config.claims.resistance(first, true, ClaimAction.BREAK)).isEqualTo(16);
    assertThat(config.claims.resistance(last, false, ClaimAction.PLACE)).isEqualTo(2048);
  }

  @Test
  void everyBranchIsPermanentAndRequiresFounding() {
    ClaimsFounding.Config founding = new ClaimsFounding.Config();
    List<AdaptationConfig> branches = List.of(new ClaimsFortification.Config(), new ClaimsCitizenship.Config(),
        new ClaimsExpansion.Config(), new ClaimsHomestead.Config());

    assertThat(founding.permanent).isTrue();
    assertThat(founding.requires).isEmpty();
    assertThat(founding.maxLevel).isEqualTo(1);
    for (AdaptationConfig branch : branches) {
      assertThat(branch.permanent).isTrue();
      assertThat(branch.requires).containsExactly("claims-founding:1");
    }
    assertThat(branches).extracting(config -> config.maxLevel).containsExactly(4, 4, 4, 1);
  }
}
