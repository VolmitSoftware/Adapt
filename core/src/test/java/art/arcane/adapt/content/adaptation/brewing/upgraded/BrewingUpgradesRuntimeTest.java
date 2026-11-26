package art.arcane.adapt.content.adaptation.brewing.upgraded;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.data.WorldDataPort;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.content.adaptation.brewing.BrewingAbsorption;
import art.arcane.adapt.content.adaptation.brewing.BrewingBlindness;
import art.arcane.adapt.content.adaptation.brewing.BrewingNausea;
import art.arcane.adapt.content.adaptation.brewing.BrewingSaturation;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.event.adapt.AdaptBrewCompleteEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.inventory.BrewEvent;
import art.arcane.adapt.platform.event.inventory.InventoryKind;
import art.arcane.adapt.platform.event.inventory.InventoryMoveItemEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.item.PotionContents;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBrewingStand;
import art.arcane.adapt.testkit.FakeInventory;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.FakeProjectile;
import art.arcane.adapt.testkit.FakeWorld;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BrewingUpgradesRuntimeTest {
  private static final Id SWIFTNESS = Id.minecraft("swiftness");
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id BLINDNESS = Id.minecraft("blindness");
  private static final Id HEALING = Id.minecraft("healing");

  @TempDir
  Path data;

  private final Map<BlockRef, UUID> owners = new HashMap<>();
  private TestPlatform platform;
  private FakeWorld world;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private SkillRegistry registry;
  private BlockRef standBlock;

  @BeforeEach
  void start() {
    AdaptPorts.install(WorldDataPort.class, new OwnerStore());
    platform = TestPlatform.builder(data).build();
    world = platform.world();
    player = platform.player("Brewer", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillBrewing.class, SkillStealth.class));
    profile = adapt.join(player);
    registry = adapt.core().getAdaptServer().getSkillRegistry();
    standBlock = world.set(0, 60, 0, "brewing_stand").blockAt(0, 60, 0);
    owners.put(standBlock, player.uuid());
    platform.host().potion(SWIFTNESS, EffectInstance.of(SPEED, 3600, 0));
  }

  @AfterEach
  void stop() {
    adapt.close();
    AdaptPorts.uninstall(WorldDataPort.class);
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void upgradesDoNothingForABrewerWhoHasNotMutated() {
    profile.getData().getSkillLine("brewing").setAdaptation(upgrade(MasterDistiller.class), 3);
    profile.getData().getSkillLine("brewing").setAdaptation(upgrade(WitchsShelf.class), 3);
    FakeLiving bystander = zombie(4.75D);

    BrewEvent brew = brew(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, SWIFTNESS));
    splash();

    assertThat(brew.results().get(0).potionContents().orElseThrow().customEffects()).isEmpty();
    assertThat(bystander.effect(BLINDNESS)).isNull();
  }

  @Test
  void masterDistillerDoublesBrewedDurations() {
    mutate();
    learn(MasterDistiller.class, 1);

    BrewEvent brew = brew(PotionBuilder.vanilla(PotionBuilder.Type.REGULAR, SWIFTNESS));

    assertThat(brew.results().get(0).potionContents().orElseThrow().customEffects())
        .containsExactly(EffectInstance.of(SPEED, 7200, 0));
    assertThat(profile.getData().getStat("brewing.master-distiller.potions-extended")).isEqualTo(1D);
  }

  @Test
  void masterDistillerSpeedsUpTheOwnersStandWithoutHeat() {
    mutate();
    MasterDistiller distiller = learn(MasterDistiller.class, 2);
    FakeBrewingStand stand = (FakeBrewingStand) standBlock.brewingStand().orElseThrow();
    stand.setBrewingTime(400);
    adapt.post(new InventoryMoveItemEvent(new FakeInventory(), InventoryKind.HOPPER, null, new FakeInventory(),
        InventoryKind.BREWING, standBlock, FakeItem.of("nether_wart"), true));

    distiller.onTick();
    platform.scheduler().advance(1);

    assertThat(distiller.getWarpTicks(2, 0D, 0D)).isPositive();
    assertThat(distiller.getWarpTicks(2, 0D, 1D)).isGreaterThan(distiller.getWarpTicks(2, 0D, 0D));
    assertThat(stand.brewingTime()).isEqualTo(400 - distiller.getWarpTicks(2, 0D, 0D));
  }

  @Test
  void witchsShelfUnlocksTheSixHostileRecipesItsOriginalsBuilt() {
    WitchsShelf shelf = upgrade(WitchsShelf.class);

    assertThat(shelf.getBrewingRecipes()).hasSize(11)
        .containsAll(original(BrewingBlindness.class).getBrewingRecipes())
        .containsAll(original(BrewingNausea.class).getBrewingRecipes());
    assertThat(shelf.isBrewingRecipe("brewing-darkness")).isTrue();
    assertThat(shelf.isBrewingRecipe("brewing-absorption-1")).isFalse();
  }

  @Test
  void witchsShelfSplashesHostileBrewsPastTheVanillaRadius() {
    mutate();
    WitchsShelf shelf = learn(WitchsShelf.class, 1);
    FakeLiving near = zombie(2D);
    FakeLiving wide = zombie(4.75D);
    FakeLiving far = zombie(shelf.getSplashRadius(1) + 1D);

    splash(near);

    assertThat(near.effect(BLINDNESS)).isNull();
    assertThat(wide.effect(BLINDNESS)).isNotNull();
    assertThat(wide.effect(BLINDNESS).durationTicks()).isLessThan(600);
    assertThat(far.effect(BLINDNESS)).isNull();
  }

  @Test
  void apothecaryUnlocksTheDefensiveRecipesAndAnElixirOfAllFour() {
    Apothecary apothecary = upgrade(Apothecary.class);

    assertThat(apothecary.getBrewingRecipes()).hasSize(11)
        .containsAll(original(BrewingAbsorption.class).getBrewingRecipes())
        .contains(apothecary.elixir());
    assertThat(apothecary.elixir().getResult().potionContents().orElseThrow().customEffects())
        .extracting(EffectInstance::type)
        .containsExactly(Id.minecraft("absorption"), Id.minecraft("health_boost"), Id.minecraft("resistance"),
            Id.minecraft("saturation"));
    assertThat(apothecary.elixir().getResult().potionContents().orElseThrow().customEffects())
        .contains(original(BrewingSaturation.class).getBrewingRecipes().get(0).getResult().potionContents()
            .orElseThrow().customEffects().get(0));
  }

  @Test
  void apothecaryLengthensTheElixirsItsHolderBrews() {
    mutate();
    Apothecary apothecary = learn(Apothecary.class, 3);
    FakeBrewingStand stand = (FakeBrewingStand) standBlock.brewingStand().orElseThrow();
    stand.setBottle(0, apothecary.elixir().getResult().copy());

    adapt.post(new AdaptBrewCompleteEvent(standBlock, Apothecary.ELIXIR_RECIPE, player.uuid(), 1));

    List<EffectInstance> effects = stand.bottle(0).potionContents().orElseThrow().customEffects();
    assertThat(effects).filteredOn((EffectInstance effect) -> effect.type().equals(Id.minecraft("absorption")))
        .singleElement()
        .extracting(EffectInstance::durationTicks)
        .isEqualTo((int) (1200 * apothecary.getElixirDuration(3)));
    assertThat(profile.getData().getStat("brewing.apothecary.elixirs-brewed")).isEqualTo(1D);
  }

  private void mutate() {
    profile.getData().setMasterXp(1_000_000D);
    for (String skill : List.of("brewing", "stealth")) {
      PlayerSkillLine line = profile.getData().getSkillLine(skill);
      line.setXp(XP.getXpForLevel(20) + 1D);
      line.settleWithoutRewards();
    }
    assertThat(SkillMutations.mutate(profile.getData(), registry.getSkill("brewing"), registry.getSkill("stealth"), "Alchemist"))
        .isEqualTo(SkillMutations.Result.MUTATED);
  }

  private <A extends Adaptation<?>> A learn(Class<A> type, int level) {
    A upgrade = upgrade(type);
    profile.getData().getSkillLine("brewing").setAdaptation(upgrade, level);
    return upgrade;
  }

  private <A extends Adaptation<?>> A upgrade(Class<A> type) {
    return registry.getSkill("brewing").getUpgrades().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private <A extends Adaptation<?>> A original(Class<A> type) {
    return registry.getSkill("brewing").getAdaptations().stream().filter(type::isInstance).map(type::cast).findFirst()
        .orElseThrow();
  }

  private BrewEvent brew(ItemRef potion) {
    return adapt.post(new BrewEvent(standBlock, FakeItem.of("redstone"), List.of(FakeItem.empty()),
        List.of(potion, FakeItem.empty(), FakeItem.empty()), 10, 0));
  }

  private void splash(LivingRef... affected) {
    FakeProjectile bottle = world.add(new FakeProjectile(Id.minecraft("splash_potion"), new Loc(world, 10.5D, 61D, 0.5D)));
    bottle.setShooter(player);
    ItemRef item = PotionBuilder.of(PotionBuilder.Type.SPLASH).setBaseType(HEALING)
        .addEffect(BLINDNESS, 600, 0, true, true, true).build();
    Map<LivingRef, Double> hit = new HashMap<>();
    for (LivingRef living : affected) {
      hit.put(living, 1D);
    }
    adapt.post(new PotionSplashEvent(bottle, item, List.of(EffectInstance.of(BLINDNESS, 600, 0)), hit));
  }

  private FakeLiving zombie(double distance) {
    FakeLiving zombie = world.add(new FakeLiving(Id.minecraft("zombie"), new Loc(world, 10.5D + distance, 61D, 0.5D)));
    zombie.trait(EntityTrait.MONSTER);
    zombie.trait(EntityTrait.ENEMY);
    return zombie;
  }

  private final class OwnerStore implements WorldDataPort {
    @Override
    public double reportEarnings(BlockRef block) {
      return 1D;
    }

    @Override
    public double getEarningsMultiplier(BlockRef block) {
      return 1D;
    }

    @Override
    public Placement placement(BlockRef block) {
      return null;
    }

    @Override
    public void setPlacement(BlockRef block, Placement placement) {
    }

    @Override
    public void clearPlacement(BlockRef block) {
    }

    @Override
    public UUID blockOwner(BlockRef block) {
      return owners.get(block);
    }

    @Override
    public void setBlockOwner(BlockRef block, UUID owner) {
      if (owner == null) {
        owners.remove(block);
      } else {
        owners.put(block, owner);
      }
    }
  }
}
