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
    SorterFetches.Request request = fetches.request(ALEX, INPUT, items.item(Material.COBBLESTONE, 1), 10, 0L);
    plans.fetch(GOLEM, request, items.item(Material.COBBLESTONE, 1), request.reserve(10), 0L);

    assertThat(plans.get(GOLEM, 1L)).hasValueSatisfying(plan -> assertThat(plan).isInstanceOf(SorterPlans.Fetch.class));
    plans.restock(GOLEM, new ContainerId(SorterYard.WORLD, 1, 64, 1), items.item(Material.TORCH, 1), 6, 5L);
    assertThat(request.remaining()).isEqualTo(10);
    assertThat(plans.get(GOLEM, 6L)).hasValueSatisfying(plan -> assertThat(plan).isInstanceOf(SorterPlans.Restock.class));
    assertThat(plans.get(GOLEM, 5L + SorterPlans.PLAN_MILLIS)).isEmpty();
    plans.sweep(SorterPlans.PLAN_MILLIS * 2L);
    assertThat(plans.isEmpty()).isTrue();
  }

  @Test
  void aFetchPlanGivesItsItemsBackToTheRequestUnlessTheyWereDelivered() {
    SorterPlans plans = new SorterPlans();
    SorterFetches.Request request = fetches.request(ALEX, INPUT, items.item(Material.COBBLESTONE, 1), 30, 0L);

    plans.fetch(GOLEM, request, items.item(Material.COBBLESTONE, 1), request.reserve(20), 0L);
    plans.forget(GOLEM);
    assertThat(request.remaining()).isEqualTo(30);

    plans.fetch(GOLEM, request, items.item(Material.COBBLESTONE, 1), request.reserve(20), 0L);
    plans.finish(GOLEM, 15);
    assertThat(request.remaining()).isEqualTo(15);

    plans.fetch(GOLEM, request, items.item(Material.COBBLESTONE, 1), request.reserve(15), 0L);
    assertThat(plans.get(GOLEM, SorterPlans.PLAN_MILLIS)).isEmpty();
    assertThat(request.remaining()).isEqualTo(15);
    plans.fetch(GOLEM, request, items.item(Material.COBBLESTONE, 1), request.reserve(15), 0L);
    plans.sweep(SorterPlans.PLAN_MILLIS);
    assertThat(request.remaining()).isEqualTo(15);
  }

  @Test
  void aPlayerWhoLeavesLosesTheirRequestAndTheirFetchPlans() {
    SorterPlans plans = new SorterPlans();
    UUID other = UUID.fromString("00000000-0000-0000-0000-000000002706");
    SorterFetches.Request alex = fetches.request(ALEX, INPUT, items.item(Material.COBBLESTONE, 1), 10, 0L);
    SorterFetches.Request sam = fetches.request(SAM, INPUT, items.item(Material.DIRT, 1), 10, 0L);
    plans.fetch(GOLEM, alex, items.item(Material.COBBLESTONE, 1), alex.reserve(10), 0L);
    plans.fetch(other, sam, items.item(Material.DIRT, 1), sam.reserve(4), 0L);

    plans.forgetPlayer(ALEX);
    fetches.cancel(ALEX);

    assertThat(plans.get(GOLEM, 1L)).isEmpty();
    assertThat(plans.get(other, 1L)).isPresent();
    assertThat(fetches.open(INPUT, 1L)).extracting(SorterFetches.Request::player).containsExactly(SAM);
  }

  @Test
  void restockPlansCountAsInboundForTheirTargetAndItem() {
    SorterPlans plans = new SorterPlans();
    ContainerId workshop = new ContainerId(SorterYard.WORLD, 1, 64, 1);
    plans.restock(GOLEM, workshop, items.item(Material.TORCH, 1), 12, 0L);
    plans.restock(UUID.fromString("00000000-0000-0000-0000-000000002704"), workshop, items.item(Material.BREAD, 1), 5, 0L);
    plans.restock(UUID.fromString("00000000-0000-0000-0000-000000002705"), new ContainerId(SorterYard.WORLD, 9, 64, 9),
        items.item(Material.TORCH, 1), 7, 0L);

    assertThat(plans.inbound(workshop, item -> item.getType() == Material.TORCH, 1L)).isEqualTo(12);
    assertThat(plans.inbound(workshop, item -> item.getType() == Material.TORCH, SorterPlans.PLAN_MILLIS)).isZero();
  }
}
