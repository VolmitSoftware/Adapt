package art.arcane.adapt.engine.player;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.player.DiscoveryKind;
import art.arcane.adapt.api.player.LearnOptions;
import art.arcane.adapt.api.player.LearnResult;
import art.arcane.adapt.api.player.MutationResult;
import art.arcane.adapt.api.player.OrphanedLine;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.PlayerSection;
import art.arcane.adapt.engine.addon.DeclaredData;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.engine.adaptation.AdaptationLearningTransaction;
import art.arcane.adapt.engine.adaptation.AdaptationLevelActions;
import art.arcane.adapt.engine.advancement.AdaptAdvancement;
import art.arcane.adapt.engine.mutation.SkillMutations;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.skill.SkillRegistry;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.Discovery;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XPMultiplier;
import art.arcane.adapt.client.server.ClientUiService;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@link PlayerProfile} over one loaded {@link AdaptPlayer}. Reads work from any thread; every change refuses to run
 * off the thread that owns the player, so callers hop there first (commands do it through
 * {@code CommandTargetExecutor}).
 */
public final class EnginePlayerProfile implements PlayerProfile {
  private final AdaptPlayer runtime;

  EnginePlayerProfile(AdaptPlayer runtime) {
    this.runtime = Objects.requireNonNull(runtime, "runtime");
  }

  public AdaptPlayer runtime() {
    return runtime;
  }

  @Override
  public PlayerRef player() {
    return runtime.getPlayer();
  }

  @Override
  public UUID uuid() {
    return runtime.getPlayer().uuid();
  }

  @Override
  public boolean isReady() {
    AdaptServer server = server();
    return runtime.isRuntimeReady() && server != null && server.getOnlineAdaptPlayer(uuid()) == runtime;
  }

  @Override
  public EngineSkillProgress skill(Id skill) {
    return new EngineSkillProgress(this, skill);
  }

  @Override
  public List<SkillProgress> skills() {
    List<SkillProgress> skills = new ArrayList<>();
    for (String key : new TreeMap<>(data().getSkillLines()).keySet()) {
      Id skill = ProgressEvents.lineSkill(key);
      if (skill != null && Orphans.skill(registry(), key) != null) {
        skills.add(skill(skill));
      }
    }
    return List.copyOf(skills);
  }

  @Override
  public int level(Id adaptation) {
    Adaptation<?> found = registry().getAdaptation(adaptation);
    if (found == null) {
      return 0;
    }
    PlayerSkillLine line = data().getSkillLineNullable(SkillHost.lineKey(found.getSkill()));
    return line == null ? 0 : line.getAdaptationLevel(found.getId().toString());
  }

  @Override
  public LearnResult learn(Id adaptation, int level, LearnOptions options) {
    return change(adaptation, level, options, true);
  }

  @Override
  public LearnResult unlearn(Id adaptation, int level, LearnOptions options) {
    return change(adaptation, level, options, false);
  }

  private LearnResult change(Id id, int level, LearnOptions options, boolean learn) {
    Objects.requireNonNull(id, "adaptation");
    Objects.requireNonNull(options, "options");
    requireOwner();
    Adaptation<?> adaptation = registry().getAdaptation(id);
    PlayerSkillLine line = adaptation == null || !adaptation.isEnabled() ? null
        : data().getSkillLine(SkillHost.lineKey(adaptation.getSkill()));
    if (line == null) {
      return new EngineLearnResult(LearnResult.Status.UNAVAILABLE, 0, 0, 0L);
    }
    String key = adaptation.getId().toString();
    int from = line.getAdaptationLevel(key);
    long knowledge = line.getKnowledge();
    AdaptationLearningTransaction.Result result = learn
        ? AdaptationLearningTransaction.learn(adaptation, player(), level, options.force())
        : AdaptationLearningTransaction.unlearn(adaptation, player(), level, options.force());
    LearnResult.Status status = status(result);
    boolean changed = status == LearnResult.Status.LEARNED || status == LearnResult.Status.UNLEARNED;
    if (changed && !options.quiet()) {
      AdaptationLevelActions.celebrate(adaptation, player(), learn);
    }
    return new EngineLearnResult(status, from, changed ? line.getAdaptationLevel(key) : from,
        changed ? Math.abs(line.getKnowledge() - knowledge) : 0L);
  }

