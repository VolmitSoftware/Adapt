package art.arcane.adapt.content.adaptation.sword.upgraded;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.sword.SwordsRiposteWindow;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.FinalDamage;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.PrepareAnvilEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Awakened Blade on Duelist's Verdict and Tempest Flow's whetstone grinds. */
class SwordDepthRuntimeTest {
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
    player = platform.player("Duelist", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void anAwakenedBladeBanksOnlyNaturalKillsByYourOwnHand() {
    mutate();
    DuelistsVerdict verdict = learn(DuelistsVerdict.class, 1);
    player.holding(awaken(FakeItem.tool("iron_sword", 250)));

    kill(zombie());
    FakeLiving spawned = zombie();
    MobOrigin.record(spawned, CreatureSpawnEvent.Reason.SPAWNER);
    kill(spawned);
    FakeLiving shot = zombie();
    FakeLiving archer = new FakeLiving(Id.minecraft("skeleton"), new Loc(platform.world(), 40.5D, 61D, 40.5D));
    adapt.post(new EntityDeathEvent(shot, player, archer, player, DamageCause.PROJECTILE, List.of(), 0));
    platform.scheduler().advance(1);
    kill(platform.player("Alt", 60.5D, 61D, 60.5D));

    ItemRef held = player.inventory().mainHand();
    assertThat(verdict.tier(player, held)).isZero();
    assertThat(held.lore()).anySatisfy((String line) -> assertThat(line).contains("1 kills"));
    FakeLiving target = platform.world().add(zombie());
    double expected = (10D * (1D + verdict.getBonusDamage(1))) + verdict.getBladeBonus(1, 1);
    assertThat(hit(target, 10D, false).damage()).isCloseTo(expected, within(1.0E-9D));
  }

  @Test
  void banksWakeRiposteAndVerdictOnThatSwordOnly() {
    mutate();
    DuelistsVerdict verdict = learn(DuelistsVerdict.class, 3);
    verdict.getConfig().riposteKills = 1;
    verdict.getConfig().lungeKills = 2;
    verdict.getConfig().verdictKills = 3;
    profile.getData().getPreferences().set("sword-riposte-window", "enabled", "OFF");
    ItemRef blade = awaken(FakeItem.tool("iron_sword", 250));
    player.holding(blade);
    kill(zombie());

    FakeLiving attacker = platform.world().add(zombie());
    player.inventory().setOffHand(FakeItem.of("shield"));
    player.setBlocking(true);
    adapt.post(new EntityDamageByEntityEvent(player, attacker, attacker, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), 4D, false, FinalDamage.IDENTITY));
    player.setBlocking(false);
    double duel = 1D + verdict.getBonusDamage(3);
    double riposte = hit(attacker, 10D, false).damage();
    double plain = hit(attacker, 10D, false).damage();
    double flat = verdict.getBladeBonus(3, 1);
    assertThat(plain).isCloseTo((10D * duel) + flat, within(1.0E-9D));
    assertThat(riposte - flat).isGreaterThanOrEqualTo(((plain - flat) * 1.5D) - 1.0E-9D);

    kill(zombie());
    kill(zombie());
    attacker.setAttributeBase(Attributes.MAX_HEALTH, 40D);
    attacker.setHealth(8D);
    EntityDamageByEntityEvent ended = hit(attacker, 1D, false);
    assertThat(ended.damage()).isGreaterThan(8D);
    assertThat(ended.ignoreReductions()).isTrue();

    FakeLiving boss = platform.world().add(zombie());
    boss.trait(EntityTrait.BOSS);
    boss.setAttributeBase(Attributes.MAX_HEALTH, 40D);
    boss.setHealth(8D);
    assertThat(hit(boss, 1D, false).ignoreReductions()).isFalse();

    player.holding(FakeItem.tool("iron_sword", 250));
    attacker.setHealth(8D);
    assertThat(hit(attacker, 1D, false).ignoreReductions()).isFalse();
  }

  @Test
  void aBlockArmsOnlyTheKeptRiposteWhenThePlayerHoldsIt() {
    mutate();
    DuelistsVerdict verdict = learn(DuelistsVerdict.class, 1);
    verdict.getConfig().riposteKills = 1;
    SwordsRiposteWindow kept = registry.getSkill("swords").getAdaptations().stream()
        .filter(SwordsRiposteWindow.class::isInstance).map(SwordsRiposteWindow.class::cast).findFirst().orElseThrow();
    profile.getData().getSkillLine("swords").setAdaptation(kept, 1);
    player.holding(awaken(FakeItem.tool("iron_sword", 250)));
    kill(zombie());

    FakeLiving attacker = platform.world().add(zombie());
    player.inventory().setOffHand(FakeItem.of("shield"));
    player.setBlocking(true);
    adapt.post(new EntityDamageByEntityEvent(player, attacker, attacker, DamageCause.ENTITY_ATTACK,
        Id.minecraft("mob_attack"), 4D, false, FinalDamage.IDENTITY));
    player.setBlocking(false);
    hit(attacker, 10D, false);

    assertThat(profile.getData().getStat("swords.riposte.ripostes-landed")).isEqualTo(1D);
    assertThat(profile.getData().getStat("swords.duelists-verdict.ripostes")).isZero();
  }

