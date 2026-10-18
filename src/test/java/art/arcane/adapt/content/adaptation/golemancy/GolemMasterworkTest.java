package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMastery;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemStatSource;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemStats;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.plinth.InfusionRecipe;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemMasterworkTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000501");

  private GolemMasterwork adaptation;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<J> scheduling;

  @BeforeEach
  void construct() {
    lenient().when(plugin.namespace()).thenReturn("adapt");
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.empty());
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenAnswer(invocation -> {
      invocation.<Runnable>getArgument(1).run();
      return true;
    });
    adaptation = new GolemMasterwork();
  }

  @AfterEach
  void close() {
    adaptation.unregister();
    scheduling.close();
    runtime.close();
  }

  @Test
  void defaultsMatchTheBrief() {
    GolemMasterwork.Config config = new GolemMasterwork.Config();

    assertThat(config.maxMasterworksByLevel).containsExactly(1, 1, 2);
    assertThat(config.healthBonusFraction).isEqualTo(0.25D);
    assertThat(config.extraRuneSlots).isEqualTo(1);
    assertThat(config.dualCoreLevel).isEqualTo(2);
    assertThat(config.tierUpLevel).isEqualTo(3);
    assertThat(config.tierThreeBoost).isEqualTo(0.25D);
    assertThat(config.heartDeathDropChance).isEqualTo(1D);
    assertThat(config.auraIntervalTicks).isEqualTo(40);
    assertThat(config.requires).containsExactly("golem-runic-augments:3", "golem-animus-binding:5");
    assertThat(config.maxLevel).isEqualTo(3);
  }

  @Test
  void configurationLoadsWithThreeLevelsAndRegistersItsSourcesOnlyAtRuntime() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-masterwork");
    assertThat(adaptation.getMaxLevel()).isEqualTo(3);
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
    }
    assertThat(adaptation.getInterval()).isEqualTo(2_000L);
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
    assertThat(GolemMastery.source()).isEmpty();
    assertThat(GolemStats.sources()).extracting(GolemStatSource::id).doesNotContain(GolemMasterwork.SOURCE);

    adaptation.onRuntimeActivated();
    assertThat(GolemMastery.source()).isPresent();
    assertThat(GolemStats.sources()).extracting(GolemStatSource::id).contains(GolemMasterwork.SOURCE);

    adaptation.unregister();
    assertThat(GolemMastery.source()).isEmpty();
    assertThat(GolemStats.sources()).extracting(GolemStatSource::id).doesNotContain(GolemMasterwork.SOURCE);
  }

  @Test
  void loadingClampsFractionsSlotsLevelsTheChanceAndTheAuraInterval() {
    String toml = "healthBonusFraction = -0.5\nextraRuneSlots = 40\ndualCoreLevel = 0\ntierUpLevel = -2\ntierThreeBoost = -1.0\n"
        + "heartDeathDropChance = 4.0\nauraIntervalTicks = 0\n";
    assertThat(adaptation.reloadConfigSnapshot(toml, dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    GolemMasterwork.Config config = adaptation.getConfig();
    assertThat(config.healthBonusFraction).isZero();
    assertThat(config.extraRuneSlots).isEqualTo(9);
    assertThat(config.dualCoreLevel).isEqualTo(1);
    assertThat(config.tierUpLevel).isEqualTo(1);
    assertThat(config.tierThreeBoost).isZero();
    assertThat(config.heartDeathDropChance).isEqualTo(1D);
    assertThat(config.auraIntervalTicks).isEqualTo(1);
    assertThat(adaptation.getInterval()).isEqualTo(50L);
  }

  @Test
  void everyLevelShowsItsMasterworksSlotsHealthAndUnlocks() {
    String cap = " Masterwork Golems";
    String slots = "+ 1 Extra Rune Slots on a Masterwork";
    String health = "+ 25% Max Health on a Masterwork";
    String dual = "+ Second Core Slot on a Masterwork";

    assertThat(lore(1)).containsExactly("+ 1" + cap, slots, health);
    assertThat(lore(2)).containsExactly("+ 1" + cap, slots, health, dual);
    assertThat(lore(3)).containsExactly("+ 2" + cap, slots, health, dual, "+ Masterwork Runes Act One Tier Higher",
        "+ 25% Bonus to Tier 3 Runes on a Masterwork");
  }

  @Test
  void perksAndTheMasterworkCapFollowTheLevel() {
    GolemMasterwork.Config config = new GolemMasterwork.Config();

    assertThat(GolemMasterwork.perks(config, 0)).isEqualTo(GolemMastery.Perks.NONE);
    assertThat(GolemMasterwork.perks(config, 1)).isEqualTo(new GolemMastery.Perks(1, false, false, 0D));
    assertThat(GolemMasterwork.perks(config, 2)).isEqualTo(new GolemMastery.Perks(1, true, false, 0D));
    assertThat(GolemMasterwork.perks(config, 3)).isEqualTo(new GolemMastery.Perks(1, true, true, 0.25D));
    assertThat(GolemMasterwork.maxMasterworks(config, 0)).isZero();
    assertThat(GolemMasterwork.maxMasterworks(config, 1)).isEqualTo(1);
    assertThat(GolemMasterwork.maxMasterworks(config, 2)).isEqualTo(1);
    assertThat(GolemMasterwork.maxMasterworks(config, 3)).isEqualTo(2);
    config.maxMasterworksByLevel = new ArrayList<>(List.of(-4));
    assertThat(GolemMasterwork.maxMasterworks(config, 1)).isZero();
  }

  @Test
  void theMasterworkSourceReadsTheOwnersLearnedLevel() {
    learnedOwner(2);
    adaptation.onRuntimeActivated();
    GolemMastery.Source source = GolemMastery.source().orElseThrow();

    assertThat(source.perks(OWNER)).isEqualTo(new GolemMastery.Perks(1, true, false, 0D));
    assertThat(source.maxMasterworks(OWNER)).isEqualTo(1);
    assertThat(source.heartDropChance()).isEqualTo(1D);
    assertThat(source.perks(UUID.randomUUID())).isEqualTo(GolemMastery.Perks.NONE);
    assertThat(source.maxMasterworks(UUID.randomUUID())).isZero();
  }

  @Test
  void theHealthSourceAddsAQuarterOfMaximumHealthToMasterworksOfLearnedOwners() {
    GolemMasterwork.Config config = new GolemMasterwork.Config();

    assertThat(GolemMasterwork.healthStats(config, true, GolemBody.IRON, 1))
        .containsExactly(GolemStatDelta.multiplyTotal(GolemStat.MAX_HEALTH, 0.25D));
    assertThat(GolemMasterwork.healthStats(config, false, GolemBody.IRON, 3)).isEmpty();
    assertThat(GolemMasterwork.healthStats(config, true, GolemBody.IRON, 0)).isEmpty();
    assertThat(GolemMasterwork.healthStats(config, true, GolemBody.BONE, 3)).isEmpty();
    config.healthBonusFraction = 0D;
    assertThat(GolemMasterwork.healthStats(config, true, GolemBody.FROST, 3)).isEmpty();

    learnedOwner(1);
    adaptation.onRuntimeActivated();
    GolemStatSource source = GolemStats.sources().stream().filter(candidate -> candidate.id().equals(GolemMasterwork.SOURCE)).findFirst().orElseThrow();
    assertThat(source.deltas(handle(true, false))).containsExactly(GolemStatDelta.multiplyTotal(GolemStat.MAX_HEALTH, 0.25D));
    assertThat(source.deltas(handle(false, false))).isEmpty();
  }

  @Test
  void theMasterworkHeartInfusionUnlocksThroughThisAdaptationAtLevelOne() {
    InfusionRecipe heart = InfusionRecipe.defaults().get("masterwork_heart");

    assertThat(heart.getUnlockAdaptation()).isEqualTo(adaptation.getName()).isEqualTo(InfusionRecipe.MASTERWORK);
    assertThat(heart.getUnlockLevel()).isEqualTo(1);
    assertThat(heart.getResult()).isEqualTo("golemancy:masterwork_heart");
  }

  @Test
  void theAuraGlowsOnlyAroundAwakeMasterworksOfLearnedOwners() {
    GolemMasterwork learned = learned(1);
    AdaptPlayer owner = mock(AdaptPlayer.class);
    Player player = mock(Player.class);
    when(owner.getPlayer()).thenReturn(player);
    when(player.getUniqueId()).thenReturn(OWNER);
    doReturn(List.of(owner)).when(learned).learnedCandidates(anyLong());
    doNothing().when(learned).auraFx(any(Mob.class));
    GolemHandle shining = handle(true, false);
    GolemHandle plain = handle(false, false);
    GolemHandle sleeping = handle(true, true);
    GolemRegistry registry = mock(GolemRegistry.class);
    when(registry.loadedOf(OWNER)).thenReturn(List.of(shining, plain, sleeping));
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));

    learned.onTick();

    verify(learned, times(1)).auraFx(any(Mob.class));
    verify(learned).auraFx(shining.entity());

    doReturn(0).when(learned).level(OWNER);
    learned.onTick();
    verify(learned, times(1)).auraFx(any(Mob.class));
  }

  @Test
  void aSecondCoreNeedsItsCoreAdaptationLearned() {
    AdaptPlayer owner = learnedOwner(1);
    Adaptation<?> lumber = mock(Adaptation.class);
    when(lumber.getName()).thenReturn("golem-lumber-core");
    when(lumber.getLevel(owner)).thenReturn(1);
    Adaptation<?> breaker = mock(Adaptation.class);
    lenient().when(breaker.getName()).thenReturn("golem-breaker-core");
    lenient().when(breaker.getLevel(owner)).thenReturn(0);
    doReturn(new KList<>(List.of(lumber, breaker))).when(adaptation.getSkill()).getAdaptations();

    assertThat(adaptation.coreUnlocked(OWNER, GolemCoreType.LUMBER)).isTrue();
    assertThat(adaptation.coreUnlocked(OWNER, GolemCoreType.BREAKER)).isFalse();
    assertThat(adaptation.coreUnlocked(OWNER, GolemCoreType.SORTER)).isFalse();
    assertThat(adaptation.coreUnlocked(UUID.randomUUID(), GolemCoreType.LUMBER)).isFalse();
  }

  private AdaptPlayer learnedOwner(int level) {
    AdaptServer server = mock(AdaptServer.class);
    AdaptPlayer owner = mock(AdaptPlayer.class);
    PlayerData data = mock(PlayerData.class);
    PlayerSkillLine line = mock(PlayerSkillLine.class);
    Skill<?> skill = mock(Skill.class);
    lenient().when(plugin.getAdaptServer()).thenReturn(server);
    lenient().when(server.getOnlineAdaptPlayer(OWNER)).thenReturn(owner);
    lenient().when(owner.isRuntimeReady()).thenReturn(true);
    lenient().when(owner.getData()).thenReturn(data);
    lenient().when(skill.isEnabled()).thenReturn(true);
    lenient().when(skill.getName()).thenReturn("golemancy");
    lenient().when(data.getSkillLineNullable("golemancy")).thenReturn(line);
    lenient().when(line.getAdaptationLevel("golem-masterwork")).thenReturn(level);
    adaptation.setSkill(skill);
    return owner;
  }

  private GolemMasterwork learned(int level) {
    GolemMasterwork learned = spy(adaptation);
    lenient().doReturn(level).when(learned).level(any(UUID.class));
    return learned;
  }

  private static GolemHandle handle(boolean masterwork, boolean dormant) {
    GolemState state = mock(GolemState.class);
    lenient().when(state.masterwork()).thenReturn(masterwork);
    GolemHandle handle = mock(GolemHandle.class);
    IronGolem golem = mock(IronGolem.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.body()).thenReturn(GolemBody.IRON);
    lenient().when(handle.state()).thenReturn(state);
    lenient().when(handle.entity()).thenReturn(golem);
    lenient().when(handle.isDormant()).thenReturn(dormant);
    lenient().when(golem.isValid()).thenReturn(true);
    return handle;
  }

  private List<String> lore(int level) {
    List<String> lines = new ArrayList<>();
    Element stats = mock(Element.class, RETURNS_SELF);
    when(stats.addLore(anyString())).thenAnswer(invocation -> {
      lines.add(invocation.<String>getArgument(0).replaceAll("§[0-9a-fk-orx]", ""));
      return stats;
    });
    adaptation.addStats(level, stats);
    return lines;
  }
}
