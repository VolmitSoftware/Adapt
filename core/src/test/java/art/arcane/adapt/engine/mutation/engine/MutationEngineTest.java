package art.arcane.adapt.engine.mutation.engine;

import art.arcane.adapt.api.item.CustomItem;
import art.arcane.adapt.engine.item.AdaptItems;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.BarterGesture;
import art.arcane.adapt.api.mutation.BlockKey;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.BoonBlock;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.StateBlock;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.api.mutation.TriggerBlock;
import art.arcane.adapt.api.mutation.TriggerKind;
import art.arcane.adapt.content.BuiltinAddon;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.mutation.blocks.MutatedAdaptation;
import art.arcane.adapt.engine.mutation.blocks.MutationRecipes;
import art.arcane.adapt.engine.mutation.kit.DefaultKit;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.support.PlatformSupport;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.xp.MobOrigin;
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
import org.mockito.MockedStatic;
import java.util.UUID;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
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
  private art.arcane.adapt.testkit.MutationKit kit;

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
    FakeLiving zombie = art.arcane.adapt.testkit.MutationKit.zombie(platform.world(), 3.5D, 61D, 0.5D);
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
    FakeLiving victim = art.arcane.adapt.testkit.MutationKit.zombie(platform.world(), 1.5D, 61D, 0.5D);
    FakeLiving other = art.arcane.adapt.testkit.MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);
    FakeLiving farmed = art.arcane.adapt.testkit.MutationKit.zombie(platform.world(), 4.5D, 61D, 0.5D);
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
    assertThat(server.hasOnlineLearner(player.uuid(), "adapt:agility/block/trigger")).isTrue();
    assertThat(server.hasOnlineLearner(player.uuid(), "adapt:chronos/block/strike_work")).isTrue();
    assertThat(server.hasOnlineLearner(player.uuid(), "adapt:chronos/block/trigger")).isFalse();

    adapt.post(new PlayerQuitEvent(player));

    assertThat(server.hasOnlineLearner(player.uuid(), "adapt:agility/block/trigger")).isFalse();
    assertThat(server.hasOnlineLearner("adapt:agility/block/trigger")).isFalse();
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
    assertThat(MutationEngine.gesture(new SkillPair(Id.adapt(lead), Id.adapt(partner)))).contains(expected);

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
    platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform, new BuiltinAddon());
    SkillRegistry registry = adapt.core().getAdaptServer().getSkillRegistry();
    for (Skill<?> lead : registry.getSkills()) {
      for (Skill<?> partner : registry.getSkills()) {
        if (lead == partner) {
          continue;
        }
        SkillPair pair = new SkillPair(lead.getId(), partner.getId());
        for (MutatedAdaptation recipe : MutationRecipes.compose(pair, Kits.of(lead).triggerKind(),
            Kits.of(partner).triggerKind(), registry.getPairRegistry())) {
          if (registry.getPairRegistry().signature(recipe.slot()).isPresent()) {
            continue;
          }
          List<BlockSlot> slots = recipe.blocks().stream().map(BlockKey::slot).toList();
          if (recipe.recipe() == Recipe.BARTER) {
            assertThat(slots).as("%s", recipe.slot()).contains(BlockSlot.FUEL);
          } else if (recipe.recipe() == Recipe.ATTUNEMENT) {
            assertThat(slots).as("%s", recipe.slot()).doesNotContain(BlockSlot.STRIKE_FIGHT, BlockSlot.STRIKE_WORK);
          } else {
            assertThat(slots).as("%s runs no Boon from a Trigger", recipe.slot()).doesNotContain(BlockSlot.BOON);
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

    try (MockedStatic<AdaptItems> items = mockStatic(AdaptItems.class, CALLS_REAL_METHODS)) {
      items.when(AdaptItems::items).thenReturn(List.<CustomItem.Definition<?>>of(GolemItems.ITEM));
      assertThat(gesture(BarterGesture.SNEAK_SWAP)).isFalse();
    }
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
      art.arcane.adapt.testkit.MutationKit.zombie(platform.world(), 3.5D, 61D, 0.5D);

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
    FakeLiving zombie = art.arcane.adapt.testkit.MutationKit.zombie(platform.world(), 2.5D, 61D, 0.5D);

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
        .contains("adapt:chronos/block/fuel", "adapt:mutated/agility/adapt/chronos/barter");
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
    kit = new art.arcane.adapt.testkit.MutationKit(adapt, profile);
  }

  private <B extends MutationBlock<?>> B block(Class<B> type, String skill) {
    for (MutationBlock<?> block : SkillHost.of(kit.registry().getSkill(skill)).getBlocks()) {
      if (type.isInstance(block)) {
        return type.cast(block);
      }
    }
    throw new AssertionError("No " + type.getSimpleName() + " on " + skill);
  }

  private ProbeStrike strike(String skill, BlockSlot slot) {
    for (MutationBlock<?> block : SkillHost.of(kit.registry().getSkill(skill)).getBlocks()) {
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
    ProbeSkill(String name, TriggerKind trigger, List<BarterGesture> reserved, PlatformNeed... fuelNeeds) {
      super(Id.adapt(name));
      registerConfiguration(Config.class);
      setIcon(Id.minecraft("stick"));
      registerAdaptation(Id.adapt(name + "/probe"), () -> new ProbeAdaptation(name));
      MutationKit.Builder kit = MutationKit.builder()
          .trigger(trigger, () -> new ProbeTrigger(name))
          .strikes(() -> new ProbeStrike(name, StrikeForm.FIGHT), () -> new ProbeStrike(name, StrikeForm.WORK))
          .boon(() -> new ProbeBoon(name))
          .fuel(() -> new ProbeFuel(name, fuelNeeds))
          .state(() -> new ProbeState(name))
          .sounds(DefaultKit.SOUNDS);
      for (BarterGesture gesture : reserved) {
        kit.reserve(gesture);
      }
      registerMutationKit(kit.build());
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    public static class Config extends SkillConfig {
    }
  }

  public static final class ProbeAgility extends ProbeSkill {
    public static final Id ID = Id.adapt("agility");

    public ProbeAgility() {
      super("agility", TriggerKind.EITHER, List.of(BarterGesture.SNEAK_LEFT_CLICK));
    }
  }

  public static final class ProbeChronos extends ProbeSkill {
    public static final Id ID = Id.adapt("chronos");

    public ProbeChronos() {
      super("chronos", TriggerKind.EITHER, List.of(BarterGesture.SNEAK_SWAP, BarterGesture.SNEAK_LEFT_CLICK),
          PlatformNeed.item("mace"));
    }
  }

  public static final class ProbeHunter extends ProbeSkill {
    public static final Id ID = Id.adapt("hunter");

    public ProbeHunter() {
      super("hunter", TriggerKind.FIGHT, List.of());
    }
  }

  public static final class ProbeStealth extends ProbeSkill {
    public static final Id ID = Id.adapt("stealth");

    public ProbeStealth() {
      super("stealth", TriggerKind.FIGHT, List.of(BarterGesture.SNEAK_SWAP, BarterGesture.DOUBLE_SNEAK));
    }
  }

  public static final class ProbeSeaborne extends ProbeSkill {
    public static final Id ID = Id.adapt("seaborne");

    public ProbeSeaborne() {
      super("seaborne", TriggerKind.EITHER, List.of(BarterGesture.DOUBLE_SNEAK, BarterGesture.SNEAK_LEFT_CLICK));
    }
  }

  public static final class ProbeBlocking extends ProbeSkill {
    public static final Id ID = Id.adapt("blocking");

    public ProbeBlocking() {
      super("blocking", TriggerKind.FIGHT, List.of());
    }
  }

  static final class ProbeAdaptation extends SimpleAdaptation<AdaptationConfig> {
    ProbeAdaptation(String skill) {
      super(Id.adapt(skill + "/probe"));
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
      super(Id.adapt(skill));
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

    ProbeStrike(String skill, StrikeForm form) {
      super(Id.adapt(skill), form);
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
      super(Id.adapt(skill));
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
      super(Id.adapt(skill));
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
      super(Id.adapt(skill));
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