  @Test
  void awakeningANewSwordPutsTheOldOneToSleep() {
    mutate();
    DuelistsVerdict verdict = learn(DuelistsVerdict.class, 1);
    ItemRef first = awaken(FakeItem.tool("iron_sword", 250));
    player.holding(first);
    assertThat(verdict.tier(player, first)).isZero();

    ItemRef second = awaken(FakeItem.tool("diamond_sword", 1561));
    assertThat(verdict.tier(player, second)).isZero();
    assertThat(verdict.tier(player, first)).isEqualTo(-1);

    player.holding(first);
    kill(zombie());
    assertThat(first.lore()).anySatisfy((String line) -> assertThat(line).contains("0 kills"));
  }

  @Test
  void anAwakenedBladeIsAnOrdinarySwordToAnyoneWithoutTheUpgrade() {
    mutate();
    learn(DuelistsVerdict.class, 1);
    ItemRef blade = awaken(FakeItem.tool("iron_sword", 250));

    FakePlayer other = platform.player("Other", 0.5D, 61D, 4.5D);
    adapt.join(other);
    other.holding(blade);
    FakeLiving target = platform.world().add(zombie());

    assertThat(adapt.post(new EntityDamageByEntityEvent(target, other, other, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), 10D, false, FinalDamage.IDENTITY)).damage()).isEqualTo(10D);
  }

  @Test
  void lungeClosesOnATargetBeyondReachOnlyOnceAwake() {
    mutate();
    DuelistsVerdict verdict = learn(DuelistsVerdict.class, 1);
    verdict.getConfig().lungeKills = 1;
    platform.world().add(new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 0.5D, 61D, 6.5D)));
    player.setSprinting(true);

    player.holding(FakeItem.tool("iron_sword", 250));
    swing();
    platform.scheduler().advance(1);
    assertThat(player.velocity().lengthSquared()).isZero();

    player.holding(awaken(FakeItem.tool("iron_sword", 250)));
    kill(zombie());
    swing();
    platform.scheduler().advance(1);
    assertThat(player.velocity().z()).isPositive();
  }

  @Test
  void tempestFlowOwnsThreeWhetstoneRecipesAtLevelTwo() {
    TempestFlow flow = upgrade(TempestFlow.class);
    List<RecipeSpec> recipes = flow.getRecipes().stream()
        .filter((RecipeSpec recipe) -> recipe.key().path().startsWith(Whetstone.ID)).toList();

    assertThat(recipes).hasSize(3);
    platform.scheduler().advance(20);
    assertThat(recipes).allSatisfy((RecipeSpec recipe) -> assertThat(registry.getRequiredAdaptation(recipe.key())).isSameAs(flow));
    assertThat(recipes).allSatisfy((RecipeSpec recipe) -> assertThat(recipe.requiredLevel()).isEqualTo(TempestFlow.WHETSTONE_LEVEL));
  }

  @Test
  void aWhetstoneIsInertWithoutTheMutationOrBelowItsLevel() {
    Whetstone stone = whetstone();
    ItemRef sword = FakeItem.tool("iron_sword", 250);
    player.inventory().setOffHand(sword);
    player.holding(stone.create(new Whetstone.Data(Whetstone.Grind.KEEN)));
    use();

    mutate();
    learn(TempestFlow.class, 1);
    use();

    assertThat(player.inventory().offHand().damage()).isZero();
    assertThat(stone.is(player.inventory().mainHand())).isTrue();
    assertThat(player.inventory().offHand().lore()).isEmpty();
  }

  @Test
  void grindingSpendsTheStoneAndTenDurabilityAndSharpensCrits() {
    mutate();
    TempestFlow flow = learn(TempestFlow.class, 2);
    Whetstone stone = whetstone();
    player.inventory().setOffHand(FakeItem.tool("iron_sword", 250));
    player.holding(stone.create(new Whetstone.Data(Whetstone.Grind.KEEN)));
    use();

    assertThat(player.inventory().offHand().damage()).isEqualTo(10);
    assertThat(player.inventory().mainHand().isEmpty()).isTrue();
    FakeLiving target = zombie();
    ItemRef ground = player.inventory().offHand();
    player.holding(FakeItem.tool("iron_sword", 250));
    double plainCrit = hit(target, 5D, true).damage();
    player.holding(ground);
    assertThat(hit(target, 5D, true).damage()).isCloseTo(plainCrit * (1D + flow.keenBonus(1)), within(1.0E-9D));
  }

  @Test
  void aSwordTooWornToPayIsNotGroundAndKeepsTheStone() {
    mutate();
    learn(TempestFlow.class, 2);
    Whetstone stone = whetstone();
    FakeItem worn = FakeItem.tool("iron_sword", 250);
    worn.setDamage(241);
    player.inventory().setOffHand(worn);
    player.holding(stone.create(new Whetstone.Data(Whetstone.Grind.SERRATED)));
    use();

    assertThat(player.inventory().offHand().damage()).isEqualTo(241);
    assertThat(stone.is(player.inventory().mainHand())).isTrue();
  }

  @Test
  void aGrindRunsOutAfterItsCombatTime() {
    mutate();
    TempestFlow flow = learn(TempestFlow.class, 2);
    flow.getConfig().whetstoneCombatMillis = 4_000;
    Whetstone stone = whetstone();
    player.inventory().setOffHand(FakeItem.tool("iron_sword", 250));
    player.holding(stone.create(new Whetstone.Data(Whetstone.Grind.KEEN)));
    use();
    FakeLiving target = zombie();
    ItemRef ground = player.inventory().offHand();
    player.holding(FakeItem.tool("iron_sword", 250));
    double plainCrit = hit(target, 5D, true).damage();
    player.holding(ground);
    int groundLore = ground.lore().size();

    assertThat(hit(target, 5D, true).damage()).isGreaterThan(plainCrit);
    assertThat(hit(target, 5D, true).damage()).isCloseTo(plainCrit, within(1.0E-9D));
    assertThat(player.inventory().mainHand().lore()).hasSize(groundLore - 1);
  }

  @Test
  void whetstoneRitualSharpnessBlocksGrindingAndGrindsDoNotStackWithIt() {
    mutate();
    TempestFlow flow = learn(TempestFlow.class, 2);
    Whetstone stone = whetstone();
    player.inventory().setOffHand(FakeItem.tool("iron_sword", 250));
    player.holding(stone.create(new Whetstone.Data(Whetstone.Grind.KEEN)));
    use();
    ItemRef ground = player.inventory().offHand();

    player.holding(FakeItem.tool("iron_sword", 250));
    double plainCrit = hit(zombie(), 5D, true).damage();
    player.setLevel(10);
    player.setSneaking(true);
    BlockRef grindstone = platform.world().blockAt(1, 61, 0);
    grindstone.setType(Id.minecraft("grindstone"), false);
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_BLOCK, Hand.MAIN_HAND,
        player.inventory().mainHand(), grindstone, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
    player.setSneaking(false);
    assertThat(player.level()).isLessThan(10);

    player.holding(ground);
    assertThat(hit(zombie(), 5D, true).damage()).isCloseTo(plainCrit, within(1.0E-9D));

    player.inventory().setOffHand(FakeItem.tool("iron_sword", 250));
    player.holding(stone.create(new Whetstone.Data(Whetstone.Grind.SERRATED)));
    use();
    assertThat(player.inventory().offHand().damage()).isZero();
    assertThat(flow.getActiveLevel(player)).isEqualTo(2);
  }

  private Whetstone whetstone() {
    return (Whetstone) upgrade(TempestFlow.class).getTools().getFirst();
  }

  private void use() {
    adapt.post(new PlayerInteractEvent(player, InteractAction.RIGHT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private void swing() {
    adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND,
        player.inventory().mainHand(), null, BlockFace.UP, null, UseResult.DEFAULT, UseResult.DEFAULT));
  }

  private ItemRef awaken(FakeItem sword) {
    return adapt.post(new PrepareAnvilEvent(player, InventoryKind.ANVIL, List.of(sword.copy()), sword, "Oathkeeper", 1))
        .result();
  }

  private void kill(art.arcane.adapt.platform.entity.LivingRef victim) {
    adapt.post(new EntityDeathEvent(victim, player, player, player, DamageCause.ENTITY_ATTACK, List.of(), 0));
    platform.scheduler().advance(1);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("swords", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("swords"), registry.getSkill("stealth"), "Duelist"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("swords").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("swords").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private FakeLiving zombie() {
    FakeLiving zombie = new FakeLiving(Id.minecraft("zombie"), new Loc(platform.world(), 2.5D, 61D, 0.5D));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    zombie.trait(EntityTrait.UNDEAD);
    zombie.trait(EntityTrait.ZOMBIE);
    return zombie;
  }

  private EntityDamageByEntityEvent hit(FakeLiving target, double damage, boolean critical) {
    return adapt.post(new EntityDamageByEntityEvent(target, player, player, DamageCause.ENTITY_ATTACK,
        Id.minecraft("player_attack"), damage, critical, FinalDamage.IDENTITY));
  }
}
