package art.arcane.adapt.content.adaptation.stealth.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockReceiveGameEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Stealth paired with Swords (both orderings) and with Herbalism for the work Strike. */
class StealthBlocksRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id INVISIBILITY = Id.minecraft("invisibility");
  private static final Id GLOWING = Id.minecraft("glowing");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    player = platform.player("Shade", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillStealth.class, SkillSwords.class, SkillHerbalism.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftBleedsATargetFacingAwayButNotOneWatching() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.GRAFT, 1);
    TestMob watcher = mob(0.5D, 2.5D, 180F);
    TestMob away = mob(2.5D, 0.5D, 0F);

    kit.hit(player, watcher, 1D, false);
    platform.scheduler().advance(10);
    assertThat(watcher.lastDamageType()).isNotEqualTo(MAGIC);

    kit.hit(player, away, 1D, false);
    platform.scheduler().advance(10);
    assertThat(away.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void swordsLeadShakesOffNearbyMobsOnACritAndBleedsAnUnwatchingTarget() {
    kit.mutate("swords", "stealth");
    kit.learn("swords", "stealth", Recipe.GRAFT, 1);
    kit.learn("swords", "stealth", Recipe.ECHO, 1);
    TestMob near = mob(2.5D, 0.5D, 0F);
    TestMob far = mob(14.5D, 0.5D, 0F);
    near.setTarget(player);
    far.setTarget(player);

    kit.hit(player, near, 4D, true);
    platform.scheduler().advance(10);

    assertThat(near.target()).isNull();
    assertThat(far.target()).isSameAs(player);
    assertThat(adapt.post(new EntityTargetEvent(near, player, EntityTargetEvent.Reason.CLOSEST_PLAYER)).isCancelled())
        .isTrue();
    assertThat(adapt.post(new EntityTargetEvent(far, player, EntityTargetEvent.Reason.CLOSEST_PLAYER)).isCancelled())
        .isFalse();
    assertThat(near.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void herbalismLeadSilencesThePlayerAndHidesThemFromMobsAfterAHarvest() {
    kit.mutate("herbalism", "stealth");
    kit.learn("herbalism", "stealth", Recipe.GRAFT, 1);
    TestMob hunter = mob(10.5D, 0.5D, 0F);
    hunter.setTarget(player);

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));
    platform.scheduler().advance(1);

    assertThat(player.isSilent()).isTrue();
    assertThat(hunter.target()).isNull();
    assertThat(adapt.post(new EntityTargetEvent(hunter, player, EntityTargetEvent.Reason.CLOSEST_PLAYER)).isCancelled())
        .isTrue();
    assertThat(adapt.post(new BlockReceiveGameEvent(platform.world().blockAt(3, 60, 0), Id.minecraft("step"), player))
        .isCancelled()).isTrue();

    platform.scheduler().advance(200);
    assertThat(player.isSilent()).isFalse();
  }

  @Test
  void aReloadNeverLeavesThePlayerSilent() {
    kit.mutate("herbalism", "stealth");
    kit.learn("herbalism", "stealth", Recipe.GRAFT, 1);

    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));
    assertThat(player.isSilent()).isTrue();
    adapt.core().getAdaptServer().getSkillRegistry().getSkill("stealth").getBlocks().stream()
        .filter(StealthWorkStrike.class::isInstance).findFirst().orElseThrow().unregister();

    assertThat(player.isSilent()).isFalse();
  }

  @Test
  void aSilenceLeftByACrashEndsWhenThePlayerJoins() {
    kit.mutate("herbalism", "stealth");
    kit.learn("herbalism", "stealth", Recipe.GRAFT, 1);
    adapt.post(new BlockBreakEvent(player, crop(-1, 61, 2, 7), true, 0));
    assertThat(player.data().has(StealthWorkStrike.SILENCED)).isTrue();
    platform.scheduler().advance(200);
    assertThat(player.data().has(StealthWorkStrike.SILENCED)).isFalse();

    player.setSilent(true);
    player.data().set(StealthWorkStrike.SILENCED, (byte) 1);
    adapt.post(new PlayerJoinEvent(player));

    assertThat(player.isSilent()).isFalse();
    assertThat(player.data().has(StealthWorkStrike.SILENCED)).isFalse();
  }

  @Test
  void aSilenceAdaptDidNotSetSurvivesAJoin() {
    player.setSilent(true);

    adapt.post(new PlayerJoinEvent(player));

    assertThat(player.isSilent()).isTrue();
  }

  @Test
  void barterTurnsSwordDurabilityIntoInvisibility() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(barter()).isTrue();

    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
    assertThat(player.effect(INVISIBILITY).durationTicks()).isEqualTo(60);
  }

  @Test
  void barterSpendsCoverOnlyWhileNoMobHuntsThePlayer() {
    kit.mutate("swords", "stealth");
    kit.learn("swords", "stealth", Recipe.BARTER, 1);
    TestMob hunter = mob(6.5D, 0.5D, 0F);
    hunter.setTarget(player);
    player.setSneaking(true);

    assertThat(barter()).isFalse();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    hunter.setTarget(null);
    player.addEffect(EffectInstance.of(INVISIBILITY, 400, 0));
    player.setFoodLevel(20);
    assertThat(barter()).isTrue();
    assertThat(player.foodLevel()).isEqualTo(18);

    assertThat(player.hasEffect(INVISIBILITY)).isFalse();
    assertThat(player.hasEffect(GLOWING)).isTrue();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void attunementHoldsWhileSneakingUnhunted() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.ATTUNEMENT, 1);
    TestMob hunter = mob(6.5D, 0.5D, 0F);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    player.setSneaking(true);
    hunter.setTarget(player);
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    hunter.setTarget(null);
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void theStateRemembersSneakingUnseenForTheRecentWindow() {
    kit.mutate("stealth", "swords");
    kit.learn("stealth", "swords", Recipe.ATTUNEMENT, 1);
    StealthState state = state();
    assertThat(state.heldWithin(player, 4_000L)).isFalse();

    player.setSneaking(true);
    adapt.post(new PlayerToggleSneakEvent(player, false));
    player.setSneaking(false);

    assertThat(state.holds(player)).isFalse();
    assertThat(state.heldWithin(player, 4_000L)).isTrue();
  }

  @Test
  void nothingHappensWithoutTheMutation() {
    TestMob away = mob(2.5D, 0.5D, 0F);
    away.setTarget(player);

    kit.hit(player, away, 4D, true);
    platform.scheduler().advance(10);

    assertThat(away.lastDamageType()).isNotEqualTo(MAGIC);
    assertThat(away.target()).isSameAs(player);
  }

  private TestMob mob(double x, double z, float yaw) {
    TestMob mob = new TestMob(new Loc(platform.world(), x, 61D, z, yaw, 0F));
    mob.trait(EntityTrait.MONSTER);
    mob.trait(EntityTrait.ENEMY);
    return platform.world().add(mob);
  }

  /** Sneak and left-click the air, the Barter gesture of Swords pairs with this skill; true when it cast. */
  private boolean barter() {
    return adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, null, null, UseResult.DENY, UseResult.DEFAULT)).isUseDenied();
  }

  private StealthState state() {
    for (MutationBlock<?> block : kit.registry().getSkill("stealth").getBlocks()) {
      if (block instanceof StealthState state) {
        return state;
      }
    }
    throw new AssertionError("No Stealth State");
  }

  private BlockRef crop(int x, int y, int z, int age) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(WHEAT, Map.of("age", Integer.toString(age))));
    return platform.world().blockAt(x, y, z);
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }

  private static final class TestMob extends FakeLiving implements MobRef {
    private LivingRef target;
    private boolean aware = true;

    private TestMob(Loc location) {
      super(Id.minecraft("zombie"), location);
    }

    @Override
    public LivingRef target() {
      return target;
    }

    @Override
    public void setTarget(LivingRef target) {
      this.target = target;
    }

    @Override
    public boolean isAware() {
      return aware;
    }

    @Override
    public void setAware(boolean aware) {
      this.aware = aware;
    }

    @Override
    public boolean pathTo(Loc destination, double speed) {
      return false;
    }

    @Override
    public boolean hasPath() {
      return false;
    }

    @Override
    public void stopPathing() {
    }

    @Override
    public void lookAt(Loc target) {
    }

    @Override
    public boolean rangedAttack(LivingRef target) {
      return false;
    }

    @Override
    public boolean isPlayerCreated() {
      return false;
    }

    @Override
    public void setPlayerCreated(boolean created) {
    }

    @Override
    public int weatheringStage() {
      return 0;
    }

    @Override
    public boolean isWaxed() {
      return false;
    }

    @Override
    public void setWeathering(int stage, boolean waxed) {
    }

    @Override
    public CopperGolemState copperGolemState() {
      return null;
    }

    @Override
    public void setCopperGolemState(CopperGolemState state) {
    }

    @Override
    public void setBurnsInDaylight(boolean burns) {
    }

    @Override
    public MobGoalsRef goals() {
      return null;
    }
  }
}
