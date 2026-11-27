package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.engine.ability.EngineAbilityCharge;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.AdaptationRequirement;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.internal.AdaptationRuntime;
import art.arcane.adapt.api.internal.ComponentHooks;
import art.arcane.adapt.api.internal.LevelGate;
import art.arcane.adapt.api.player.PlayerDirectory;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.client.view.ViewIcons;
import art.arcane.adapt.engine.advancement.AdaptAdvancement;
import art.arcane.adapt.engine.advancement.AdvancementTrees;
import art.arcane.adapt.engine.advancement.ToastQuips;
import art.arcane.adapt.engine.attribute.AdaptAttributeService;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.upgrade.Lending;
import art.arcane.adapt.engine.mutation.blocks.MutatedText;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.engine.mutation.upgrade.UpgradeCaches;
import art.arcane.adapt.engine.player.PlayerApi;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.preference.PreferenceConfirmation;
import art.arcane.adapt.engine.protection.Protectors;
import art.arcane.adapt.engine.protection.WorldPolicyLatencyTelemetry;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.AdaptStatTracker;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.Cancellable;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityKnockbackEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.volmlib.util.format.Form;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

/** The engine side of one adaptation: its config, ticks, levels, costs, protection checks and display text. */
public final class AdaptationHost extends ComponentHost implements AdaptationRuntime {
  private static final Id PLAYER_ATTACK = Id.minecraft("player_attack");
  private static final ThreadLocal<UUID> HEALTH_COST_TARGET = new ThreadLocal<>();

  private final SimpleAdaptation<?> adaptation;
  private final Id skillId;
  private volatile @Nullable SkillHost skill;
  private volatile List<AdaptationRequirement> requirements = List.of();
  private long cachedDemandRevision = Long.MIN_VALUE;
  private boolean cachedDemand;
  private final List<AdaptationOwnerPulse.Registration> ownerPulses = new CopyOnWriteArrayList<>();

  public AdaptationHost(SimpleAdaptation<?> adaptation, ComponentHooks hooks, String owner, AdaptCore core,
                        Id skillId, @Nullable SkillHost skill) {
    super(adaptation, hooks, owner, core, "adaptations", "adaptations", 1000);
    this.adaptation = adaptation;
    this.skillId = skillId;
    this.skill = skill;
  }

  /** The host of {@code adaptation}; throws for one no running engine attached. */
  public static AdaptationHost of(Adaptation<?> adaptation) {
    if (find(adaptation) instanceof AdaptationHost host) {
      return host;
    }
    throw new IllegalStateException(adaptation + " is not attached to a running Adapt engine");
  }

  public SimpleAdaptation<?> adaptation() {
    return adaptation;
  }

  public AdaptationConfig config() {
    return (AdaptationConfig) loadedConfig();
  }

  /** Places the adaptation on the skill that lists it. */
  public void placeOn(SkillHost host) {
    this.skill = host;
  }

  public @Nullable SkillHost skillHost() {
    return skill;
  }

  @Override
  protected String kind() {
    return "adaptation";
  }

  @Override
  protected void configApplied(@Nullable Object previous, Object current) {
    if (current instanceof AdaptationConfig shared) {
      if (shared.setInterval > 0) {
        setInterval(shared.setInterval);
      }
      requirements = parseRequirements(getId(), shared.requires);
    } else {
      requirements = List.of();
    }
    UpgradeCaches.forget();
  }

  @Override
  protected void validateConfig(Object loaded) {
    if (loaded instanceof AdaptationConfig shared) {
      PlayerPreferences.validate(adaptation, shared);
    }
  }

  @Override
  protected void onConfigHotloaded() {
    PlayerPreferences.reconcileOnline(adaptation);
  }

