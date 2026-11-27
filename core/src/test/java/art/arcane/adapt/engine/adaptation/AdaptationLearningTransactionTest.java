package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.platform.value.Id;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.TestCores;
import art.arcane.adapt.testkit.MockedHosts;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerAdaptation;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdaptationLearningTransactionTest {
  private static MockedHosts hosts;
  private AdaptConfig previousConfig;
  private Harness harness;

  @BeforeEach
  void setUp() throws Exception {
    Field configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    previousConfig = (AdaptConfig) configField.get(null);

    AdaptConfig config = new AdaptConfig();
    set(config.getLearningEconomy(), "enabled", true);
    set(config.getLearningEconomy(), "moneyPerKnowledge", 2D);
    set(config.getLearningEconomy(), "refundPercent", 50D);
    configField.set(null, config);
    hosts = new MockedHosts();
    harness = new Harness();
  }

  @AfterEach
  void tearDown() throws Exception {
    hosts.close();
    TestCores.clear();
    AdaptPorts.uninstall(LearningEconomyPort.class);
    Field configField = AdaptConfig.class.getDeclaredField("config");
    configField.setAccessible(true);
    configField.set(null, previousConfig);
  }

  @Test
  void successfulLearnWithdrawsOnceAndCommitsAReceiptBackedCharge() {
    LearningEconomyPort.Charge charge = mock(LearningEconomyPort.Charge.class);
    when(harness.economy.withdraw(harness.player.uuid(), 20D, "Adapt learning adapt:test-adaptation for " + PLAYER_ID))
        .thenReturn(success(charge));

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.learn(harness.adaptation, harness.player, 2, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.LEARNED);
    assertThat(harness.level.get()).isEqualTo(2);
    verify(harness.skillLine).spendKnowledge(10);
    assertThat(harness.storage.get("vault-learning-refund-adapt:test-adaptation-level-1")).isEqualTo(5D);
    assertThat(harness.storage.get("vault-learning-refund-adapt:test-adaptation-level-2")).isEqualTo(5D);
    verify(charge).commit();
  }

  @Test
  void unavailableRuntimeCannotLearnUnlearnOrQuoteARefund() {
    when(harness.server.getPlayer(harness.player)).thenReturn(null);

    assertThat(AdaptationLearningTransaction.learn(harness.adaptation, harness.player, 2, false))
        .isEqualTo(AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE);
    assertThat(AdaptationLearningTransaction.unlearn(harness.adaptation, harness.player, 0, false))
        .isEqualTo(AdaptationLearningTransaction.Result.SKILL_LINE_UNAVAILABLE);
    assertThat(AdaptationLearningTransaction.formattedRefund(harness.adaptation, harness.player, 0, 2))
        .isEmpty();
    verify(harness.economy, never()).withdraw(any(UUID.class), anyDouble(), anyString());
  }

  @Test
  void insufficientVaultFundsLeaveKnowledgeAndLevelUntouched() {
    when(harness.economy.withdraw(harness.player.uuid(), 20D, "Adapt learning adapt:test-adaptation for " + PLAYER_ID))
        .thenReturn(new LearningEconomyPort.Withdrawal(LearningEconomyPort.Status.INSUFFICIENT_FUNDS, null));

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.learn(harness.adaptation, harness.player, 2, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.INSUFFICIENT_FUNDS);
    assertThat(harness.level.get()).isZero();
    verify(harness.skillLine, never()).spendKnowledge(anyInt());
    verify(harness.skillLine, never()).setAdaptation(harness.adaptation, 2);
  }

  @Test
  void unlearningRefundsOnlyTheStoredPaidReceipts() {
    harness.level.set(2);
    harness.storage.put("vault-learning-refund-adapt:test-adaptation-level-1", 3D);
    harness.storage.put("vault-learning-refund-adapt:test-adaptation-level-2", 7D);
    when(harness.economy.deposit(harness.player.uuid(), 10D, "Adapt unlearning refund for adapt:test-adaptation and " + PLAYER_ID))
        .thenReturn(true);

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.unlearn(harness.adaptation, harness.player, 0, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.UNLEARNED);
    assertThat(harness.level.get()).isZero();
    assertThat(harness.storage).doesNotContainKeys(
        "vault-learning-refund-adapt:test-adaptation-level-1",
        "vault-learning-refund-adapt:test-adaptation-level-2"
    );
    verify(harness.skillLine).giveKnowledge(10);
    verify(harness.economy).deposit(harness.player.uuid(), 10D, "Adapt unlearning refund for adapt:test-adaptation and " + PLAYER_ID);
  }

  @Test
  void partialUnlearningPreservesReceiptsForLevelsStillOwned() {
    harness.level.set(2);
    harness.storage.put("vault-learning-refund-adapt:test-adaptation-level-1", 3D);
    harness.storage.put("vault-learning-refund-adapt:test-adaptation-level-2", 7D);
    when(harness.economy.deposit(harness.player.uuid(), 7D, "Adapt unlearning refund for adapt:test-adaptation and " + PLAYER_ID))
        .thenReturn(true);

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.unlearn(harness.adaptation, harness.player, 1, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.UNLEARNED);
    assertThat(harness.storage.get("vault-learning-refund-adapt:test-adaptation-level-1")).isEqualTo(3D);
    assertThat(harness.storage).doesNotContainKey("vault-learning-refund-adapt:test-adaptation-level-2");
    verify(harness.economy).deposit(harness.player.uuid(), 7D, "Adapt unlearning refund for adapt:test-adaptation and " + PLAYER_ID);
  }

  @Test
  void upgradingPreservesEarlierPaidLevelReceipts() {
    harness.level.set(1);
    harness.storage.put("vault-learning-refund-adapt:test-adaptation-level-1", 3D);
    when(harness.adaptation.getCostFor(2, 1)).thenReturn(5);
    when(harness.adaptation.getPowerCostFor(2, 1)).thenReturn(1);
    LearningEconomyPort.Charge charge = mock(LearningEconomyPort.Charge.class);
    when(harness.economy.withdraw(harness.player.uuid(), 10D, "Adapt learning adapt:test-adaptation for " + PLAYER_ID))
        .thenReturn(success(charge));

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.learn(harness.adaptation, harness.player, 2, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.LEARNED);
    assertThat(harness.storage.get("vault-learning-refund-adapt:test-adaptation-level-1")).isEqualTo(3D);
    assertThat(harness.storage.get("vault-learning-refund-adapt:test-adaptation-level-2")).isEqualTo(5D);
    verify(charge).commit();
  }

  @Test
  void failedKnowledgeMutationQueuesAChargeThatCouldNotBeCompensatedImmediately() {
    when(harness.skillLine.spendKnowledge(10)).thenReturn(false);
    LearningEconomyPort.Charge charge = mock(LearningEconomyPort.Charge.class);
    when(charge.amount()).thenReturn(20D);
    when(charge.refund()).thenReturn(false);
    when(harness.economy.withdraw(harness.player.uuid(), 20D, "Adapt learning adapt:test-adaptation for " + PLAYER_ID))
        .thenReturn(success(charge));
    when(harness.economy.deposit(
        harness.player.uuid(),
        20D,
        "Adapt unlearning refund for adapt:test-adaptation learning rollback and " + PLAYER_ID
    )).thenReturn(false);

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.learn(harness.adaptation, harness.player, 2, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.INSUFFICIENT_KNOWLEDGE);
    assertThat(harness.storage.get("vault-learning-pending-refund")).isEqualTo(20D);
    assertThat(harness.level.get()).isZero();
  }

  @Test
  void learningOverARegionGrantChargesTheFullCostFromZeroAndClearsTheMarker() {
    RegionHarness region = new RegionHarness();
    LearningEconomyPort.Charge charge = mock(LearningEconomyPort.Charge.class);
    when(region.economy.withdraw(region.player.uuid(), 20D, "Adapt learning adapt:test-adaptation for " + PLAYER_ID))
        .thenReturn(success(charge));

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.learn(region.adaptation, region.player, 1, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.LEARNED);
    assertThat(region.skillLine.getAdaptationLevel("adapt:test-adaptation")).isEqualTo(1);
    assertThat(region.skillLine.getAdaptation("adapt:test-adaptation").isRegionGranted()).isFalse();
    assertThat(region.skillLine.getKnowledge()).isEqualTo(RegionHarness.KNOWLEDGE - 10);
    assertThat(region.skillLine.getStorage().get("vault-learning-refund-adapt:test-adaptation-level-1")).isEqualTo(10D);
    verify(charge).commit();
  }

  @Test
  void learningPastARegionGrantChargesEveryLevelFromZero() {
    RegionHarness region = new RegionHarness();
    LearningEconomyPort.Charge charge = mock(LearningEconomyPort.Charge.class);
    when(region.economy.withdraw(region.player.uuid(), 28D, "Adapt learning adapt:test-adaptation for " + PLAYER_ID))
        .thenReturn(success(charge));

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.learn(region.adaptation, region.player, 2, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.LEARNED);
    assertThat(region.skillLine.getKnowledge()).isEqualTo(RegionHarness.KNOWLEDGE - 14);
    assertThat(region.skillLine.getAdaptation("adapt:test-adaptation").isRegionGranted()).isFalse();
    assertThat(region.skillLine.getStorage().get("vault-learning-refund-adapt:test-adaptation-level-1")).isEqualTo(10D);
    assertThat(region.skillLine.getStorage().get("vault-learning-refund-adapt:test-adaptation-level-2")).isEqualTo(4D);
    verify(charge).commit();
  }

  @Test
  void anAdaptationLearnedOverARegionGrantCountsTowardUsedPower() {
    RegionHarness region = new RegionHarness();
    assertThat(region.data.getUsedPower()).isZero();
    when(region.economy.withdraw(region.player.uuid(), 20D, "Adapt learning adapt:test-adaptation for " + PLAYER_ID))
        .thenReturn(success(mock(LearningEconomyPort.Charge.class)));

    AdaptationLearningTransaction.learn(region.adaptation, region.player, 1, false);

    assertThat(region.data.getUsedPower()).isEqualTo(1);
  }

  @Test
  void refundingAnAdaptationLearnedOverARegionGrantReturnsEverythingPaid() {
    RegionHarness region = new RegionHarness();
    when(region.economy.withdraw(region.player.uuid(), 20D, "Adapt learning adapt:test-adaptation for " + PLAYER_ID))
        .thenReturn(success(mock(LearningEconomyPort.Charge.class)));
    when(region.economy.deposit(region.player.uuid(), 10D, "Adapt unlearning refund for adapt:test-adaptation and " + PLAYER_ID))
        .thenReturn(true);
    AdaptationLearningTransaction.learn(region.adaptation, region.player, 1, false);

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.unlearn(region.adaptation, region.player, 0, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.UNLEARNED);
    assertThat(region.skillLine.getAdaptationLevel("adapt:test-adaptation")).isZero();
    assertThat(region.skillLine.getKnowledge()).isEqualTo(RegionHarness.KNOWLEDGE);
    assertThat(region.skillLine.getStorage()).doesNotContainKey("vault-learning-refund-adapt:test-adaptation-level-1");
    verify(region.economy).deposit(region.player.uuid(), 10D, "Adapt unlearning refund for adapt:test-adaptation and " + PLAYER_ID);
  }

  @Test
  void unlearningAnUntouchedRegionGrantRefundsNothing() {
    RegionHarness region = new RegionHarness();

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.unlearn(region.adaptation, region.player, 0, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.UNLEARNED);
    assertThat(region.skillLine.getAdaptation("adapt:test-adaptation")).isNull();
    assertThat(region.skillLine.getKnowledge()).isEqualTo(RegionHarness.KNOWLEDGE);
    verify(region.economy, never()).deposit(any(UUID.class), anyDouble(), anyString());
  }

  @Test
  void learningOverANormalLevelStillChargesOnlyTheDelta() {
    RegionHarness region = new RegionHarness();
    region.skillLine.getAdaptation("adapt:test-adaptation").setRegionGranted(false);
    LearningEconomyPort.Charge charge = mock(LearningEconomyPort.Charge.class);
    when(region.economy.withdraw(region.player.uuid(), 8D, "Adapt learning adapt:test-adaptation for " + PLAYER_ID))
        .thenReturn(success(charge));

    AdaptationLearningTransaction.Result result =
        AdaptationLearningTransaction.learn(region.adaptation, region.player, 2, false);

    assertThat(result).isEqualTo(AdaptationLearningTransaction.Result.LEARNED);
    assertThat(region.skillLine.getKnowledge()).isEqualTo(RegionHarness.KNOWLEDGE - 4);
    assertThat(region.skillLine.getStorage()).doesNotContainKey("vault-learning-refund-adapt:test-adaptation-level-1");
    assertThat(region.skillLine.getStorage().get("vault-learning-refund-adapt:test-adaptation-level-2")).isEqualTo(4D);
    assertThat(region.data.getUsedPower()).isEqualTo(1);
    verify(charge).commit();
  }

  private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-00000000c0de");

  private static PlayerRef player() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(PLAYER_ID);
    return player;
  }

  private static LearningEconomyPort economy() {
    LearningEconomyPort economy = mock(LearningEconomyPort.class);
    when(economy.isPresent()).thenReturn(true);
    when(economy.isAvailable()).thenReturn(true);
    AdaptPorts.install(LearningEconomyPort.class, economy);
    return economy;
  }

  private static LearningEconomyPort.Withdrawal success(LearningEconomyPort.Charge charge) {
    return new LearningEconomyPort.Withdrawal(LearningEconomyPort.Status.SUCCESS, charge);
  }

  private static void set(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static final class Harness {
    private final LearningEconomyPort economy = economy();
    private final PlayerRef player = player();
    private final Adaptation<?> adaptation = mock(Adaptation.class);
    private final Skill<?> skill = mock(Skill.class);
    private final AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    private final PlayerData playerData = mock(PlayerData.class);
    private final PlayerSkillLine skillLine = mock(PlayerSkillLine.class);
    private final KMap<String, Object> storage = new KMap<>();
    private final AtomicInteger level = new AtomicInteger();
    private final AdaptServer server = mock(AdaptServer.class);

    private Harness() {
      hosts.skill(skill);
      when(server.getPlayer(player)).thenReturn(adaptPlayer);
      TestCores.setField(TestCores.install(), "adaptServer", server);
      org.mockito.Mockito.doReturn(skill).when(adaptation).getSkill();
      lenient().when(adaptation.getId()).thenReturn(Id.adapt("test-adaptation"));
      when(adaptation.getMaxLevel()).thenReturn(100);
      lenient().when(skill.getId()).thenReturn(Id.adapt("test-skill"));
      when(adaptPlayer.getSkillLine("adapt:test-skill")).thenReturn(skillLine);
      when(adaptPlayer.getData()).thenReturn(playerData);
      when(playerData.hasPowerAvailable(anyInt())).thenReturn(true);
      when(skillLine.getAdaptationLevel("adapt:test-adaptation")).thenAnswer(invocation -> level.get());
      when(skillLine.getKnowledge()).thenReturn(100L);
      when(skillLine.spendKnowledge(anyInt())).thenReturn(true);
      when(skillLine.getStorage()).thenReturn(storage);
      when(adaptation.getCostFor(2, 0)).thenReturn(10);
      when(adaptation.getCostFor(anyInt())).thenReturn(5);
      when(adaptation.getPowerCostFor(2, 0)).thenReturn(2);
      org.mockito.Mockito.doAnswer(invocation -> {
        level.set(invocation.getArgument(1, Integer.class));
        return null;
      }).when(skillLine).setAdaptation(org.mockito.ArgumentMatchers.eq(adaptation), anyInt());
    }
  }

  private static final class RegionHarness {
    private static final long KNOWLEDGE = 500L;

    private final LearningEconomyPort economy = economy();
    private final PlayerRef player = player();
    private final Adaptation<?> adaptation = mock(Adaptation.class);
    private final Skill<?> skill = mock(Skill.class);
    private final AdaptPlayer adaptPlayer = mock(AdaptPlayer.class);
    private final PlayerData data = new PlayerData();
    private final PlayerSkillLine skillLine = new PlayerSkillLine();

    private RegionHarness() {
      hosts.skill(skill);
      doReturn(skill).when(adaptation).getSkill();
      lenient().when(adaptation.getId()).thenReturn(Id.adapt("test-adaptation"));
      when(adaptation.getMaxLevel()).thenReturn(5);
      lenient().when(skill.getId()).thenReturn(Id.adapt("test-skill"));
      when(skill.isEnabled()).thenReturn(true);
      when(adaptation.isEnabled()).thenReturn(true);
      doReturn(new KList<>(List.of(adaptation))).when(skill).getAdaptations();
      SkillRegistry registry = mock(SkillRegistry.class);
      doReturn(skill).when(registry).getSkill("test-skill");
      lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt("test-skill").toString());
      lenient().doReturn(skill).when(registry).getSkill(Id.parseAdapt("test-skill"));
      AdaptServer server = mock(AdaptServer.class);
      when(server.getSkillRegistry()).thenReturn(registry);
      when(server.getPlayer(player)).thenReturn(adaptPlayer);
      TestCores.setField(TestCores.install(), "adaptServer", server);
      when(adaptation.getCostFor(1, 0)).thenReturn(10);
      when(adaptation.getCostFor(2, 0)).thenReturn(14);
      when(adaptation.getCostFor(2, 1)).thenReturn(4);
      when(adaptation.getCostFor(1)).thenReturn(10);
      when(adaptation.getCostFor(2)).thenReturn(4);
      when(adaptation.getPowerCostFor(1, 0)).thenReturn(1);
      when(adaptation.getPowerCostFor(2, 0)).thenReturn(2);
      when(adaptation.getPowerCostFor(2, 1)).thenReturn(1);

      skillLine.setLine("adapt:test-skill");
      skillLine.setKnowledge(KNOWLEDGE);
      PlayerAdaptation granted = new PlayerAdaptation();
      granted.setId("adapt:test-adaptation");
      granted.setLevel(1);
      granted.setRegionGranted(true);
      skillLine.getAdaptations().put("adapt:test-adaptation", granted);
      data.getSkillLines().put("adapt:test-skill", skillLine);
      data.setRegionPowerBonus(64);
      when(adaptPlayer.getSkillLine("adapt:test-skill")).thenReturn(skillLine);
      when(adaptPlayer.getData()).thenReturn(data);
    }
  }
}