  /** The public status of a learning transaction's result; one to one, the line being unavailable included. */
  public static LearnResult.Status status(AdaptationLearningTransaction.Result result) {
    return switch (result) {
      case LEARNED -> LearnResult.Status.LEARNED;
      case UNLEARNED -> LearnResult.Status.UNLEARNED;
      case NO_CHANGE -> LearnResult.Status.NO_CHANGE;
      case SKILL_LINE_UNAVAILABLE -> LearnResult.Status.UNAVAILABLE;
      case INSUFFICIENT_POWER -> LearnResult.Status.INSUFFICIENT_POWER;
      case INSUFFICIENT_KNOWLEDGE -> LearnResult.Status.INSUFFICIENT_KNOWLEDGE;
      case INSUFFICIENT_FUNDS -> LearnResult.Status.INSUFFICIENT_FUNDS;
      case ECONOMY_UNAVAILABLE -> LearnResult.Status.ECONOMY_UNAVAILABLE;
      case ECONOMY_FAILED -> LearnResult.Status.ECONOMY_FAILED;
      case PERMANENT -> LearnResult.Status.PERMANENT;
      case REQUIREMENTS_MISSING -> LearnResult.Status.REQUIREMENTS_MISSING;
      case REQUIRED_BY_DEPENDENTS -> LearnResult.Status.REQUIRED_BY_DEPENDENTS;
      case CANCELLED -> LearnResult.Status.CANCELLED;
    };
  }

  @Override
  public int power() {
    return data().getUsedPower();
  }

  @Override
  public int maxPower() {
    return data().getMaxPower();
  }

  @Override
  public MutationResult mutate(Id lead, Id partner) {
    Objects.requireNonNull(lead, "lead");
    Objects.requireNonNull(partner, "partner");
    requireOwner();
    Skill<?> leadSkill = registry().getSkill(lead);
    Skill<?> partnerSkill = registry().getSkill(partner);
    SkillMutations.Result result = (leadSkill == null && registry().getAnySkill(lead) != null)
        || (partnerSkill == null && registry().getAnySkill(partner) != null)
        ? SkillMutations.Result.UNAVAILABLE_SKILL
        : SkillMutations.mutate(data(), leadSkill, partnerSkill, player().name());
    MutationResult.Status status = status(result);
    if (status != MutationResult.Status.MUTATED) {
      return new EngineMutationResult(status, Optional.empty());
    }
    ClientUiService.changed(runtime);
    return new EngineMutationResult(status, Optional.of(new SkillPair(lead, partner)));
  }

  /** The public status of a merge result; one to one. */
  public static MutationResult.Status status(SkillMutations.Result result) {
    return switch (result) {
      case MUTATED -> MutationResult.Status.MUTATED;
      case UNKNOWN_SKILL -> MutationResult.Status.UNKNOWN_SKILL;
      case UNAVAILABLE_SKILL -> MutationResult.Status.UNAVAILABLE_SKILL;
      case SAME_SKILL -> MutationResult.Status.SAME_SKILL;
      case ALREADY_MUTATED -> MutationResult.Status.ALREADY_MUTATED;
      case LEVEL_TOO_LOW -> MutationResult.Status.LEVEL_TOO_LOW;
      case LIMIT_REACHED -> MutationResult.Status.LIMIT_REACHED;
      case CANCELLED -> MutationResult.Status.CANCELLED;
    };
  }