  /** The requirements written in {@code entries}; a malformed entry is reported and left out. */
  public static List<AdaptationRequirement> parseRequirements(Id owner, @Nullable List<String> entries) {
    if (entries == null || entries.isEmpty()) {
      return List.of();
    }
    List<AdaptationRequirement> parsed = new ArrayList<>(entries.size());
    for (String entry : entries) {
      AdaptationRequirement.parse(entry, owner.namespace()).ifPresentOrElse(parsed::add,
          () -> AdaptEnvironment.warn("Ignored malformed requirement \"" + entry + "\" in "
              + AdaptEnvironment.contentConfigPath("adaptations", owner) + "; use adaptationId"
              + AdaptationRequirement.LEVEL_SEPARATOR + "level with a level of at least 1."));
    }
    return List.copyOf(parsed);
  }

  @Override
  public void unregister() {
    super.unregister();
    for (AdaptationOwnerPulse.Registration pulse : ownerPulses) {
      pulse.unregister();
    }
    ownerPulses.clear();
    AdaptAttributeService.onAdaptationUnregistered(getId().toString());
  }

  /** The key the adaptation's messages sit under, {@code adapt.<path>} for a built-in. */
  public String textKey() {
    return adaptation.getLocalizationKey();
  }

  @Override
  public void pulseOwners(LongSupplier cadenceMillis, Consumer<PlayerRef> pulse) {
    ownerPulses.add(AdaptationOwnerPulse.register(adaptation, cadenceMillis, pulse::accept));
  }

  @Override
  public void pulseOwners(LongSupplier cadenceMillis, BooleanSupplier extraDemand, Predicate<UUID> interest,
                          Consumer<PlayerRef> pulse) {
    ownerPulses.add(AdaptationOwnerPulse.register(adaptation, cadenceMillis, extraDemand, interest::test,
        pulse::accept));
  }

  @Override
  public boolean confirm(PlayerRef player, String action, @Nullable ItemRef[] items) {
    return PreferenceConfirmation.confirm(adaptation, player, action, items);
  }

  @Override
  public @Nullable Skill<?> skill() {
    SkillHost placed = skill;
    return placed == null ? null : placed.skill();
  }

  @Override
  public Id skillId() {
    return skillId;
  }

  @Override
  public List<AdaptationRequirement> requirements() {
    if (getConfigurationClass() == null) {
      return List.of();
    }
    loadedConfig();
    return requirements;
  }

  @Override
  public boolean hasTickDemand() {
    return adaptation.hasTickDemand();
  }

  @Override
  public boolean hasOnlineLearner() {
    AdaptServer server = core().getAdaptServer();
    if (server == null) {
      return true;
    }
    synchronized (this) {
      long revision = server.getLearnerIndexRevision();
      if (revision != cachedDemandRevision) {
        cachedDemand = server.hasOnlineLearner(getId().toString());
        cachedDemandRevision = revision;
      }
      return cachedDemand;
    }
  }

  @Override
  public int level(@Nullable PlayerRef player) {
    if (player == null) {
      return 0;
    }
    int own = AdaptationRuntimeGuards.getLevel(adaptation, player);
    return Lending.fusedLevel(adaptation, skill, own, (Adaptation<?> upgrade) -> upgrade.getLevel(player));
  }

  @Override
  public int level(@Nullable PlayerProfile profile) {
    return profile == null ? 0 : level(profile.player());
  }

  /** The level {@code player}'s loaded record holds. */
  public int level(@Nullable AdaptPlayer player) {
    int own = AdaptationRuntimeGuards.getLevel(adaptation, player);
    return Lending.fusedLevel(adaptation, skill, own, (Adaptation<?> upgrade) -> of(upgrade).level(player));
  }

  @Override
  public int activeLevel(@Nullable PlayerRef player, LevelGate gate, @Nullable Loc location,
                         @Nullable EntityRef target, @Nullable Predicate<PlayerRef> requirement,
                         boolean survivalOnly) {
    if (player == null) {
      return 0;
    }
    int own = ownActiveLevel(player, gate, location, target, requirement, survivalOnly);
    int resolved = Lending.fusedLevel(adaptation, skill, own,
        (Adaptation<?> upgrade) -> of(upgrade).activeLevel(player, gate, location, target, requirement, survivalOnly));
    return gate == LevelGate.NONE || gate == LevelGate.DEATH
        ? effective(player, resolved, requirement, survivalOnly) : resolved;
  }

