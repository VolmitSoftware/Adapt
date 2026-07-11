package art.arcane.adapt.api.mutation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerAdaptation;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.service.MutationRuntimeSVC;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MutationManager {
  private static final long DORMANT_QUALIFICATION_RECHECK_MILLIS = 1_000L;
  private static final long EXTERNAL_SNAPSHOT_REFRESH_MILLIS = 1_000L;

  private final Map<UUID, MutationSnapshot> snapshots = new ConcurrentHashMap<>();
  private final Map<UUID, SnapshotGuard> snapshotGuards = new ConcurrentHashMap<>();
  private final Map<UUID, BookshelfToken> bookshelfTokens = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> perfectOverrides = new ConcurrentHashMap<>();
  private final Map<UUID, Long> externalSnapshotRefreshes = new ConcurrentHashMap<>();
  private final MutationCombatLock combatLock;
  private volatile MutationConfig config;

  public MutationManager(MutationConfig config) {
    this.config = config == null ? MutationConfig.defaults() : config;
    combatLock = new MutationCombatLock(this.config.getCombatLockMillis());
  }

  public MutationConfig getConfig() {
    return config;
  }

  public MutationCombatLock getCombatLock() {
    return combatLock;
  }

  public MutationType find(String mutationId) {
    return MutationType.find(mutationId);
  }

  public MutationSnapshot snapshot(Player player) {
    if (player == null) {
      return MutationSnapshot.empty();
    }
    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
      MutationSnapshot remote = snapshots.get(player.getUniqueId());
      return remote == null ? MutationSnapshot.empty() : remote;
    }
    AdaptPlayer adaptPlayer = resolvePlayer(player);
    MutationSnapshot current = snapshots.get(player.getUniqueId());
    SnapshotGuard guard = snapshotGuards.get(player.getUniqueId());
    if (current != null && adaptPlayer != null && guard != null
        && isGuardCurrent(player, adaptPlayer.getData(), guard)) {
      return current;
    }
    return reconcile(adaptPlayer);
  }

  public MutationSnapshot snapshot(UUID playerId) {
    if (playerId == null) {
      return MutationSnapshot.empty();
    }
    requestExternalSnapshotRefresh(playerId);
    MutationSnapshot current = snapshots.get(playerId);
    if (current != null) {
      return current;
    }
    if (Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return MutationSnapshot.empty();
    }
    PlayerData data = Adapt.instance.getAdaptServer().peekData(playerId);
    return data == null ? MutationSnapshot.empty() : offlineSnapshot(playerId, data);
  }

  public MutationSnapshot reconcile(Player player) {
    if (player == null || Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return MutationSnapshot.empty();
    }
    AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(player);
    return reconcile(adaptPlayer);
  }

  public MutationSnapshot reconcile(AdaptPlayer adaptPlayer) {
    if (adaptPlayer == null || adaptPlayer.getPlayer() == null) {
      return MutationSnapshot.empty();
    }
    Player player = adaptPlayer.getPlayer();
    if (J.isFoliaThreading() && !J.isOwnedByCurrentRegion(player)) {
      J.runEntity(player, () -> reconcile(adaptPlayer));
      MutationSnapshot current = snapshots.get(player.getUniqueId());
      return current == null ? MutationSnapshot.empty() : current;
    }
    PlayerData playerData = adaptPlayer.getData();
    PlayerMutationData mutationData = playerData.getMutationData();
    double ichorBeforeNormalization = mutationData.getDeepbloodIchor();
    double rootChargeBeforeNormalization = mutationData.getLivingLatticeRootCharge();
    mutationData.normalize();
    boolean repairedData = Double.compare(ichorBeforeNormalization, mutationData.getDeepbloodIchor()) != 0
        || Double.compare(rootChargeBeforeNormalization, mutationData.getLivingLatticeRootCharge()) != 0;
    repairedData = normalizeDurableState(mutationData) || repairedData;
    repairedData = mutationData.discover(mutationData.getSlotOneId()) || repairedData;
    repairedData = mutationData.discover(mutationData.getSlotTwoId()) || repairedData;

    int level = playerData.getLevel();
    boolean slotOneUnlocked = isSlotUnlocked(playerData, level, 1);
    boolean slotTwoUnlocked = isSlotUnlocked(playerData, level, 2);
    boolean perfect = resolvePerfect(player.getUniqueId(), level);
    EnumMap<MutationType, MutationState> states = new EnumMap<>(MutationType.class);
    EnumMap<MutationType, String> reasons = new EnumMap<>(MutationType.class);
    EnumMap<MutationType, List<String>> qualifyingAdaptations = new EnumMap<>(MutationType.class);
    EnumMap<MutationType, String> qualificationReasons = new EnumMap<>(MutationType.class);
    EnumSet<MutationType> expressed = EnumSet.noneOf(MutationType.class);
    EnumSet<MutationType> qualified = EnumSet.noneOf(MutationType.class);
    MutationType slotOne = MutationType.find(mutationData.getSlotOneId());
    MutationType slotTwo = MutationType.find(mutationData.getSlotTwoId());
    boolean duplicateSelection = !mutationData.getSlotOneId().isBlank()
        && mutationData.getSlotOneId().equals(mutationData.getSlotTwoId());
    boolean selectedConflict = duplicateSelection
        || (slotOne != null && slotTwo != null && conflicts(slotOne, slotTwo));
    boolean basePermission = player.hasPermission("adapt.mutations");
    EnumMap<MutationDomain, DomainMatch> domainMatches = config.isEnabled() && basePermission
        ? domainMatches(player)
        : new EnumMap<>(MutationDomain.class);

    for (MutationType type : MutationType.values()) {
      int selectedSlot = selectedSlot(type, mutationData);
      boolean selected = selectedSlot > 0;
      boolean slotUnlocked = selectedSlot == 1 ? slotOneUnlocked : selectedSlot == 2 && slotTwoUnlocked;
      MutationQualification qualification = qualification(type, domainMatches);
      qualifyingAdaptations.put(type, qualification.qualifyingAdaptations());
      qualificationReasons.put(type, qualification.reason());
      if (qualification.qualified()) {
        qualified.add(type);
      }
      MutationState state;
      String reason;

      if (!config.isEnabled()) {
        state = selected ? MutationState.DORMANT : MutationState.DISABLED;
        reason = "Experimental Mutations are turned off on this server";
      } else if (!config.profile(type).isEnabled()) {
        state = selected ? MutationState.DORMANT : MutationState.DISABLED;
        reason = type.displayName() + " is turned off in server settings";
      } else if (!basePermission) {
        state = selected ? MutationState.DORMANT : MutationState.RESTRICTED;
        reason = "Missing permission adapt.mutations";
      } else if (selectedConflict && selected) {
        state = MutationState.CONFLICT;
        reason = duplicateSelection
            ? "The same Mutation cannot be used in both slots"
            : "This choice conflicts with your other Mutation";
      } else if (selected && !slotUnlocked) {
        state = MutationState.DORMANT;
        reason = "This slot is locked at your current level";
      } else if (!selected && !slotOneUnlocked) {
        state = MutationState.LOCKED;
        reason = "The first Mutation slot unlocks at level " + config.getSlotOneUnlockLevel();
      } else if (!player.hasPermission(type.permission())) {
        state = selected ? MutationState.DORMANT : MutationState.RESTRICTED;
        reason = "Missing permission " + type.permission();
      } else if (!config.isWorldAllowed(player.getWorld().getName(), type)) {
        state = selected ? MutationState.DORMANT : MutationState.RESTRICTED;
        reason = "This Mutation does not work in " + player.getWorld().getName();
      } else {
        if (!qualification.qualified()) {
          state = selected ? MutationState.DORMANT : MutationState.RESTRICTED;
          reason = qualification.reason();
        } else if (selected) {
          state = MutationState.EXPRESSED;
          reason = perfect ? "Active with no downside" : "Active; downside applies";
          expressed.add(type);
        } else {
          state = MutationState.AVAILABLE;
          reason = "Ready to use";
        }
      }

      states.put(type, state);
      reasons.put(type, reason);
    }

    MutationSnapshot snapshot = new MutationSnapshot(
        mutationData.getSlotOneId(),
        mutationData.getSlotTwoId(),
        expressed,
        perfect,
        slotOneUnlocked,
        slotTwoUnlocked,
        mutationData.isCooperativeOptIn(),
        new LinkedHashSet<>(mutationData.getDiscovered()),
        states,
        reasons,
        qualified,
        qualifyingAdaptations,
        qualificationReasons
    );
    MutationSnapshot previous = snapshots.put(player.getUniqueId(), snapshot);
    snapshotGuards.put(player.getUniqueId(), captureGuard(player, playerData, level, domainMatches));
    MutationRuntimeSVC runtime = MutationRuntimeSVC.get();
    if (runtime != null) {
      runtime.onLoadoutChanged(player, previous, snapshot);
    }
    if (repairedData) {
      adaptPlayer.saveNow();
    }
    return snapshot;
  }

  public MutationQualification qualification(Player player, MutationType type) {
    if (player == null || type == null || Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return MutationQualification.rejected("Mutation requirements are unavailable");
    }
    EnumMap<MutationDomain, DomainMatch> matches = new EnumMap<>(MutationDomain.class);
    matches.put(type.firstDomain(), matchDomain(player, type.firstDomain()));
    matches.put(type.secondDomain(), matchDomain(player, type.secondDomain()));
    return qualification(type, matches);
  }

  private MutationQualification qualification(MutationType type, Map<MutationDomain, DomainMatch> matches) {
    DomainMatch first = matches.getOrDefault(type.firstDomain(), DomainMatch.empty());
    DomainMatch second = matches.getOrDefault(type.secondDomain(), DomainMatch.empty());
    ArrayList<String> qualifying = new ArrayList<>(first.adaptations().size() + second.adaptations().size());
    qualifying.addAll(first.adaptations());
    qualifying.addAll(second.adaptations());
    if (!first.qualified() && !second.qualified()) {
      return new MutationQualification(false, false, false, qualifying,
          "Learn an Adaptation from both the " + type.firstDomain().displayName() + " and " + type.secondDomain().displayName() + " skill groups");
    }
    if (!first.qualified()) {
      return new MutationQualification(false, false, true, qualifying,
          "Learn a " + type.firstDomain().displayName() + " Adaptation at level " + config.getMinimumAdaptationLevel() + " or higher");
    }
    if (!second.qualified()) {
      return new MutationQualification(false, true, false, qualifying,
          "Learn a " + type.secondDomain().displayName() + " Adaptation at level " + config.getMinimumAdaptationLevel() + " or higher");
    }
    return new MutationQualification(true, true, true, qualifying, "Ready");
  }

  public boolean isExpressed(Player player, MutationType type) {
    return player != null && type != null && snapshot(player).expressed().contains(type);
  }

  public boolean isPerfect(Player player) {
    return player != null && snapshot(player).perfect();
  }

  public boolean isSlotUnlocked(Player player, int slot) {
    if (player == null || Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return false;
    }
    PlayerData data = Adapt.instance.getAdaptServer().getPlayer(player).getData();
    return isSlotUnlocked(data, data.getLevel(), slot);
  }

  public void authorizeBookshelf(Player player, Location bookshelf) {
    if (!config.isEnabled() || player == null || bookshelf == null || bookshelf.getWorld() == null) {
      return;
    }
    long expiresAt = safeAdd(System.currentTimeMillis(), config.getBookshelfTokenMillis());
    bookshelfTokens.put(player.getUniqueId(), BookshelfToken.capture(bookshelf, expiresAt));
  }

  public boolean hasValidBookshelfAccess(Player player) {
    if (player == null) {
      return false;
    }
    BookshelfToken token = bookshelfTokens.get(player.getUniqueId());
    if (token == null || token.expiresAt() <= System.currentTimeMillis()) {
      bookshelfTokens.remove(player.getUniqueId(), token);
      return false;
    }
    World world = player.getWorld();
    if (!world.getUID().equals(token.worldId())) {
      return false;
    }
    Location location = new Location(world, token.x(), token.y(), token.z());
    if (player.getLocation().distanceSquared(location) > config.getBookshelfMaximumDistance() * config.getBookshelfMaximumDistance()) {
      return false;
    }
    return true;
  }

  public MutationSelectionResult select(Player player, int slot, MutationType type, boolean administrative) {
    return select(player, slot, type, administrative, null);
  }

  public MutationSelectionResult select(
      Player player,
      int slot,
      MutationType type,
      boolean administrative,
      String expectedCurrentId
  ) {
    return select(player, slot, type, administrative, expectedCurrentId, null);
  }

  public MutationSelectionResult select(
      Player player,
      int slot,
      MutationType type,
      boolean administrative,
      String expectedCurrentId,
      String expectedOtherId
  ) {
    if (player == null || type == null || (slot != 1 && slot != 2)) {
      return MutationSelectionResult.rejected("Invalid Mutation selection");
    }
    AdaptPlayer adaptPlayer = resolvePlayer(player);
    if (adaptPlayer == null) {
      return MutationSelectionResult.rejected("Player Mutation data is unavailable");
    }
    PlayerMutationData data = adaptPlayer.getData().getMutationData();
    String currentId = data.slotId(slot);
    if (expectedCurrentId != null && !normalizeId(expectedCurrentId).equals(currentId)) {
      return MutationSelectionResult.rejected("The Mutation slot changed while confirmation was open; review it again");
    }
    String otherId = data.slotId(slot == 1 ? 2 : 1);
    if (expectedOtherId != null && !normalizeId(expectedOtherId).equals(otherId)) {
      return MutationSelectionResult.rejected("The Mutation loadout changed while confirmation was open; review it again");
    }
    if (type.id().equals(currentId)) {
      if (data.discover(type.id())) {
        adaptPlayer.saveNow();
        reconcile(adaptPlayer);
      }
      return MutationSelectionResult.success(type.displayName() + " is already equipped in slot " + slot);
    }
    MutationSelectionResult rejection = validateChange(player, data, slot, type, administrative, false);
    if (rejection != null) {
      return rejection;
    }

    data.setSlotId(slot, type.id());
    data.discover(type.id());
    if (!administrative) {
      data.setSlotReadyAt(slot, safeAdd(System.currentTimeMillis(), config.getSwitchCooldownMillis()));
    }
    adaptPlayer.saveNow();
    reconcile(adaptPlayer);
    return MutationSelectionResult.success("Equipped " + type.displayName() + " in slot " + slot);
  }

  public MutationSelectionResult clear(Player player, int slot, boolean administrative) {
    return clear(player, slot, administrative, null);
  }

  public MutationSelectionResult clear(Player player, int slot, boolean administrative, String expectedCurrentId) {
    if (player == null || (slot != 1 && slot != 2)) {
      return MutationSelectionResult.rejected("Invalid Mutation slot");
    }
    AdaptPlayer adaptPlayer = resolvePlayer(player);
    if (adaptPlayer == null) {
      return MutationSelectionResult.rejected("Player Mutation data is unavailable");
    }
    PlayerMutationData data = adaptPlayer.getData().getMutationData();
    if (expectedCurrentId != null && !normalizeId(expectedCurrentId).equals(data.slotId(slot))) {
      return MutationSelectionResult.rejected("The Mutation slot changed while confirmation was open; review it again");
    }
    if (data.slotId(slot).isBlank()) {
      return MutationSelectionResult.success("Mutation slot " + slot + " is already empty");
    }
    MutationSelectionResult rejection = validateChange(player, data, slot, null, administrative, true);
    if (rejection != null) {
      return rejection;
    }

    data.setSlotId(slot, "");
    if (!administrative) {
      data.setSlotReadyAt(slot, safeAdd(System.currentTimeMillis(), config.getSwitchCooldownMillis()));
    }
    adaptPlayer.saveNow();
    reconcile(adaptPlayer);
    return MutationSelectionResult.success("Cleared Mutation slot " + slot);
  }

  public void clearCooldowns(Player player) {
    AdaptPlayer adaptPlayer = resolvePlayer(player);
    if (adaptPlayer == null) {
      return;
    }
    adaptPlayer.getData().getMutationData().clearCooldowns();
    combatLock.clear(player.getUniqueId());
    adaptPlayer.saveNow();
  }

  public void setDiscovered(Player player, MutationType type, boolean discovered) {
    AdaptPlayer adaptPlayer = resolvePlayer(player);
    if (adaptPlayer == null || type == null) {
      return;
    }
    PlayerMutationData data = adaptPlayer.getData().getMutationData();
    if (discovered) {
      data.discover(type.id());
    } else {
      data.undiscover(type.id());
      if (type.id().equals(data.getSlotOneId())) {
        data.setSlotOneId("");
      }
      if (type.id().equals(data.getSlotTwoId())) {
        data.setSlotTwoId("");
      }
    }
    adaptPlayer.saveNow();
    reconcile(adaptPlayer);
  }

  public void setSlotOverride(Player player, int slot, Boolean unlocked) {
    AdaptPlayer adaptPlayer = resolvePlayer(player);
    if (adaptPlayer == null || (slot != 1 && slot != 2)) {
      return;
    }
    adaptPlayer.getData().getMutationData().setSlotUnlockedOverride(slot, unlocked);
    adaptPlayer.saveNow();
    reconcile(adaptPlayer);
  }

  public void setPerfectOverride(Player player, Boolean perfect) {
    if (player == null) {
      return;
    }
    if (perfect == null) {
      perfectOverrides.remove(player.getUniqueId());
    } else {
      perfectOverrides.put(player.getUniqueId(), perfect);
    }
    reconcile(player);
  }

  public void setCooperativeOptIn(Player player, boolean optedIn) {
    AdaptPlayer adaptPlayer = resolvePlayer(player);
    if (adaptPlayer == null || !player.hasPermission("adapt.mutations")) {
      return;
    }
    adaptPlayer.getData().getMutationData().setCooperativeOptIn(optedIn);
    adaptPlayer.saveNow();
    reconcile(adaptPlayer);
  }

  public void reload(MutationConfig replacement) {
    config = replacement == null ? MutationConfig.defaults() : replacement;
    combatLock.setDurationMillis(config.getCombatLockMillis());
    bookshelfTokens.clear();
    snapshots.clear();
    snapshotGuards.clear();
    externalSnapshotRefreshes.clear();
  }

  public void cleanup(UUID playerId) {
    if (playerId == null) {
      return;
    }
    snapshots.remove(playerId);
    snapshotGuards.remove(playerId);
    externalSnapshotRefreshes.remove(playerId);
    bookshelfTokens.remove(playerId);
    perfectOverrides.remove(playerId);
    combatLock.clear(playerId);
  }

  public void cleanupTransient(UUID playerId) {
    if (playerId == null) {
      return;
    }
    bookshelfTokens.remove(playerId);
    combatLock.clear(playerId);
  }

  public void shutdown() {
    snapshots.clear();
    snapshotGuards.clear();
    externalSnapshotRefreshes.clear();
    bookshelfTokens.clear();
    perfectOverrides.clear();
    combatLock.clearAll();
  }

  private MutationSelectionResult validateChange(
      Player player,
      PlayerMutationData data,
      int slot,
      MutationType candidate,
      boolean administrative,
      boolean clearing
  ) {
    if (administrative) {
      MutationType other = MutationType.find(data.slotId(slot == 1 ? 2 : 1));
      if (candidate != null && other == candidate) {
        return MutationSelectionResult.rejected("The same Mutation cannot occupy both slots");
      }
      if (candidate != null && other != null && conflicts(candidate, other)) {
        return MutationSelectionResult.rejected(candidate.displayName() + " conflicts with " + other.displayName());
      }
      return null;
    }
    if (!config.isEnabled()) {
      return MutationSelectionResult.rejected("Experimental Mutations are turned off on this server");
    }
    if (!config.isSwitchingEnabled()) {
      return MutationSelectionResult.rejected("Mutation changes are turned off on this server");
    }
    if (!player.hasPermission("adapt.mutations")) {
      return MutationSelectionResult.rejected("Missing permission adapt.mutations");
    }
    if (!hasValidBookshelfAccess(player)) {
      return MutationSelectionResult.rejected("Return to the Adapt bookshelf before changing a Mutation slot");
    }
    if (!isSlotUnlocked(player, slot)) {
      int required = slot == 1 ? config.getSlotOneUnlockLevel() : config.getSlotTwoUnlockLevel();
      return MutationSelectionResult.rejected("Mutation slot " + slot + " unlocks at level " + required);
    }
    if (config.isPermanentSelection() && !data.slotId(slot).isBlank()) {
      return MutationSelectionResult.rejected("This server makes Mutation choices permanent");
    }
    long cooldownRemaining = data.slotReadyAt(slot) - System.currentTimeMillis();
    if (cooldownRemaining > 0L) {
      return MutationSelectionResult.cooldown("Mutation slot " + slot + " cannot be changed yet", cooldownRemaining);
    }
    long combatRemaining = combatLock.remainingMillis(player.getUniqueId());
    if (combatRemaining > 0L) {
      return MutationSelectionResult.cooldown("You cannot change Mutations during combat", combatRemaining);
    }
    if (clearing) {
      return null;
    }
    if (candidate == null || !config.profile(candidate).isEnabled()) {
      return MutationSelectionResult.rejected("That Mutation is disabled");
    }
    if (!player.hasPermission(candidate.permission())) {
      return MutationSelectionResult.rejected("Missing permission " + candidate.permission());
    }
    if (!config.isWorldAllowed(player.getWorld().getName(), candidate)) {
      return MutationSelectionResult.rejected("That Mutation does not work in this world");
    }
    MutationQualification qualification = qualification(player, candidate);
    if (!qualification.qualified()) {
      return MutationSelectionResult.rejected(qualification.reason());
    }
    MutationType other = MutationType.find(data.slotId(slot == 1 ? 2 : 1));
    if (other == candidate) {
      return MutationSelectionResult.rejected("The same Mutation cannot occupy both slots");
    }
    if (other != null && conflicts(candidate, other)) {
      return MutationSelectionResult.rejected(candidate.displayName() + " conflicts with " + other.displayName());
    }
    return null;
  }

  private void requestExternalSnapshotRefresh(UUID playerId) {
    if (!config.isEnabled() || Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return;
    }
    AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getOnlineAdaptPlayer(playerId);
    Player player = adaptPlayer == null ? null : adaptPlayer.getPlayer();
    if (player == null) {
      return;
    }
    long now = System.currentTimeMillis();
    long reservedUntil = safeAdd(now, EXTERNAL_SNAPSHOT_REFRESH_MILLIS);
    while (true) {
      Long current = externalSnapshotRefreshes.get(playerId);
      if (current != null && current > now) {
        return;
      }
      boolean reserved = current == null
          ? externalSnapshotRefreshes.putIfAbsent(playerId, reservedUntil) == null
          : externalSnapshotRefreshes.replace(playerId, current, reservedUntil);
      if (!reserved) {
        continue;
      }
      if (!J.runEntity(player, () -> reconcile(adaptPlayer))) {
        externalSnapshotRefreshes.remove(playerId, reservedUntil);
      }
      return;
    }
  }

  private MutationSnapshot offlineSnapshot(UUID playerId, PlayerData playerData) {
    PlayerMutationData mutationData = playerData.getMutationData();
    int level = playerData.getLevel();
    boolean slotOneUnlocked = isSlotUnlocked(playerData, level, 1);
    boolean slotTwoUnlocked = isSlotUnlocked(playerData, level, 2);
    EnumMap<MutationType, MutationState> states = new EnumMap<>(MutationType.class);
    EnumMap<MutationType, String> reasons = new EnumMap<>(MutationType.class);
    EnumMap<MutationType, String> qualificationReasons = new EnumMap<>(MutationType.class);
    for (MutationType type : MutationType.values()) {
      qualificationReasons.put(type, config.isEnabled()
          ? "Log in to check requirements"
          : "Experimental Mutations are turned off on this server");
      boolean selected = type.id().equals(mutationData.getSlotOneId()) || type.id().equals(mutationData.getSlotTwoId());
      if (!config.isEnabled()) {
        states.put(type, selected ? MutationState.DORMANT : MutationState.DISABLED);
        reasons.put(type, "Experimental Mutations are turned off on this server");
      } else if (selected) {
        states.put(type, MutationState.DORMANT);
        reasons.put(type, "Equipped but inactive while the player is offline");
      } else if (!slotOneUnlocked) {
        states.put(type, MutationState.LOCKED);
        reasons.put(type, "The first Mutation slot is locked");
      } else {
        states.put(type, MutationState.RESTRICTED);
        reasons.put(type, "Log in to check requirements");
      }
    }
    return new MutationSnapshot(
        mutationData.getSlotOneId(),
        mutationData.getSlotTwoId(),
        Set.of(),
        resolvePerfect(playerId, level),
        slotOneUnlocked,
        slotTwoUnlocked,
        mutationData.isCooperativeOptIn(),
        new LinkedHashSet<>(mutationData.getDiscovered()),
        states,
        reasons,
        Set.of(),
        Map.of(),
        qualificationReasons
    );
  }

  private DomainMatch matchDomain(Player player, MutationDomain domain) {
    ArrayList<String> qualifying = new ArrayList<>();
    ArrayList<QualificationCandidate> candidates = new ArrayList<>();
    QualificationCandidate witness = null;
    int minimumLevel = config.getMinimumAdaptationLevel();
    candidateScan:
    for (String skillId : config.skills(domain)) {
      Skill<?> skill = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(skillId);
      if (skill == null || !skill.isEnabled()) {
        continue;
      }
      PlayerSkillLine line = Adapt.instance.getAdaptServer().getPlayer(player).getData().getSkillLineNullable(skill.getName());
      if (line == null) {
        continue;
      }
      boolean skillAllowed = skill.hasUsePermission(player, skill);
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (adaptation == null || !adaptation.isEnabled()) {
          continue;
        }
        PlayerAdaptation learned = line.getAdaptation(adaptation.getName());
        if (learned != null && learned.getLevel() >= minimumLevel) {
          if (candidates.size() >= MutationLimits.QUALIFICATION_CANDIDATES_PER_DOMAIN) {
            break candidateScan;
          }
          QualificationCandidate candidate = QualificationCandidate.capture(skill, adaptation);
          candidates.add(candidate);
          if (skillAllowed && adaptation.hasUsePermission(player, adaptation)) {
            qualifying.add(adaptation.getName());
            if (witness == null) {
              witness = candidate;
            }
          }
        }
      }
    }
    boolean qualified = !qualifying.isEmpty();
    List<QualificationCandidate> guardedCandidates = qualified && witness != null
        ? List.of(witness)
        : List.copyOf(candidates);
    return new DomainMatch(
        qualified,
        qualifying,
        new DomainQualificationGuard(qualified, guardedCandidates)
    );
  }

  private EnumMap<MutationDomain, DomainMatch> domainMatches(Player player) {
    EnumMap<MutationDomain, DomainMatch> matches = new EnumMap<>(MutationDomain.class);
    for (MutationDomain domain : MutationDomain.values()) {
      matches.put(domain, matchDomain(player, domain));
    }
    return matches;
  }

  private boolean normalizeDurableState(PlayerMutationData data) {
    boolean changed = false;
    double maximumIchor = config.getDeepblood().getMaximumIchor();
    if (data.getDeepbloodIchor() > maximumIchor) {
      data.setDeepbloodIchor(maximumIchor);
      changed = true;
    }
    double maximumRootCharge = config.getLivingLattice().getMaximumRootCharge();
    if (data.getLivingLatticeRootCharge() > maximumRootCharge) {
      data.setLivingLatticeRootCharge(maximumRootCharge);
      changed = true;
    }
    return changed;
  }

  private boolean isSlotUnlocked(PlayerData data, int level, int slot) {
    Boolean override = data.getMutationData().slotUnlockedOverride(slot);
    if (override != null) {
      return override;
    }
    return switch (slot) {
      case 1 -> level >= config.getSlotOneUnlockLevel();
      case 2 -> level >= config.getSlotTwoUnlockLevel();
      default -> false;
    };
  }

  private boolean resolvePerfect(UUID playerId, int level) {
    if (!config.isEnabled()) {
      return false;
    }
    Boolean override = perfectOverrides.get(playerId);
    return override == null
        ? config.isPerfectAdaptationEnabled() && level >= config.getPerfectAdaptationLevel()
        : override;
  }

  private boolean conflicts(MutationType first, MutationType second) {
    return containsIgnoreCase(config.profile(first).getConflicts(), second.id())
        || containsIgnoreCase(config.profile(second).getConflicts(), first.id());
  }

  private int selectedSlot(MutationType type, PlayerMutationData data) {
    if (type.id().equals(data.getSlotOneId())) {
      return 1;
    }
    return type.id().equals(data.getSlotTwoId()) ? 2 : 0;
  }

  private AdaptPlayer resolvePlayer(Player player) {
    if (player == null || Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return null;
    }
    return Adapt.instance.getAdaptServer().getPlayer(player);
  }

  private boolean containsIgnoreCase(List<String> values, String expected) {
    if (values == null || expected == null) {
      return false;
    }
    for (String value : values) {
      if (value != null && value.equalsIgnoreCase(expected)) {
        return true;
      }
    }
    return false;
  }

  private long safeAdd(long value, long addition) {
    if (addition <= 0L) {
      return value;
    }
    return Long.MAX_VALUE - value < addition ? Long.MAX_VALUE : value + addition;
  }

  private String normalizeId(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
  }

  private SnapshotGuard captureGuard(
      Player player,
      PlayerData data,
      int level,
      Map<MutationDomain, DomainMatch> domainMatches
  ) {
    PlayerMutationData mutationData = data.getMutationData();
    MutationType slotOne = MutationType.find(mutationData.getSlotOneId());
    MutationType slotTwo = MutationType.find(mutationData.getSlotTwoId());
    return new SnapshotGuard(
        player.getWorld().getUID(),
        level,
        Double.doubleToLongBits(data.getMasterXp()),
        mutationData.getSlotOneId(),
        mutationData.getSlotTwoId(),
        isSlotUnlocked(data, level, 1),
        isSlotUnlocked(data, level, 2),
        resolvePerfect(player.getUniqueId(), level),
        mutationData.isCooperativeOptIn(),
        player.hasPermission("adapt.mutations"),
        slotOne == null || player.hasPermission(slotOne.permission()),
        slotTwo == null || player.hasPermission(slotTwo.permission()),
        qualificationGuards(slotOne, slotTwo, domainMatches)
    );
  }

  private boolean isGuardCurrent(Player player, PlayerData data, SnapshotGuard guard) {
    PlayerMutationData mutationData = data.getMutationData();
    MutationType slotOne = MutationType.find(mutationData.getSlotOneId());
    MutationType slotTwo = MutationType.find(mutationData.getSlotTwoId());
    int level = guard.level();
    World world = player.getWorld();
    return guard.worldId().equals(world.getUID())
        && guard.masterXpBits() == Double.doubleToLongBits(data.getMasterXp())
        && guard.slotOneId().equals(mutationData.getSlotOneId())
        && guard.slotTwoId().equals(mutationData.getSlotTwoId())
        && guard.slotOneUnlocked() == isSlotUnlocked(data, level, 1)
        && guard.slotTwoUnlocked() == isSlotUnlocked(data, level, 2)
        && guard.perfect() == resolvePerfect(player.getUniqueId(), level)
        && guard.cooperativeOptIn() == mutationData.isCooperativeOptIn()
        && guard.basePermission() == player.hasPermission("adapt.mutations")
        && qualificationsCurrent(player, data, guard.qualificationGuards())
        && guard.slotOnePermission() == (slotOne == null || player.hasPermission(slotOne.permission()))
        && guard.slotTwoPermission() == (slotTwo == null || player.hasPermission(slotTwo.permission()));
  }

  private List<DomainQualificationGuard> qualificationGuards(
      MutationType slotOne,
      MutationType slotTwo,
      Map<MutationDomain, DomainMatch> domainMatches
  ) {
    EnumSet<MutationDomain> selectedDomains = EnumSet.noneOf(MutationDomain.class);
    if (slotOne != null) {
      selectedDomains.add(slotOne.firstDomain());
      selectedDomains.add(slotOne.secondDomain());
    }
    if (slotTwo != null) {
      selectedDomains.add(slotTwo.firstDomain());
      selectedDomains.add(slotTwo.secondDomain());
    }
    ArrayList<DomainQualificationGuard> guards = new ArrayList<>(selectedDomains.size());
    for (MutationDomain domain : selectedDomains) {
      DomainMatch match = domainMatches.get(domain);
      if (match != null && match.guard() != null) {
        guards.add(match.guard());
      }
    }
    return List.copyOf(guards);
  }

  private boolean qualificationsCurrent(
      Player player,
      PlayerData data,
      List<DomainQualificationGuard> guards
  ) {
    for (DomainQualificationGuard guard : guards) {
      if (!guard.isCurrent(player, data, config.getMinimumAdaptationLevel())) {
        return false;
      }
    }
    return true;
  }

  private static boolean usePermission(Player player, String permission) {
    if (player.isOp()) {
      return true;
    }
    return !player.isPermissionSet(permission) || player.hasPermission(permission);
  }

  private record DomainMatch(
      boolean qualified,
      List<String> adaptations,
      DomainQualificationGuard guard
  ) {
    private DomainMatch {
      adaptations = adaptations == null ? List.of() : List.copyOf(adaptations);
    }

    private static DomainMatch empty() {
      return new DomainMatch(false, List.of(), new DomainQualificationGuard(false, List.of()));
    }
  }

  private record DomainQualificationGuard(
      boolean qualified,
      List<QualificationCandidate> candidates,
      AtomicLong nextDormantCheckAt
  ) {
    private DomainQualificationGuard(boolean qualified, List<QualificationCandidate> candidates) {
      this(
          qualified,
          candidates,
          new AtomicLong(qualified ? 0L : System.currentTimeMillis() + DORMANT_QUALIFICATION_RECHECK_MILLIS)
      );
    }

    private DomainQualificationGuard {
      candidates = candidates == null ? List.of() : List.copyOf(candidates);
      nextDormantCheckAt = nextDormantCheckAt == null ? new AtomicLong() : nextDormantCheckAt;
    }

    private boolean isCurrent(Player player, PlayerData data, int minimumLevel) {
      if (!qualified) {
        long now = System.currentTimeMillis();
        long nextCheck = nextDormantCheckAt.get();
        if (now < nextCheck
            || !nextDormantCheckAt.compareAndSet(nextCheck, now + DORMANT_QUALIFICATION_RECHECK_MILLIS)) {
          return true;
        }
      }
      boolean currentlyQualified = false;
      for (QualificationCandidate candidate : candidates) {
        if (candidate.isQualified(player, data, minimumLevel)) {
          currentlyQualified = true;
          break;
        }
      }
      return currentlyQualified == qualified;
    }
  }

  private record QualificationCandidate(
      Skill<?> skill,
      Adaptation<?> adaptation,
      String skillPermission,
      String adaptationPermission
  ) {
    private static QualificationCandidate capture(Skill<?> skill, Adaptation<?> adaptation) {
      return new QualificationCandidate(
          skill,
          adaptation,
          permission(skill.getName()),
          permission(adaptation.getName())
      );
    }

    private boolean isQualified(Player player, PlayerData data, int minimumLevel) {
      if (skill == null || adaptation == null || !skill.isEnabled() || !adaptation.isEnabled()) {
        return false;
      }
      PlayerSkillLine line = data.getSkillLineNullable(skill.getName());
      PlayerAdaptation learned = line == null ? null : line.getAdaptation(adaptation.getName());
      return learned != null
          && learned.getLevel() >= minimumLevel
          && usePermission(player, skillPermission)
          && usePermission(player, adaptationPermission);
    }

    private static String permission(String name) {
      return "adapt.use." + name.replace("-", "");
    }
  }

  private record SnapshotGuard(
      UUID worldId,
      int level,
      long masterXpBits,
      String slotOneId,
      String slotTwoId,
      boolean slotOneUnlocked,
      boolean slotTwoUnlocked,
      boolean perfect,
      boolean cooperativeOptIn,
      boolean basePermission,
      boolean slotOnePermission,
      boolean slotTwoPermission,
      List<DomainQualificationGuard> qualificationGuards
  ) {
    private SnapshotGuard {
      qualificationGuards = qualificationGuards == null ? List.of() : List.copyOf(qualificationGuards);
    }
  }

  private record BookshelfToken(UUID worldId, int x, int y, int z, long expiresAt) {
    private static BookshelfToken capture(Location location, long expiresAt) {
      return new BookshelfToken(
          location.getWorld().getUID(),
          location.getBlockX(),
          location.getBlockY(),
          location.getBlockZ(),
          expiresAt
      );
    }
  }
}
