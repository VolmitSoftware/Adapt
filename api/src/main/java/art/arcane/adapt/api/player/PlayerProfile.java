package art.arcane.adapt.api.player;

import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * One online player's progress, and the only way to change it. Adapt's own commands and menus go through this
 * interface, so anything an admin can do, an addon can do.
 *
 * <p>Every change fires the matching event in {@code art.arcane.adapt.api.event} first and does nothing when a
 * listener cancels it.
 *
 * <p>Threads: a method that changes progress (learning, mutating, XP, knowledge, stats, boosts, grants, orphans,
 * resets, food, and the writes of {@link #skill} and {@link #preferences()}) throws {@link IllegalStateException}
 * when called off the thread that owns the player: the server thread on Bukkit and modded servers, the player's
 * region thread on Folia. Reads, {@link #discover} and {@link #discoverChunk}, the saves and {@link #section} do not
 * check the thread; a read from another thread sees the data without a lock and may see a change half made.
 *
 * <p>Logout: a profile belongs to one session of the player. When they leave, or their data is handed to another
 * server, {@link #isReady()} turns false and the profile is detached: reads return the data as it was at that
 * moment, changes reach only that stale copy, {@link #requestSave()} does nothing and {@link #checkpoint()} fails,
 * while {@link #saveNow()} writes the stale copy over whatever was saved since. Do not keep a profile past logout;
 * look the player up again with {@link PlayerDirectory#find(UUID)}.
 */
@ApiStatus.AvailableSince("1.0.0")
@ApiStatus.NonExtendable
public interface PlayerProfile {
  /** The player this profile belongs to. */
  PlayerRef player();

  /** The player's UUID. */
  UUID uuid();

  /** False while the player's data is still being claimed or is being handed to another server. */
  boolean isReady();

  /** The player's line in a skill; a skill the player never touched reads as level 0 with no XP. */
  SkillProgress skill(Id skill);

  /** Every line of a registered skill the player has progress in, mutated lines included. */
  List<SkillProgress> skills();

  /** The learned level of an adaptation, 0 when it is not learned or not registered. */
  int level(Id adaptation);

  /**
   * Raises an adaptation to {@code level}.
   *
   * @param level the target level, above the current one
   */
  LearnResult learn(Id adaptation, int level, LearnOptions options);

  /**
   * Lowers an adaptation to {@code level}; 0 unlearns it.
   *
   * @param level the target level, below the current one
   */
  LearnResult unlearn(Id adaptation, int level, LearnOptions options);

  /** Power the player's held adaptations use, and the most they may use. */
  int power();

  /** The most power the player's adaptations may use; see {@link #power()}. */
  int maxPower();

  /** Merges two of the player's skills into a mutation that {@code lead} leads. */
  @ApiStatus.Experimental
  MutationResult mutate(Id lead, Id partner);

  /** Splits the mutation {@code skill} is part of and refunds its adaptations. */
  @ApiStatus.Experimental
  MutationResult unmutate(Id skill);

  /** Every mutation the player holds, as lead and partner. */
  @ApiStatus.Experimental
  List<SkillPair> mutations();

  /** The mutation a skill is merged into for this player. */
  @ApiStatus.Experimental
  Optional<SkillPair> mutationOf(Id skill);

  /** The player's saved preference choices. */
  PreferenceStore preferences();

  /**
   * The player's live value of an addon's own data section, read from the save or made by the section's declared
   * initial value. It is saved with the player; change it on the player's owning thread or keep it thread-safe.
   *
   * @param section the object {@link art.arcane.adapt.api.addon.ContentRegistrar#playerSection} returned
   * @throws IllegalStateException when no installed addon declared {@code section}, when it was not made by a
   *                               registrar, when the live value has another type, or when the saved value cannot
   *                               be read as the section's type; the message names the section, its owner and the
   *                               caller
   */
  @ApiStatus.Experimental
  <T> T section(PlayerSection<T> section);

  /** A counter such as {@code food.eaten}; milestones watch these. */
  double stat(String stat);

  /** Raises the counter {@code stat} by {@code amount}, which may complete a {@code Milestone}. */
  void addStat(String stat, double amount);

  /** Whether the player holds the advancement {@code key} that the content with id {@code owner} registered. */
  boolean isGranted(Id owner, String key);

  /**
   * Grants the advancement {@code key} that the content with id {@code owner} registered, once; false when it was
   * already granted or advancements are off.
   */
  boolean grant(Id owner, String key);

  /** Multiplies all XP the player earns for {@code durationMillis}. */
  void boost(double multiplier, long durationMillis);

  /**
   * As {@link #boost(double, long)}, held under {@code source}, such as the giving adaptation's id, so
   * {@link #endBoosts(String)} can take it back before it runs out.
   */
  void boost(String source, double multiplier, long durationMillis);

  /** Ends every boost given under {@code source}. */
  void endBoosts(String source);

  /**
   * Saved progress whose provider is not installed: skill lines, adaptations and mutated lines with a missing
   * parent. It is kept untouched and counts toward nothing until the provider returns. An addon's adaptation on
   * another provider's skill is listed as that adaptation alone; the skill's own XP, level, knowledge and adaptations
   * are never part of an orphan.
   */
  List<OrphanedLine> orphans();

  /**
   * Returns the knowledge spent on {@code namespace}'s orphaned adaptations, at their own prices, to the registered
   * lines that hold them, and deletes those adaptations; nothing else in those lines changes. Whole orphaned lines are
   * left for {@link #purgeOrphans(String)}.
   *
   * @return the knowledge returned
   */
  long refundOrphans(String namespace);

  /**
   * Deletes every orphan of {@code namespace} without refund: lines, adaptations, and mutations with a missing
   * parent, which are split first. An orphaned adaptation in a registered line is deleted alone.
   *
   * @return how many orphans were deleted
   */
  int purgeOrphans(String namespace);

  /** Wipes all progress in registered content. Orphans are kept. */
  void reset();

  /**
   * Sets XP to 0 in every registered line, master XP included, and unlearns every adaptation without refund.
   * Orphans are kept.
   */
  void clearXp();

  /** Takes all unspent knowledge from every registered line, each through {@code KnowledgeEvent}. */
  void clearKnowledge();

  /** Unlearns every registered adaptation without refund, each as a forced unlearn. Orphans are kept. */
  void clearAdaptations();

  /** Sets every counter read by {@link #stat(String)} to 0. */
  void clearStats();

  /** Forgets every discovery (biomes, mobs, foods, items, recipes, enchantments, worlds, people), so each earns its first-time XP again. */
  void clearDiscoveries();

  /**
   * Records that the player found {@code key} of this kind; true when it is the first time. The keys are the strings
   * the finder chose, stored with the player's data.
   */
  @ApiStatus.Experimental
  boolean discover(DiscoveryKind kind, String key);

  /** Records that the player entered the chunk; true when they had never entered it before. */
  @ApiStatus.Experimental
  boolean discoverChunk(UUID world, int chunkX, int chunkZ);

  /**
   * Takes {@code cost} food points from the player, saturation first, when food and saturation together exceed
   * {@code minimumFood} by more than {@code cost}; false and nothing taken otherwise.
   */
  @ApiStatus.Experimental
  boolean consumeFood(double cost, int minimumFood);

  /** Takes {@code cost} food points from the player, saturation first, as far as they have any. */
  @ApiStatus.Experimental
  void chargeFood(double cost);

  /**
   * Asks Adapt to save the player soon, after a change to a {@link #section(PlayerSection) section} that the
   * methods here do not see. Requests close together are saved once.
   */
  @ApiStatus.Experimental
  void requestSave();

  /** Saves the player now, for a change that must not be lost if the server stops before the next save. */
  @ApiStatus.Experimental
  void saveNow();

  /**
   * Saves the player and completes once their data as of this call is stored, for work that must not go on before
   * it is; completes exceptionally when the data cannot be saved now, for example while it is being handed over.
   */
  @ApiStatus.Experimental
  CompletableFuture<Void> checkpoint();

  /**
   * Deletes everything Adapt stores for the player and starts them over, as {@code /adapt reset} does. The new data
   * may load a moment later.
   */
  @ApiStatus.Experimental
  void erase();
}
