package art.arcane.adapt.content.skill.golemancy;

import io.papermc.paper.world.WeatheringCopperState;
import org.bukkit.entity.CopperGolem;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CopperCareTest {
  @ParameterizedTest(name = "{0} -> {1} scraped={2}")
  @CsvSource({
      "EXPOSED, UNAFFECTED, true",
      "WEATHERED, EXPOSED, true",
      "OXIDIZED, WEATHERED, true",
      "OXIDIZED, UNAFFECTED, true",
      "WEATHERED, UNAFFECTED, true",
      "UNAFFECTED, UNAFFECTED, false",
      "EXPOSED, EXPOSED, false",
      "WEATHERED, WEATHERED, false",
      "OXIDIZED, OXIDIZED, false",
      "UNAFFECTED, EXPOSED, false",
      "EXPOSED, WEATHERED, false",
      "WEATHERED, OXIDIZED, false",
      "UNAFFECTED, OXIDIZED, false"
  })
  void scrapePaysOnlyWhenTheStageDrops(WeatheringCopperState before, WeatheringCopperState after, boolean scraped) {
    assertThat(CopperCare.scraped(before, after)).isEqualTo(scraped);
  }

  @Test
  void scrapeNeedsBothReadings() {
    assertThat(CopperCare.scraped(null, WeatheringCopperState.UNAFFECTED)).isFalse();
    assertThat(CopperCare.scraped(WeatheringCopperState.OXIDIZED, null)).isFalse();
  }

  @Test
  void waxedRecognisesOnlyTheWaxedState() {
    assertThat(CopperCare.waxed(CopperGolem.Oxidizing.waxed())).isTrue();
    assertThat(CopperCare.waxed(CopperGolem.Oxidizing.unset())).isFalse();
    assertThat(CopperCare.waxed(CopperGolem.Oxidizing.atTime(1200L))).isFalse();
    assertThat(CopperCare.waxed(null)).isFalse();
  }

  @Test
  void newlyWaxedRequiresTheWaxToBeFresh() {
    assertThat(CopperCare.newlyWaxed(false, true)).isTrue();
    assertThat(CopperCare.newlyWaxed(true, true)).isFalse();
    assertThat(CopperCare.newlyWaxed(false, false)).isFalse();
    assertThat(CopperCare.newlyWaxed(true, false)).isFalse();
  }

  @Test
  void waxPaymentIsClaimedOncePerGolem() {
    CopperGolem golem = StoredEntityData.entity(CopperGolem.class);

    assertThat(CopperCare.claimWaxPayment(golem)).isTrue();
    assertThat(golem.getPersistentDataContainer().get(CopperCare.WAX_PAID_KEY, PersistentDataType.BYTE)).isEqualTo((byte) 1);
    assertThat(CopperCare.claimWaxPayment(golem)).isFalse();
  }
}
