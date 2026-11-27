package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.AdaptCore;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.event.entity.BlockedDamage;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.inventory.InventoryAction;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareItemCraftEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.item.BannerLayer;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.ShieldDesign;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingRuntimeTest {
  private static final Id NETHERITE_SHIELD = Id.adapt("blocking-phalanx-netherite-shield");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, List.of(SkillBlocking.class));
    player = platform.player("Guard", 0.5D, 61D, 0.5D);
    player.inventory().setOffHand(FakeItem.tool("shield", 336));
    profile = adapt.join(player);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    Skill<?> skill = AdaptCore.get().getAdaptServer().getSkillRegistry().getSkill("blocking");
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (type.isInstance(adaptation)) {
        profile.getData().getSkillLine("blocking").setAdaptation(adaptation, level);
        return type.cast(adaptation);
      }
    }
    throw new AssertionError("No adaptation " + type.getSimpleName());
  }

  private FakeLiving zombie() {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 2.5D, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return platform.world().add(zombie);
  }

  private Map<?, ?> stanceStates(BlockingBastionStance stance) throws ReflectiveOperationException {
    Field field = BlockingBastionStance.class.getDeclaredField("stanceStates");
    field.setAccessible(true);
    return (Map<?, ?>) field.get(stance);
  }

  @Test
  void shieldBlockedHitsCountTheBlockedShareOfTheDamage() {
    player.setBlocking(true);
    FakeLiving attacker = zombie();

    adapt.post(new EntityDamageByEntityEvent(player, attacker, attacker, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), 6D, false, FinalDamage.IDENTITY, (double base) -> base));

    assertThat(profile.getData().getStat("blocked.hits")).isEqualTo(1D);
    assertThat(profile.getData().getStat("blocked.damage")).isEqualTo(6D);
    assertThat(profile.getData().getStat("blocked.melee")).isEqualTo(1D);
    assertThat(profile.getData().getStat("blocked.heavy")).isEqualTo(1D);
  }

  @Test
  void hitsTheShieldDidNotBlockEarnNothing() {
    player.setBlocking(true);
    FakeLiving attacker = zombie();

    adapt.post(new EntityDamageByEntityEvent(player, attacker, attacker, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), 6D, false, FinalDamage.IDENTITY, BlockedDamage.NONE));

    assertThat(profile.getData().getStat("blocked.hits")).isZero();
  }

  @Test
  void blockedDamageFollowsTheCurrentBaseDamage() {
    FakeLiving attacker = zombie();
    EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, attacker, attacker,
        DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 8D, false, FinalDamage.IDENTITY,
        (double base) -> base * 0.5D);

    event.setDamage(10D);

    assertThat(event.blockedDamage()).isEqualTo(5D);
  }

  /**
   * Stance refreshes requested on the player since {@code requested} entity tasks. The skill's owner pulse also
   * visits the player with immediate (zero-delay) tasks whenever its wall-clock cadence comes due, which can fall
   * on any tick these tests advance, so only the stance's delayed refreshes are counted.
   */
  private List<Long> stanceRefreshDelays(int requested) {
    List<Long> delays = platform.scheduler().entityTaskDelays(player);
    return delays.subList(requested, delays.size()).stream()
        .filter((Long delay) -> delay > 0L)
        .toList();
  }

  @Test
  void firstOwnerRefreshWaitsForTheCommittedSneakState() throws Exception {
    BlockingBastionStance stance = learn(BlockingBastionStance.class, 5);
    int requested = platform.scheduler().entityTaskDelays(player).size();

    adapt.post(new PlayerToggleSneakEvent(player, true));

    assertThat(stanceRefreshDelays(requested)).containsExactly(1L);
    assertThat(stanceStates(stance).containsKey(player.uuid())).isTrue();

    player.setSneaking(true);
    platform.scheduler().advance(1);

    List<Long> refreshes = stanceRefreshDelays(requested);
    assertThat(stanceStates(stance).containsKey(player.uuid())).isTrue();
    assertThat(refreshes).hasSize(2);
    assertThat(refreshes.getFirst()).isEqualTo(1L);
  }

  @Test
  void rejectedInitialOwnerTaskDoesNotLeaveAnUnrefreshableSession() throws Exception {
    BlockingBastionStance stance = learn(BlockingBastionStance.class, 5);
    int requested = platform.scheduler().entityTaskDelays(player).size();
    platform.scheduler().setRejectEntityTasks(true);

    adapt.post(new PlayerToggleSneakEvent(player, true));

    assertThat(stanceRefreshDelays(requested)).containsExactly(1L);
    assertThat(stanceStates(stance)).isEmpty();
  }

  @Test
  void cancelledOrRevertedSneakDoesNotStartAMaintenanceLoop() throws Exception {
    BlockingBastionStance stance = learn(BlockingBastionStance.class, 5);
    int requested = platform.scheduler().entityTaskDelays(player).size();

    adapt.post(new PlayerToggleSneakEvent(player, true));
    assertThat(stanceRefreshDelays(requested)).containsExactly(1L);
    platform.scheduler().advance(1);

    assertThat(stanceStates(stance)).isEmpty();
    assertThat(stanceRefreshDelays(requested)).containsExactly(1L);
  }

  @Test
  void aStoppedSessionCannotBeResurrectedByItsQueuedInitialRefresh() throws Exception {
    BlockingBastionStance stance = learn(BlockingBastionStance.class, 5);
    int requested = platform.scheduler().entityTaskDelays(player).size();

    adapt.post(new PlayerToggleSneakEvent(player, true));
    adapt.post(new PlayerToggleSneakEvent(player, false));
    assertThat(stanceStates(stance)).isEmpty();
    player.setSneaking(true);
    platform.scheduler().advance(1);

    assertThat(stanceStates(stance)).isEmpty();
    assertThat(stanceRefreshDelays(requested)).containsExactly(1L);
  }

  @Test
  void phalanxNetheriteShieldKeepsACustomFaceAndGetsTheDefaultOtherwise() {
    learn(BlockingPhalanxCrafter.class, 2);
    FakeItem plain = FakeItem.tool("shield", 336);
    plain.setDamage(40);

    PrepareItemCraftEvent prepare = adapt.post(new PrepareItemCraftEvent(player,
        List.of(FakeItem.of("netherite_ingot"), plain, FakeItem.of("netherite_ingot")), NETHERITE_SHIELD,
        CraftItemEvent.RecipeKind.SHAPED, false, FakeItem.of("shield")));
    ItemRef result = prepare.result();

    assertThat(result.maxDamage()).isEqualTo(1200);
    assertThat(result.damage()).isZero();
    assertThat(result.shieldDesign().baseColor()).isEqualTo(DyeColor.BLACK);
    assertThat(result.shieldDesign().patterns()).containsExactly(
        new BannerLayer(DyeColor.ORANGE, Id.minecraft("border")),
        new BannerLayer(DyeColor.LIGHT_GRAY, Id.minecraft("rhombus")));

    FakeItem painted = FakeItem.tool("shield", 336);
    ShieldDesign face = new ShieldDesign(DyeColor.RED, List.of());
    painted.setShieldDesign(face);
    PrepareItemCraftEvent custom = adapt.post(new PrepareItemCraftEvent(player,
        List.of(painted), NETHERITE_SHIELD, CraftItemEvent.RecipeKind.SHAPED, false, FakeItem.of("shield")));

    assertThat(custom.result().shieldDesign()).isEqualTo(face);
  }

  @Test
  void phalanxNetheriteShieldNeedsTheSecondLevel() {
    learn(BlockingPhalanxCrafter.class, 1);

    CraftItemEvent craft = adapt.post(new CraftItemEvent(player, InventoryKind.WORKBENCH, 0, 0, ClickKind.LEFT,
        InventoryAction.PICKUP_ALL, -1, FakeItem.of("shield"), FakeItem.empty(), NETHERITE_SHIELD,
        CraftItemEvent.RecipeKind.SHAPED, List.of()));

    assertThat(craft.isCancelled()).isTrue();
    assertThat(profile.getData().getStat("blocking.phalanx-crafter.items-crafted")).isZero();
  }
}
