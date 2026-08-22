package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TamingTeleportOutcomeTest {
  private static final Path BEAST_RECALL_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/taming/TamingBeastRecall.java");
  private static final Path FETCH_SOURCE = Path.of(
      "src/main/java/art/arcane/adapt/content/adaptation/taming/TamingFetch.java");

  @Test
  void beastRecallCommitsOnlyConfirmedTeleportSuccess() {
    assertThat(TamingBeastRecall.successfulRecallTeleport(true, null)).isTrue();
    assertThat(TamingBeastRecall.successfulRecallTeleport(false, null)).isFalse();
    assertThat(TamingBeastRecall.successfulRecallTeleport(null, null)).isFalse();
    assertThat(TamingBeastRecall.successfulRecallTeleport(true, new IllegalStateException())).isFalse();
  }

  @Test
  void beastRecallDefersFallResetCostsCooldownAndRewardsUntilCompletion() throws Exception {
    String source = Files.readString(BEAST_RECALL_SOURCE);
    int teleport = source.indexOf("PaperCompat.teleportAsync(tameable, safe)");
    int completion = source.indexOf("private void completeRecallTeleport");
    int outcome = source.indexOf("successfulRecallTeleport(success, failure)", completion);
    int fallReset = source.indexOf("tameable.setFallDistance(0)", outcome);
    int cooldown = source.indexOf("scan.player.setCooldown", fallReset);
    int cost = source.indexOf("scan.player.setFoodLevel", cooldown);
    int reward = source.indexOf("addStat(scan.player", cost);

    assertThat(teleport).isGreaterThanOrEqualTo(0);
    assertThat(completion).isGreaterThan(teleport);
    assertThat(outcome).isGreaterThan(completion);
    assertThat(fallReset).isGreaterThan(outcome);
    assertThat(cooldown).isGreaterThan(fallReset);
    assertThat(cost).isGreaterThan(cooldown);
    assertThat(reward).isGreaterThan(cost);
    assertThat(source).doesNotContain("J.teleport(tameable, safe)");
  }

  @Test
  void fetchRequiresTheWolfToCarryAndDropTheItemWithoutTeleportingIt() throws Exception {
    String source = Files.readString(FETCH_SOURCE);
    int pickup = source.indexOf("private void pickUpItemOwned");
    int removal = source.indexOf("item.remove()", pickup);
    int returnPath = source.indexOf("private void steerWolfHomeOwned", removal);
    int delivery = source.indexOf("private void releaseCarriedOwned", returnPath);
    int dropped = source.indexOf("wolf.getWorld().dropItem(from, carried)", delivery);
    int reward = source.indexOf("creditFetch(job.owner)", dropped);

    assertThat(pickup).isGreaterThanOrEqualTo(0);
    assertThat(removal).isGreaterThan(pickup);
    assertThat(returnPath).isGreaterThan(removal);
    assertThat(delivery).isGreaterThan(returnPath);
    assertThat(dropped).isGreaterThan(delivery);
    assertThat(reward).isGreaterThan(dropped);
    assertThat(source)
        .doesNotContain("teleportAsync(item")
        .doesNotContain("fallBackToTeleport")
        .doesNotContain("deliverItem(")
        .doesNotContain(".stream().toList()");
  }
}