  /** The adaptation's own {@code effectiveLevel} over {@code resolved}; a raised level still meets the query's terms. */
  private int effective(PlayerRef player, int resolved, @Nullable Predicate<PlayerRef> requirement,
                        boolean survivalOnly) {
    int level = effectiveLevel(player, resolved);
    if (level == resolved) {
      return resolved;
    }
    if (level <= 0 || (requirement != null && !requirement.test(player))
        || (survivalOnly && player.gameMode() != GameMode.SURVIVAL)) {
      return 0;
    }
    return level;
  }

  /** {@code player}'s active level of the adaptation itself, without what a fusing upgrade lends it. */
  @Override
  public int ownActiveLevel(@Nullable PlayerRef player) {
    return player == null ? 0 : ownActiveLevel(player, LevelGate.NONE, null, null, null, false);
  }

  private int ownActiveLevel(PlayerRef player, LevelGate gate, @Nullable Loc location, @Nullable EntityRef target,
                             @Nullable Predicate<PlayerRef> requirement, boolean survivalOnly) {
    return switch (gate) {
      case INTERACT -> located(location, AdaptationRuntimeGuards.resolveInteractContext(adaptation, player, location,
          requirement, survivalOnly), player, requirement, survivalOnly);
      case BLOCK_BREAK -> located(location, AdaptationRuntimeGuards.resolveBlockBreakContext(adaptation, player,
          location, requirement, survivalOnly), player, requirement, survivalOnly);
      case BLOCK_PLACE -> located(location, AdaptationRuntimeGuards.resolveBlockPlaceContext(adaptation, player,
          location, requirement, survivalOnly), player, requirement, survivalOnly);
      case INTERACT_BREAK -> located(location, AdaptationRuntimeGuards.resolveInteractBreakContext(adaptation, player,
          location, requirement, survivalOnly), player, requirement, survivalOnly);
      case DAMAGE -> filtered(target == null ? AdaptationRuntimeGuards.getActiveLevel(adaptation, player)
          : AdaptationRuntimeGuards.getActiveDamageLevel(adaptation, player, target), player, requirement,
          survivalOnly);
      case DEATH -> filtered(AdaptationRuntimeGuards.getActiveDeathLevel(adaptation, player), player, requirement,
          survivalOnly);
      case NONE -> plain(player, requirement, survivalOnly);
    };
  }

  private int located(@Nullable Loc location, Adaptation.@Nullable BlockActionContext context, PlayerRef player,
                      @Nullable Predicate<PlayerRef> requirement, boolean survivalOnly) {
    if (location == null) {
      return plain(player, requirement, survivalOnly);
    }
    return context == null ? 0 : context.level();
  }

  private int plain(PlayerRef player, @Nullable Predicate<PlayerRef> requirement, boolean survivalOnly) {
    if (survivalOnly) {
      return requirement == null ? AdaptationRuntimeGuards.getActiveSurvivalLevel(adaptation, player)
          : AdaptationRuntimeGuards.getActiveSurvivalLevel(adaptation, player, requirement);
    }
    return requirement == null ? AdaptationRuntimeGuards.getActiveLevel(adaptation, player)
        : AdaptationRuntimeGuards.getActiveLevel(adaptation, player, requirement);
  }

  private int filtered(int level, PlayerRef player, @Nullable Predicate<PlayerRef> requirement,
                       boolean survivalOnly) {
    if (level <= 0 || (requirement != null && !requirement.test(player))) {
      return 0;
    }
    if (survivalOnly && AdaptationRuntimeGuards.getActiveSurvivalLevel(adaptation, player) <= 0) {
      return 0;
    }
    return level;
  }

