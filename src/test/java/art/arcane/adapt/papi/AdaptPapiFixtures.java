package art.arcane.adapt.papi;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.mutation.MutationSnapshot;
import art.arcane.adapt.api.mutation.MutationState;
import art.arcane.adapt.api.mutation.MutationType;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.Curves;
import art.arcane.adapt.api.xp.NewtonCurve;
import art.arcane.volmlib.util.collection.KList;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class AdaptPapiFixtures {
  static final NewtonCurve SQUARE_CURVE = Curves.X2.getCurve();
  static final double POWER_PER_LEVEL = 1.0D;
  static final int CONFIGURED_MAX_LEVEL = 1000;
  static final MutationType SLOT_ONE_MUTATION = MutationType.BASTION_SPINE;
  static final MutationType SLOT_TWO_MUTATION = MutationType.VERDANT_MOLT;
  static final MutationType UNSELECTED_MUTATION = MutationType.TEMPERBOUND;
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

  static AdaptPlayerSnapshot player(AdaptCatalogSnapshot catalog) {
    return AdaptPlayerSnapshotBuilder.build(
        null,
        playerData(),
        AdaptPlayerSnapshotBuilder.mutationView(mutationSnapshot(), true, 12_500L, catalog),
        SQUARE_CURVE,
        CONFIGURED_MAX_LEVEL,
        POWER_PER_LEVEL
    );
  }

  static AdaptPlayerSnapshot powerBoundPlayer(AdaptCatalogSnapshot catalog, int spentPower) {
    return AdaptPlayerSnapshotBuilder.build(
        null,
        powerBoundPlayerData(spentPower),
        AdaptPlayerSnapshotBuilder.mutationView(mutationSnapshot(), true, 12_500L, catalog),
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

  static AdaptPlayerSnapshot playerWithoutMutations() {
    return AdaptPlayerSnapshotBuilder.build(
        null,
        playerData(),
        AdaptPlayerSnapshotBuilder.mutationView(null, true, 0L, null),
        SQUARE_CURVE,
        CONFIGURED_MAX_LEVEL,
        POWER_PER_LEVEL
    );
  }

  static AdaptPlayerSnapshot playerWithStaleMutationView() {
    return AdaptPlayerSnapshotBuilder.build(
        null,
        playerData(),
        new AdaptMutationView(
            false,
            true,
            SLOT_ONE_MUTATION.id(),
            SLOT_TWO_MUTATION.id(),
            "Bastion Spine",
            "Verdant Molt",
            true,
            true,
            true,
            3,
            "3",
            5_000L,
            "5.00",
            false,
            mutationSnapshot()
        ),
        SQUARE_CURVE,
        CONFIGURED_MAX_LEVEL,
        POWER_PER_LEVEL
    );
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

  static MutationSnapshot mutationSnapshot() {
    Map<MutationType, MutationState> states = new EnumMap<>(MutationType.class);
    Map<MutationType, String> reasons = new EnumMap<>(MutationType.class);

    for (MutationType type : MutationType.values()) {
      states.put(type, MutationState.LOCKED);
      reasons.put(type, "");
    }

    states.put(SLOT_ONE_MUTATION, MutationState.EXPRESSED);
    states.put(SLOT_TWO_MUTATION, MutationState.AVAILABLE);

    return new MutationSnapshot(
        SLOT_ONE_MUTATION.id(),
        SLOT_TWO_MUTATION.id(),
        Set.of(SLOT_ONE_MUTATION),
        false,
        true,
        false,
        true,
        Set.of(SLOT_ONE_MUTATION.id(), SLOT_TWO_MUTATION.id()),
        states,
        reasons,
        Set.of(SLOT_ONE_MUTATION, SLOT_TWO_MUTATION),
        Map.of(),
        Map.of()
    );
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
