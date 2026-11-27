package art.arcane.adapt.content.adaptation.agility.upgraded;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.testkit.XpPaid;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The grappling line hooks what is in sight and in reach, pulls the holder in, and costs durability and a charge. */
class GrapplingLineTest {
  private static final int ROD_DURABILITY = 64;

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private Skyrunner skyrunner;
  private GrapplingLine line;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Climber", 0.5D, 64D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillAgility.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    skyrunner = SkillHost.of(registry.getSkill("agility")).getUpgrades().stream().filter(Skyrunner.class::isInstance)
        .map(Skyrunner.class::cast).findFirst().orElseThrow();
    line = (GrapplingLine) skyrunner.getTools().getFirst();
    player.setOnGround(true);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void itIsAToolOfSkyrunnerCraftedFromARodStringAndIron() {
    assertThat(line.item().id()).isEqualTo(Id.adapt("agility/tool/grappling_line"));
    assertThat(skyrunner.getRecipes()).anySatisfy((RecipeSpec recipe) -> {
      assertThat(recipe.key()).isEqualTo(Id.adapt("agility/tool/grappling_line"));
      assertThat(line.is(recipe.result())).isTrue();
    });
  }

  @Test
  void aHolderHooksABlockInSightAndIsPulledTowardIt() {
    mutate(1);
    platform.world().set(0, 65, 10, "stone");
    ItemRef rod = hold();

    PlayerInteractEvent use = use();
    platform.scheduler().advance(1);

    assertThat(use.useItem()).isEqualTo(UseResult.DENY);
    assertThat(player.velocity().z()).isPositive();
    assertThat(wear()).isEqualTo(rod.damage() + 1);
  }

  @Test
  void anyoneWithoutSkyrunnerGetsNothingFromIt() {
    platform.world().set(0, 65, 10, "stone");
    hold();

    use();
    platform.scheduler().advance(3);

    assertThat(player.velocity().lengthSquared()).isZero();
    assertThat(wear()).isZero();
  }

  @Test
  void aBlockPastTheReachIsNotHooked() {
    mutate(1);
    platform.world().set(0, 65, 20, "stone");
    hold();

    use();
    platform.scheduler().advance(3);

    assertThat(player.velocity().lengthSquared()).isZero();
    assertThat(wear()).isZero();
  }

  @Test
  void theHookStopsAtTheFirstBlockAndNeverPassesThroughIt() {
    mutate(3);
    platform.world().set(0, 65, 4, "stone");
    platform.world().set(0, 65, 20, "stone");
    hold();

    Vec3 hook = line.hookPoint(player, skyrunner.getGrappleRange(3));

    assertThat(hook).isNotNull();
    assertThat(hook.z()).isLessThan(5D);
  }

  @Test
  void aPlayerInTheWayIsNeverHooked() {
    mutate(1);
    platform.world().set(0, 65, 10, "stone");
    platform.player("Bystander", 0.5D, 64D, 5.5D);
    hold();

    use();
    platform.scheduler().advance(3);

    assertThat(player.velocity().lengthSquared()).isZero();
    assertThat(wear()).isZero();
  }

  @Test
  void aProtectedBlockIsNotHookedAndCostsNothing() {
    mutate(1);
    platform.world().set(0, 65, 10, "stone");
    platform.protection().deny(true);
    hold();

    use();
    platform.scheduler().advance(3);

    assertThat(player.velocity().lengthSquared()).isZero();
    assertThat(wear()).isZero();
  }

  @Test
  void inTheAirItSpendsTheSharedChargeSoItCannotChainPastIt() {
    mutate(1);
    platform.world().set(0, 65, 10, "stone");
    hold();
    takeOff();

    use();
    use();
    player.setVelocity(Vec3.ZERO);
    use();
    platform.scheduler().advance(1);

    assertThat(player.velocity().lengthSquared()).isZero();
    assertThat(wear()).isEqualTo(1);
  }

  @Test
  void aGroundGrappleLeavesTheNextAirtimeOneChargeShort() {
    mutate(2);
    platform.world().set(0, 65, 10, "stone");
    hold();

    use();
    use();
    takeOff();
    use();
    use();
    player.setVelocity(Vec3.ZERO);
    use();
    platform.scheduler().advance(1);

    assertThat(player.velocity().lengthSquared()).isZero();
    assertThat(wear()).isEqualTo(2);
  }

  @Test
  void rightClickingAgainLetsGoAndKeepsTheMomentum() {
    mutate(1);
    platform.world().set(0, 65, 10, "stone");
    hold();

    use();
    platform.scheduler().advance(1);
    Vec3 swing = player.velocity();
    use();
    platform.scheduler().advance(3);

    assertThat(swing.z()).isPositive();
    assertThat(player.velocity()).isEqualTo(swing);
  }

  @Test
  void grapplingPaysNoSkillXp() {
    mutate(1);
    platform.world().set(0, 65, 10, "stone");
    hold();

    use();
    platform.scheduler().advance(5);

    assertThat(XpPaid.by(profile, skyrunner)).isZero();
  }

  private ItemRef hold() {
    ItemRef made = line.create(CustomItem.Plain.INSTANCE);
    FakeItem rod = FakeItem.tool("fishing_rod", ROD_DURABILITY);
    DataKey<CustomItem.Plain> stored = DataKey.of(line.item().id(), line.item().codec());
    rod.data().set(stored, made.data().get(stored));
    assertThat(line.is(rod)).isTrue();
    player.holding(rod);
    player.setEquipment(EquipmentSlot.MAIN_HAND, rod);
    return rod.copy();
  }

  private int wear() {
    return player.equipment(EquipmentSlot.MAIN_HAND).damage();
  }

  private PlayerInteractEvent use() {
    return adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private void takeOff() {
    run();
    player.setOnGround(false);
    run();
  }

  private void run() {
    Loc from = player.location();
    Loc to = from.add(0D, 0D, 0.01D);
    player.moveTo(to);
    adapt.post(new PlayerMoveEvent(player, from, to));
  }

  private void mutate(int level) {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("agility", "stealth")) {
      PlayerSkillLine skillLine = profile.getData().getSkillLine(skill);
      skillLine.setXp(XP.getXpForLevel(20) + 1D);
      skillLine.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("agility"), registry.getSkill("stealth"), "Climber"))
        .isEqualTo(SkillMutations.Result.MUTATED);
    profile.getData().getSkillLine("agility").setAdaptation(skyrunner, level);
  }
}