  /** The engine records of the online players who learned this adaptation. */
  public List<AdaptPlayer> learnedPlayers(long now) {
    return AdaptationRuntimeGuards.learnedCandidates(adaptation, now);
  }

  @Override
  public List<PlayerProfile> learnedCandidates(long now) {
    PlayerDirectory directory = PlayerApi.directory();
    List<PlayerProfile> profiles = new ArrayList<>();
    for (AdaptPlayer player : learnedPlayers(now)) {
      directory.find(player.getPlayer().uuid()).ifPresent(profiles::add);
    }
    return List.copyOf(profiles);
  }

  @Override
  public void withPlayerThread(PlayerRef player, @Nullable Cancellable cancellable, Runnable action) {
    if (cancellable == null) {
      AdaptationRuntimeGuards.withPlayerThread(adaptation, player, action);
    } else {
      AdaptationRuntimeGuards.withPlayerThread(adaptation, player, cancellable, action);
    }
  }

  @Override
  public void withAdaptedPlayer(PlayerRef player, @Nullable Cancellable cancellable, Runnable action) {
    if (cancellable == null) {
      AdaptationRuntimeGuards.withAdaptedPlayer(adaptation, player, action);
    } else {
      AdaptationRuntimeGuards.withAdaptedPlayer(adaptation, player, cancellable, action);
    }
  }

  @Override
  public void withActiveLevel(PlayerRef player, @Nullable Cancellable cancellable, IntConsumer action) {
    if (cancellable == null) {
      AdaptationRuntimeGuards.withActiveLevel(adaptation, player, action);
    } else {
      AdaptationRuntimeGuards.withActiveLevel(adaptation, player, cancellable, action);
    }
  }

  @Override
  public <E extends Enum<E>> E preference(@Nullable PlayerRef player, PlayerPreference<E> preference) {
    AdaptPlayer adaptPlayer = readyPlayer(player);
    return PlayerPreferences.resolve(adaptation, adaptPlayer == null ? null : adaptPlayer.getData(),
        level(player), preference);
  }

  @Override
  public <E extends Enum<E>> E preferenceFor(int level, @Nullable String saved, PlayerPreference<E> preference) {
    return PlayerPreferences.resolveSaved(adaptation, saved, level, preference);
  }

  @Override
  public boolean isPlayerEnabled(@Nullable PlayerRef player) {
    AdaptPlayer adaptPlayer = readyPlayer(player);
    return adaptPlayer != null
        && PlayerPreferences.isEnabled(adaptation, adaptPlayer.getData(), PlayerPreferences.editLevel(adaptation,
        adaptPlayer));
  }

  @Override
  public void xp(PlayerRef player, @Nullable Loc at, double amount, @Nullable String rewardKey, boolean silent) {
    if (!isPlayerEnabled(player) || !Double.isFinite(amount) || amount <= 0D) {
      return;
    }
    double payable = adaptation.payableXp(player, amount);
    if (payable <= 0D) {
      return;
    }
    double baseline = AdaptationRuntimeGuards.claimUsageBaselineXp(adaptation, player, level(player));
    String key = SkillHost.rewardKey(AdaptationRuntimeGuards.adaptationRewardKey(adaptation, rewardKey));
    Skill<?> owner = adaptation.getSkill();
    if (silent) {
      owner.xpSilent(player, payable + baseline, key);
    } else {
      owner.xp(player, at == null ? player.location() : at, payable + baseline, key);
    }
  }

  @Override
  public void awardUsageBaselineXp(PlayerRef player, int level) {
    AdaptationRuntimeGuards.awardUsageBaselineXp(adaptation, player, level);
  }

  @Override
  public <F> @Nullable F storage(PlayerRef player, String key, @Nullable F fallback) {
    return AdaptationRuntimeGuards.getStorage(adaptation, player, key, fallback);
  }

