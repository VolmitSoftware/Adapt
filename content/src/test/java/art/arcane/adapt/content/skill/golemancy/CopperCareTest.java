package art.arcane.adapt.content.skill.golemancy;

import art.arcane.adapt.platform.entity.MobRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CopperCareTest {
  private static final int UNREAD = -1;

  enum Stage {
    UNAFFECTED,
    EXPOSED,
    WEATHERED,
    OXIDIZED
  }

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
  void scrapePaysOnlyWhenTheStageDrops(Stage before, Stage after, boolean scraped) {
    assertThat(CopperCare.scraped(before.ordinal(), after.ordinal())).isEqualTo(scraped);
  }

  @Test
  void scrapeNeedsBothReadings() {
    assertThat(CopperCare.scraped(UNREAD, Stage.UNAFFECTED.ordinal())).isFalse();
    assertThat(CopperCare.scraped(Stage.OXIDIZED.ordinal(), UNREAD)).isFalse();
  }

  @Test
  void newlyWaxedRequiresTheWaxToBeFresh() {
    assertThat(CopperCare.newlyWaxed(false, true)).isTrue();
    assertThat(CopperCare.newlyWaxed(true, true)).isFalse();
    assertThat(CopperCare.newlyWaxed(false, false)).isFalse();
    assertThat(CopperCare.newlyWaxed(true, false)).isFalse();
  }

  @Test
  void scrapingPaysOnlyForStagesTheGolemOxidizedOnItsOwn() {
    MobRef golem = copperGolem();
    CopperCare.recordStageFloor(golem, Stage.OXIDIZED.ordinal());

    assertThat(CopperCare.claimScrape(golem, Stage.OXIDIZED.ordinal(), Stage.WEATHERED.ordinal())).isFalse();
    assertThat(CopperCare.claimScrape(golem, Stage.OXIDIZED.ordinal(), Stage.WEATHERED.ordinal())).isTrue();
    assertThat(CopperCare.claimScrape(golem, Stage.WEATHERED.ordinal(), Stage.EXPOSED.ordinal())).isFalse();
    assertThat(CopperCare.claimScrape(golem, Stage.EXPOSED.ordinal(), Stage.UNAFFECTED.ordinal())).isFalse();

    assertThat(CopperCare.claimScrape(golem, Stage.EXPOSED.ordinal(), Stage.UNAFFECTED.ordinal())).isTrue();
    assertThat(CopperCare.claimScrape(golem, Stage.WEATHERED.ordinal(), Stage.EXPOSED.ordinal())).isTrue();
    assertThat(CopperCare.claimScrape(golem, Stage.EXPOSED.ordinal(), Stage.UNAFFECTED.ordinal())).isTrue();
    assertThat(CopperCare.claimScrape(golem, Stage.UNAFFECTED.ordinal(), Stage.UNAFFECTED.ordinal())).isFalse();
  }

  @Test
  void aGolemWithoutAFloorPaysFromItsFirstScrape() {
    MobRef golem = copperGolem();

    assertThat(CopperCare.claimScrape(golem, Stage.EXPOSED.ordinal(), Stage.UNAFFECTED.ordinal())).isTrue();
    assertThat(CopperCare.claimScrape(golem, Stage.EXPOSED.ordinal(), Stage.UNAFFECTED.ordinal())).isTrue();
  }

  @Test
  void aRestoredGolemHasItsStageFloorAndItsWaxAlreadyPaid() {
    MobRef golem = copperGolem();

    CopperCare.restored(golem, Stage.EXPOSED.ordinal());

    assertThat(golem.data().get(CopperCare.STAGE_FLOOR_KEY)).isEqualTo(1);
    assertThat(CopperCare.claimWaxPayment(golem)).isFalse();
    assertThat(CopperCare.claimScrape(golem, Stage.EXPOSED.ordinal(), Stage.UNAFFECTED.ordinal())).isFalse();
  }

  @Test
  void waxPaymentIsClaimedOncePerGolem() {
    MobRef golem = copperGolem();

    assertThat(CopperCare.claimWaxPayment(golem)).isTrue();
    assertThat(golem.data().get(CopperCare.WAX_PAID_KEY)).isEqualTo((byte) 1);
    assertThat(CopperCare.claimWaxPayment(golem)).isFalse();
  }

  private static MobRef copperGolem() {
    MobRef golem = StoredEntityData.entity(MobRef.class);
    when(golem.type()).thenReturn(CopperCare.COPPER_GOLEM);
    return golem;
  }
}
