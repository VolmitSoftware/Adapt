package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.testkit.FakeGameContent;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;

class ItemDurabilityTest {
  @TempDir
  Path data;

  private TestAdapt adapt;

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
      assertThat(adapt.handlerFailures()).isEmpty();
    }
  }

  private TestPlatform start(FakeGameContent game) {
    TestPlatform platform = TestPlatform.builder(data).build();
    platform.host().runGame(game);
    adapt = TestAdapt.start(platform, List.of());
    return platform;
  }

  @Test
  void perStackVersionsSetTheMaximumOnTheStack() {
    start(FakeGameContent.v1_21_1());
    FakeItem shield = FakeItem.tool("shield", 336);

    ItemDurability.setMaxDamage(shield, 1200);

    assertThat(ItemDurability.perStack()).isTrue();
    assertThat(shield.maxDamage()).isEqualTo(1200);
    assertThat(shield.data().has(ItemDurability.MAX_DURABILITY)).isFalse();
  }

  @Test
  void olderVersionsStoreTheWantedMaximumAndDropItWhenItMatchesTheType() {
    start(FakeGameContent.v1_20_1());
    FakeItem shield = FakeItem.tool("shield", 336);

    ItemDurability.setMaxDamage(shield, 1200);

    assertThat(ItemDurability.perStack()).isFalse();
    assertThat(shield.maxDamage()).isEqualTo(336);
    assertThat(shield.data().get(ItemDurability.MAX_DURABILITY)).isEqualTo(1200);

    ItemDurability.setMaxDamage(shield, 336);

    assertThat(shield.data().isEmpty()).isTrue();
  }

  @Test
  void maxDamageReportsTheNativeOrStoredMaximum() {
    start(FakeGameContent.v1_20_1());
    FakeItem shield = FakeItem.tool("shield", 336);

    assertThat(ItemDurability.maxDamage(shield)).isEqualTo(336);

    ItemDurability.setMaxDamage(shield, 1200);

    assertThat(ItemDurability.maxDamage(shield)).isEqualTo(1200);
    assertThat(shield.maxDamage()).isEqualTo(336);
  }

  @Test
  void maxDamageOnPerStackVersionsIsTheStackValue() {
    start(FakeGameContent.v1_21_1());
    FakeItem shield = FakeItem.tool("shield", 336);

    ItemDurability.setMaxDamage(shield, 1200);

    assertThat(ItemDurability.maxDamage(shield)).isEqualTo(1200);
  }

  @Test
  void olderVersionsScaleDamageInTheItemDamageEvent() {
    TestPlatform platform = start(FakeGameContent.v1_20_1());
    FakePlayer player = platform.player("Smith", 0.5D, 61D, 0.5D);
    FakeItem pickaxe = FakeItem.tool("iron_pickaxe", 250);
    ItemDurability.setMaxDamage(pickaxe, 125);

    PlayerItemDamageEvent event = adapt.post(new PlayerItemDamageEvent(player, pickaxe, 3, 3));

    assertThat(event.damage()).isEqualTo(6);
  }

  @Test
  void theListenerKeepsOrDropsADamagePointByChance() {
    TestPlatform platform = start(FakeGameContent.v1_20_1());
    FakePlayer player = platform.player("Smith", 0.5D, 61D, 0.5D);
    FakeItem shield = FakeItem.tool("shield", 336);
    ItemDurability.setMaxDamage(shield, 1200);
    PlayerItemDamageEvent kept = new PlayerItemDamageEvent(player, shield, 1, 1);
    PlayerItemDamageEvent ignored = new PlayerItemDamageEvent(player, shield, 1, 1);

    new ItemDurability(() -> 0.1D).on(kept);
    new ItemDurability(() -> 0.9D).on(ignored);

    assertThat(kept.damage()).isEqualTo(1);
    assertThat(ignored.damage()).isZero();
  }

  @Test
  void perStackVersionsRegisterNoListener() {
    TestPlatform platform = start(FakeGameContent.v1_21_1());
    FakePlayer player = platform.player("Smith", 0.5D, 61D, 0.5D);
    FakeItem pickaxe = FakeItem.tool("iron_pickaxe", 250);
    pickaxe.data().set(ItemDurability.MAX_DURABILITY, 125);

    PlayerItemDamageEvent event = adapt.post(new PlayerItemDamageEvent(player, pickaxe, 3, 3));

    assertThat(event.damage()).isEqualTo(3);
  }

  @Test
  void scaledDamageRoundsByTheFractionalPart() {
    assertThat(ItemDurability.scaledDamage(1, 336, 1200, 0.27D)).isEqualTo(1);
    assertThat(ItemDurability.scaledDamage(1, 336, 1200, 0.29D)).isEqualTo(0);
    assertThat(ItemDurability.scaledDamage(10, 250, 125, 0.5D)).isEqualTo(20);
    assertThat(ItemDurability.scaledDamage(3, 100, 100, 0.0D)).isEqualTo(3);
  }

  @Test
  void averageUsesMatchTheWantedMaximum() {
    SplittableRandom random = new SplittableRandom(7L);
    int typeMax = 336;
    int wantedMax = 1200;
    long uses = 0;
    int runs = 200;
    for (int run = 0; run < runs; run++) {
      int damage = 0;
      while (damage < typeMax) {
        damage += ItemDurability.scaledDamage(1, typeMax, wantedMax, random.nextDouble());
        uses++;
      }
    }

    assertThat((double) uses / runs).isBetween(wantedMax * 0.97D, wantedMax * 1.03D);
  }
}
