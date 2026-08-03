package art.arcane.adapt.content.adaptation.seaborrne;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SeaborneFishWhispererLuckAttributeTest {
  private static final Path SOURCE =
      Path.of("src/main/java/art/arcane/adapt/content/adaptation/seaborrne/SeaborneFishWhisperer.java");

  @Test
  void luckAmountMatchesLegacyPotionAmplifierPlusOne() {
    assertThat(SeaborneFishWhisperer.luckAmount(1, 5)).isEqualTo(1);
    assertThat(SeaborneFishWhisperer.luckAmount(3, 5)).isEqualTo(3);
    assertThat(SeaborneFishWhisperer.luckAmount(5, 5)).isEqualTo(5);
  }

  @Test
  void luckAmountClampsAtMaxLevelTier() {
    assertThat(SeaborneFishWhisperer.luckAmount(9, 5)).isEqualTo(5);
    assertThat(SeaborneFishWhisperer.luckAmount(0, 5)).isEqualTo(1);
  }

  @Test
  void charmSessionsStartOnlyAfterTheirGap() {
    assertThat(SeaborneFishWhisperer.startsNewCharmSession(null, 10_000L)).isTrue();
    assertThat(SeaborneFishWhisperer.startsNewCharmSession(1_000L, 12_999L)).isFalse();
    assertThat(SeaborneFishWhisperer.startsNewCharmSession(1_000L, 13_000L)).isTrue();
  }

  @Test
  void velocityCreditRequiresARealFiniteChange() {
    Vector stationary = new Vector();
    Vector moved = new Vector(0.1D, 0D, 0D);
    Vector invalid = new Vector(Double.NaN, 0D, 0D);

    assertThat(SeaborneFishWhisperer.hasVelocityChange(stationary, moved)).isTrue();
    assertThat(SeaborneFishWhisperer.hasVelocityChange(stationary, stationary)).isFalse();
    assertThat(SeaborneFishWhisperer.hasVelocityChange(stationary, invalid)).isFalse();
    assertThat(SeaborneFishWhisperer.hasVelocityChange(null, moved)).isFalse();
  }

  @Test
  void fishCreditFollowsSuccessfulFishOwnedNudgeAndReturnsToPlayerOwner() throws IOException {
    String source = Files.readString(SOURCE);
    int flowStart = source.indexOf("private void nudgeAndCreditFish");
    int creditStart = source.indexOf("private void creditCharmedFish", flowStart);
    String flow = source.substring(flowStart, creditStart);

    assertThat(flow.indexOf("if (!nudgeFish")).isLessThan(flow.indexOf("charmedRecently.put"));
    assertThat(flow).contains("J.runEntity(player, () -> creditCharmedFish(player))");
    assertThat(source)
        .contains("addStat(player, \"seaborne.fish-whisperer.charmed\", 1)")
        .contains("if (scheduled >= MAX_FISH)")
        .contains(
            "PaperCompat.nearbyEntitiesByType(Fish.class, center, range, range, range)",
            "PaperCompat.nearbyEntitiesByType(\n        Mob.class, victimLocation, range, range, range)"
        );
  }
}