  @Override
  public boolean setStorage(PlayerRef player, String key, @Nullable Object value) {
    return AdaptationRuntimeGuards.setStorage(adaptation, player, key, value);
  }

  @Override
  public boolean canUse(PlayerRef player) {
    return AdaptationRuntimeGuards.canUse(adaptation, player);
  }

  public boolean canUse(AdaptPlayer player) {
    return AdaptationRuntimeGuards.canUse(adaptation, player);
  }

  @Override
  public boolean hasUsePermission(PlayerRef player) {
    return AdaptationRuntimeGuards.hasUsePermission(adaptation, player, adaptation);
  }

  public Set<String> getProtectorNames() {
    return AdaptationRuntimeGuards.getProtectorNames(adaptation);
  }

  @Override
  public boolean allows(ProtectionCheck check, PlayerRef player, @Nullable Loc location, @Nullable EntityRef victim) {
    Loc at = location != null ? location : victim != null ? victim.location() : player.location();
    long start = System.nanoTime();
    try {
      Set<String> protectors = getProtectorNames();
      return protectors.isEmpty() || Protectors.allows(protectors, check, player, at, victim, getId());
    } finally {
      WorldPolicyLatencyTelemetry.recordNanos(System.nanoTime() - start);
    }
  }

  @Override
  public boolean canDamageTarget(PlayerRef attacker, EntityRef target) {
    return AdaptationRuntimeGuards.canDamageTarget(adaptation, attacker, target);
  }

  @Override
  public boolean isProtectedFriendly(@Nullable PlayerRef actor, EntityRef target) {
    return AdaptationRuntimeGuards.isProtectedFriendly(actor, target);
  }

  @Override
  public boolean isProtectedFriendlyOwned(@Nullable UUID actor, EntityRef target) {
    return AdaptationRuntimeGuards.isProtectedFriendlyOwned(actor, target);
  }

  public boolean hasUsageConflict(PlayerRef player) {
    return AdaptationRuntimeGuards.hasUsageConflict(adaptation, player);
  }

  @Override
  public Adaptation.@Nullable MeleeContext meleeContext(EntityDamageByEntityEvent event,
                                                         @Nullable Predicate<ItemRef> mainHand) {
    return AdaptationRuntimeGuards.resolveMeleeContext(adaptation, event, mainHand);
  }

  @Override
  public Adaptation.@Nullable AttackContext attackContext(EntityDamageByEntityEvent event,
                                                           @Nullable Predicate<ItemRef> mainHand) {
    return AdaptationRuntimeGuards.resolveAttackContext(adaptation, event, mainHand);
  }

  @Override
  public Adaptation.@Nullable ProjectileContext projectileContext(EntityDamageByEntityEvent event,
                                                                   @Nullable Predicate<ProjectileRef> projectile) {
    return AdaptationRuntimeGuards.resolveProjectileContext(adaptation, event, projectile);
  }

  @Override
  public boolean charge(PlayerRef player, String costKey, AbilityCostKind kind, @Nullable ItemRef unit, int amount,
                        AbilityDefaultCost defaultCost) {
    return AbilityApiBridge.charge(adaptation, player, costKey, kind, unit, amount,
        defaultCost == null ? AbilityDefaultCost.NONE : defaultCost);
  }

  @Override
  public AbilityCharge chargeDeferred(PlayerRef player, String costKey, AbilityCostKind kind, @Nullable ItemRef unit,
                                      int amount, AbilityDefaultCost defaultCost) {
    AbilityDefaultCost cost = defaultCost == null ? AbilityDefaultCost.NONE : defaultCost;
    return switch (kind) {
      case ITEM, VANILLA_EXPERIENCE -> AbilityApiBridge.chargeDeferred(adaptation, player, costKey, kind, unit, amount,
          cost);
      case HUNGER, HEALTH, DURABILITY -> immediate(charge(player, costKey, kind, unit, amount, cost));
    };
  }

