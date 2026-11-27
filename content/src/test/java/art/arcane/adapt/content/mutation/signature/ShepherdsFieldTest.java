package art.arcane.adapt.content.mutation.signature;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.xp.XpProvenance;
import art.arcane.adapt.content.adaptation.herbalism.upgraded.Wildgrowth;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.entity.EntityChangeBlockEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.value.BlockPos;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.testkit.FakeBlockState;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.FakePlayer;
import art.arcane.adapt.testkit.MutationKit;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;

/** Herbalism and Taming in either order: a natural harvest feeds your animals from your produce; they heal and graze. */
class ShepherdsFieldTest {
  private static final Id WHEAT = Id.minecraft("wheat");
  private static final Id COW = Id.minecraft("cow");
  private static final Id BEEF = Id.minecraft("beef");

  @TempDir
  Path data;

  private TestPlatform platform;
  private TestAdapt adapt;
  private FakePlayer player;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void start() {
    platform = TestPlatform.builder(data).build();
    platform.host().animals().peaceful(COW, WHEAT).peaceful(Id.minecraft("wolf"), BEEF);
    player = platform.player("Shepherd", 0.5D, 61D, 0.5D);
    adapt = TestAdapt.start(platform, List.of(SkillHerbalism.class, SkillTaming.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  @AfterEach
  void stop() {
    adapt.close();
    assertThat(adapt.handlerFailures()).isEmpty();
  }

  @Test
  void aNaturalHarvestFeedsAPetAndAFedCowFromYourProduceAndHealsThem() {
    kit.mutate("herbalism", "taming");
    kit.learn("herbalism", "taming", Recipe.CONVERGENCE, 1);
    wildgrowth(1);
    PairMob wolf = PairMob.pet(platform, player, 3.5D, 0.5D);
    PairMob cow = fedCow(-3.5D);
    player.inventory().addItem(new FakeItem(WHEAT, 5));
    player.inventory().addItem(new FakeItem(BEEF, 5));
    wolf.setHealth(10D);
    cow.setHealth(10D);

    harvest(2, 7);
    platform.scheduler().advance(2);

    assertThat(wolf.health()).isEqualTo(14D);
    assertThat(cow.health()).isEqualTo(14D);
    assertThat(player.inventory().contains(WHEAT, 4)).isTrue();
    assertThat(player.inventory().contains(WHEAT, 5)).isFalse();
    assertThat(player.inventory().contains(BEEF, 4)).isTrue();
    assertThat(player.inventory().contains(BEEF, 5)).isFalse();
    assertThat(platform.host().animals().isInLove(cow)).isFalse();
    assertThat(player.heardSounds()).isNotEmpty();
  }

  @Test
  void tamingAsLeadFeedsTheFlockToo() {
    kit.mutate("taming", "herbalism");
    kit.learn("taming", "herbalism", Recipe.CONVERGENCE, 1);
    wildgrowth(1);
    player.inventory().addItem(new FakeItem(BEEF, 2));
    PairMob wolf = PairMob.pet(platform, player, 3.5D, 0.5D);
    wolf.setHealth(10D);

    harvest(2, 7);
    platform.scheduler().advance(2);

    assertThat(wolf.health()).isEqualTo(14D);
  }

  @Test
  void fedAnimalsGrazeInPlaceWithoutTramplingAndWalkBackWhenTheyWander() {
    kit.mutate("herbalism", "taming");
    kit.learn("herbalism", "taming", Recipe.CONVERGENCE, 1);
    wildgrowth(1);
    PairMob cow = fedCow(-3.5D);
    player.inventory().addItem(new FakeItem(WHEAT, 5));
    BlockRef farmland = block(-3, 60, 0, Id.minecraft("farmland"), Map.of());

    harvest(2, 7);
    platform.scheduler().advance(2);
    EntityChangeBlockEvent trample = adapt.post(new EntityChangeBlockEvent(cow, farmland, new FakeBlockState(Id.minecraft("dirt"))));
    assertThat(trample.isCancelled()).isTrue();

    cow.moveTo(new Loc(platform.world(), -12.5D, 61D, 0.5D));
    signature("herbalism", "taming").onTick();
    platform.scheduler().advance(2);
    assertThat(cow.pathedTo()).isNotNull();
    assertThat(cow.pathedTo().x()).isEqualTo(-3.5D);
  }

  @Test
  void strangersAnimalsAndUnfedLivestockAreNeverFed() {
    kit.mutate("herbalism", "taming");
    kit.learn("herbalism", "taming", Recipe.CONVERGENCE, 3);
    wildgrowth(1);
    player.inventory().addItem(new FakeItem(WHEAT, 5));
    player.inventory().addItem(new FakeItem(BEEF, 5));
    PairMob stray = PairMob.pet(platform, player, 3.5D, 0.5D);
    stray.setTameOwner(UUID.randomUUID());
    PairMob wild = PairMob.animal(platform, COW, -3.5D, 0.5D);
    stray.setHealth(10D);
    wild.setHealth(10D);

    harvest(2, 7);
    platform.scheduler().advance(2);

    assertThat(stray.health()).isEqualTo(10D);
    assertThat(wild.health()).isEqualTo(10D);
    assertThat(player.inventory().contains(WHEAT, 5)).isTrue();
  }

  @Test
  void aBoneMealedHarvestFeedsNothingAndAnUnripeCropDoesNotCount() {
    kit.mutate("herbalism", "taming");
    kit.learn("herbalism", "taming", Recipe.CONVERGENCE, 1);
    wildgrowth(1);
    player.inventory().addItem(new FakeItem(BEEF, 5));
    PairMob wolf = PairMob.pet(platform, player, 3.5D, 0.5D);
    wolf.setHealth(10D);

    harvest(2, 3);
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class, CALLS_REAL_METHODS)) {
      provenance.when(() -> XpProvenance.isBonemealed(any(BlockRef.class))).thenReturn(true);
      harvest(2, 7);
    }
    platform.scheduler().advance(2);

    assertThat(wolf.health()).isEqualTo(10D);
    assertThat(player.inventory().contains(BEEF, 5)).isTrue();
  }

  @Test
  void withoutFoodOrTheLearnedAdaptationNothingIsFedAndTheCooldownHolds() {
    PairMob wolf = PairMob.pet(platform, player, 3.5D, 0.5D);
    wolf.setHealth(10D);
    player.inventory().addItem(new FakeItem(BEEF, 5));
    harvest(2, 7);
    kit.mutate("herbalism", "taming");
    harvest(2, 7);
    platform.scheduler().advance(2);
    assertThat(wolf.health()).isEqualTo(10D);

    kit.learn("herbalism", "taming", Recipe.CONVERGENCE, 1);
    wildgrowth(1);
    harvest(2, 7);
    harvest(2, 7);
    platform.scheduler().advance(2);
    assertThat(wolf.health()).isEqualTo(14D);
    assertThat(player.inventory().contains(BEEF, 4)).isTrue();
  }

  @Test
  void theFlockIsFedOnlyInsideWildgrowthsReach() {
    kit.mutate("herbalism", "taming");
    kit.learn("herbalism", "taming", Recipe.CONVERGENCE, 1);
    player.inventory().addItem(new FakeItem(BEEF, 5));
    PairMob wolf = PairMob.pet(platform, player, 20.5D, 0.5D);
    wolf.setHealth(10D);

    harvest(2, 7);
    platform.scheduler().advance(2);
    assertThat(wolf.health()).isEqualTo(10D);

    wildgrowth(1);
    harvest(2, 7);
    platform.scheduler().advance(2);
    assertThat(wolf.health()).isEqualTo(10D);

    wildgrowth(2);
    platform.scheduler().advance(20 * 600);
    harvest(2, 7);
    platform.scheduler().advance(2);
    assertThat(wolf.health()).isEqualTo(14D);
  }

  @Test
  void withBoneMealTrackingOffNoHarvestFeeds() {
    kit.mutate("herbalism", "taming");
    kit.learn("herbalism", "taming", Recipe.CONVERGENCE, 1);
    wildgrowth(1);
    player.inventory().addItem(new FakeItem(BEEF, 5));
    PairMob wolf = PairMob.pet(platform, player, 3.5D, 0.5D);
    wolf.setHealth(10D);
    AdaptConfig config = spy(AdaptConfig.get());
    AdaptConfig.XpIntegrity integrity = spy(config.getXpIntegrity());
    doReturn(false).when(integrity).isBonemealTrackingEnabled();
    doReturn(integrity).when(config).getXpIntegrity();

    try (MockedStatic<AdaptConfig> configs = mockStatic(AdaptConfig.class, CALLS_REAL_METHODS)) {
      configs.when(AdaptConfig::get).thenReturn(config);
      harvest(2, 7);
    }
    platform.scheduler().advance(2);

    assertThat(wolf.health()).isEqualTo(10D);
    assertThat(player.inventory().contains(BEEF, 5)).isTrue();
  }

  @Test
  void higherLevelsFeedMoreAnimalsHealMoreAndGrazeLonger() {
    kit.mutate("herbalism", "taming");
    MutationAdaptation convergence = kit.learn("herbalism", "taming", Recipe.CONVERGENCE, 1);
    ShepherdsField field = (ShepherdsField) convergence.getSignature();

    assertThat(field.animals(3)).isGreaterThan(field.animals(2)).isGreaterThan(field.animals(1));
    assertThat(field.healPerFeed(3)).isGreaterThan(field.healPerFeed(1));
    assertThat(field.grazeMillis(3)).isGreaterThan(field.grazeMillis(1));
  }

  private void wildgrowth(int level) {
    Wildgrowth wildgrowth = PairParts.owned(Id.adapt("herbalism/upgrade/wildgrowth"), Wildgrowth.class);
    profile.getData().getSkillLine("herbalism").setAdaptation(wildgrowth, level);
  }

  private PairMob fedCow(double x) {
    PairMob cow = PairMob.animal(platform, COW, x, 0.5D);
    player.holding(new FakeItem(WHEAT, 1));
    adapt.post(new PlayerInteractEntityEvent(player, cow, Hand.MAIN_HAND, new Vec3(x, 61D, 0.5D)));
    player.holding(FakeItem.empty());
    return cow;
  }

  private ShepherdsField signature(String lead, String partner) {
    return (ShepherdsField) kit.adaptation(lead, partner, Recipe.CONVERGENCE).getSignature();
  }

  private void harvest(int x, int age) {
    BlockRef crop = block(x, 61, 2, WHEAT, Map.of("age", Integer.toString(age)));
    adapt.post(new BlockBreakEvent(player, crop, true, 0));
  }

  private BlockRef block(int x, int y, int z, Id type, Map<String, String> states) {
    platform.world().set(new BlockPos(x, y, z), new FakeBlockState(type, states));
    return platform.world().blockAt(x, y, z);
  }
}
