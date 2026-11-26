package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.mutation.SkillMutations;
import art.arcane.adapt.api.mutation.blocks.BarterGesture;
import art.arcane.adapt.api.mutation.blocks.BlockRef;
import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.api.mutation.blocks.MutatedAdaptation;
import art.arcane.adapt.api.mutation.blocks.MutationRecipes;
import art.arcane.adapt.api.mutation.blocks.Recipe;
import art.arcane.adapt.api.mutation.blocks.SkillBlockCatalog;
import art.arcane.adapt.api.mutation.blocks.SkillBlocks;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.xp.MobOrigin;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.CreatureSpawnEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerItemHeldEvent;
import art.arcane.adapt.platform.event.player.PlayerJumpEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerSwapHandItemsEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeGameContent;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The recipe rules every skill relies on, driven through probe skills whose blocks record what they were asked. */
class MutationEngineTest {
  private static final Id MAGIC = Id.minecraft("magic");

  @TempDir
  Path data;

  private TestPlatform platform;
  private FakePlayer player;
  private TestAdapt adapt;
  private AdaptPlayer profile;
  private MutationKit kit;

  @BeforeEach
  void forget() {
    PlatformSupport.reset();
  }

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
      assertThat(adapt.handlerFailures()).isEmpty();
    }
    PlatformSupport.reset();
  }

  @Test
  void anEitherTriggerUsesTheWorkStrikeAloneAndTheFightStrikeNearAHostile() {
    boot(null);
    kit.mutate("agility", "chronos");
    kit.learn("agility", "chronos", Recipe.GRAFT, 1);
    kit.learn("agility", "chronos", Recipe.ECHO, 1);

    block(ProbeTrigger.class, "agility").trip(player, null, false);
    block(ProbeTrigger.class, "agility").trip(player, null, false);
    FakeLiving zombie = MutationKit.zombie(platform.world(), 3.5D, 61D, 0.5D);
    block(ProbeTrigger.class, "chronos").trip(player, null, false);

    assertThat(strike("chronos", BlockSlot.STRIKE_WORK).uses).hasSize(1);
    assertThat(strike("chronos", BlockSlot.STRIKE_WORK).uses.getFirst().at()).isNotNull();
    assertThat(strike("chronos", BlockSlot.STRIKE_FIGHT).uses).isEmpty();
    assertThat(strike("agility", BlockSlot.STRIKE_FIGHT).uses).singleElement()
        .extracting(BlockUse::target).isSameAs(zombie);
    assertThat(strike("agility", BlockSlot.STRIKE_WORK).uses).isEmpty();
  }

  @Test
  void aKillHitsTheNearestOtherHostileAndAFarmedKillSkipsStrikesThatPayOut() {
    boot(null);
    kit.mutate("hunter", "chronos");
    kit.learn("hunter", "chronos", Recipe.GRAFT, 1);
    FakeLiving victim = MutationKit.zombie(platform.world(), 1.5D, 61D, 0.5D);
    FakeLiving other = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    FakeLiving farmed = MutationKit.zombie(platform.world(), 4.5D, 61D, 0.5D);
    MobOrigin.record(farmed, CreatureSpawnEvent.Reason.SPAWNER);
    strike("chronos", BlockSlot.STRIKE_FIGHT).pays = true;

    block(ProbeTrigger.class, "hunter").trip(player, farmed, true);
    assertThat(strike("chronos", BlockSlot.STRIKE_FIGHT).uses).isEmpty();

    block(ProbeTrigger.class, "hunter").trip(player, victim, true);
    assertThat(strike("chronos", BlockSlot.STRIKE_FIGHT).uses).singleElement()
        .extracting(BlockUse::target).isSameAs(other);
  }

  @Test
  void chronosPairsBarterOnADoubleSneakInsteadOfSneakSwap() {
    boot(null);
    kit.mutate("agility", "chronos");
    kit.learn("agility", "chronos", Recipe.BARTER, 1);
    player.setSneaking(true);

    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, FakeItem.empty(), FakeItem.empty()));
    assertThat(swap.isCancelled()).isFalse();
    assertThat(boon("agility").uses).isEmpty();

    adapt.post(new PlayerToggleSneakEvent(player, true));
    adapt.post(new PlayerToggleSneakEvent(player, false));
    adapt.post(new PlayerToggleSneakEvent(player, true));

    assertThat(boon("agility").uses).hasSize(1);
    assertThat(block(ProbeFuel.class, "chronos").stock).isEqualTo(1);
  }

  @Test
  void otherPairsBarterOnSneakSwapAndNotOnADoubleSneak() {
    boot(null);
    kit.mutate("agility", "hunter");
    kit.learn("agility", "hunter", Recipe.BARTER, 1);
    player.setSneaking(true);

    adapt.post(new PlayerToggleSneakEvent(player, true));
    adapt.post(new PlayerToggleSneakEvent(player, true));
    assertThat(boon("agility").uses).isEmpty();

    PlayerSwapHandItemsEvent swap = adapt.post(new PlayerSwapHandItemsEvent(player, FakeItem.empty(), FakeItem.empty()));
    assertThat(swap.isCancelled()).isTrue();
    assertThat(boon("agility").uses).hasSize(1);
    assertThat(block(ProbeFuel.class, "hunter").stock).isEqualTo(1);
  }

  @Test
  void aBlocksMoveAndJumpHandlersRunForHoldersOnlyAndFollowLearning() {
    boot(null);
    FakePlayer other = platform.player("Other", 4.5D, 61D, 0.5D);
    adapt.join(other);
    ProbeTrigger trigger = block(ProbeTrigger.class, "agility");

    kit.mutate("agility", "chronos");
    stepAndJump(player);
    assertThat(trigger.moved).as("mutated, nothing learned").isEmpty();

    kit.learn("agility", "chronos", Recipe.GRAFT, 1);
    stepAndJump(player);
    stepAndJump(other);
    assertThat(trigger.moved).containsExactly(player.uuid());
    assertThat(trigger.jumped).containsExactly(player.uuid());

    kit.learn("agility", "chronos", Recipe.GRAFT, 0);
    stepAndJump(player);
    assertThat(trigger.moved).as("unlearned").hasSize(1);

    kit.learn("agility", "chronos", Recipe.GRAFT, 1);
    stepAndJump(player);
    assertThat(trigger.moved).as("learned again").hasSize(2);

    SkillMutations.unmutateAll(profile.getData(), "Reset");
    stepAndJump(player);
    assertThat(trigger.moved).as("mutations reset").hasSize(2);
    assertThat(trigger.jumped).hasSize(2);
  }

  @Test
  void aHolderStopsCountingAsTheBlocksLearnerOnQuit() {
    boot(null);
    kit.mutate("agility", "chronos");
    kit.learn("agility", "chronos", Recipe.GRAFT, 1);
    AdaptServer server = adapt.core().getAdaptServer();
    assertThat(server.hasOnlineLearner(player.uuid(), "block-agility-trigger")).isTrue();
    assertThat(server.hasOnlineLearner(player.uuid(), "block-chronos-strike-work")).isTrue();
    assertThat(server.hasOnlineLearner(player.uuid(), "block-chronos-trigger")).isFalse();

    adapt.post(new PlayerQuitEvent(player));

    assertThat(server.hasOnlineLearner(player.uuid(), "block-agility-trigger")).isFalse();
    assertThat(server.hasOnlineLearner("block-agility-trigger")).isFalse();
  }

  private void stepAndJump(FakePlayer mover) {
    Loc from = mover.location();
    Loc to = new Loc(from.world(), from.x() + 1D, from.y(), from.z());
    adapt.post(new PlayerMoveEvent(mover, from, to));
    adapt.post(new PlayerJumpEvent(mover, from, to));
  }

  @ParameterizedTest
  @CsvSource({"agility, hunter, SNEAK_SWAP", "agility, chronos, DOUBLE_SNEAK", "stealth, hunter, SNEAK_LEFT_CLICK",
      "stealth, seaborne, SNEAK_SCROLL"})
  void barterCastsOnItsPairsGestureOnly(String lead, String partner, BarterGesture expected) {
    boot(null);
    kit.mutate(lead, partner);
    kit.learn(lead, partner, Recipe.BARTER, 1);
    player.setSneaking(true);
    assertThat(BarterGesture.of(lead, partner)).isEqualTo(expected);

    for (BarterGesture gesture : BarterGesture.values()) {
      int before = boon(lead).uses.size();
      boolean cancelled = gesture(gesture);
      int cast = boon(lead).uses.size() - before;
      assertThat(cast).as("%s on %s", expected, gesture).isEqualTo(gesture == expected ? 1 : 0);
      if (gesture != BarterGesture.DOUBLE_SNEAK) {
        assertThat(cancelled).as("%s cancelled", gesture).isEqualTo(gesture == expected);
      }
      platform.scheduler().advance(20);
    }
  }

  @Test
  void barterWaitsOutItsCooldownBeforeSpendingTheFuelAgain() {
    boot(null);
    kit.mutate("agility", "hunter");
    MutationAdaptation barter = kit.learn("agility", "hunter", Recipe.BARTER, 1);
    player.setSneaking(true);

    assertThat(gesture(BarterGesture.SNEAK_SWAP)).isTrue();
    assertThat(gesture(BarterGesture.SNEAK_SWAP)).isFalse();

    assertThat(barter.getCooldownMillis(1)).isEqualTo(AdaptConfig.get().getMutations().getBarterCooldownMillis())
        .isPositive();
    assertThat(boon("agility").uses).hasSize(1);
    assertThat(block(ProbeFuel.class, "hunter").stock).isEqualTo(1);
  }

  @Test
  void passiveRecipesOnlyRunStrikesAFuelPaidForAndTriggeredRecipesRunNoBoon() {
    for (SkillBlocks lead : SkillBlockCatalog.all()) {
      for (SkillBlocks partner : SkillBlockCatalog.all()) {
        if (lead == partner) {
          continue;
        }
        for (MutatedAdaptation recipe : MutationRecipes.compose(lead, partner)) {
          List<BlockSlot> slots = recipe.blocks().stream().map(BlockRef::slot).toList();
          if (recipe.recipe() == Recipe.BARTER) {
            assertThat(slots).as("%s", recipe.name()).contains(BlockSlot.FUEL);
          } else if (recipe.recipe() == Recipe.ATTUNEMENT) {
            assertThat(slots).as("%s", recipe.name()).doesNotContain(BlockSlot.STRIKE_FIGHT, BlockSlot.STRIKE_WORK);
          } else {
            assertThat(slots).as("%s runs no Boon from a Trigger", recipe.name()).doesNotContain(BlockSlot.BOON);
          }
        }
      }
    }
  }

  @Test
  void barterDoesNotCastWhileTheGolemCrookIsHeld() {
    boot(null);
    kit.mutate("agility", "hunter");
    kit.learn("agility", "hunter", Recipe.BARTER, 1);
    player.holding(GolemItems.crook());
    player.setSneaking(true);

    assertThat(gesture(BarterGesture.SNEAK_SWAP)).isFalse();
    assertThat(boon("agility").uses).isEmpty();
  }

  /** Makes the gesture; true when its event ended cancelled, or for a left click with its item use denied. */
  private boolean gesture(BarterGesture gesture) {
    return switch (gesture) {
      case SNEAK_SWAP -> adapt.post(new PlayerSwapHandItemsEvent(player, FakeItem.empty(), FakeItem.empty())).isCancelled();
      case DOUBLE_SNEAK -> {
        adapt.post(new PlayerToggleSneakEvent(player, true));
        adapt.post(new PlayerToggleSneakEvent(player, false));
        adapt.post(new PlayerToggleSneakEvent(player, true));
        yield false;
      }
      case SNEAK_LEFT_CLICK -> adapt.post(new PlayerInteractEvent(player, InteractAction.LEFT_CLICK_AIR, Hand.MAIN_HAND,
          player.inventory().mainHand(), null, null, null, UseResult.DENY, UseResult.DEFAULT)).isUseDenied();
      case SNEAK_SCROLL -> adapt.post(new PlayerItemHeldEvent(player, 0, 1)).isCancelled();
    };
  }

  @Test
  void theEngineReadsItsTimingFromTheMutationsConfig() throws Exception {
    boot(null);
    kit.mutate("agility", "chronos");
    MutationAdaptation graft = kit.learn("agility", "chronos", Recipe.GRAFT, 1);
    kit.learn("agility", "chronos", Recipe.ECHO, 1);
    assertThat(graft.getCooldownMillis(1)).isEqualTo(8_000L);
    assertThat(graft.getCooldownMillis(graft.getMaxLevel())).isEqualTo(4_000L);
    assertThat(kit.adaptation("agility", "chronos", Recipe.CONVERGENCE).getCooldownMillis(1)).isEqualTo(45_000L);

    AdaptConfig.Mutations config = AdaptConfig.get().getMutations();
    try {
      setMutations(config, "triggerCooldownMillisAtFirstLevel", 0L);
      setMutations(config, "hostileRadius", 2D);
      MutationKit.zombie(platform.world(), 3.5D, 61D, 0.5D);

      block(ProbeTrigger.class, "agility").trip(player, null, false);
      block(ProbeTrigger.class, "agility").trip(player, null, false);
      block(ProbeTrigger.class, "chronos").trip(player, null, false);

      assertThat(graft.getCooldownMillis(1)).isZero();
      assertThat(strike("chronos", BlockSlot.STRIKE_WORK).uses).hasSize(2);
      assertThat(strike("agility", BlockSlot.STRIKE_FIGHT).uses).isEmpty();
      assertThat(strike("agility", BlockSlot.STRIKE_WORK).uses).hasSize(1);
    } finally {
      setMutations(config, "triggerCooldownMillisAtFirstLevel", 8_000L);
      setMutations(config, "hostileRadius", 6D);
    }
  }

  private static void setMutations(AdaptConfig.Mutations config, String field, Object value) throws Exception {
    Field declared = AdaptConfig.Mutations.class.getDeclaredField(field);
    declared.setAccessible(true);
    declared.set(config, value);
  }

  @Test
  void anOverriddenConvergenceAcceptsAStateHeldInTheLastFourSeconds() {
    boot(null);
    kit.mutate("swords", "blocking");
    kit.learn("swords", "blocking", Recipe.CONVERGENCE, 1);
    ProbeState state = block(ProbeState.class, "blocking");
    FakeLiving zombie = MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

    kit.hit(player, zombie, 4D, true);
    assertThat(strike("blocking", BlockSlot.STRIKE_FIGHT).uses).isEmpty();

    state.recently = true;
    kit.hit(player, zombie, 4D, true);
    platform.scheduler().advance(10);

    assertThat(state.windows).contains(4_000L);
    assertThat(strike("blocking", BlockSlot.STRIKE_FIGHT).uses).hasSize(1);
    assertThat(zombie.lastDamageType()).isEqualTo(MAGIC);
  }

  @Test
  void aStrikeThatSetsOffItsOwnTriggerRunsOnce() {
    boot(null);
    kit.mutate("agility", "chronos");
    kit.learn("agility", "chronos", Recipe.GRAFT, 1);
    kit.learn("agility", "chronos", Recipe.CONVERGENCE, 1);
    block(ProbeState.class, "chronos").now = true;
    strike("chronos", BlockSlot.STRIKE_WORK).echo = () -> block(ProbeTrigger.class, "agility").trip(player, null, false);

    block(ProbeTrigger.class, "agility").trip(player, null, false);

    assertThat(strike("chronos", BlockSlot.STRIKE_WORK).uses).hasSize(2);
    assertThat(strike("agility", BlockSlot.STRIKE_WORK).uses).hasSize(1);
  }

  @Test
  void aBlockTheVersionLacksIsLeftOutAndSoAreTheAdaptationsUsingIt() {
    boot(FakeGameContent.v1_20_1());
    kit.mutate("agility", "chronos");

    assertThat(kit.mutated("agility", "chronos").getMutationAdaptations()).extracting(MutationAdaptation::getRecipe)
        .containsExactly(Recipe.GRAFT, Recipe.ECHO, Recipe.ATTUNEMENT, Recipe.CONVERGENCE);
    assertThat(PlatformSupport.leftOut()).extracting(PlatformSupport.LeftOut::name)
        .contains("block-chronos-fuel", "mutated-agility-chronos-barter");
  }

  private void boot(FakeGameContent game) {
    platform = TestPlatform.builder(data).build();
    if (game != null) {
      platform.host().runGame(game);
    }
    player = platform.player("Prober", 0.5D, 61D, 0.5D);
    player.holding(FakeItem.tool("iron_sword", 250));
    adapt = TestAdapt.start(platform, List.of(SkillSwords.class, ProbeAgility.class, ProbeChronos.class,
        ProbeHunter.class, ProbeBlocking.class, ProbeStealth.class, ProbeSeaborne.class));
    profile = adapt.join(player);
    kit = new MutationKit(adapt, profile);
  }

  private <B extends MutationBlock<?>> B block(Class<B> type, String skill) {
    for (MutationBlock<?> block : kit.registry().getSkill(skill).getBlocks()) {
      if (type.isInstance(block)) {
        return type.cast(block);
      }
    }
    throw new AssertionError("No " + type.getSimpleName() + " on " + skill);
  }

  private ProbeStrike strike(String skill, BlockSlot slot) {
    for (MutationBlock<?> block : kit.registry().getSkill(skill).getBlocks()) {
      if (block instanceof ProbeStrike strike && block.getSlot() == slot) {
        return strike;
      }
    }
    throw new AssertionError("No " + slot + " on " + skill);
  }

  private ProbeBoon boon(String skill) {
    return block(ProbeBoon.class, skill);
  }

  abstract static class ProbeSkill extends SimpleSkill<ProbeSkill.Config> {
    ProbeSkill(String name, PlatformNeed... fuelNeeds) {
      super(name, SkillPresentation.of(SkillMessages.HUNTER_NAME, SkillMessages.HUNTER_ICON,
          SkillMessages.HUNTER_DESCRIPTION, SkillMessages.HUNTER_QUIP, SkillMessages.HUNTER_UNLOCK_QUIP_1));
      registerConfiguration(Config.class);
      setIcon(Id.minecraft("stick"));
      registerAdaptation(new ProbeAdaptation(name));
      registerBlocks(new ProbeTrigger(name), new ProbeStrike(name, BlockSlot.STRIKE_FIGHT),
          new ProbeStrike(name, BlockSlot.STRIKE_WORK), new ProbeBoon(name), new ProbeFuel(name, fuelNeeds),
          new ProbeState(name));
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    public static class Config extends SkillConfig {
    }
  }

  public static final class ProbeAgility extends ProbeSkill {
    public ProbeAgility() {
      super("agility");
    }
  }

  public static final class ProbeChronos extends ProbeSkill {
    public ProbeChronos() {
      super("chronos", PlatformNeed.item("mace"));
    }
  }

  public static final class ProbeHunter extends ProbeSkill {
    public ProbeHunter() {
      super("hunter");
    }
  }

  public static final class ProbeStealth extends ProbeSkill {
    public ProbeStealth() {
      super("stealth");
    }
  }

  public static final class ProbeSeaborne extends ProbeSkill {
    public ProbeSeaborne() {
      super("seaborne");
    }
  }

  public static final class ProbeBlocking extends ProbeSkill {
    public ProbeBlocking() {
      super("blocking");
    }
  }

  static final class ProbeAdaptation extends SimpleAdaptation<AdaptationConfig> {
    ProbeAdaptation(String skill) {
      super(skill + "-probe");
      registerConfiguration(AdaptationConfig.class);
      setLocalizationKey("pickaxe.chisel");
    }

    @Override
    public void addStats(int level, StatLines v) {
    }
  }

  static final class ProbeTrigger extends TriggerBlock<MutationBlock.Config> {
    private final List<UUID> moved = new ArrayList<>();
    private final List<UUID> jumped = new ArrayList<>();

    ProbeTrigger(String skill) {
      super(MutationBlocks.id(skill, BlockSlot.TRIGGER));
      registerConfiguration(MutationBlock.Config.class);
    }

    @Handle
    public void on(PlayerMoveEvent e) {
      moved.add(e.player().uuid());
    }

    @Handle
    public void on(PlayerJumpEvent e) {
      jumped.add(e.player().uuid());
    }

    void trip(PlayerRef player, EntityRef target, boolean slain) {
      if (slain) {
        fireKill(player, target);
      } else {
        fire(player, target, player.location(), false);
      }
    }
  }

  static final class ProbeStrike extends StrikeBlock<MutationBlock.Config> {
    private final List<BlockUse> uses = new ArrayList<>();
    private boolean pays;
    private Runnable echo;

    ProbeStrike(String skill, BlockSlot slot) {
      super(MutationBlocks.id(skill, slot));
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public Outcome strike(BlockUse use) {
      uses.add(use);
      if (echo != null) {
        echo.run();
      }
      return Outcome.INSTANT;
    }

    @Override
    public boolean paysOut() {
      return pays;
    }
  }

  static final class ProbeBoon extends BoonBlock<MutationBlock.Config> {
    private final List<BlockUse> uses = new ArrayList<>();

    ProbeBoon(String skill) {
      super(MutationBlocks.id(skill, BlockSlot.BOON));
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public Outcome grant(BlockUse use) {
      uses.add(use);
      return Outcome.INSTANT;
    }
  }

  static final class ProbeFuel extends FuelBlock<MutationBlock.Config> {
    private int stock = 2;

    ProbeFuel(String skill, PlatformNeed... needs) {
      super(MutationBlocks.id(skill, BlockSlot.FUEL));
      registerConfiguration(MutationBlock.Config.class);
      needs(needs);
    }

    @Override
    public boolean canPay(BlockUse use) {
      return stock > 0;
    }

    @Override
    public boolean pay(BlockUse use) {
      stock--;
      return true;
    }
  }

  static final class ProbeState extends StateBlock<MutationBlock.Config> {
    private final List<Long> windows = new ArrayList<>();
    private boolean now;
    private boolean recently;

    ProbeState(String skill) {
      super(MutationBlocks.id(skill, BlockSlot.STATE));
      registerConfiguration(MutationBlock.Config.class);
    }

    @Override
    public boolean holds(PlayerRef player) {
      return now;
    }

    @Override
    public boolean heldWithin(PlayerRef player, long windowMillis) {
      windows.add(windowMillis);
      return now || recently;
    }
  }
}
