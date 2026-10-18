package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationRequirement;
import art.arcane.adapt.api.adaptation.AdaptationRequirements;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemRune;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemSettings;
import art.arcane.adapt.api.golem.GolemStat;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.minion.ServantPack;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemBoneFrameTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000c01");

  private final GolemRoster roster = new GolemRoster();
  private MockedStatic<GolemancyRuntime> runtime;
  private GolemBoneFrame adaptation;

  @BeforeEach
  void construct() {
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    adaptation = new GolemBoneFrame();
  }

  @AfterEach
  void close() {
    adaptation.unregister();
    runtime.close();
  }

  @Test
  void theDefaultsFollowTheBrief() {
    GolemBoneFrame.Config config = new GolemBoneFrame.Config();

    assertThat(config.latticeSlotsByLevel).containsExactly(0, 2, 3);
    assertThat(config.latticeAspects).containsExactly("swiftness", "might", "ember", "bulwark");
    assertThat(config.servantShareByLevel).containsExactly(0.35D, 0.35D, 1.0D);
    assertThat(config.servantLabelRange).isEqualTo(8D);
    assertThat(config.requires).containsExactly("golem-animus-binding:1", "tragoul-skeletal-servant:1");
  }

  @Test
  void configurationLoadsWithThreeLevelsAndBothPrerequisites() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();

    assertThat(adaptation.getName()).isEqualTo("golem-bone-frame");
    assertThat(adaptation.getMaxLevel()).isEqualTo(3);
    assertThat(adaptation.getRequirements()).containsExactly(new AdaptationRequirement("golem-animus-binding", 1),
        new AdaptationRequirement("tragoul-skeletal-servant", 1));
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
  }

  @Test
  void loadingClampsTheLabelRange() {
    assertThat(adaptation.reloadConfigSnapshot("servantLabelRange = 500.0\n", dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();
    assertThat(adaptation.getConfig().servantLabelRange).isEqualTo(64D);

    assertThat(adaptation.reloadConfigSnapshot("servantLabelRange = -2.0\n", dataFolder.toPath().resolve("low.toml").toFile(), false)).isTrue();
    assertThat(adaptation.getConfig().servantLabelRange).isEqualTo(1D);
  }

  @Test
  void everyLevelShowsItsUnlocks() {
    assertThat(lore(1)).containsExactly("+ Servants Answer Crook Signals and Show Status Labels", "+ 35% Golemancy XP from Servant Kills");
    assertThat(lore(2)).containsExactly("+ Servants Answer Crook Signals and Show Status Labels", "+ 2 Bone Lattice Rune Slots",
        "+ 35% Golemancy XP from Servant Kills");
    assertThat(lore(3)).containsExactly("+ Servants Answer Crook Signals and Show Status Labels", "+ 3 Bone Lattice Rune Slots",
        "+ 100% Golemancy XP from Servant Kills");
  }

  @Test
  void learningNeedsSkeletalServantFromTheTragoulSkill() {
    Skill<?> golemancy = skill("golemancy", adaptation("golem-animus-binding"));
    Skill<?> tragoul = skill("tragoul", adaptation("tragoul-skeletal-servant"));
    AdaptServer server = mock(AdaptServer.class);
    SkillRegistry registry = mock(SkillRegistry.class);
    when(plugin.getAdaptServer()).thenReturn(server);
    when(server.getSkillRegistry()).thenReturn(registry);
    when(registry.getSkills()).thenReturn(List.of(golemancy, tragoul));
    PlayerData data = new PlayerData();
    learn(data, "golemancy", "golem-animus-binding", 1);

    assertThat(AdaptationRequirements.missing(adaptation, data)).containsExactly(new AdaptationRequirement("tragoul-skeletal-servant", 1));

    learn(data, "tragoul", "tragoul-skeletal-servant", 1);

    assertThat(AdaptationRequirements.missing(adaptation, data)).isEmpty();
  }

  @Test
  void theServantShareNeedsBoneFrame() {
    GolemBoneFrame learned = spy(adaptation);

    doReturn(0).when(learned).level(OWNER);
    assertThat(learned.share(OWNER)).isZero();
    doReturn(2).when(learned).level(OWNER);
    assertThat(learned.share(OWNER)).isEqualTo(0.35D);
    doReturn(3).when(learned).level(OWNER);
    assertThat(learned.share(OWNER)).isEqualTo(1.0D);
  }

  @Test
  void theServantMenuOpensFromLevelOneWhileLatticeSlotsWaitForLevelTwo() {
    GolemBoneFrame learned = spy(adaptation);

    doReturn(0).when(learned).level(OWNER);
    assertThat(learned.servantMenuUnlocked(OWNER)).isFalse();
    doReturn(1).when(learned).level(OWNER);
    assertThat(learned.servantMenuUnlocked(OWNER)).isTrue();
    assertThat(learned.latticeSlots(1)).isZero();
    assertThat(learned.slotUnlockLevel(0)).isEqualTo(2);
    assertThat(learned.slotUnlockLevel(2)).isEqualTo(3);
    assertThat(learned.mostLatticeSlots()).isEqualTo(3);
  }

  @Test
  void summoningScalesServantsOnlyWithBoneFrameAndABoneScaleOtherThanOne() {
    GolemBoneFrame learned = spy(adaptation);
    Skeleton servant = mock(Skeleton.class);
    GolemSettings settings = boneScale(0.8D);
    doReturn(1).when(learned).level(OWNER);
    doNothing().when(learned).resize(any(Skeleton.class), anyDouble());
    runtime.when(GolemancyRuntime::settings).thenReturn(settings);

    learned.summoned(owner(), servant);

    verify(learned).resize(eq(servant), doubleThat(amount -> Math.abs(amount + 0.2D) < 1e-9));
  }

  @Test
  void aBoneScaleOfOneOrNoBoneFrameLeavesTheServantsSize() {
    GolemBoneFrame learned = spy(adaptation);
    Skeleton servant = mock(Skeleton.class);
    GolemSettings settings = boneScale(0.8D);
    doReturn(1).when(learned).level(OWNER);
    doNothing().when(learned).resize(any(Skeleton.class), anyDouble());

    learned.summoned(owner(), servant);
    doReturn(0).when(learned).level(OWNER);
    runtime.when(GolemancyRuntime::settings).thenReturn(settings);
    learned.summoned(owner(), servant);

    verify(learned, never()).resize(any(Skeleton.class), anyDouble());
  }

  @Test
  void summoningAppliesTheLatticeAtTheOwnersRunicAugmentsTier() {
    GolemBoneFrame learned = spy(adaptation);
    GolemRunicAugments augments = augments(3);
    Skeleton servant = servant();
    LivingEntity victim = mock(Zombie.class);
    roster.addLattice(new GolemRune(GolemRuneType.MIGHT, 3));
    roster.addLattice(new GolemRune(GolemRuneType.EMBER, 2));
    doReturn(2).when(learned).level(OWNER);
    doReturn(Optional.of(roster)).when(learned).roster(OWNER);
    doReturn(Optional.of(augments)).when(learned).runicAugments();
    doReturn(Optional.of(pack(servant))).when(learned).pack();
    doNothing().when(learned).fireproof(servant);
    doNothing().when(learned).strengthen(any(Skeleton.class), any(GolemStatDelta.class));

    learned.summoned(owner(), servant);
    learned.struck(servant, victim);

    verify(augments).lattice(Map.of(GolemRuneType.MIGHT, 2, GolemRuneType.EMBER, 2));
    verify(learned).strengthen(servant, GolemStatDelta.add(GolemStat.ATTACK_DAMAGE, 4.0D));
    verify(learned).fireproof(servant);
    verify(augments).ignite(victim, 80);
  }

  @Test
  void aLockedLatticeOrAnUnpoweredRuneChangesNothing() {
    GolemBoneFrame learned = spy(adaptation);
    GolemRunicAugments augments = augments(5);
    Skeleton servant = servant();
    roster.addLattice(new GolemRune(GolemRuneType.MIGHT, 3));
    doReturn(Optional.of(roster)).when(learned).roster(OWNER);
    doReturn(Optional.of(augments)).when(learned).runicAugments();
    doReturn(Optional.of(pack(servant))).when(learned).pack();
    doNothing().when(learned).strengthen(any(Skeleton.class), any(GolemStatDelta.class));

    doReturn(1).when(learned).level(OWNER);
    learned.summoned(owner(), servant);
    doReturn(2).when(learned).level(OWNER);
    when(augments.level(OWNER)).thenReturn(0);
    learned.summoned(owner(), servant);

    verify(learned, never()).strengthen(any(Skeleton.class), any(GolemStatDelta.class));
  }

  @Test
  void activatingTheRuntimeRegistersTheServantFrameUntilUnregistered() {
    adaptation.onRuntimeActivated();

    assertThat(ServantFrame.frame()).isPresent();

    adaptation.unregister();

    assertThat(ServantFrame.frame()).isEmpty();
  }

  private Player owner() {
    Player owner = mock(Player.class);
    lenient().when(owner.getUniqueId()).thenReturn(OWNER);
    return owner;
  }

  private static Skeleton servant() {
    Skeleton servant = mock(Skeleton.class);
    lenient().when(servant.getUniqueId()).thenReturn(UUID.randomUUID());
    return servant;
  }

  private static GolemRunicAugments augments(int level) {
    GolemRunicAugments.Config config = new GolemRunicAugments.Config();
    GolemRunicAugments augments = mock(GolemRunicAugments.class);
    lenient().when(augments.level(OWNER)).thenReturn(level);
    lenient().when(augments.maxTier(anyInt())).thenAnswer(call -> GolemRunicAugments.maxTier(config, call.getArgument(0)));
    lenient().when(augments.lattice(any())).thenAnswer(call -> GolemRunicAugments.lattice(config, call.getArgument(0)));
    return augments;
  }

  private static ServantPack pack(Skeleton servant) {
    ServantPack pack = mock(ServantPack.class);
    lenient().when(pack.expiresAt(servant)).thenReturn(Long.MAX_VALUE);
    return pack;
  }

  private static GolemSettings boneScale(double scale) {
    GolemSettings settings = mock(GolemSettings.class);
    lenient().when(settings.bodyScale(GolemBody.BONE)).thenReturn(scale);
    return settings;
  }

  private static Skill<?> skill(String name, Adaptation<?> adaptation) {
    Skill<?> skill = mock(Skill.class);
    lenient().when(skill.getName()).thenReturn(name);
    KList<Adaptation<?>> adaptations = new KList<>();
    adaptations.add(adaptation);
    doReturn(adaptations).when(skill).getAdaptations();
    doReturn(skill).when(adaptation).getSkill();
    return skill;
  }

  private static Adaptation<?> adaptation(String name) {
    Adaptation<?> adaptation = mock(Adaptation.class);
    lenient().when(adaptation.getName()).thenReturn(name);
    return adaptation;
  }

  private static void learn(PlayerData data, String skill, String adaptationId, int level) {
    PlayerSkillLine line = new PlayerSkillLine();
    line.setLine(skill);
    PlayerAdaptation stored = new PlayerAdaptation();
    stored.setId(adaptationId);
    stored.setLevel(level);
    line.getAdaptations().put(adaptationId, stored);
    data.getSkillLines().put(skill, line);
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
