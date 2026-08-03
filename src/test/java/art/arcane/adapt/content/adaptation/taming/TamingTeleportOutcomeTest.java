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
  void fetchCreditsOnlyConfirmedTeleportSuccess() {
    assertThat(TamingFetch.successfulFetchTeleport(true, null)).isTrue();
    assertThat(TamingFetch.successfulFetchTeleport(false, null)).isFalse();
    assertThat(TamingFetch.successfulFetchTeleport(null, null)).isFalse();
    assertThat(TamingFetch.successfulFetchTeleport(true, new IllegalStateException())).isFalse();
  }

  @Test
  void fetchDefersDeliveryEffectsAndRewardsUntilCompletion() throws Exception {
    String source = Files.readString(FETCH_SOURCE);
    int teleport = source.indexOf("PaperCompat.teleportAsync(item, to)");
    int completion = source.indexOf("private void completeFetchTeleport");
    int outcome = source.indexOf("successfulFetchTeleport(success, failure)", completion);
    int finish = source.indexOf("private void finishFetchTeleportOwned", outcome);
    int pickup = source.indexOf("item.setPickupDelay(0)", finish);
    int effect = source.indexOf("fx(from, FxPriority.TRAIL)", pickup);
    int reward = source.indexOf("addStat(owner", effect);

    assertThat(teleport).isGreaterThanOrEqualTo(0);
    assertThat(completion).isGreaterThan(teleport);
    assertThat(outcome).isGreaterThan(completion);
    assertThat(finish).isGreaterThan(outcome);
    assertThat(pickup).isGreaterThan(finish);
    assertThat(effect).isGreaterThan(pickup);
    assertThat(reward).isGreaterThan(effect);
    assertThat(source)
        .doesNotContain("J.teleport(item, to)")
        .doesNotContain(".stream().toList()");
  }
}
