package art.arcane.adapt.content.adaptation.taming.blocks;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.sound.SoundPalettes;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.content.skill.SkillTragOul;
import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Taming with Swords in both orders, with Herbalism for the work Strike and with TragOul for a paying Strike. */
class TamingBlocksRuntimeTest {
  private static final Id MAGIC = Id.minecraft("magic");
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id BONE = Id.minecraft("bone");

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
    player = platform.player("Packmaster", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform,
        List.of(SkillSwords.class, SkillHerbalism.class, SkillTaming.class, SkillTragOul.class));
    player.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void graftAsLeadBleedsWhatThePetBitesWithBothCues() {
    kit.mutate("taming", "swords");
    kit.learn("taming", "swords", Recipe.GRAFT, 1);
    Wolf wolf = wolf(-1.5D);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    petHit(wolf, zombie);
    platform.scheduler().advance(10);

    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
    assertThat(player.heardSounds()).containsSubsequence(SoundPalettes.of("taming").fight(),
        SoundPalettes.of("swords").fight());
  }

  @Test
  void echoAsPartnerBleedsWhatThePetBites() {
    kit.mutate("swords", "taming");
    kit.learn("swords", "taming", Recipe.ECHO, 1);
    Wolf wolf = wolf(-1.5D);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);

    petHit(wolf, zombie);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void theNearestPetStandsUpAndLeapsAtTheTargetOfASwordCrit() {
    kit.mutate("swords", "taming");
    kit.learn("swords", "taming", Recipe.GRAFT, 1);
    Wolf near = wolf(-1.5D);
    Wolf far = wolf(-6.5D);
    platform.host().animals().setSitting(near, true);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(2);

    assertThat(near.target()).isSameAs(zombie);
    assertThat(near.velocity().x()).isPositive();
    assertThat(near.velocity().y()).isGreaterThanOrEqualTo(0.25D);
    assertThat(platform.host().animals().isSitting(near)).isFalse();
    assertThat(far.target()).isNull();
  }

  @Test
  void harvestingHealsNearbyPetsAndSpeedsThemUp() {
    kit.mutate("herbalism", "taming");
    kit.learn("herbalism", "taming", Recipe.GRAFT, 1);
    Wolf near = wolf(-1.5D);
    Wolf far = wolf(-12.5D);
    near.setHealth(10D);
    far.setHealth(10D);

    adapt.post(new BlockBreakEvent(player, crop(1, 61, 2, 7), true, 0));
    platform.scheduler().advance(2);

    assertThat(near.health()).isEqualTo(12D);
    assertThat(near.attributeValue(Attributes.MOVEMENT_SPEED)).isCloseTo(1.2D, within(1.0E-9D));
    assertThat(far.health()).isEqualTo(10D);
    assertThat(far.attributeValue(Attributes.MOVEMENT_SPEED)).isEqualTo(1D);
  }

  @Test
  void barterSpendsSwordDurabilityEvenWithNoPetToHealFrom() {
    kit.mutate("taming", "swords");
    kit.learn("taming", "swords", Recipe.BARTER, 1);
    player.setHealth(8D);
    player.setSneaking(true);

    PlayerSwapHandItemsEvent alone = swap();

    assertThat(alone.isCancelled()).isTrue();
    assertThat(player.health()).isEqualTo(8D);
  }

  @Test
  void barterHealsForNoMoreThanThreePetsHoweverManyStandNear() {
    kit.mutate("taming", "swords");
    kit.learn("taming", "swords", Recipe.BARTER, 1);
    player.setHealth(1D);
    player.setSneaking(true);
    for (int i = 0; i < 6; i++) {
      wolf(-3D + i);
    }

    swap();

    assertThat(player.health()).isEqualTo(7D);
  }

  @Test
    void barterHealsTwoHeartsPerPetUpToThreeHeartsForSwordDurability() {
    kit.mutate("taming", "swords");
    kit.learn("taming", "swords", Recipe.BARTER, 1);
    player.setHealth(4D);
    player.setSneaking(true);
    wolf(-1.5D);
    wolf(1.5D);
    wolf(2.5D);

    swap();

    assertThat(player.health()).isEqualTo(10D);
    assertThat(player.inventory().mainHand().damage()).isEqualTo(5);
  }

