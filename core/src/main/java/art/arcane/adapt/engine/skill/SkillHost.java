package art.arcane.adapt.engine.skill;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.advancement.Milestone;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationGroup;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.LearnerPulse;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.SkillConfig;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.internal.ComponentHooks;
import art.arcane.adapt.api.internal.SkillRuntime;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.client.view.ViewIcons;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.engine.advancement.AdaptAdvancement;
import art.arcane.adapt.engine.advancement.AdvancementTrees;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.fx.Fx;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.world.AdaptStatTracker;
import art.arcane.adapt.engine.xp.XP;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.TextBundle;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.MessageKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/** The engine side of one skill: its config, ticks, adaptations, mutation pieces, advancements and display text. */
public final class SkillHost extends ComponentHost implements SkillRuntime {
  private final SimpleSkill<?> skill;
  private final List<Adaptation<?>> adaptations = new CopyOnWriteArrayList<>();
  private final List<Adaptation<?>> upgrades = new CopyOnWriteArrayList<>();
  private final List<MutationBlock<?>> blocks = new CopyOnWriteArrayList<>();
  private final List<MutationBlock<?>> unavailableBlocks = new CopyOnWriteArrayList<>();
  private final List<SkillOwnerPulse.Registration> learnerPulses = new CopyOnWriteArrayList<>();
  private final String textKey;

  public SkillHost(SimpleSkill<?> skill, ComponentHooks hooks, String owner, AdaptCore core) {
    super(skill, hooks, owner, core, "skill", "skills", 50);
    this.skill = skill;
    this.textKey = TextKeys.skill(skill.getId());
  }

  /** The host of {@code skill}; throws for a skill no running engine attached. */
  public static SkillHost of(Skill<?> skill) {
    if (find(skill) instanceof SkillHost host) {
      return host;
    }
    throw new IllegalStateException(skill + " is not attached to a running Adapt engine");
  }

  public SimpleSkill<?> skill() {
    return skill;
  }

  public String getLineKey() {
    return skill instanceof MutatedSkill mutated ? mutated.lineKey() : getId().toString();
  }

  /** The player-data line key of {@code skill}: its id, or a mutated skill's pair key. */
  public static String lineKey(Skill<?> skill) {
    return skill instanceof MutatedSkill mutated ? mutated.lineKey() : skill.getId().toString();
  }

  @Override
  public Object loadedConfig() {
    return skill instanceof MutatedSkill ? MutatedSkill.defaults() : super.loadedConfig();
  }

  public SkillConfig config() {
    return (SkillConfig) loadedConfig();
  }

  @Override
  protected String kind() {
    return "skill";
  }

  @Override
  protected void onRuntimeActivated() {
    super.onRuntimeActivated();
    Tasks.a(() -> {
      if (!isRuntimeRegistered()) {
        return;
      }
      Tasks.attempt(this::loadedConfig);
      ownedAdaptations(skill).forEach(adaptation -> Tasks.attempt(() -> AdaptationHost.of(adaptation).loadedConfig()));
    }, 1);
  }

  @Override
  protected void configApplied(@Nullable Object previous, Object current) {
    if (current instanceof SkillConfig shared && shared.setInterval > 0) {
      setInterval(shared.setInterval);
    }
  }

  @Override
  protected void validateConfig(Object loaded) {
    if (loaded instanceof SkillConfig shared) {
      PlayerPreferences.validate(skill, shared);
    }
  }

  @Override
  protected void onConfigHotloaded() {
    PlayerPreferences.reconcileOnline(skill);
  }

  public void addAdaptation(Adaptation<?> adaptation) {
    adaptations.add(adaptation);
  }

  public void removeAdaptation(Adaptation<?> adaptation) {
    adaptations.remove(adaptation);
  }

  public void addUpgrade(Adaptation<?> upgrade) {
    upgrades.add(upgrade);
  }

  public void addBlock(MutationBlock<?> block, boolean available) {
    (available ? blocks : unavailableBlocks).add(block);
  }

