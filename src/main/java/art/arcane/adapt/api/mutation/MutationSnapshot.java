package art.arcane.adapt.api.mutation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MutationSnapshot {
  private final String slotOneId;
  private final String slotTwoId;
  private final Set<MutationType> expressed;
  private final boolean perfect;
  private final boolean slotOneUnlocked;
  private final boolean slotTwoUnlocked;
  private final boolean cooperativeOptIn;
  private final Set<String> discoveredIds;
  private final Set<MutationType> discovered;
  private final Map<MutationType, MutationState> states;
  private final Map<MutationType, String> reasons;
  private final Set<MutationType> qualified;
  private final Map<MutationType, List<String>> qualifyingAdaptations;
  private final Map<MutationType, String> qualificationReasons;

  public MutationSnapshot(
      String slotOneId,
      String slotTwoId,
      Set<MutationType> expressed,
      boolean perfect,
      boolean slotOneUnlocked,
      boolean slotTwoUnlocked,
      boolean cooperativeOptIn,
      Map<MutationType, MutationState> states,
      Map<MutationType, String> reasons
  ) {
    this(slotOneId, slotTwoId, expressed, perfect, slotOneUnlocked, slotTwoUnlocked,
        cooperativeOptIn, Set.of(), states, reasons, Set.of(), Map.of(), Map.of());
  }

  public MutationSnapshot(
      String slotOneId,
      String slotTwoId,
      Set<MutationType> expressed,
      boolean perfect,
      boolean slotOneUnlocked,
      boolean slotTwoUnlocked,
      boolean cooperativeOptIn,
      Set<String> discoveredIds,
      Map<MutationType, MutationState> states,
      Map<MutationType, String> reasons
  ) {
    this(slotOneId, slotTwoId, expressed, perfect, slotOneUnlocked, slotTwoUnlocked,
        cooperativeOptIn, discoveredIds, states, reasons, Set.of(), Map.of(), Map.of());
  }

  public MutationSnapshot(
      String slotOneId,
      String slotTwoId,
      Set<MutationType> expressed,
      boolean perfect,
      boolean slotOneUnlocked,
      boolean slotTwoUnlocked,
      boolean cooperativeOptIn,
      Set<String> discoveredIds,
      Map<MutationType, MutationState> states,
      Map<MutationType, String> reasons,
      Set<MutationType> qualified,
      Map<MutationType, List<String>> qualifyingAdaptations,
      Map<MutationType, String> qualificationReasons
  ) {
    this.slotOneId = slotOneId == null ? "" : slotOneId;
    this.slotTwoId = slotTwoId == null ? "" : slotTwoId;
    this.expressed = expressed == null || expressed.isEmpty()
        ? Collections.emptySet()
        : Collections.unmodifiableSet(EnumSet.copyOf(expressed));
    this.perfect = perfect;
    this.slotOneUnlocked = slotOneUnlocked;
    this.slotTwoUnlocked = slotTwoUnlocked;
    this.cooperativeOptIn = cooperativeOptIn;
    LinkedHashSet<String> rawDiscovered = new LinkedHashSet<>();
    EnumSet<MutationType> knownDiscovered = EnumSet.noneOf(MutationType.class);
    if (discoveredIds != null) {
      for (String id : discoveredIds) {
        if (id == null || id.isBlank()) {
          continue;
        }
        rawDiscovered.add(id);
        MutationType type = MutationType.find(id);
        if (type != null) {
          knownDiscovered.add(type);
        }
      }
    }
    this.discoveredIds = Collections.unmodifiableSet(rawDiscovered);
    this.discovered = knownDiscovered.isEmpty()
        ? Collections.emptySet()
        : Collections.unmodifiableSet(knownDiscovered);
    EnumMap<MutationType, MutationState> stateCopy = new EnumMap<>(MutationType.class);
    if (states != null) {
      stateCopy.putAll(states);
    }
    this.states = Collections.unmodifiableMap(stateCopy);
    EnumMap<MutationType, String> reasonCopy = new EnumMap<>(MutationType.class);
    if (reasons != null) {
      reasonCopy.putAll(reasons);
    }
    this.reasons = Collections.unmodifiableMap(reasonCopy);
    this.qualified = qualified == null || qualified.isEmpty()
        ? Collections.emptySet()
        : Collections.unmodifiableSet(EnumSet.copyOf(qualified));
    EnumMap<MutationType, List<String>> adaptationCopy = new EnumMap<>(MutationType.class);
    if (qualifyingAdaptations != null) {
      for (Map.Entry<MutationType, List<String>> entry : qualifyingAdaptations.entrySet()) {
        adaptationCopy.put(entry.getKey(), entry.getValue() == null ? List.of() : List.copyOf(entry.getValue()));
      }
    }
    this.qualifyingAdaptations = Collections.unmodifiableMap(adaptationCopy);
    EnumMap<MutationType, String> qualificationReasonCopy = new EnumMap<>(MutationType.class);
    if (qualificationReasons != null) {
      qualificationReasonCopy.putAll(qualificationReasons);
    }
    this.qualificationReasons = Collections.unmodifiableMap(qualificationReasonCopy);
  }

  public static MutationSnapshot empty() {
    EnumMap<MutationType, MutationState> states = new EnumMap<>(MutationType.class);
    EnumMap<MutationType, String> reasons = new EnumMap<>(MutationType.class);
    for (MutationType type : MutationType.values()) {
      states.put(type, MutationState.LOCKED);
      reasons.put(type, "Mutation runtime is not available");
    }
    return new MutationSnapshot("", "", Set.of(), false, false, false, false, states, reasons);
  }

  public String slotOneId() {
    return slotOneId;
  }

  public String slotTwoId() {
    return slotTwoId;
  }

  public Set<MutationType> expressed() {
    return expressed;
  }

  public boolean perfect() {
    return perfect;
  }

  public boolean slotOneUnlocked() {
    return slotOneUnlocked;
  }

  public boolean slotTwoUnlocked() {
    return slotTwoUnlocked;
  }

  public boolean cooperativeOptIn() {
    return cooperativeOptIn;
  }

  public Set<MutationType> discovered() {
    return discovered;
  }

  public Set<String> discoveredIds() {
    return discoveredIds;
  }

  public MutationState state(MutationType type) {
    MutationState state = states.get(type);
    return state == null ? MutationState.RESTRICTED : state;
  }

  public String reason(MutationType type) {
    String reason = reasons.get(type);
    return reason == null ? "" : reason;
  }

  public boolean qualified(MutationType type) {
    return type != null && qualified.contains(type);
  }

  public List<String> qualifyingAdaptations(MutationType type) {
    List<String> adaptations = qualifyingAdaptations.get(type);
    return adaptations == null ? List.of() : adaptations;
  }

  public String qualificationReason(MutationType type) {
    String reason = qualificationReasons.get(type);
    return reason == null ? "" : reason;
  }
}
