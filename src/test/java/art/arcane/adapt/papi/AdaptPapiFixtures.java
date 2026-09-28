package art.arcane.adapt.papi;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.Curves;
import art.arcane.adapt.api.xp.NewtonCurve;
import art.arcane.volmlib.util.collection.KList;

import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class AdaptPapiFixtures {
  static final NewtonCurve SQUARE_CURVE = Curves.X2.getCurve();
  static final double POWER_PER_LEVEL = 1.0D;
  static final int CONFIGURED_MAX_LEVEL = 1000;
  static final int MAX_POWER = 10;
  static final int BASE_SPENT_POWER = 2;
  static final long UNBOUNDED_KNOWLEDGE = 1_000L;

  private AdaptPapiFixtures() {
  }

  static AdaptCatalogSnapshot catalog() {
    Adaptation<?> vein = adaptation("mining-vein", 3, new int[]{6, 7, 8}, true, false, "§aVein Miner");
    Adaptation<?> oreScan = adaptation("mining-ore-scan", 2, new int[]{4, 5}, false, true, "§aOre Scan");
    Skill<?> mining = skill("mining", true, "Mining", vein, oreScan);
    Skill<?> hunter = skill("hunter", false, "Hunter");
    return AdaptCatalogSnapshot.build(7L, List.of(mining, hunter));
  }

  static AdaptPlayerSnapshot player() {
    return AdaptPlayerSnapshotBuilder.build(
        null,
        playerData(),
        SQUARE_CURVE,
        CONFIGURED_MAX_LEVEL,
        POWER_PER_LEVEL
    );
  }

  static AdaptPlayerSnapshot powerBoundPlayer(int spentPower) {
    return AdaptPlayerSnapshotBuilder.build(
        null,
        powerBoundPlayerData(spentPower),
        SQUARE_CURVE,
        CONFIGURED_MAX_LEVEL,
        POWER_PER_LEVEL
    );
  }

  static PlayerData powerBoundPlayerData(int spentPower) {
    PlayerData data = playerData();
    PlayerSkillLine mining = data.getSkillLines().get("mining");
    mining.setKnowledge(UNBOUNDED_KNOWLEDGE);

    for (int index = 0; index < spentPower - BASE_SPENT_POWER; index++) {
      mining.getAdaptations().put("power-sink-" + index, playerAdaptation("power-sink-" + index, 1));
    }

    return data;
  }

  static PlayerData playerData() {
    PlayerData data = new PlayerData();
    data.setMasterXp(100.0D);
    data.setMultiplier(1.25D);
    data.setWisdom(3L);

    PlayerSkillLine mining = new PlayerSkillLine();
    mining.setLine("mining");
    mining.setXp(30.25D);
    mining.setKnowledge(10L);
    mining.setMultiplier(1.5D);
    mining.getAdaptations().put("mining-vein", playerAdaptation("mining-vein", 1));
    mining.getAdaptations().put("mining-ore-scan", playerAdaptation("mining-ore-scan", 1));
    data.getSkillLines().put("mining", mining);
    return data;
  }

  static PlayerAdaptation playerAdaptation(String id, int level) {
    PlayerAdaptation adaptation = new PlayerAdaptation();
    adaptation.setId(id);
    adaptation.setLevel(level);
    return adaptation;
  }

  static Skill<?> skill(String name, boolean enabled, String localizedName, Adaptation<?>... adaptations) {
    Skill<?> skill = mock(Skill.class);
    KList<Adaptation<?>> owned = new KList<>();

    for (Adaptation<?> adaptation : adaptations) {
      owned.add(adaptation);
    }

    lenient().when(skill.getName()).thenReturn(name);
    lenient().when(skill.isEnabled()).thenReturn(enabled);
    lenient().when(skill.getLocalizedName()).thenReturn(localizedName);
    lenient().when(skill.getAdaptations()).thenReturn(owned);
    return skill;
  }

  static Adaptation<?> adaptation(
      String name,
      int maxLevel,
      int[] stepCosts,
      boolean enabled,
      boolean permanent,
      String displayName
  ) {
    Adaptation<?> adaptation = mock(Adaptation.class);
    lenient().when(adaptation.getName()).thenReturn(name);
    lenient().when(adaptation.getMaxLevel()).thenReturn(maxLevel);
    lenient().when(adaptation.isEnabled()).thenReturn(enabled);
    lenient().when(adaptation.isPermanent()).thenReturn(permanent);
    lenient().when(adaptation.getDisplayName()).thenReturn(displayName);

    for (int level = 1; level <= stepCosts.length; level++) {
      when(adaptation.getCostFor(level)).thenReturn(stepCosts[level - 1]);
    }

    return adaptation;
  }
}