  @Override
  public MutationResult unmutate(Id skill) {
    Objects.requireNonNull(skill, "skill");
    requireOwner();
    SkillPair mutation = mutationNamed(skill);
    SkillMutations.Unmutation result = mutation == null ? SkillMutations.Unmutation.NOT_MUTATED
        : SkillMutations.unmutate(data(), mutation, player().name());
    return switch (result) {
      case UNMUTATED -> {
        ClientUiService.changed(runtime);
        yield new EngineMutationResult(MutationResult.Status.UNMUTATED, Optional.of(mutation));
      }
      case NOT_MUTATED -> new EngineMutationResult(MutationResult.Status.NOT_MUTATED, Optional.empty());
      case CANCELLED -> new EngineMutationResult(MutationResult.Status.CANCELLED, Optional.empty());
    };
  }

  /** Splits every mutation the player holds; returns the ones split. */
  public List<SkillPair> unmutateAll() {
    requireOwner();
    List<SkillPair> removed = SkillMutations.unmutateAll(data(), player().name());
    ClientUiService.changed(runtime);
    return List.copyOf(removed);
  }

  @Override
  public List<SkillPair> mutations() {
    return data().getMutations();
  }

  @Override
  public Optional<SkillPair> mutationOf(Id skill) {
    return Optional.ofNullable(mutationNamed(skill));
  }

  /** The player's mutation a skill id names: one of its parents, or the mutated skill itself. */
  private SkillPair mutationNamed(Id skill) {
    for (SkillPair mutation : data().getMutations()) {
      Skill<?> mutated = registry().getMutatedSkill(mutation);
      if (mutation.involves(skill) || (mutated != null && mutated.getId().equals(skill))) {
        return mutation;
      }
    }
    return null;
  }

  @Override
  public EnginePreferenceStore preferences() {
    return new EnginePreferenceStore(this);
  }

  @Override
  public <T> T section(PlayerSection<T> section) {
    return data().section(DeclaredData.requireSection(section));
  }

  @Override
  public double stat(String stat) {
    return data().getStat(stat);
  }

  @Override
  public void addStat(String stat, double amount) {
    Objects.requireNonNull(stat, "stat");
    requireOwner();
    data().addStat(stat, amount);
  }

  @Override
  public boolean isGranted(Id owner, String key) {
    return data().isGranted(AdaptAdvancement.contentKey(owner, key));
  }

  @Override
  public boolean grant(Id owner, String key) {
    Objects.requireNonNull(key, "key");
    String advancement = AdaptAdvancement.contentKey(Objects.requireNonNull(owner, "owner"), key);
    requireOwner();
    return !data().isGranted(advancement) && runtime.getAdvancementHandler().grant(advancement);
  }

  @Override
  public void boost(double multiplier, long durationMillis) {
    requireOwner();
    data().globalXPMultiplier(multiplier, durationMillis);
  }

  @Override
  public void boost(String source, double multiplier, long durationMillis) {
    Objects.requireNonNull(source, "source");
    requireOwner();
    data().globalXPMultiplier(XPMultiplier.owned(source, multiplier, durationMillis));
  }

  @Override
  public void endBoosts(String source) {
    Objects.requireNonNull(source, "source");
    requireOwner();
    data().removeGlobalXPMultipliers(source);
  }

  @Override
  public List<OrphanedLine> orphans() {
    return List.copyOf(Orphans.list(data(), registry()));
  }

  @Override
  public long refundOrphans(String namespace) {
    requireOwner();
    long refunded = Orphans.refund(data(), registry(), namespace);
    ClientUiService.changed(runtime);
    return refunded;
  }

  @Override
  public int purgeOrphans(String namespace) {
    requireOwner();
    int purged = Orphans.purge(data(), registry(), namespace, player().name());
    ClientUiService.changed(runtime);
    return purged;
  }

  @Override
  public void reset() {
    requireOwner();
    clearXp();
    clearKnowledge();
    PlayerData data = data();
    data.clearStats();
    data.clearDiscoveries();
    data.getAdvancements().clear();
    data.getMultipliers().clear();
    data.setWisdom(0L);
  }