  @Test
  void barterSpendsABoneForAttackSpeed() {
    kit.mutate("swords", "taming");
    kit.learn("swords", "taming", Recipe.BARTER, 1);
    player.inventory().addItem(new FakeItem(BONE, 1));
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().contains(BONE, 1)).isFalse();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));

  }

  @Test
  void barterSpendsMeatWhenNoBoneIsCarriedAndRefusesWithNeither() {
    kit.mutate("swords", "taming");
    kit.learn("swords", "taming", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(swap().isCancelled()).isFalse();

    player.inventory().addItem(new FakeItem(Id.minecraft("cooked_beef"), 1));
    assertThat(swap().isCancelled()).isTrue();
    assertThat(player.inventory().contains(Id.minecraft("cooked_beef"), 1)).isFalse();
  }

  @Test
  void attunementGrantsAttackSpeedOnlyWhileAPetIsFighting() {
    kit.mutate("taming", "swords");
    kit.learn("taming", "swords", Recipe.ATTUNEMENT, 1);
    Wolf wolf = wolf(-1.5D);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isEqualTo(1D);

    adapt.post(new EntityDamageByEntityEvent(wolf, zombie, zombie, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), 2D, false, FinalDamage.IDENTITY));
    tickEngine();
    assertThat(player.attributeValue(Attributes.ATTACK_SPEED)).isCloseTo(1.15D, within(1.0E-9D));
  }

  @Test
  void convergenceWaitsForAFightingPetBeforeTheLeapAndTheBleed() {
    kit.mutate("swords", "taming");
    kit.learn("swords", "taming", Recipe.CONVERGENCE, 1);
    Wolf wolf = wolf(-1.5D);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);
    assertThat(wolf.target()).isNull();

    petHit(wolf, zombie);
    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
    assertThat(wolf.target()).isSameAs(zombie);
  }

  @Test
  void bredAndEggPetsStillCountButASpawnerVictimPaysNoDrain() {
    kit.mutate("taming", "tragoul");
    kit.learn("taming", "tragoul", Recipe.GRAFT, 1);
    player.setHealth(10D);
    Wolf bred = wolf(-1.5D);
    MobOrigin.record(bred, CreatureSpawnEvent.Reason.BREEDING);
    Wolf hatched = wolf(1.5D);
    MobOrigin.record(hatched, CreatureSpawnEvent.Reason.SPAWNER_EGG);
    FakeLiving caged = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    MobOrigin.record(caged, CreatureSpawnEvent.Reason.SPAWNER);
    FakeLiving wild = MutationKit.zombie(platform.world(), 0.5D, 61D, 2.5D);

    petHit(bred, caged);
    platform.scheduler().advance(10);
    assertThat(caged.health()).isEqualTo(20D);
    assertThat(player.health()).isEqualTo(10D);

    petHit(hatched, wild);
    platform.scheduler().advance(10);
    assertThat(wild.health()).isEqualTo(18D);
    assertThat(player.health()).isEqualTo(12D);
  }

  @Test
  void nothingHappensWithoutTheMutationOrForSomeoneElsesPet() {
    Wolf wolf = wolf(-1.5D);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    petHit(wolf, zombie);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);

    kit.mutate("taming", "swords");
    kit.learn("taming", "swords", Recipe.GRAFT, 1);
    Wolf stray = wolf(-1.5D);
    stray.setTameOwner(UUID.randomUUID());
    petHit(stray, zombie);
    platform.scheduler().advance(10);
    assertThat(zombie.lastDamageType()).isNotEqualTo(MAGIC);
  }

  private void petHit(FakeLiving pet, FakeLiving target) {
    adapt.post(new EntityDamageByEntityEvent(target, pet, pet, DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"),
        4D, false, FinalDamage.IDENTITY));
  }

  private PlayerSwapHandItemsEvent swap() {
    return adapt.post(new PlayerSwapHandItemsEvent(player, player.inventory().mainHand(), player.inventory().offHand()));
  }

  private BlockRef crop(int x, int y, int z, int age) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(WHEAT, Map.of("age", Integer.toString(age))));
    return platform.world().blockAt(x, y, z);
  }

  private void tickEngine() {
    adapt.core().getAdaptServer().getSkillRegistry().getMutationEngine().onTick();
    platform.scheduler().advance(1);
  }

  private Wolf wolf(double x) {
    Wolf wolf = platform.world().add(new Wolf(x));
    wolf.trait(EntityTrait.TAMEABLE);
    wolf.setTameOwner(player.uuid());
    wolf.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    wolf.setHealth(20D);
    platform.host().track(wolf);
    return wolf;
  }

  private final class Wolf extends FakeLiving implements MobRef {
    private LivingRef target;

    private Wolf(double x) {
      super(Id.minecraft("wolf"), new Loc(platform.world(), x, 61D, 0.5D));
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
      return true;
    }

    @Override
    public void setAware(boolean aware) {
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
      return CopperGolemState.IDLE;
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
