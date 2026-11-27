package art.arcane.adapt.content.skill;

import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpEarnings;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SkillAgilityRuntimeTest {
  private static final int MOVES_PER_ROUND = 20;
  private static final int ROUNDS = 4;

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private double x = 0.5D;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    platform.items().hardness("oak_log", 2F, 2F);
    player = platform.player("Runner", x, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillAxes.class, SkillAgility.class));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void walkingPaysMoveXpOncePerPulseInsteadOfPerMoveEvent() {
    walkRounds();

    XpEarnings.SourceSnapshot move = source("adapt:agility/adapt:agility:move").orElseThrow();
    assertThat(move.credited()).isPositive();
    assertThat(move.awards()).isLessThanOrEqualTo(ROUNDS);
  }

  @Test
  void walkingToATreeDoesNotDiscountTheChop() {
    walkRounds();
    player.inventory().setMainHand(FakeItem.tool("iron_axe", 250));
    int logX = (int) Math.floor(x) + 1;
    platform.world().set(logX, 62, 0, "oak_log");

    adapt.post(new BlockBreakEvent(player, platform.world().blockAt(logX, 62, 0), true, 0));

    XpEarnings.SourceSnapshot chop = source("adapt:axes/adapt:axes:chop").orElseThrow();
    assertThat(chop.contextual()).isGreaterThanOrEqualTo(chop.requested() * 0.75D);
  }

  private void walkRounds() {
    for (int round = 0; round < ROUNDS; round++) {
      for (int step = 0; step < MOVES_PER_ROUND; step++) {
        Loc from = new Loc(platform.world(), x, 61D, 0.5D);
        x += 0.215D;
        Loc to = new Loc(platform.world(), x, 61D, 0.5D);
        player.moveTo(to);
        adapt.post(new PlayerMoveEvent(player, from, to));
      }
      long due = System.currentTimeMillis() + 1100L;
      adapt.awaitTicks(() -> System.currentTimeMillis() >= due, 5_000L);
    }
    adapt.awaitTicks(() -> source("adapt:agility/adapt:agility:move").isPresent(), 5_000L);
  }

  private Optional<XpEarnings.SourceSnapshot> source(String key) {
    return profile.getData().getXpEarnings().snapshot().stream()
        .filter(snapshot -> snapshot.source().equals(key))
        .findFirst();
  }
}