  @Override
  public void clearXp() {
    requireOwner();
    clearAdaptations();
    for (SkillProgress progress : skills()) {
      PlayerSkillLine line = data().getSkillLineNullable(((EngineSkillProgress) progress).key());
      if (line != null) {
        line.flushXpPool(null);
        line.setXp(0D);
        line.setLastXP(0D);
        line.setLastLevel(0);
      }
    }
    data().resetMasterXp();
  }

  @Override
  public void clearKnowledge() {
    requireOwner();
    for (SkillProgress progress : skills()) {
      if (progress.knowledge() > 0L) {
        progress.addKnowledge(-progress.knowledge());
      }
    }
  }

  @Override
  public void clearAdaptations() {
    requireOwner();
    for (SkillProgress progress : skills()) {
      EngineSkillProgress line = (EngineSkillProgress) progress;
      Skill<?> owner = Orphans.skill(registry(), line.key());
      if (owner != null) {
        line.unlearnAll(owner);
      }
    }
  }

  @Override
  public void clearStats() {
    requireOwner();
    data().clearStats();
  }

  @Override
  public void clearDiscoveries() {
    requireOwner();
    data().clearDiscoveries();
  }

  @Override
  public boolean discover(DiscoveryKind kind, String key) {
    Objects.requireNonNull(key, "key");
    PlayerData data = data();
    Discovery<String> seen = switch (Objects.requireNonNull(kind, "kind")) {
      case BLOCK -> data.getSeenBlocks();
      case ITEM -> data.getSeenItems();
      case RECIPE -> data.getSeenRecipes();
      case FOOD -> data.getSeenFoods();
      case MOB -> data.getSeenMobs();
      case PERSON -> data.getSeenPeople();
      case ENCHANTMENT -> data.getSeenEnchants();
      case WORLD -> data.getSeenWorlds();
      case ENVIRONMENT -> data.getSeenEnvironments();
      case POTION_EFFECT -> data.getSeenPotionEffects();
      case BIOME -> data.getSeenBiomes();
    };
    return seen.isNewDiscovery(key);
  }

  @Override
  public boolean discoverChunk(UUID world, int chunkX, int chunkZ) {
    return data().getSeenChunks().isNewDiscovery(Objects.requireNonNull(world, "world"), chunkX, chunkZ);
  }

  @Override
  public boolean consumeFood(double cost, int minimumFood) {
    requireOwner();
    return runtime.consumeFood(cost, minimumFood);
  }

  @Override
  public void chargeFood(double cost) {
    requireOwner();
    runtime.applyFoodCharge(cost);
  }

  @Override
  public void requestSave() {
    runtime.requestSave();
  }

  @Override
  public void saveNow() {
    runtime.saveNow();
  }

  @Override
  public CompletableFuture<Void> checkpoint() {
    return runtime.checkpoint();
  }

  @Override
  public void erase() {
    AdaptServer server = server();
    if (server == null) {
      throw new IllegalStateException("Adapt is not running");
    }
    server.resetPlayerData(uuid());
  }

  /** Throws unless the current thread owns the player; every change to the player's state goes through it. */
  void requireOwner() {
    if (!Tasks.isOwnedByCurrentRegion(player())) {
      throw new IllegalStateException("Change " + player().name()
          + "'s Adapt progress on the thread that owns the player");
    }
  }

  PlayerData data() {
    return runtime.getData();
  }

  SkillRegistry registry() {
    AdaptServer server = server();
    if (server == null) {
      throw new IllegalStateException("Adapt is not running");
    }
    return server.getSkillRegistry();
  }

  private static AdaptServer server() {
    AdaptCore core = AdaptCore.current();
    return core == null ? null : core.getAdaptServer();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof EnginePlayerProfile profile && profile.runtime == runtime;
  }

  @Override
  public int hashCode() {
    return runtime.hashCode();
  }
}