  private static AbilityCharge immediate(boolean allowed) {
    return new EngineAbilityCharge(UUID.randomUUID(), allowed ? AbilityOutcome.ALLOWED_DEFAULT
        : AbilityOutcome.DENIED_INSUFFICIENT, false, "", "", List.of());
  }

  @Override
  public boolean settle(UUID activationId) {
    return AbilityApiBridge.settle(activationId);
  }

  @Override
  public boolean refund(UUID activationId, AbilityRefundReason reason) {
    return AbilityApiBridge.refund(activationId, reason);
  }

  /** Damages {@code player} as an ability's health cost, without the knockback a hit would add. */
  @Override
  public void applyPlayerDamage(PlayerRef player, double amount) {
    UUID previous = HEALTH_COST_TARGET.get();
    HEALTH_COST_TARGET.set(player.uuid());
    try {
      player.damage(amount, PLAYER_ATTACK);
    } finally {
      if (previous == null) {
        HEALTH_COST_TARGET.remove();
      } else {
        HEALTH_COST_TARGET.set(previous);
      }
    }
  }

  /** Cancels the knockback of the damage {@link #applyPlayerDamage} deals. */
  public static final class PlayerDamageListener {
    @Handle(priority = EventPriority.HIGHEST)
    public void on(EntityKnockbackEvent event) {
      UUID target = HEALTH_COST_TARGET.get();
      if (target != null
          && event.cause() == EntityKnockbackEvent.Cause.DAMAGE
          && target.equals(event.entity().uuid())) {
        event.setCancelled(true);
      }
    }
  }

  @Override
  public Cooldowns cooldowns() {
    return PlayerStateRegistry.newCooldowns(adaptation);
  }

  @Override
  public boolean particlesEnabled() {
    return AdaptationGuiSupport.areParticlesEnabled(adaptation, super.particlesEnabled());
  }

  @Override
  public boolean soundsEnabled() {
    return AdaptationGuiSupport.areSoundsEnabled(adaptation, super.soundsEnabled());
  }

  @Override
  public @Nullable String localizedName() {
    if (adaptation instanceof MutationBlock<?> block) {
      return AdaptLanguage.render(MutatedText.blockName(MutatedText.VIEWER, block.getSkill(), block.getSlot()));
    }
    String resolved = SkillHost.optionalText(adaptation.getLocalizationKey() + ".name");
    return resolved.isEmpty() ? null : resolved;
  }

  @Override
  public @Nullable String localizedShortName() {
    if (adaptation instanceof MutationAdaptation mutated) {
      return mutated.mutatedShortName();
    }
    String resolved = SkillHost.optionalText(adaptation.getLocalizationKey() + ".short_name");
    return resolved.isEmpty() ? null : resolved;
  }

  @Override
  public String description() {
    if (adaptation instanceof MutationBlock<?> block) {
      return AdaptLanguage.render(MutatedText.phrase(MutatedText.VIEWER, block.getSkill(), block.getSlot()));
    }
    String resolved = SkillHost.optionalText(adaptation.getLocalizationKey() + ".description");
    return resolved.isEmpty() ? AdaptLanguage.text(RuntimeMessages.NO_DESCRIPTION_PROVIDED) : resolved;
  }

  /** The name the hooks give, in the skill colour, before any level suffix. */
  private String baseName() {
    String shown = hooks().localizedName();
    if (shown == null) {
      return AdaptationGuiSupport.getDisplayName(adaptation);
    }
    return C.RESET + "" + C.BOLD + skillColor() + shown;
  }

  private String skillColor() {
    SkillHost placed = skill;
    return placed == null ? C.WHITE.toString() : placed.getColor().toString();
  }

  @Override
  public String displayName(int level) {
    return level <= 0 ? baseName() : AdaptationGuiSupport.getDisplayName(adaptation, level);
  }

