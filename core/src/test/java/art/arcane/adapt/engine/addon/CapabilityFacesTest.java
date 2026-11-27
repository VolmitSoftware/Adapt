package art.arcane.adapt.engine.addon;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.ability.AbilityDamage;
import art.arcane.adapt.api.ability.MinionKind;
import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.service.ServiceRegistration;
import art.arcane.adapt.api.world.BuildBatch;
import art.arcane.adapt.api.world.BuildBatches;
import art.arcane.adapt.api.world.BuildResult;
import art.arcane.adapt.engine.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.engine.adaptation.ItemCooldownGroupPort;
import art.arcane.adapt.engine.minion.MinionIdentity;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The public build, minion, ability damage, integration and item faces drive the engine behind them. */
class CapabilityFacesTest {
  private static final Id STONE = Id.minecraft("stone");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Builder", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of());
    adapt.join(player);
    AdaptApi.addons().install(new PublicAddonTest.TinkerAddon());
  }

  @AfterEach
  void stop() {
    AdaptPorts.uninstall(AdaptationTeleportPort.class);
    AdaptPorts.uninstall(ItemCooldownGroupPort.class);
    adapt.close();
  }

  @Test
  void aBuildBatchFromTheApiPlacesPaidBlocksAndUndoesThemByOwnerId() {
    Adaptation<?> gears = AdaptApi.content().adaptation(PublicAddonTest.GEARS).orElseThrow();
    player.holding(new FakeItem(STONE, 3));
    BlockStateRef stone = AdaptCore.platform().host().items().blockState(STONE);
    List<BlockRef> row = new ArrayList<>();
    BuildBatch.Builder batch = BuildBatch.of(gears, player).pace(3).undoable(1);
    for (int x = 1; x <= 3; x++) {
      BlockRef block = platform.world().blockAt(x, 61, 0);
      row.add(block);
      batch.place(block, stone);
    }
    AtomicReference<BuildResult> built = new AtomicReference<>();

    assertThat(BuildBatches.start(batch.onDone(built::set).build())).isTrue();
    platform.scheduler().advance(3);

    assertThat(built.get()).extracting(BuildResult::placed, BuildResult::broken, BuildResult::undone,
        BuildResult::skipped, BuildResult::stop, BuildResult::blocks).containsExactly(3, 0, 0, 0, BuildResult.Stop.DONE, row);
    assertThat(player.inventory().mainHand().isEmpty()).isTrue();
    assertThat(BuildBatches.undoable(player)).isEqualTo(1);

    AtomicReference<BuildResult> undone = new AtomicReference<>();
    assertThat(BuildBatches.undo(player, List.of(PublicAddonTest.GEARS), 60_000L, 3, 0L, undone::set)).isTrue();
    platform.scheduler().advance(3);

    assertThat(undone.get().undone()).isEqualTo(3);
    assertThat(row).allMatch(BlockRef::isAir);
    assertThat(BuildBatches.isRunning(player)).isFalse();
  }

  @Test
  void aRegisteredMinionKindIsAMinionToTheEngineAndNeverATarget() {
    Adaptation<?> gears = AdaptApi.content().adaptation(PublicAddonTest.GEARS).orElseThrow();
    FakeLiving golem = platform.world().add(new FakeLiving(Id.minecraft("iron_golem"), player.location()));
    FakeLiving cow = platform.world().add(new FakeLiving(Id.minecraft("cow"), player.location()));
    UUID owner = player.uuid();
    ServiceRegistration registration = Minions.register(new MinionKind() {
      @Override
      public boolean matches(EntityRef entity) {
        return entity.uuid().equals(golem.uuid());
      }

      @Override
      public UUID ownerOf(EntityRef entity) {
        return owner;
      }
    });
    try {
      assertThat(Minions.isMinion(golem)).isTrue();
      assertThat(MinionIdentity.isMinion(golem)).isTrue();
      assertThat(Minions.ownerOf(golem)).contains(owner);
      assertThat(Minions.isMinion(cow)).isFalse();
      assertThat(Minions.mayAttack(gears, player, golem)).isFalse();
      assertThat(Minions.mayAttack(gears, player, player)).isFalse();
      assertThat(Minions.mayAttack(gears, player, cow)).isEqualTo(gears.canDamageTarget(player, cow));
    } finally {
      registration.withdraw();
    }
    assertThat(Minions.isMinion(golem)).isFalse();
  }

  @Test
  void abilityDamageLandsAsThePlayersAndIsFlaggedWhileItLands() {
    AtomicBoolean flagged = new AtomicBoolean();
    FakeLiving target = platform.world().add(new FakeLiving(Id.minecraft("zombie"), player.location()) {
      @Override
      public void damage(double amount, EntityRef source) {
        flagged.set(AbilityDamage.dealing());
        super.damage(amount, source);
      }
    });

    AbilityDamage.deal(target, 4D, player);

    assertThat(flagged).isTrue();
    assertThat(AbilityDamage.dealing()).isFalse();
    assertThat(target.damageTaken()).isEqualTo(4D);
  }

  @Test
  void integrationsFallBackNeutrallyAndATeleportVetoReachesTheAddon() {
    Adaptation<?> gears = AdaptApi.content().adaptation(PublicAddonTest.GEARS).orElseThrow();
    FakeLiving cow = platform.world().add(new FakeLiving(Id.minecraft("cow"), player.location()));
    Loc from = player.location();
    Loc to = from.add(5D, 0D, 0D);

    assertThat(Integrations.isServerPlayer(player)).isTrue();
    assertThat(Integrations.isShielded(cow)).isFalse();
    assertThat(Integrations.marked(cow, "NPC")).isFalse();
    assertThat(Integrations.hiddenVeins(from, 8)).isEmpty();
    assertThat(Integrations.treeFellingAvailable()).isFalse();
    assertThat(Integrations.announceTeleport(player, gears, from, to)).contains(to);

    List<Id> announced = new ArrayList<>();
    AdaptPorts.install(AdaptationTeleportPort.class, new AdaptationTeleportPort() {
      @Override
      public Loc announce(PlayerRef who, Adaptation<?> adaptation, Loc start, Loc end) {
        announced.add(adaptation.getId());
        return null;
      }
    });

    assertThat(Integrations.announceTeleport(player, gears, from, to)).isEmpty();
    assertThat(announced).containsExactly(PublicAddonTest.GEARS);
  }

  @Test
  void aCustomItemStackCarriesItsModelStackSizeAndCooldownGroup() {
    List<Id> stamped = new ArrayList<>();
    AdaptPorts.install(ItemCooldownGroupPort.class, (ItemRef stack, Id group, float markerSeconds) -> {
      stamped.add(group);
      return true;
    });
    Id model = Id.of("tinker", "item/wrench_model");
    Id group = Id.of("tinker", "wrench");
    CustomItem.Definition<CustomItem.Plain> wrench = CustomItem.builder(Id.of("tinker", "wrench"),
        Id.minecraft("iron_ingot")).itemModel(model).ownCooldownGroup().maxStackSize(1).build();

    ItemRef stack = wrench.create(1);

    assertThat(stack.itemModel()).isEqualTo(model);
    assertThat(stack.maxStackSize()).isEqualTo(1);
    assertThat(stamped).containsExactly(group);
    assertThat(wrench.is(stack)).isTrue();
  }
}
