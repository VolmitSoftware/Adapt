package art.arcane.adapt.content.adaptation.taming.upgraded;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.content.adaptation.taming.TameBonds;
import art.arcane.adapt.content.adaptation.taming.TamePetRoles;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.MutationTool;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.CombatXp;
import art.arcane.adapt.engine.xp.CombatXpLedger;
import art.arcane.adapt.engine.xp.MobOrigin;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.platform.entity.CopperGolemState;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ai.MobGoalsRef;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TamingUpgradesRuntimeTest {
  private static final Id GENERIC = Id.minecraft("generic");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    for (int x = -3; x <= 3; x++) {
      for (int z = -3; z <= 3; z++) {
        platform.world().set(x, 60, z, "stone");
      }
    }
    player = platform.player("Packmaster", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillTaming.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void bloodBondDoesNothingForAPlayerWhoHasNotMutated() {
    profile.getData().getSkillLine("taming").setAdaptation(upgrade(BloodBond.class), 3);
    FakeLiving wolf = pet(2.5D);

    EntityDamageEvent hit = adapt.post(damage(player, 10D));

    assertThat(hit.damage()).isEqualTo(10D);
    assertThat(wolf.health()).isEqualTo(20D);
  }

  @Test
  void bloodBondSpreadsTheOwnersDamageAcrossNearbyPets() {
    mutate();
    BloodBond bond = learn(BloodBond.class, 1);
    FakeLiving wolf = pet(2.5D);

    EntityDamageEvent hit = adapt.post(damage(player, 10D));

    double shared = 10D * bond.getSharePercent(1);
    assertThat(hit.damage()).isCloseTo(10D - shared, within(1.0E-9D));
    assertThat(wolf.health()).isCloseTo(20D - shared, within(1.0E-9D));
  }

  @Test
  void bloodBondSharesTheHitAfterArmorNotBefore() {
    mutate();
    BloodBond bond = learn(BloodBond.class, 1);
    FakeLiving wolf = pet(2.5D);
    FinalDamage halvedByArmor = (double base) -> base * 0.5D;

    EntityDamageEvent hit = adapt.post(new EntityDamageEvent(player, DamageCause.ENTITY_ATTACK, GENERIC, 10D, halvedByArmor));

    double shared = 5D * bond.getSharePercent(1);
    assertThat(wolf.health()).isCloseTo(20D - shared, within(1.0E-9D));
    assertThat(hit.finalDamage()).isCloseTo(5D - shared, within(1.0E-9D));
  }

  @Test
  void bloodBondSendsAPetInFrontOfAShot() {
    mutate();
    BloodBond bond = learn(BloodBond.class, 1);
    bond.getConfig().interceptChanceBase = 1D;
    bond.getConfig().maxInterceptChance = 1D;
    FakeLiving wolf = pet(2.5D);
    FakeProjectile arrow = platform.world().add(new FakeProjectile(Id.minecraft("arrow"), new Loc(platform.world(), 6.5D, 62D, 0.5D)));

    EntityDamageByEntityEvent shot = adapt.post(new EntityDamageByEntityEvent(player, arrow, arrow, DamageCause.PROJECTILE,
        Id.minecraft("arrow"), 6D, false, FinalDamage.IDENTITY));
    platform.scheduler().advance(2);

    assertThat(shot.isCancelled()).isTrue();
    assertThat(wolf.health()).isCloseTo(20D - (6D * (1D - bond.getConfig().petReduction)), within(1.0E-9D));
  }

  @Test
  void bloodBondKeepsADyingPetAtOneHealthAndBringsItBack() {
    mutate();
    learn(BloodBond.class, 1);
    FakeLiving wolf = pet(40.5D);
    wolf.setHealth(3D);

    EntityDamageEvent lethal = adapt.post(damage(wolf, 10D));
    platform.scheduler().advance(2);
    EntityDamageEvent followUp = adapt.post(damage(wolf, 10D));

    assertThat(lethal.isCancelled()).isTrue();
    assertThat(followUp.isCancelled()).isTrue();
    assertThat(wolf.health()).isEqualTo(1D);
    assertThat(wolf.location().distanceSquared(player.location())).isLessThan(9D);
  }

  @Test
  void alphaMakesPetsStrongerAndTougherAndMoreSoNearTheOwner() {
    mutate();
    Alpha alpha = learn(Alpha.class, 1);
    FakeLiving near = pet(2.5D);
    FakeLiving far = pet(alpha.getAuraRadius(1) + 10D);

    alpha.onTick();
    platform.scheduler().advance(1);
    alpha.onTick();

    double boost = alpha.getDamageBoost(1);
    assertThat(far.attributeValue(Attributes.ATTACK_DAMAGE)).isCloseTo(4D * (1D + boost), within(1.0E-9D));
    assertThat(near.attributeValue(Attributes.ATTACK_DAMAGE))
        .isCloseTo(4D * (1D + (boost * alpha.getConfig().nearMultiplier)), within(1.0E-9D));
    assertThat(far.attributeValue(Attributes.MAX_HEALTH)).isCloseTo(20D * (1D + alpha.getHealthBoost(1)), within(1.0E-9D));
    assertThat(near.effect(Id.minecraft("regeneration"))).isNotNull();
    assertThat(far.effect(Id.minecraft("regeneration"))).isNull();
  }

  @Test
  void alphaHealsAHurtPet() {
    mutate();
    Alpha alpha = learn(Alpha.class, 1);
    FakeLiving wolf = pet(2.5D);
    wolf.setHealth(10D);
    alpha.onTick();

    adapt.post(new EntityDamageByEntityEvent(wolf, player, player, DamageCause.ENTITY_ATTACK, Id.minecraft("player_attack"),
        1D, false, FinalDamage.IDENTITY));
    platform.scheduler().advance(2);

    assertThat(wolf.health()).isGreaterThan(10D);
  }

  @Test
  void alphaLeavesPetsAloneWithoutAMutation() {
    profile.getData().getSkillLine("taming").setAdaptation(upgrade(Alpha.class), 3);
    Alpha alpha = upgrade(Alpha.class);
    FakeLiving wolf = pet(2.5D);

    alpha.onTick();
    platform.scheduler().advance(1);
    alpha.onTick();

    assertThat(wolf.attributeValue(Attributes.ATTACK_DAMAGE)).isEqualTo(4D);
  }

  @Test
  void theWhistleRecallsTheNearestPetForOneHunger() {
    mutate();
    learn(OneMind.class, 1);
    FakeLiving wolf = pet(30.5D);
    player.holding(whistle().create(CustomItem.Plain.INSTANCE));
    player.setSneaking(true);

    use(InteractAction.RIGHT_CLICK_AIR);
    platform.scheduler().advance(25);

    assertThat(wolf.location().distanceSquared(player.location())).isLessThan(9D);
    assertThat(player.foodLevel()).isEqualTo(19);
    assertThat(player.hasCooldown(BeastmastersWhistle.MATERIAL)).isTrue();
  }

  @Test
  void theWhistleDoesNothingWithoutOneMind() {
    learn(OneMind.class, 3);
    FakeLiving wolf = pet(30.5D);
    player.holding(whistle().create(CustomItem.Plain.INSTANCE));
    player.setSneaking(true);

    PlayerInteractEvent click = use(InteractAction.RIGHT_CLICK_AIR);
    platform.scheduler().advance(25);

    assertThat(click.useItem()).isEqualTo(UseResult.DENY);
    assertThat(wolf.location().x()).isEqualTo(30.5D);
    assertThat(player.foodLevel()).isEqualTo(20);
    assertThat(player.actionBars()).isNotEmpty();
  }

  @Test
  void bonesNoLongerRecallOrCommandSoTheyFeedWolvesAgain() {
    mutate();
    learn(OneMind.class, 3);
    FakeLiving wolf = pet(30.5D);
    player.holding(new FakeItem(Id.minecraft("bone"), 5));
    player.setSneaking(true);

    PlayerInteractEvent click = use(InteractAction.RIGHT_CLICK_AIR);
    platform.scheduler().advance(25);

    assertThat(click.isCancelled()).isFalse();
    assertThat(wolf.location().x()).isEqualTo(30.5D);
    assertThat(player.inventory().mainHand().amount()).isEqualTo(5);
  }

  @Test
  void theWhistleIsCraftedFromAHornOrABoneOnlyAtTheUpgrade() {
    assertThat(upgrade(OneMind.class).getRecipes()).hasSize(2)
        .allSatisfy((RecipeSpec recipe) -> assertThat(whistle().is(recipe.result())).isTrue());
  }

  @Test
  void onlyNamedPetsHoldRolesAndTheRoleLivesOnThePet() {
    FakeLiving named = pet(2.5D);
    FakeLiving plain = pet(3.5D);
    named.setCustomName("Rex", true);

    TamePetRoles.assign(named, TamePetRoles.Role.GUARD_SPOT, new Loc(platform.world(), 4D, 61D, 4D));
    TamePetRoles.assign(plain, TamePetRoles.Role.FETCH, null);

    assertThat(TamePetRoles.role(named)).isEqualTo(TamePetRoles.Role.GUARD_SPOT);
    assertThat(TamePetRoles.spot(named).blockX()).isEqualTo(4);
    assertThat(TamePetRoles.role(plain)).isEqualTo(TamePetRoles.Role.NONE);
    assertThat(TamePetRoles.Role.FETCH.next()).isEqualTo(TamePetRoles.Role.NONE);
  }

  @Test
  void alphaBondsOneMountThatLendsMountedTacticsOnlyToItsOwner() {
    mutate();
    Alpha alpha = learn(Alpha.class, 1);
    FakeLiving first = horse(player);
    FakeLiving second = horse(player);
    FakePlayer stranger = platform.player("Stranger", 4.5D, 61D, 0.5D);
    Adaptation<?> tactics = registry.getAdaptation(Id.adapt("tame/mounted_tactics"));

    assertThat(alpha.bond(player, first)).isTrue();
    platform.scheduler().advance(1);
    assertThat(TameBonds.tacticsLevel(tactics, player, first)).isEqualTo(tactics.getMaxLevel());
    assertThat(TameBonds.tacticsLevel(tactics, stranger, first)).isZero();

    assertThat(alpha.bond(player, second)).isTrue();
    platform.scheduler().advance(1);
    assertThat(TameBonds.bondedTo(player, first)).isFalse();
    assertThat(TameBonds.bondedTo(player, second)).isTrue();
    assertThat(TameBonds.tacticsLevel(tactics, player, first)).isZero();
  }

  @Test
  void alphaNeverBondsSomeoneElsesHorseOrWithoutAMutation() {
    FakePlayer other = platform.player("Other", 4.5D, 61D, 0.5D);
    FakeLiving theirs = horse(other);
    FakeLiving mine = horse(player);
    Alpha alpha = learn(Alpha.class, 3);

    assertThat(alpha.bond(player, mine)).isFalse();
    mutate();
    learn(Alpha.class, 3);
    assertThat(alpha.bond(player, theirs)).isFalse();
    assertThat(alpha.bond(player, mine)).isTrue();
  }

  @Test
  void alphaNeverBondsAMountWhereTheOwnerCannotInteract() {
    mutate();
    Alpha alpha = learn(Alpha.class, 1);
    FakeLiving strider = strider();

    Protector protector = new NoInteract();
    AdaptApi.protection().register(protector);
    try {
      assertThat(alpha.bond(player, strider)).isFalse();
    } finally {
      AdaptApi.protection().unregister(protector);
    }
    assertThat(alpha.bond(player, strider)).isTrue();
  }

  @Test
  void aBondedMountTamedBySomeoneElseAnswersNoLongerToItsFormerOwner() {
    mutate();
    Alpha alpha = learn(Alpha.class, 1);
    FakeLiving mount = horse(player);
    FakePlayer buyer = platform.player("Buyer", 4.5D, 61D, 0.5D);
    assertThat(alpha.bond(player, mount)).isTrue();
    platform.scheduler().advance(1);

    mount.setTameOwner(buyer.uuid());

    assertThat(TameBonds.bondedTo(player, mount)).isFalse();
    assertThat(alpha.call(player)).isFalse();
  }

  @Test
  void aSpotGuardIgnoresFarmedMobsAndHitsOnThemPayNoCombatXp() {
    mutate();
    learn(OneMind.class, 2);
    Mob wolf = guard();
    FakeLiving caged = MutationKit.zombie(platform.world(), 4.5D, 61D, 0.5D);
    MobOrigin.record(caged, CreatureSpawnEvent.Reason.SPAWNER);
    caged.setHealth(20D);

    upgrade(OneMind.class).onTick();
    platform.scheduler().advance(2);
    EntityDamageByEntityEvent bite = new EntityDamageByEntityEvent(caged, wolf, wolf, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), 4D, false, FinalDamage.IDENTITY);

    assertThat(wolf.target()).isNull();
    assertThat(CombatXp.credit(new CombatXpLedger(), "taming", player, bite)).isZero();

    FakeLiving wild = MutationKit.zombie(platform.world(), 5.5D, 61D, 0.5D);
    wild.setHealth(20D);
    upgrade(OneMind.class).onTick();
    platform.scheduler().advance(2);

    assertThat(wolf.target()).isSameAs(wild);
    assertThat(CombatXp.credit(new CombatXpLedger(), "taming", player, new EntityDamageByEntityEvent(wild, wolf, wolf,
        DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 4D, false, FinalDamage.IDENTITY))).isPositive();
  }

  @Test
  void aMountOnlyRecallRespectsTheHungerPreference() {
    mutate();
    learn(OneMind.class, 1);
    Alpha alpha = learn(Alpha.class, 1);
    Mob mount = tamed(new Mob(Id.minecraft("horse"), 20.5D), EntityTrait.HORSE);
    assertThat(alpha.bond(player, mount)).isTrue();
    platform.scheduler().advance(1);
    assertThat(alpha.call(player)).isTrue();
    player.setFoodLevel(0);
    player.holding(whistle().create(CustomItem.Plain.INSTANCE));
    player.setSneaking(true);

    use(InteractAction.RIGHT_CLICK_AIR);

    assertThat(player.hasCooldown(BeastmastersWhistle.MATERIAL)).isFalse();
  }

  @Test
  void alphaMountGrowthStopsAtItsCap() {
    Alpha alpha = upgrade(Alpha.class);
    double cap = alpha.getGrowthCap(1);

    assertThat(cap).isEqualTo(0.1D);
    assertThat(alpha.getGrowthCap(3)).isCloseTo(0.3D, within(1.0E-9D));
    assertThat(alpha.growth(500D, cap)).isCloseTo(0.05D, within(1.0E-9D));
    assertThat(alpha.growth(1_000_000D, cap)).isEqualTo(cap);
  }

  @Test
  void alphaReachesAtLeastTheMaxedOriginalsAtItsTopLevel() {
    Alpha alpha = upgrade(Alpha.class);

    assertThat(alpha.getHealthBoost(3)).isGreaterThanOrEqualTo(3.07D - 1.0E-9D);
    assertThat(alpha.getDamageBoost(3)).isGreaterThanOrEqualTo(0.73D);
    assertThat(alpha.getRegeneration(3)).isGreaterThanOrEqualTo(6D);
    assertThat(alpha.getAuraRadius(3)).isGreaterThanOrEqualTo(22D);
    assertThat(alpha.getAuraAmplifier(3)).isEqualTo(2);
  }

  private BeastmastersWhistle whistle() {
    return (BeastmastersWhistle) upgrade(OneMind.class).getTools().getFirst();
  }

  private PlayerInteractEvent use(InteractAction action) {
    return adapt.post(new PlayerInteractEvent(player, action, Hand.MAIN_HAND, player.inventory().mainHand(), null, null, null,
        UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private FakeLiving horse(FakePlayer owner) {
    FakeLiving horse = platform.world().add(new FakeLiving(Id.minecraft("horse"), new Loc(platform.world(), 3.5D, 61D, 0.5D)));
    horse.trait(EntityTrait.HORSE);
    horse.trait(EntityTrait.TAMEABLE);
    horse.setTameOwner(owner.uuid());
    platform.host().track(horse);
    return horse;
  }

  private FakeLiving strider() {
    FakeLiving strider = platform.world().add(new FakeLiving(Id.minecraft("strider"), new Loc(platform.world(), 3.5D, 61D, 0.5D)));
    platform.host().track(strider);
    return strider;
  }

  private Mob guard() {
    Mob wolf = tamed(new Mob(Id.minecraft("wolf"), 2.5D), EntityTrait.TAMEABLE);
    wolf.setCustomName("Rex", true);
    TamePetRoles.assign(wolf, TamePetRoles.Role.GUARD_SPOT, new Loc(platform.world(), 2D, 61D, 0D));
    return wolf;
  }

  private Mob tamed(Mob mob, EntityTrait kind) {
    platform.world().add(mob);
    mob.trait(kind);
    mob.trait(EntityTrait.TAMEABLE);
    mob.setTameOwner(player.uuid());
    mob.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    mob.setHealth(20D);
    platform.host().track(mob);
    return mob;
  }

  private FakeLiving pet(double x) {
    FakeLiving wolf = platform.world().add(new FakeLiving(Id.minecraft("wolf"), new Loc(platform.world(), x, 61D, 0.5D)));
    wolf.trait(EntityTrait.TAMEABLE);
    wolf.setTameOwner(player.uuid());
    wolf.setAttributeBase(Attributes.ATTACK_DAMAGE, 4D);
    wolf.setAttributeBase(Attributes.MAX_HEALTH, 20D);
    wolf.setHealth(20D);
    platform.host().track(wolf);
    return wolf;
  }

  private EntityDamageEvent damage(FakeLiving victim, double amount) {
    return new EntityDamageEvent(victim, DamageCause.ENTITY_ATTACK, GENERIC, amount, FinalDamage.IDENTITY);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("taming", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("taming"), registry.getSkill("stealth"), "Packmaster"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("taming").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return SkillHost.of(registry.getSkill("taming")).getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  /** A claim where the player may not interact with anything. */
  private static final class NoInteract implements Protector {
    @Override
    public String name() {
      return "claims";
    }

    @Override
    public boolean enabledByDefault() {
      return true;
    }

    @Override
    public boolean allows(ProtectionCheck check, PlayerRef player, Loc location, EntityRef victim,
                          Id adaptation) {
      return check != ProtectionCheck.INTERACT;
    }
  }

  private final class Mob extends FakeLiving implements MobRef {
    private LivingRef target;

    private Mob(Id type, double x) {
      super(type, new Loc(platform.world(), x, 61D, 0.5D));
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
      return true;
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