  @Override
  public List<Adaptation<?>> adaptations() {
    return skill instanceof MutatedSkill mutated ? mutated.combinedAdaptations()
        : Collections.unmodifiableList(adaptations);
  }

  public List<Adaptation<?>> getUpgrades() {
    return Collections.unmodifiableList(upgrades);
  }

  @Override
  public List<Adaptation<?>> upgrades() {
    return getUpgrades();
  }

  @Override
  public List<MutationBlock<?>> blocks() {
    return getBlocks();
  }

  public List<MutationBlock<?>> getBlocks() {
    return Collections.unmodifiableList(blocks);
  }

  public List<MutationBlock<?>> getUnavailableBlocks() {
    return Collections.unmodifiableList(unavailableBlocks);
  }

  /** Tears the skill's runtime down, then retires every adaptation, upgrade and block it owns. */
  @Override
  public void unregister() {
    List<Adaptation<?>> owned = List.copyOf(ownedAdaptations(skill));
    super.unregister();
    for (SkillOwnerPulse.Registration pulse : learnerPulses) {
      pulse.unregister();
    }
    learnerPulses.clear();
    for (Adaptation<?> adaptation : owned) {
      ComponentHost host = find(adaptation);
      if (host != null) {
        host.retireComponent();
      }
    }
  }

  @Override
  public void pulseLearners(LongSupplier cadenceMillis, LearnerPulse pulse) {
    learnerPulses.add(SkillOwnerPulse.register(skill, cadenceMillis,
        (adaptPlayer, player, elapsedMillis, cadence) -> pulse.pulse(player, elapsedMillis)));
  }

  /**
   * The skill's adaptations, then its upgrades and mutation blocks; for a mutated skill, the pair's own adaptations
   * and signatures built so far, never the parents' upgrades it lists.
   */
  public static List<Adaptation<?>> ownedAdaptations(Skill<?> skill) {
    if (skill instanceof MutatedSkill mutated) {
      return mutated.builtParts();
    }
    SkillHost host = find(skill) instanceof SkillHost found ? found : null;
    if (host == null || (host.upgrades.isEmpty() && host.blocks.isEmpty())) {
      return skill.getAdaptations();
    }
    List<Adaptation<?>> owned = new ArrayList<>(skill.getAdaptations().size() + 9);
    owned.addAll(skill.getAdaptations());
    owned.addAll(host.upgrades);
    owned.addAll(host.blocks);
    return owned;
  }

  public static boolean owns(Skill<?> skill, Adaptation<?> adaptation) {
    return ownedAdaptations(skill).contains(adaptation);
  }

  /** The ids of each group's adaptations, one row per group. */
  public static List<List<String>> adaptationRowGroups(Skill<?> skill) {
    if (skill instanceof MutatedSkill mutated) {
      return mutated.rowGroups();
    }
    List<List<String>> rows = new ArrayList<>();
    for (AdaptationGroup group : skill.getAdaptationGroups()) {
      List<String> ids = new ArrayList<>();
      for (Id id : group.adaptations()) {
        ids.add(id.toString());
      }
      rows.add(ids);
    }
    return rows;
  }

  @Override
  public List<AdaptStatTracker> getStatTrackers() {
    return statTrackers(getId(), skill.getMilestones());
  }

  /** The trackers of {@code milestones} declared by the content with id {@code owner}. */
  public static List<AdaptStatTracker> statTrackers(Id owner, List<Milestone> milestones) {
    List<AdaptStatTracker> trackers = new ArrayList<>(milestones.size());
    for (Milestone milestone : milestones) {
      trackers.add(AdaptStatTracker.builder()
          .advancement(AdaptAdvancement.contentKey(owner, milestone.advancement()))
          .stat(milestone.stat())
          .goal(milestone.goal())
          .rewardSupplier(milestone.reward())
          .build());
    }
    return trackers;
  }

