package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeKey;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SorterFetchesTest {
  private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000002701");
  private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000002702");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-000000002703");
  private static final NodeKey INPUT = new NodeKey(SorterYard.WORLD, 0, 64, 0, BlockFace.UP);
  private static final NodeKey OTHER = new NodeKey(SorterYard.WORLD, 9, 64, 0, BlockFace.UP);

  private final SorterItems items = new SorterItems();
  private final SorterFetches fetches = new SorterFetches(() -> 60_000L);

  @Test
  void aPlayersNewRequestReplacesTheirOldOneAndRequestsAreFoundByTheirInput() {
    fetches.request(ALEX, INPUT, items.item(Material.COBBLESTONE, 1), 32, 0L);
    fetches.request(ALEX, INPUT, items.item(Material.DIRT, 1), 16, 10L);
    fetches.request(SAM, OTHER, items.item(Material.STONE, 1), 8, 20L);

    assertThat(fetches.open(INPUT, 30L)).singleElement().satisfies(request -> {
      assertThat(request.player()).isEqualTo(ALEX);
      assertThat(request.item().getType()).isEqualTo(Material.DIRT);
      assertThat(request.remaining()).isEqualTo(16);
    });
    assertThat(fetches.open(OTHER, 30L)).hasSize(1);
    assertThat(fetches.next(INPUT, items.item(Material.DIRT, 1), 30L)).isPresent();
    assertThat(fetches.next(INPUT, items.item(Material.COBBLESTONE, 1), 30L)).isEmpty();
  }

  @Test
  void reservingTakesAtMostWhatRemainsAndReleasingGivesItBack() {
    SorterFetches.Request request = fetches.request(ALEX, INPUT, items.item(Material.COBBLESTONE, 1), 20, 0L);

    assertThat(request.reserve(12)).isEqualTo(12);
    assertThat(request.reserve(12)).isEqualTo(8);
    assertThat(request.reserve(12)).isZero();
    assertThat(fetches.open(INPUT, 1L)).isEmpty();

    request.release(5);
    assertThat(request.remaining()).isEqualTo(5);
    assertThat(fetches.open(INPUT, 1L)).hasSize(1);
  }

  @Test
  void requestsExpireAndSweepingForgetsFinishedOnes() {
    SorterFetches.Request done = fetches.request(ALEX, INPUT, items.item(Material.COBBLESTONE, 1), 4, 0L);
    fetches.request(SAM, INPUT, items.item(Material.DIRT, 1), 4, 0L);
    done.reserve(4);

    assertThat(fetches.open(INPUT, 59_999L)).hasSize(1);
    assertThat(fetches.open(INPUT, 60_000L)).isEmpty();

    fetches.sweep(1L);
    assertThat(fetches.isEmpty()).isFalse();
    fetches.sweep(60_000L);
    assertThat(fetches.isEmpty()).isTrue();
  }

  @Test
  void plansBelongToOneGolemAndExpire() {
    SorterPlans plans = new SorterPlans();
    plans.fetch(GOLEM, ALEX, items.item(Material.COBBLESTONE, 1), 10, 0L);

    assertThat(plans.get(GOLEM, 1L)).hasValueSatisfying(plan -> assertThat(plan).isInstanceOf(SorterPlans.Fetch.class));
    plans.restock(GOLEM, new ContainerId(SorterYard.WORLD, 1, 64, 1), items.item(Material.TORCH, 1), 5L);
    assertThat(plans.get(GOLEM, 6L)).hasValueSatisfying(plan -> assertThat(plan).isInstanceOf(SorterPlans.Restock.class));
    assertThat(plans.get(GOLEM, 5L + SorterPlans.PLAN_MILLIS)).isEmpty();

    plans.fetch(GOLEM, ALEX, items.item(Material.COBBLESTONE, 1), 10, 0L);
    plans.forget(GOLEM);
    assertThat(plans.get(GOLEM, 1L)).isEmpty();
    plans.fetch(GOLEM, ALEX, items.item(Material.COBBLESTONE, 1), 10, 0L);
    plans.sweep(SorterPlans.PLAN_MILLIS);
    assertThat(plans.isEmpty()).isTrue();
  }
}