  @Override
  public String shortName(int level) {
    if (level > 0) {
      return AdaptationGuiSupport.getShortName(adaptation, level);
    }
    String shown = localizedShortName();
    return shown == null ? adaptation.getDisplayName() : C.RESET + "" + C.BOLD + skillColor() + shown;
  }

  public String getDisplayNameNoRoman(int level) {
    return AdaptationGuiSupport.getDisplayNameNoRoman(adaptation, level);
  }

  @Override
  public String statLine(String prefix, Object value, String labelFormat, String label) {
    String shown = value instanceof Double number ? Form.f(number, 2)
        : value instanceof Float number ? Form.f(number, 2) : String.valueOf(value);
    return AdaptLanguage.text(RuntimeMessages.STAT_LORE, trusted("prefix", prefix), trusted("value", shown),
        trusted("separator", labelFormat + " "), trusted("label", label));
  }

  @Override
  public List<AdaptStatTracker> getStatTrackers() {
    return SkillHost.statTrackers(getId(), adaptation.getMilestones());
  }

  @Override
  public AdaptAdvancement buildAdvancements() {
    List<AdaptAdvancement> children = new ArrayList<>();
    for (art.arcane.adapt.api.advancement.AdaptAdvancement declared : adaptation.getAdvancements()) {
      children.add(AdvancementTrees.of(getId(), declared));
    }
    return AdaptAdvancement.builder()
        .key(AdaptAdvancement.adaptationKey(getId()))
        .title(C.WHITE + "[     " + adaptation.getDisplayName() + C.WHITE + "     ]")
        .description(AdaptLanguage.text(
            RuntimeMessages.ADVANCEMENT_UNLOCK,
            trusted("description", adaptation.getDescription()),
            trusted("instruction", AdaptLanguage.text(SnippetsMessages.GUI_UNLOCK_THIS_BY_CLICKING)),
            untrusted("block", AdaptConfig.get().adaptActivatorBlockName)
        ))
        .toastBody(ToastQuips.unlockLine(adaptation))
        .icon(adaptation.getIcon().item())
        .clientIcon(ViewIcons.adaptation(adaptation))
        .children(children)
        .visibility(AdvancementVisibility.VANILLA)
        .build();
  }

  @Override
  public CustomModel getModel() {
    return AdaptationGuiSupport.getModel(adaptation);
  }

  public CustomModel getModel(int level) {
    return AdaptationGuiSupport.getModel(adaptation, level);
  }

  @Override
  public boolean openGui(PlayerRef player, boolean checkPermissions) {
    return AdaptationGuiSupport.openGui(adaptation, player, checkPermissions);
  }

  @Override
  public void openGui(PlayerRef player) {
    AdaptationGuiSupport.openGui(adaptation, player);
  }

  @Override
  public void openGui(PlayerRef player, int page) {
    AdaptationGuiSupport.openGui(adaptation, player, page);
  }

  public boolean isPermanentLearnConfirmationPending(PlayerRef player, int level) {
    return AdaptationGuiSupport.isPermanentLearnConfirmationPending(player, adaptation, level);
  }

  public boolean consumePermanentLearnConfirmation(PlayerRef player, int level) {
    return AdaptationGuiSupport.consumePermanentLearnConfirmation(player, adaptation, level);
  }

  public AdaptationLearningTransaction.Result unlearn(PlayerRef player, int level, boolean force) {
    return AdaptationGuiSupport.unlearn(adaptation, player, level, force);
  }

  /** The knowledge refunded when {@code myLevel} drops to {@code level}. */
  public static int refundCostFor(Adaptation<?> adaptation, int level, int myLevel) {
    if (myLevel <= level) {
      return 0;
    }
    long cost = 0L;
    for (int index = level + 1; index <= myLevel; index++) {
      cost += adaptation.getCostFor(index);
      if (cost >= Integer.MAX_VALUE) {
        return Integer.MAX_VALUE;
      }
    }
    return (int) cost;
  }
}