  @Override
  public AdaptAdvancement buildAdvancements() {
    List<AdaptAdvancement> children = new ArrayList<>();
    for (art.arcane.adapt.api.advancement.AdaptAdvancement declared : skill.getAdvancements()) {
      children.add(AdvancementTrees.of(getId(), declared));
    }
    for (Adaptation<?> adaptation : adaptations) {
      children.add(AdaptationHost.of(adaptation).buildAdvancements());
    }
    return AdaptAdvancement.builder()
        .background(skill.getAdvancementBackground().toString())
        .key(AdaptAdvancement.skillKey(getId()))
        .title(localizedName())
        .description(skill.getDescription())
        .toastBody(quip())
        .icon(skill.getIcon().item())
        .model(getModel())
        .clientIcon(ViewIcons.skill(skill))
        .children(children)
        .visibility(AdvancementVisibility.HIDDEN)
        .build();
  }

  /** The skill's discovery toast line, or empty when it has none. */
  public String quip() {
    return optionalText(textKey + ".quip");
  }

  /** The skill's unlock toast lines: {@code unlock_quip.<n>}, or the built-in bundle's {@code unlock_quip_<n>}. */
  public List<String> unlockQuips() {
    List<String> quips = new ArrayList<>();
    String separator = isBuiltIn() ? "_" : ".";
    for (int index = 1; ; index++) {
      String line = optionalText(textKey + ".unlock_quip" + separator + index);
      if (line.isEmpty()) {
        return quips;
      }
      quips.add(line);
    }
  }

  @Override
  public CustomModel getModel() {
    return CustomModel.get(skill.getIcon().item(), "skill", getLineKey());
  }

  /** The legacy chat colour nearest to the skill colour, for text that cannot carry an RGB colour. */
  public C getColor() {
    return nearest(skill.getColor());
  }

  /** The colour code that starts the skill's name: the legacy code when the colour is one, else an RGB code. */
  public String colorPrefix() {
    Rgb rgb = skill.getColor();
    C nearest = nearest(rgb);
    String hex = nearest.hex();
    if (hex != null && hex.length() == 7 && Integer.parseInt(hex.substring(1), 16) == (rgb.packed() & 0xFFFFFF)) {
      return nearest.toString();
    }
    return C.translateAlternateColorCodes('&', "&#" + String.format("%06x", rgb.packed() & 0xFFFFFF));
  }

  public void knowledge(PlayerRef player, long knowledge) {
    SkillRuntimeGuards.grantKnowledge(skill, player, knowledge);
  }

  @Override
  public boolean openGui(PlayerRef player, boolean checkPermissions) {
    if (checkPermissions && !hasUsePermission(player)) {
      return false;
    }
    openGui(player, 0);
    return true;
  }

  @Override
  public void openGui(PlayerRef player) {
    openGui(player, 0);
  }

  @Override
  public void openGui(PlayerRef player, int page) {
    SkillGuiSupport.openGui(skill, player, page);
  }

  @Override
  public Cooldowns cooldowns() {
    return PlayerStateRegistry.newCooldowns();
  }

  @Override
  public boolean particlesEnabled() {
    return SkillGuiSupport.areParticlesEnabled(skill, super.particlesEnabled());
  }

  @Override
  public boolean soundsEnabled() {
    return SkillGuiSupport.areSoundsEnabled(skill, super.soundsEnabled());
  }

  @Override
  public boolean hasUsePermission(PlayerRef player) {
    if (skill instanceof MutatedSkill mutated) {
      return mutated.leadAllows(player);
    }
    return SkillRuntimeGuards.hasUsePermission(player, skill);
  }

  @Override
  public boolean shouldSkipPlayer(PlayerRef player) {
    return SkillRuntimeGuards.shouldSkipPlayer(skill, player);
  }

  @Override
  public void withPlayer(PlayerRef player, @Nullable Cancellable cancellable, Runnable action) {
    if (cancellable == null) {
      SkillRuntimeGuards.withPlayer(skill, player, action);
    } else {
      SkillRuntimeGuards.withPlayer(skill, player, cancellable, action);
    }
  }

  @Override
  public boolean shouldSkipWorld(WorldRef world) {
    return SkillRuntimeGuards.shouldSkipWorld(skill, world);
  }

  @Override
  public boolean isWorldBlacklisted(PlayerRef player) {
    return SkillRuntimeGuards.isWorldBlacklisted(player);
  }

  @Override
  public void xp(PlayerRef player, @Nullable Loc at, double amount, @Nullable String rewardKey, boolean silent,
                 boolean visual) {
    String key = rewardKey(rewardKey);
    if (silent && !visual) {
      SkillRuntimeGuards.grantXpSilent(skill, player, amount, key);
    } else {
      SkillRuntimeGuards.grantXp(skill, player, at == null ? player.location() : at, amount, key, silent, true);
    }
  }

  @Override
  public void spatialXp(Loc at, double amount, int radius, long durationMillis) {
    XP.spatialXP(at, skill, amount, radius, durationMillis);
    if (AdaptConfig.get().isUseEnchantmentTableParticleForActiveEffects()) {
      Fx.now(skill, at, FxPriority.AMBIENT).particle(Particles.ENCHANT, 3, 0, 1.7, 0, 0.1, 3);
    }
  }

  @Override
  public String emoji() {
    if (skill instanceof MutatedSkill mutated) {
      return of(mutated.getLead()).emoji();
    }
    return optionalText(textKey + (isBuiltIn() ? ".icon" : ".emoji"));
  }

  @Override
  public String localizedName() {
    if (skill instanceof MutatedSkill mutated) {
      return mutated.mutatedName();
    }
    String localized = optionalText(textKey + ".name");
    return localized.isBlank() ? Form.capitalize(getId().path()) : localized;
  }

  @Override
  public String description() {
    return optionalText(textKey + ".description");
  }

  @Override
  public String displayName(int level) {
    if (skill instanceof MutatedSkill mutated) {
      return mutated.mutatedDisplayName(level);
    }
    if (!skill.isEnabled()) {
      return C.DARK_GRAY + localizedName();
    }
    String name = C.RESET + "" + C.BOLD + colorPrefix() + emoji() + " " + localizedName();
    return level > 0 ? name + C.RESET + " " + C.UNDERLINE + C.WHITE + level + C.RESET : name;
  }

  @Override
  public String shortName() {
    if (!skill.isEnabled()) {
      return C.DARK_GRAY + localizedName();
    }
    return C.RESET + "" + C.BOLD + colorPrefix() + emoji();
  }

  private boolean isBuiltIn() {
    return TextBundle.BUILT_IN.equals(getId().namespace());
  }

  static final int MAX_REWARD_KEY = 160;

  public static @Nullable String rewardKey(@Nullable String key) {
    return key == null || key.length() <= MAX_REWARD_KEY ? key : key.substring(0, MAX_REWARD_KEY);
  }

  /** The text of {@code key} when a bundle declares it, else empty. */
  public static String optionalText(String key) {
    MessageKey declared = AdaptLanguage.declared(key);
    if (declared == null) {
      return "";
    }
    String resolved = AdaptLanguage.text(declared);
    return resolved == null ? "" : resolved;
  }

  /** The legacy chat colour nearest to {@code rgb}. */
  public static C legacyColor(Rgb rgb) {
    return nearest(rgb);
  }

  static C nearest(Rgb rgb) {
    int r = rgb.red();
    int g = rgb.green();
    int b = rgb.blue();
    C best = C.WHITE;
    long bestDistance = Long.MAX_VALUE;
    for (C candidate : C.values()) {
      if (!candidate.isColor()) {
        continue;
      }
      String hex = candidate.hex();
      if (hex == null || hex.length() != 7) {
        continue;
      }
      int packed = Integer.parseInt(hex.substring(1), 16);
      long dr = r - ((packed >> 16) & 0xFF);
      long dg = g - ((packed >> 8) & 0xFF);
      long db = b - (packed & 0xFF);
      long distance = dr * dr + dg * dg + db * db;
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    return best;
  }
}
