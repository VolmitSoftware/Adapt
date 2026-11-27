/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.engine.skill;

import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.item.BrewingRecipe;
import art.arcane.adapt.engine.addon.BuildScope;
import art.arcane.adapt.engine.bridge.ComponentBuilder;
import art.arcane.adapt.engine.component.ComponentHost;
import java.util.Set;

import java.util.HashSet;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Signature;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.client.server.AdaptScreens;
import art.arcane.adapt.engine.adaptation.AdaptationOwnerPulse;
import art.arcane.adapt.engine.adaptation.AdaptationRequirements;
import art.arcane.adapt.engine.adaptation.VelocityBurstRuntime;
import art.arcane.adapt.engine.addon.AddonContent;
import art.arcane.adapt.engine.addon.AddonLifecycle;
import art.arcane.adapt.engine.addon.ExtensionSource;
import art.arcane.adapt.engine.addon.SkillSource;
import art.arcane.adapt.engine.advancement.AdvancementManager;
import art.arcane.adapt.engine.minion.MinionBurden;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.PairKeys;
import art.arcane.adapt.engine.mutation.engine.MutationAdaptation;
import art.arcane.adapt.engine.mutation.engine.MutationEngine;
import art.arcane.adapt.engine.mutation.pair.PairRegistry;
import art.arcane.adapt.engine.potion.BrewingManager;
import art.arcane.adapt.engine.recipe.AdaptRecipeBook;
import art.arcane.adapt.engine.support.PlatformSupport;
import art.arcane.adapt.engine.tick.TickedObject;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.engine.world.PlayerSkillLine;
import art.arcane.adapt.engine.xp.XPMultiplier;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.fx.ParticleSpec;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.protection.ProtectionCheck;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SkillRegistry extends TickedObject {
  private final KMap<Id, Skill<?>> skills = new KMap<>();
  private final Map<SkillPair, MutatedSkill> mutatedSkills = new ConcurrentHashMap<>();
  private final MutationEngine mutationEngine = new MutationEngine();
  private final PairRegistry pairs = new PairRegistry();
  private static final long SLOW_SKILL_REG_MS = 300L;
  private static final int DEFERRED_SKILLS_PER_TICK = 2;
  private static final int STAT_RECONCILIATION_BATCH_SIZE = 32;
  private final KMap<Id, Skill<?>> knownSkills = new KMap<>();
  private final Map<Id, SkillSource> sources = new LinkedHashMap<>();
  private final List<ExtensionSource> extensions = new ArrayList<>();
  private final Map<Id, Id> hosts = new HashMap<>();
  private volatile Set<Id> vetoed = Set.of();
  private static final Id LECTERN = Id.minecraft("lectern");
  private static final Id OBSERVER = Id.minecraft("observer");
  private static final Id BOOKSHELF = Id.minecraft("bookshelf");
  private static final Id EXPERIENCE_BOTTLE = Id.minecraft("experience_bottle");
  private static final Id ENCHANTED_BOOK = Id.minecraft("enchanted_book");
  private final Map<Id, AdaptRecipeBook.Unlock> adaptationRecipeIndex = new ConcurrentHashMap<>();
  private final Deque<Skill<?>> deferredBootstrapRecipeRegistration = new ArrayDeque<>();
  private final Map<Id, DeferredRecipeTransition> deferredRecipeTransitions = new LinkedHashMap<>();
  private final AtomicLong catalogRevision = new AtomicLong();
  private final AtomicBoolean statReconciliationScheduled = new AtomicBoolean();
  private final AtomicLong statReconciliationRevision = new AtomicLong();
  private final AtomicBoolean unregistered = new AtomicBoolean();
  private volatile StatTrackerIndex statTrackerIndex = StatTrackerIndex.empty();
  private volatile AdaptationIndex adaptationIndex;
  private volatile boolean deferredBootstrapRecipeTaskScheduled;
  private volatile boolean bootstrapLoading = true;
  private volatile boolean foliaRecipeRegistrationWaitWarned;
  private volatile boolean runtimeStarted;

  public SkillRegistry() {
    super("registry", UUID.randomUUID() + "-sk", 1250);
    AdaptCore core = AdaptCore.current();
    AddonLifecycle addons = core == null ? null : core.addons();
    if (addons != null) {
      addons.bootstrap(this);
    }
    bootstrapLoading = false;
    rebuildStatTrackerIndex();
    publishPlaceholderCatalog(catalogRevision.get());
    AdaptationRequirements.warnUnresolved(getSkills());
    announceCatalog(catalogRevision.get());
  }

  public synchronized void startRuntime() {
    if (runtimeStarted || unregistered.get()) {
      return;
    }
    runtimeStarted = true;
    for (Skill<?> skill : skills.values()) {
      prepareSkillRuntime(skill);
    }
    activateRuntime();
    mutationEngine.activateRuntime();
    refreshSignatures();
    scheduleDeferredBootstrapRecipeRegistration();
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerExpChangeEvent e) {
    PlayerRef p = e.player();
    AdaptPlayer adaptPlayer = resolveReadyPlayer(p);
    if (adaptPlayer != null && e.amount() > 0) {
      adaptPlayer.refreshRecentXpBoost("vanilla-xp", 0.03, 10000);
    }
  }

  private AdaptPlayer resolveReadyPlayer(PlayerRef player) {
    AdaptServer adaptServer = getServer();
    AdaptPlayer adaptPlayer = adaptServer == null
        ? null
        : adaptServer.getOnlineAdaptPlayer(player.uuid());
    return SkillRuntimeGuards.isReadyRuntimePlayer(player, adaptPlayer) ? adaptPlayer : null;
  }

  private boolean canInteract(PlayerRef player, Loc targetLocation) {
    if (player == null || targetLocation == null) {
      return false;
    }

    return AdaptCore.platform().protection().allows(player, ProtectionCheck.INTERACT, targetLocation, null);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (e.protectionProbe()) {
      return;
    }
    PlayerRef p = e.player();
    AdaptPlayer adaptPlayer = resolveReadyPlayer(p);
    if (adaptPlayer == null) {
      return;
    }

    BlockRef clicked = e.clickedBlock();
    boolean commonConditions = p.isSneaking() && e.action() == InteractAction.RIGHT_CLICK_BLOCK && clicked != null;
    boolean isLectern = commonConditions && clicked.type().equals(LECTERN);
    boolean isObserver = commonConditions && clicked.type().equals(OBSERVER);
    AdaptConfig config = AdaptConfig.get();
    boolean allowVerticalFaces = config.adaptActivatorAllowVerticalFaces;
    BlockFace face = e.clickedFace();
    boolean validActivatorFace = face != null
        && (allowVerticalFaces || (face != BlockFace.UP && face != BlockFace.DOWN));
    boolean isAdaptActivator = validActivatorFace && !p.isSneaking() && e.action() == InteractAction.RIGHT_CLICK_BLOCK
        && clicked != null
        && canInteract(p, clicked.location())
        && clicked.type().equals(activatorBlock())
        && !isBlockItem(p.inventory().mainHand())
        && !isBlockItem(p.inventory().offHand());

    if (isAdaptActivator) {
      Loc at = clicked.location();
      SoundPlayer spw = SoundPlayer.of(clicked.world());
      spw.play(at, Sounds.ITEM_BOOK_PAGE_TURN, 0.5f, 0.72f);
      spw.play(at, Sounds.BLOCK_ENCHANTMENT_TABLE_USE, 0.35f, 0.755f);
      AdaptScreens.openSkills(p);
      e.setCancelled(true);
      Loc burst = at.add(0.5, 1, 0.5);
      spawnParticle(burst, Particles.CRIT, 25);
      spawnParticle(burst, Particles.ENCHANT, 12);
    }

    if (isLectern) {
      ItemRef it = p.inventory().mainHand();
      if (it != null && !it.isEmpty() && !it.data().keys().isEmpty()) {
        e.setCancelled(true);
        playDebug(p);
        if (AdaptConfig.get().isVerbose()) {
          DataHolder data = it.data();
          data.keys().forEach(key -> {
            String value = data.get(DataKey.of(key, DataCodecs.STRING));
            AdaptLog.verbose(key + " = " + (value == null ? "Not a String" : value));
          });
        }
      }
    }

    if (isObserver) {
      ItemRef it = p.inventory().mainHand();
      if (it != null && it.type().equals(EXPERIENCE_BOTTLE)) {
        e.setCancelled(true);
        p.setCooldown(ENCHANTED_BOOK, 3);
        playDebug(p);
        if (!AdaptConfig.get().isVerbose()) {
          return;
        }

        String xv = adaptPlayer.getData().getMultiplier() - 1d > 0 ? "+" + Form.pc(adaptPlayer.getData().getMultiplier() - 1D) : Form.pc(adaptPlayer.getData().getMultiplier() - 1D);
        AdaptLog.verbose("Global" + C.GRAY + ": " + C.GREEN + xv);

        for (XPMultiplier i : adaptPlayer.getData().getMultipliers()) {
          String vv = i.getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier()) : Form.pc(i.getMultiplier());
          AdaptLog.verbose(C.GREEN + "* " + vv + C.GRAY + " for " + Form.duration(i.getGoodFor() - M.ms(), 0));
        }
        for (XPMultiplier i : AdaptCore.get().getAdaptServer().getData().getMultipliers()) {
          String vv = i.getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier()) : Form.pc(i.getMultiplier());
          AdaptLog.verbose(C.GREEN + "* " + vv + C.GRAY + " for " + Form.duration(i.getGoodFor() - M.ms(), 0));
        }

        for (PlayerSkillLine i : adaptPlayer.getData().getSkillLines().v()) {
          Skill<?> s = i.getRawSkill(adaptPlayer);
          if (s == null) {
            continue;
          }
          String v = i.getMultiplier() - adaptPlayer.getData().getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier() - adaptPlayer.getData().getMultiplier()) : Form.pc(i.getMultiplier() - adaptPlayer.getData().getMultiplier());
          AdaptLog.verbose("  " + s.getDisplayName() + C.GRAY + ": " + SkillHost.legacyColor(s.getColor()) + v);
          for (XPMultiplier j : i.getMultipliers()) {
            String vv = j.getMultiplier() > 0 ? "+" + Form.pc(j.getMultiplier()) : Form.pc(j.getMultiplier());
            AdaptLog.verbose("  " + s.getShortName() + C.GRAY + " " + vv + " for "
                + Form.duration(j.getGoodFor() - M.ms(), 0));
          }
        }
      }
    }
  }

  private static Id activatorBlock() {
    String configured = AdaptConfig.get().getAdaptActivatorBlock();
    Id parsed = configured == null ? null : Id.tryParse(configured.trim().toLowerCase(Locale.ROOT));
    return parsed == null ? BOOKSHELF : parsed;
  }

  private static boolean isBlockItem(ItemRef item) {
    if (item == null || item.isEmpty() || item.type().isAir()) {
      return false;
    }
    return AdaptCore.platform().host().registries().exists(RegistryKind.BLOCK, item.type());
  }

  private static void spawnParticle(Loc at, ParticleSpec particle, int count) {
    AdaptCore.platform().fx().particle(at.world(), particle, at.x(), at.y(), at.z(), count, 0, 0, 0, 1.1);
  }

  private void playDebug(PlayerRef p) {
    SoundPlayer sp = SoundPlayer.of(p);
    sp.play(p.location(), Sounds.BLOCK_BELL_RESONATE, 1f, 0.6f);
    sp.play(p.location(), Sounds.BLOCK_BEACON_ACTIVATE, 1f, 0.1f);
    sp.play(p.location(), Sounds.ITEM_BOOK_PAGE_TURN, 1f, 1.6f);
    sp.play(p.location(), Sounds.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);

  }

  /** The enabled registered skill with this id, or null. */
  public Skill<?> getSkill(Id id) {
    return id == null ? null : skills.get(id);
  }

  /**
   * The skill a line key or user input names: a mutation key such as {@code adapt:swords+adapt:stealth} gives the
   * mutated skill, anything else is an id where a bare name is in the {@code adapt} namespace. Null for none.
   */
  public Skill<?> getSkill(String key) {
    if (key == null) {
      return null;
    }
    SkillPair mutation = PairKeys.parse(key);
    return mutation != null ? getMutatedSkill(mutation) : getSkill(Id.tryParseAdapt(key.trim()));
  }

  /** The generated skill for a mutation, built on first use; null when its lead is not a known skill. */
  public MutatedSkill getMutatedSkill(SkillPair mutation) {
    if (mutation == null) {
      return null;
    }
    MutatedSkill known = mutatedSkills.get(mutation);
    if (known != null) {
      return known;
    }
    return mutatedSkills.computeIfAbsent(mutation, ignored -> createMutatedSkill(mutation));
  }

  /** The mutated skills built so far. */
  public List<MutatedSkill> getBuiltMutatedSkills() {
    return List.copyOf(mutatedSkills.values());
  }

  private MutatedSkill createMutatedSkill(SkillPair mutation) {
    return getAnySkill(mutation.lead()) instanceof SimpleSkill<?> lead && getAnySkill(mutation.partner()) != null
        ? (MutatedSkill) BuildScope.run(lead.getOwner(),
            () -> ComponentBuilder.buildSkill(() -> new MutatedSkill(mutation, lead, this::getAnySkill, pairs,
                this::signaturesChanged))) : null;
  }

  /** The registered skill with this id, enabled or not, or null. */
  public Skill<?> getAnySkill(Id id) {
    return id == null ? null : knownSkills.get(id);
  }

  public List<Skill<?>> getSkills() {
    List<Skill<?>> sorted = new ArrayList<>(skills.values());
    sorted.sort(Comparator.comparing(Skill::getId));
    return sorted;
  }

  public List<Skill<?>> getAllSkills() {
    List<Skill<?>> sorted = new ArrayList<>(knownSkills.values());
    sorted.sort(Comparator.comparing(Skill::getId));
    return sorted;
  }

  /** The signatures, texts and names installed addons claimed for skill pairs. */
  public PairRegistry getPairRegistry() {
    return pairs;
  }

  public long getCatalogRevision() {
    return catalogRevision.get();
  }

  public void evaluateStatTrackers(AdaptPlayer player, String stat) {
    StatTrackerIndex index = statTrackerIndex;
    List<StatTrackerIndex.Binding> bindings = index.bindingsFor(stat);
    if (bindings.isEmpty() || !SkillRuntimeGuards.canEvaluateStatTrackers(player)) {
      return;
    }

    SkillRuntimeGuards.evaluateStatTrackers(bindings, player, player.getData().getStat(stat));
  }

  public void reconcileStatTrackers(AdaptPlayer player) {
    if (!SkillRuntimeGuards.canEvaluateStatTrackers(player)) {
      return;
    }

    StatTrackerIndex index = statTrackerIndex;
    for (String stat : index.trackedStats()) {
      SkillRuntimeGuards.evaluateStatTrackers(index.bindingsFor(stat), player, player.getData().getStat(stat));
    }
  }

  public void reconcileStatTrackersForOnlinePlayers() {
    AdaptCore core = AdaptCore.current();
    if (!AdaptConfig.get().isAdvancements()
        || core == null || core.getAdaptServer() == null) {
      return;
    }
    AdvancementManager manager = core.getAdvancementManager();
    if (manager == null || !manager.isCatalogReady(catalogRevision.get())) {
      return;
    }

    statReconciliationRevision.incrementAndGet();
    if (!statReconciliationScheduled.compareAndSet(false, true)) {
      return;
    }

    Tasks.s(this::startStatTrackerReconciliation, 1);
  }

  /**
   * Registers everything one addon declared, or nothing: every skill is built, its declared and extension
   * adaptations are checked against the live registry, and only then is anything committed. Skills of other addons
   * that the addon extends are rebuilt with the new adaptations in the same step. Throws naming the reason and both
   * owners of a clashing id.
   */
  public synchronized void install(AddonContent content) {
    String owner = content.namespace();
    pairs.requireFree(content.pairs());
    for (SkillSource source : content.skills()) {
      SkillSource held = sources.get(source.id());
      if (held != null) {
        throw new IllegalStateException("Skill " + source.id() + " is registered by addon " + held.owner()
            + "; addon " + owner + " cannot register it too");
      }
    }
    Map<Id, SkillSource> nextSources = new LinkedHashMap<>(sources);
    for (SkillSource source : content.skills()) {
      nextSources.put(source.id(), source);
    }
    List<ExtensionSource> nextExtensions = new ArrayList<>(extensions);
    nextExtensions.addAll(content.adaptations());
    Set<Id> targets = new LinkedHashSet<>();
    for (ExtensionSource extension : content.adaptations()) {
      if (sources.containsKey(extension.skill())) {
        targets.add(extension.skill());
      }
    }
    List<Staged> staged = new ArrayList<>();
    try {
      for (SkillSource source : content.skills()) {
        staged.add(stage(source, nextExtensions, null));
      }
      for (Id target : targets) {
        staged.add(stage(sources.get(target), nextExtensions, knownSkills.get(target)));
      }
      rejectDuplicates(staged, nextSources, nextExtensions);
      for (Staged candidate : staged) {
        requireOwnNamespace(candidate, nextExtensions);
      }
    } catch (RuntimeException | Error failure) {
      staged.forEach(Staged::discard);
      throw failure;
    }
    sources.clear();
    sources.putAll(nextSources);
    extensions.clear();
    extensions.addAll(nextExtensions);
    pairs.install(content.pairs());
    for (Staged candidate : staged) {
      if (candidate.previous() == null) {
        commitNew(candidate);
      } else {
        swapIn(candidate.source().id(), candidate.instantiation(), candidate.enabled(), candidate.previous());
      }
    }
    catalogChanged(true);
  }

  /**
   * Removes every skill and extension adaptation {@code owner} registered. Skills of other addons it extended are
   * rebuilt without its adaptations. Player data is untouched.
   */
  public synchronized void retire(String owner) {
    List<Id> removed = new ArrayList<>();
    for (SkillSource source : sources.values()) {
      if (source.owner().equals(owner)) {
        removed.add(source.id());
      }
    }
    Set<Id> targets = new LinkedHashSet<>();
    for (ExtensionSource extension : extensions) {
      if (extension.owner().equals(owner) && !extension.owner().equals(ownerOfSkill(extension.skill()))) {
        targets.add(extension.skill());
      }
    }
    extensions.removeIf(extension -> extension.owner().equals(owner));
    pairs.withdraw(owner);
    for (Id id : removed) {
      sources.remove(id);
      List<Skill<?>> retired = new ArrayList<>(3);
      addRetired(retired, knownSkills.remove(id), null);
      addRetired(retired, skills.remove(id), null);
      DeferredRecipeTransition deferred = deferredRecipeTransitions.remove(id);
      if (deferred != null) {
        deferred.retired().forEach(previous -> addRetired(retired, previous, null));
        addRetired(retired, deferred.replacement(), null);
      }
      for (Skill<?> previous : retired) {
        unregisterRecipes(previous);
        ComponentHost host = ComponentHost.find(previous);
        if (host != null) {
          host.retireComponent();
        }
      }
      hosts.values().removeIf(id::equals);
    }
    mutatedSkills.entrySet().removeIf(entry -> {
      boolean gone = removed.contains(entry.getKey().lead()) || removed.contains(entry.getKey().partner());
      if (gone && entry.getValue() != null) {
        unregisterRecipes(entry.getValue());
        entry.getValue().unregister();
      }
      return gone;
    });
    for (Id target : targets) {
      Skill<?> loaded = knownSkills.get(target);
      if (sources.containsKey(target)) {
        replaceSkillInstance(target, loaded);
      }
    }
    catalogChanged(true);
  }

  /**
   * Treats the listed skills and adaptations as disabled: a vetoed skill leaves the active set, a vetoed adaptation is
   * left off its skill. The skills a change touches are rebuilt from their suppliers.
   */
  public synchronized void veto(Set<Id> blocked) {
    Set<Id> previous = vetoed;
    Set<Id> next = Set.copyOf(blocked);
    vetoed = next;
    Set<Id> affected = new LinkedHashSet<>();
    Set<Id> changed = new HashSet<>(previous);
    changed.addAll(next);
    for (Id id : changed) {
      if (previous.contains(id) == next.contains(id)) {
        continue;
      }
      if (sources.containsKey(id)) {
        affected.add(id);
      } else if (hosts.containsKey(id)) {
        affected.add(hosts.get(id));
      }
    }
    for (Id skill : affected) {
      if (sources.containsKey(skill)) {
        replaceSkillInstance(skill, knownSkills.get(skill));
      }
    }
    if (!affected.isEmpty()) {
      catalogChanged(true);
    }
  }

  public boolean isVetoed(Id id) {
    return id != null && vetoed.contains(id);
  }

  /** Whether a skill or adaptation with this id is registered, enabled, disabled or vetoed. */
  public synchronized boolean isRegistered(Id id) {
    return id != null && (sources.containsKey(id) || hosts.containsKey(id));
  }

  /** The namespace of the addon that registered a skill or adaptation. */
  public synchronized Optional<String> ownerOf(Id id) {
    if (id == null) {
      return Optional.empty();
    }
    SkillSource source = sources.get(id);
    if (source != null) {
      return Optional.of(source.owner());
    }
    Id host = hosts.get(id);
    return host == null ? Optional.empty() : Optional.of(adaptationOwner(id, host, sources, extensions));
  }

  private Staged stage(SkillSource source, List<ExtensionSource> extensionSources, Skill<?> previous) {
    SkillInstantiation instantiation = instantiateSkill(source, extensionSources);
    Skill<?> skill = instantiation.skill();
    try {
      boolean enabled = isActiveEnabled(skill);
      if (previous == null) {
        PlatformSupport.Verdict verdict = PlatformSupport.check(skill.getPlatformNeeds());
        if (!verdict.usable()) {
          PlatformSupport.leaveOut("skill", SkillHost.lineKey(skill), SkillHost.lineKey(skill), verdict);
          enabled = false;
        }
      }
      return new Staged(source, instantiation, enabled, previous);
    } catch (RuntimeException | Error error) {
      instantiation.rollbackRegistrations();
      ComponentHost.of(skill).retireComponent();
      throw error;
    }
  }

  private void commitNew(Staged staged) {
    Skill<?> skill = staged.instantiation().skill();
    Id skillName = skill.getId();
    knownSkills.put(skillName, skill);
    recordHosts(staged.instantiation());
    if (!staged.enabled()) {
      staged.instantiation().rollbackRegistrations();
      ComponentHost.of(skill).retireComponent();
      skills.remove(skillName);
      return;
    }
    staged.instantiation().commitRegistrations();
    skills.put(skillName, skill);
    prepareSkillRuntime(skill);
    if (bootstrapLoading) {
      deferredBootstrapRecipeRegistration.addLast(skill);
    } else {
      registerRecipes(skill);
    }
  }

  private void recordHosts(SkillInstantiation instantiation) {
    Id skill = instantiation.skill().getId();
    hosts.values().removeIf(skill::equals);
    for (Id adaptation : instantiation.adaptations()) {
      hosts.put(adaptation, skill);
    }
  }

  private boolean isActiveEnabled(Skill<?> skill) {
    return skill.isEnabled() && !vetoed.contains(skill.getId());
  }

  private String ownerOfSkill(Id skill) {
    SkillSource source = sources.get(skill);
    return source == null ? null : source.owner();
  }

  public synchronized boolean hotReloadSkillConfig(Id skillName) {
    Id normalized = skillName;
    Skill<?> loaded = knownSkills.get(normalized);
    if (!sources.containsKey(normalized)) {
      AdaptLog.verbose("No registered skill for config hotload: " + skillName);
      return false;
    }
    if (!(loaded instanceof SimpleSkill<?> simpleSkill)) {
      return replaceSkillInstance(normalized, loaded);
    }
    if (!ComponentHost.of(simpleSkill).validateConfigFromDisk()) {
      return false;
    }

    List<AdaptationConfigState> adaptationStates = new ArrayList<>();
    for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(loaded)) {
      if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)) {
        continue;
      }
      if (!ComponentHost.of(simpleAdaptation).validateConfigFromDisk()) {
        return false;
      }
      adaptationStates.add(new AdaptationConfigState(simpleAdaptation, simpleAdaptation.isEnabled()));
    }

    boolean previouslyEnabled = loaded.isEnabled();
    if (!ComponentHost.of(simpleSkill).reloadConfigFromDisk(true)) {
      return false;
    }
    if (previouslyEnabled != loaded.isEnabled()) {
      return replaceSkillInstance(normalized, loaded);
    }

    boolean adaptationLifecycleChanged = false;
    for (AdaptationConfigState state : adaptationStates) {
      if (!ComponentHost.of(state.adaptation()).reloadConfigFromDisk(false)) {
        return false;
      }
      adaptationLifecycleChanged |= state.previouslyEnabled() != state.adaptation().isEnabled();
    }
    if (adaptationLifecycleChanged) {
      return replaceSkillInstance(normalized, loaded);
    }

    for (AdaptationConfigState state : adaptationStates) {
      synchronizeAdaptationRecipes(loaded, state.adaptation(), state.previouslyEnabled());
    }

    catalogChanged(true);
    return true;
  }

  public synchronized boolean hotReloadSkillConfig(Id skillName, String raw, File sourceFile) {
    Id normalized = skillName;
    Skill<?> loaded = knownSkills.get(normalized);
    if (!sources.containsKey(normalized)) {
      AdaptLog.verbose("No registered skill for config hotload: " + skillName);
      return false;
    }
    ReplacementConfigSnapshot snapshot = ReplacementConfigSnapshot.skill(skillName, raw, sourceFile);
    if (!(loaded instanceof SimpleSkill<?> simpleSkill)) {
      return replaceSkillInstance(normalized, loaded, snapshot);
    }

    boolean previouslyEnabled = loaded.isEnabled();
    if (!ComponentHost.of(simpleSkill).reloadConfigSnapshot(raw, sourceFile, true)) {
      return false;
    }
    if (previouslyEnabled != loaded.isEnabled()) {
      return replaceSkillInstance(normalized, loaded, snapshot);
    }

    catalogChanged(true);
    return true;
  }

  public synchronized boolean hotReloadAdaptationConfig(Id adaptationName) {
    if (adaptationName == null) {
      return false;
    }

    for (Skill<?> skill : knownSkills.v()) {
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        if (!adaptation.getId().equals(adaptationName)) {
          continue;
        }
        if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)
            || !ComponentHost.of(simpleAdaptation).validateConfigFromDisk()) {
          return false;
        }
        boolean previouslyEnabled = simpleAdaptation.isEnabled();
        if (!ComponentHost.of(simpleAdaptation).reloadConfigFromDisk(true)) {
          return false;
        }
        if (previouslyEnabled != simpleAdaptation.isEnabled()) {
          Id normalized = skill.getId();
          return sources.containsKey(normalized) && replaceSkillInstance(normalized, skill);
        }
        synchronizeAdaptationRecipes(skill, simpleAdaptation, previouslyEnabled);
        catalogChanged(true);
        return true;
      }
    }
    return false;
  }

  public synchronized boolean hotReloadAdaptationConfig(Id adaptationName, String raw, File sourceFile) {
    if (adaptationName == null) {
      return false;
    }

    for (Skill<?> skill : knownSkills.v()) {
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        if (!adaptation.getId().equals(adaptationName)) {
          continue;
        }
        if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)) {
          return false;
        }
        boolean previouslyEnabled = simpleAdaptation.isEnabled();
        if (!ComponentHost.of(simpleAdaptation).reloadConfigSnapshot(raw, sourceFile, true)) {
          return false;
        }
        if (previouslyEnabled != simpleAdaptation.isEnabled()) {
          Id normalized = skill.getId();
          ReplacementConfigSnapshot snapshot = ReplacementConfigSnapshot.adaptation(
              adaptationName,
              raw,
              sourceFile
          );
          return sources.containsKey(normalized) && replaceSkillInstance(normalized, skill, snapshot);
        }
        synchronizeAdaptationRecipes(skill, simpleAdaptation, previouslyEnabled);
        catalogChanged(true);
        return true;
      }
    }
    return false;
  }

  private boolean replaceSkillInstance(Id normalizedName, Skill<?> previousLoaded) {
    return replaceSkillInstance(normalizedName, previousLoaded, null);
  }

  private boolean replaceSkillInstance(Id normalizedName, Skill<?> previousLoaded, ReplacementConfigSnapshot snapshot) {
    SkillInstantiation instantiation;
    try {
      instantiation = instantiateSkill(sources.get(normalizedName), extensions);
    } catch (RuntimeException | LinkageError failure) {
      AdaptLog.warn("Could not rebuild skill " + normalizedName + ": " + failure.getMessage());
      AdaptLog.error(failure);
      return false;
    }
    Skill<?> replacement = instantiation.skill();
    boolean initialized;
    try {
      initialized = snapshot == null || initializeReplacementConfigs(replacement, snapshot);
    } catch (RuntimeException | Error error) {
      instantiation.rollbackRegistrations();
      ComponentHost.of(replacement).retireComponent();
      throw error;
    }
    if (!initialized) {
      instantiation.rollbackRegistrations();
      ComponentHost.of(replacement).retireComponent();
      return false;
    }

    boolean replacementEnabled;
    try {
      replacementEnabled = isActiveEnabled(replacement);
    } catch (RuntimeException | Error error) {
      instantiation.rollbackRegistrations();
      ComponentHost.of(replacement).retireComponent();
      throw error;
    }
    swapIn(normalizedName, instantiation, replacementEnabled, previousLoaded);
    catalogChanged(true);
    return true;
  }

  private void swapIn(Id normalizedName, SkillInstantiation instantiation, boolean replacementEnabled,
                      Skill<?> previousLoaded) {
    Skill<?> replacement = instantiation.skill();
    Skill<?> previousKnown = knownSkills.put(normalizedName, replacement);
    recordHosts(instantiation);
    Skill<?> previousActive = replacementEnabled
        ? skills.put(normalizedName, replacement)
        : skills.remove(normalizedName);
    if (replacementEnabled) {
      instantiation.commitRegistrations();
    } else {
      instantiation.rollbackRegistrations();
    }
    List<Skill<?>> retired = new ArrayList<>(3);
    addRetired(retired, previousKnown, replacement);
    addRetired(retired, previousLoaded, replacement);
    addRetired(retired, previousActive, replacement);
    DeferredRecipeTransition deferred = deferredRecipeTransitions.remove(normalizedName);
    if (deferred != null) {
      for (Skill<?> previous : deferred.retired()) {
        addRetired(retired, previous, replacement);
      }
      addRetired(retired, deferred.replacement(), replacement);
    }
    boolean deferRecipeTransition = shouldDelayRecipeRegistrationForFolia();
    for (Skill<?> previous : retired) {
      if (!deferRecipeTransition) {
        unregisterRecipes(previous);
      }
      ComponentHost retiring = ComponentHost.find(previous);
      if (retiring != null) {
        retiring.retireComponent();
      }
    }

    if (!replacementEnabled) {
      ComponentHost.of(replacement).retireComponent();
      if (deferRecipeTransition) {
        enqueueDeferredRecipeTransition(normalizedName, retired, replacement);
      }
      return;
    }

    prepareSkillRuntime(replacement);
    if (deferRecipeTransition) {
      enqueueDeferredRecipeTransition(normalizedName, retired, replacement);
    } else {
      registerRecipesNow(replacement);
    }
  }

  private boolean initializeReplacementConfigs(Skill<?> replacement, ReplacementConfigSnapshot snapshot) {
    if (replacement instanceof SimpleSkill<?> simpleSkill) {
      boolean skillLoaded = snapshot.skill()
          ? ComponentHost.of(simpleSkill).reloadConfigSnapshot(snapshot.raw(), snapshot.sourceFile(), false)
          : ComponentHost.of(simpleSkill).reloadConfigFromDiskPassive(false);
      if (!skillLoaded) {
        return false;
      }
    } else if (snapshot.skill()) {
      return false;
    }

    boolean targetAdaptationFound = snapshot.skill();
    for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(replacement)) {
      if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)
          || simpleAdaptation.getConfigurationClass() == null) {
        continue;
      }
      boolean target = !snapshot.skill() && adaptation.getId().equals(snapshot.configName());
      boolean loaded = target
          ? ComponentHost.of(simpleAdaptation).reloadConfigSnapshot(snapshot.raw(), snapshot.sourceFile(), false)
          : ComponentHost.of(simpleAdaptation).reloadConfigFromDiskPassive(false);
      if (!loaded) {
        return false;
      }
      targetAdaptationFound |= target;
    }
    return targetAdaptationFound;
  }

  private void addRetired(List<Skill<?>> retired, Skill<?> candidate, Skill<?> replacement) {
    if (candidate == null || candidate == replacement) {
      return;
    }
    for (Skill<?> existing : retired) {
      if (existing == candidate) {
        return;
      }
    }
    retired.add(candidate);
  }

  private void prepareSkillRuntime(Skill<?> skill) {
    if (runtimeStarted) {
      ComponentHost.of(skill).activateRuntime();
    }
    for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
      if (!adaptation.isEnabled()) {
        ComponentHost.of(adaptation).retireComponent();
      } else if (runtimeStarted) {
        ComponentHost.of(adaptation).activateRuntime();
      }
    }
    if (runtimeStarted) {
      AdaptationOwnerPulse.startRuntime();
      SkillOwnerPulse.startRuntime();
      VelocityBurstRuntime.startRuntime();
      MinionBurden.startRuntime();
    }
  }

  private synchronized void enqueueDeferredRecipeTransition(Id normalizedName, List<Skill<?>> retired,
                                                             Skill<?> replacement) {
    DeferredRecipeTransition existing = deferredRecipeTransitions.get(normalizedName);
    List<Skill<?>> mergedRetired = new ArrayList<>();
    if (existing != null) {
      for (Skill<?> previous : existing.retired()) {
        addRetired(mergedRetired, previous, replacement);
      }
    }
    for (Skill<?> previous : retired) {
      addRetired(mergedRetired, previous, replacement);
    }
    deferredRecipeTransitions.put(
        normalizedName,
        new DeferredRecipeTransition(List.copyOf(mergedRetired), replacement)
    );
    scheduleDeferredBootstrapRecipeRegistration();
  }

  public void synchronizeAdvancementRuntime() {
    synchronizeAdvancementRuntime(catalogRevision.get());
  }

  public boolean isKnownSkill(Id skillName) {
    if (skillName == null) {
      return false;
    }

    return sources.containsKey(skillName);
  }

  /** The enabled adaptation, upgrade, mutation block, mutated adaptation or signature with this id, or null. */
  public Adaptation<?> getAdaptation(Id adaptationId) {
    if (adaptationId == null) {
      return null;
    }
    Adaptation<?> found = adaptationIndex().byId().get(adaptationId);
    if (found != null && !isActive(found.getSkill())) {
      adaptationIndex = null;
      found = adaptationIndex().byId().get(adaptationId);
    }
    if (found != null) {
      return found;
    }
    for (MutatedSkill mutated : mutatedSkills.values()) {
      for (MutationAdaptation adaptation : mutated.getMutationAdaptations()) {
        if (adaptation.getId().equals(adaptationId)) {
          return adaptation;
        }
      }
      for (Signature<?> signature : mutated.getSignatures()) {
        if (signature.getId().equals(adaptationId)) {
          return signature;
        }
      }
    }
    return null;
  }

  /** The namespaces of every enabled skill and adaptation, and {@code adapt}: the ones whose recipes and items are Adapt's. */
  public Set<String> getContentNamespaces() {
    return adaptationIndex().namespaces();
  }

  private AdaptationIndex adaptationIndex() {
    long revision = catalogRevision.get();
    AdaptationIndex index = adaptationIndex;
    if (index != null && index.revision() == revision) {
      return index;
    }
    Map<Id, Adaptation<?>> byId = new HashMap<>();
    Set<String> namespaces = new HashSet<>();
    namespaces.add(Id.ADAPT);
    for (Skill<?> skill : getSkills()) {
      namespaces.add(skill.getId().namespace());
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        byId.putIfAbsent(adaptation.getId(), adaptation);
        namespaces.add(adaptation.getId().namespace());
      }
    }
    index = new AdaptationIndex(revision, Map.copyOf(byId), Set.copyOf(namespaces));
    adaptationIndex = index;
    return index;
  }

  /**
   * Fails when one of the staged skills' adaptation ids is already held by another skill, by another staged skill,
   * or twice by the same skill. The message names the addon of each holder.
   */
  private void rejectDuplicates(List<Staged> staged, Map<Id, SkillSource> nextSources,
                                List<ExtensionSource> nextExtensions) {
    Set<Id> replaced = new HashSet<>();
    for (Staged candidate : staged) {
      replaced.add(candidate.source().id());
    }
    Map<Id, Id> owners = new HashMap<>();
    for (Map.Entry<Id, Id> hosted : hosts.entrySet()) {
      if (!replaced.contains(hosted.getValue())) {
        owners.putIfAbsent(hosted.getKey(), hosted.getValue());
      }
    }
    for (Staged candidate : staged) {
      Id skill = candidate.source().id();
      Set<Id> own = new HashSet<>();
      for (Id adaptation : candidate.instantiation().adaptations()) {
        Id clash = owners.get(adaptation);
        if (clash == null && !own.add(adaptation)) {
          clash = skill;
        }
        if (clash != null) {
          throw new IllegalStateException("Duplicate adaptation id " + adaptation + ": addon "
              + adaptationOwner(adaptation, clash, nextSources, nextExtensions) + " already registers it on skill "
              + clash + ", addon " + adaptationOwner(adaptation, skill, nextSources, nextExtensions)
              + " cannot register it on skill " + skill + " too");
        }
      }
      for (Id adaptation : own) {
        owners.put(adaptation, skill);
      }
    }
  }

  /** An addon may only add adaptations in its own namespace, whichever skill they land on. */
  private void requireOwnNamespace(Staged candidate, List<ExtensionSource> nextExtensions) {
    Id skill = candidate.source().id();
    for (Id adaptation : candidate.instantiation().adaptations()) {
      String owner = adaptationOwner(adaptation, skill, Map.of(skill, candidate.source()), nextExtensions);
      if (!adaptation.namespace().equals(owner)) {
        throw new IllegalStateException("Addon " + owner + " cannot register adaptation " + adaptation
            + " outside its namespace");
      }
    }
  }

  private static String adaptationOwner(Id adaptation, Id host, Map<Id, SkillSource> skillSources,
                                        List<ExtensionSource> extensionSources) {
    for (ExtensionSource extension : extensionSources) {
      if (extension.id().equals(adaptation) && extension.skill().equals(host)) {
        return extension.owner();
      }
    }
    SkillSource source = skillSources.get(host);
    return source == null ? "unknown" : source.owner();
  }

  private boolean isActive(Skill<?> skill) {
    return skill != null && skills.get(skill.getId()) == skill;
  }

  /** Runs every mutation's Graft, Echo, Barter, Attunement and Convergence. */
  public MutationEngine getMutationEngine() {
    return mutationEngine;
  }

  public Adaptation<?> getRequiredAdaptation(Id recipe) {
    if (recipe == null) {
      return null;
    }

    AdaptRecipeBook.Unlock unlock = adaptationRecipeIndex.get(recipe);
    return unlock == null ? null : unlock.adaptation();
  }

  public List<AdaptRecipeBook.Unlock> getRegisteredRecipeUnlocks() {
    List<AdaptRecipeBook.Unlock> unlocks = new ArrayList<>(adaptationRecipeIndex.values());
    unlocks.sort(Comparator.comparing(unlock -> unlock.key().toString()));
    return List.copyOf(unlocks);
  }

  public int getRequiredRecipeLevel(Id recipe) {
    if (recipe == null) {
      return 0;
    }
    AdaptRecipeBook.Unlock unlock = adaptationRecipeIndex.get(recipe);
    return unlock == null ? 0 : unlock.requiredLevel();
  }

  public synchronized boolean isRecipeRegistrationReady() {
    return !deferredBootstrapRecipeTaskScheduled
        && deferredBootstrapRecipeRegistration.isEmpty()
        && deferredRecipeTransitions.isEmpty();
  }

  /**
   * Builds a skill from its supplier, adds the extension adaptations registered on it and leaves vetoed adaptations
   * off. Throws when the supplier builds a skill with another id; nothing stays registered then.
   */
  private SkillInstantiation instantiateSkill(SkillSource source, List<ExtensionSource> extensionSources) {
    AdaptationOwnerPulse.RegistrationBatch adaptationRegistrations = AdaptationOwnerPulse.beginRegistrationBatch();
    SkillOwnerPulse.RegistrationBatch skillRegistrations;
    try {
      skillRegistrations = SkillOwnerPulse.beginRegistrationBatch();
    } catch (RuntimeException | Error error) {
      adaptationRegistrations.rollback();
      throw error;
    }
    Skill<?> skill = null;
    try {
      skill = source.build();
      if (!source.id().equals(skill.getId())) {
        throw new IllegalStateException("Addon " + source.owner() + " registered skill " + source.id()
            + ", but its supplier built " + skill.getClass().getName() + " with id " + skill.getId());
      }
      for (ExtensionSource extension : extensionSources) {
        if (!extension.skill().equals(source.id())) {
          continue;
        }
        Adaptation<?> adaptation = extension.build();
        if (!extension.id().equals(adaptation.getId())) {
          ComponentHost.of(adaptation).retireComponent();
          throw new IllegalStateException("Addon " + extension.owner() + " registered adaptation " + extension.id()
              + ", but its supplier built " + adaptation.getClass().getName() + " with id " + adaptation.getId());
        }
        ComponentBuilder.place(SkillHost.of(skill), adaptation);
      }
      List<Id> adaptations = new ArrayList<>();
      for (Adaptation<?> adaptation : SkillHost.ownedAdaptations(skill)) {
        adaptations.add(adaptation.getId());
      }
      withholdVetoed(skill);
      adaptationRegistrations.endCapture();
      skillRegistrations.endCapture();
      return new SkillInstantiation(skill, List.copyOf(adaptations), adaptationRegistrations, skillRegistrations);
    } catch (RuntimeException | Error error) {
      skillRegistrations.rollback();
      adaptationRegistrations.rollback();
      if (skill != null) {
        ComponentHost.of(skill).retireComponent();
      }
      throw error;
    }
  }

  private void withholdVetoed(Skill<?> skill) {
    Set<Id> blocked = vetoed;
    if (blocked.isEmpty()) {
      return;
    }
    List<Adaptation<?>> withheld = new ArrayList<>();
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (blocked.contains(adaptation.getId())) {
        withheld.add(adaptation);
      }
    }
    SkillHost host = SkillHost.of(skill);
    for (Adaptation<?> adaptation : withheld) {
      host.removeAdaptation(adaptation);
      ComponentHost.of(adaptation).retireComponent();
    }
  }

  private void unregisterRecipes(Skill<?> s) {
    synchronized (this) {
      deferredBootstrapRecipeRegistration.removeIf(candidate -> candidate == s);
    }
    RecipeHostAccess.unregister(s.getRecipes());
    SkillHost.ownedAdaptations(s).forEach(this::unregisterAdaptationRecipes);
  }

  private void registerRecipes(Skill<?> s) {
    if (s == null) {
      return;
    }

    if (shouldDelayRecipeRegistrationForFolia()) {
      enqueueDeferredRecipeRegistration(s);
      return;
    }

    registerRecipesNow(s);
  }

  private void registerRecipesNow(Skill<?> s) {
    if (!s.isEnabled()) {
      return;
    }
    RecipeHostAccess.register(s.getRecipes());
    SkillHost.ownedAdaptations(s).forEach(adaptation -> {
      if (!adaptation.isEnabled()) {
        return;
      }
      registerAdaptationRecipesNow(adaptation);
    });
  }

  private void synchronizeAdaptationRecipes(Skill<?> skill, SimpleAdaptation<?> adaptation, boolean previouslyEnabled) {
    boolean enabled = skill.isEnabled() && adaptation.isEnabled();
    if (previouslyEnabled && !enabled) {
      unregisterAdaptationRecipes(adaptation);
      return;
    }
    if (previouslyEnabled || !enabled) {
      return;
    }
    if (shouldDelayRecipeRegistrationForFolia()) {
      enqueueDeferredRecipeRegistration(skill);
      return;
    }
    registerAdaptationRecipesNow(adaptation);
  }

  private void unregisterAdaptationRecipes(Adaptation<?> adaptation) {
    for (RecipeSpec recipe : adaptation.getRecipes()) {
      removeAdaptationRecipeIndex(recipe.key(), adaptation);
      RecipeHostAccess.unregister(recipe);
    }
    for (BrewingRecipe recipe : adaptation.getBrewingRecipes()) {
      BrewingManager.unregisterRecipe(adaptation.getId().toString(), recipe);
    }
  }

  private void registerAdaptationRecipesNow(Adaptation<?> adaptation) {
    for (RecipeSpec recipe : adaptation.getRecipes()) {
      RecipeHostAccess.register(recipe);
      indexAdaptationRecipe(new AdaptRecipeBook.Unlock(recipe.key(), adaptation, recipe.requiredLevel()));
    }
    for (BrewingRecipe recipe : adaptation.getBrewingRecipes()) {
      BrewingManager.registerRecipe(adaptation.getId().toString(), recipe);
    }
  }

  private boolean shouldDelayRecipeRegistrationForFolia() {
    if (!Tasks.isFoliaThreading()) {
      return false;
    }

    return !AdaptCore.platform().host().onlinePlayers().isEmpty();
  }

  private synchronized void enqueueDeferredRecipeRegistration(Skill<?> skill) {
    if (skill == null) {
      return;
    }

    deferredBootstrapRecipeRegistration.remove(skill);
    deferredBootstrapRecipeRegistration.addLast(skill);
    scheduleDeferredBootstrapRecipeRegistration();
  }

  @Override
  public void unregister() {
    if (!unregistered.compareAndSet(false, true)) {
      return;
    }
    runtimeStarted = false;
    mutationEngine.unregister();
    deferredBootstrapRecipeTaskScheduled = false;
    deferredBootstrapRecipeRegistration.clear();
    for (DeferredRecipeTransition transition : deferredRecipeTransitions.values()) {
      transition.retired().forEach(this::unregisterRecipes);
    }
    deferredRecipeTransitions.clear();
    for (MutatedSkill mutated : mutatedSkills.values()) {
      unregisterRecipes(mutated);
      ComponentHost.of(mutated).retireComponent();
    }
    mutatedSkills.clear();
    for (Skill<?> i : knownSkills.v()) {
      unregisterRecipes(i);
      ComponentHost host = ComponentHost.find(i);
      if (host != null) {
        host.retireComponent();
      }
    }
    skills.clear();
    knownSkills.clear();
    sources.clear();
    extensions.clear();
    hosts.clear();
    adaptationRecipeIndex.clear();
    statTrackerIndex = StatTrackerIndex.empty();
    statReconciliationScheduled.set(false);
    catalogRevision.incrementAndGet();
    super.unregister();
  }


  private void catalogChanged(boolean reconcilePlayers) {
    long revision = catalogRevision.incrementAndGet();
    if (!bootstrapLoading) {
      rebuildStatTrackerIndex();
      publishPlaceholderCatalog(revision);
      AdaptationRequirements.warnUnresolved(getSkills());
      refreshSignatures();
      if (reconcilePlayers) {
        synchronizeAdvancementRuntime(revision);
        synchronizeRecipeBookRuntime();
      }
      announceCatalog(revision);
    }
  }

  /** Builds the pair of every claimed signature slot whose parents are registered, so its recipes and tools exist. */
  public void refreshSignatures() {
    for (MutationSlot slot : pairs.signatureSlots()) {
      if (getSkill(slot.pair().lead()) != null && getSkill(slot.pair().partner()) != null) {
        MutatedSkill mutated = getMutatedSkill(slot.pair());
        if (mutated != null) {
          mutated.getSignatures();
        }
      }
    }
  }

  /** Starts the pair's rebuilt adaptations and signatures and moves recipe registration from the ones it retired. */
  private void signaturesChanged(MutatedSkill mutated, List<Signature<?>> retired) {
    retired.forEach(this::unregisterAdaptationRecipes);
    if (runtimeStarted) {
      for (Adaptation<?> built : SkillHost.ownedAdaptations(mutated)) {
        if (built.isEnabled()) {
          ComponentHost.of(built).activateRuntime();
        }
      }
      registerRecipes(mutated);
    }
  }

  private void announceCatalog(long revision) {
    AdaptCore core = AdaptCore.current();
    AddonLifecycle addons = core == null ? null : core.addons();
    if (addons != null) {
      addons.catalogChanged(revision);
    }
  }

  private void publishPlaceholderCatalog(long revision) {
    AdaptPorts.get(SkillRegistryPort.class, SkillRegistryPort.NONE).publishCatalog(revision, getAllSkills());
  }

  private void rebuildStatTrackerIndex() {
    statTrackerIndex = StatTrackerIndex.build(new ArrayList<>(knownSkills.v()));
  }

  private void synchronizeAdvancementRuntime(long revision) {
    AdaptCore core = AdaptCore.current();
    if (core == null || core.getAdvancementManager() == null) {
      return;
    }
    core.getAdvancementManager().synchronizeCatalog(revision, this::reconcileStatTrackersForOnlinePlayers);
  }

  private void synchronizeRecipeBookRuntime() {
    AdaptCore core = AdaptCore.current();
    if (core == null || core.getAdaptServer() == null) {
      return;
    }
    core.getAdaptServer().synchronizeRecipeBooksForOnlinePlayers();
  }

  private void startStatTrackerReconciliation() {
    long revision = statReconciliationRevision.get();
    List<AdaptPlayer> players = List.copyOf(AdaptCore.get().getAdaptServer().getOnlineAdaptPlayerSnapshot());
    if (players.isEmpty()) {
      finishStatTrackerReconciliation(revision);
      return;
    }
    reconcileStatTrackerBatch(players, 0, revision);
  }

  private void reconcileStatTrackerBatch(List<AdaptPlayer> players, int startIndex, long revision) {
    int endIndex = Math.min(startIndex + STAT_RECONCILIATION_BATCH_SIZE, players.size());
    for (int index = startIndex; index < endIndex; index++) {
      players.get(index).reconcileStatTrackers();
    }

    if (endIndex < players.size()) {
      Tasks.s(() -> reconcileStatTrackerBatch(players, endIndex, revision), 1);
      return;
    }

    finishStatTrackerReconciliation(revision);
  }

  private void finishStatTrackerReconciliation(long revision) {
    if (statReconciliationRevision.get() != revision) {
      Tasks.s(this::startStatTrackerReconciliation, 1);
      return;
    }

    statReconciliationScheduled.set(false);
    if (statReconciliationRevision.get() != revision
        && statReconciliationScheduled.compareAndSet(false, true)) {
      Tasks.s(this::startStatTrackerReconciliation, 1);
    }
  }

  private void indexAdaptationRecipe(AdaptRecipeBook.Unlock unlock) {
    if (unlock == null) {
      return;
    }

    Id key = unlock.key();
    Adaptation<?> adaptation = unlock.adaptation();
    AdaptRecipeBook.Unlock previous = adaptationRecipeIndex.put(key, unlock);
    if (previous != null && previous.adaptation() != adaptation) {
      AdaptLog.warn("Recipe key conflict for " + key + ": " + previous.adaptation().getId().toString() + " replaced by " + adaptation.getId().toString());
    }
  }

  private void removeAdaptationRecipeIndex(Id key, Adaptation<?> adaptation) {
    if (key == null) {
      return;
    }

    if (adaptation == null) {
      adaptationRecipeIndex.remove(key);
      return;
    }

    adaptationRecipeIndex.computeIfPresent(key, (k, current) -> current.adaptation() == adaptation ? null : current);
  }

  private synchronized void scheduleDeferredBootstrapRecipeRegistration() {
    if (!isRuntimeRegistered()
        || (deferredBootstrapRecipeRegistration.isEmpty() && deferredRecipeTransitions.isEmpty())
        || deferredBootstrapRecipeTaskScheduled) {
      return;
    }

    deferredBootstrapRecipeTaskScheduled = true;
    int deferredCount = deferredBootstrapRecipeRegistration.size() + deferredRecipeTransitions.size();
    AdaptLog.info("Deferring recipe registration for " + deferredCount + " skills.");
    Tasks.s(this::runDeferredBootstrapRecipeRegistrationTick, 1);
  }

  private void runDeferredBootstrapRecipeRegistrationTick() {
    if (!isRuntimeRegistered() || !deferredBootstrapRecipeTaskScheduled) {
      return;
    }
    if (shouldDelayRecipeRegistrationForFolia()) {
      if (!foliaRecipeRegistrationWaitWarned) {
        foliaRecipeRegistrationWaitWarned = true;
        AdaptLog.warn("Delaying Adapt recipe registration on Folia while players are online to avoid unsafe recipe reload races. Recipes will register automatically when the server has no online players.");
      }
      Tasks.s(this::runDeferredBootstrapRecipeRegistrationTick, 20);
      return;
    }

    foliaRecipeRegistrationWaitWarned = false;
    boolean complete;

    synchronized (this) {
      if (!deferredBootstrapRecipeTaskScheduled) {
        return;
      }

      int processed = 0;
      long started = System.currentTimeMillis();
      while (processed < DEFERRED_SKILLS_PER_TICK) {
        if (shouldDelayRecipeRegistrationForFolia()) {
          break;
        }

        DeferredRecipeTransition transition = pollDeferredRecipeTransition();
        if (transition != null) {
          applyDeferredRecipeTransition(transition);
          processed++;
          if (System.currentTimeMillis() - started > 8L) {
            break;
          }
          continue;
        }

        Skill<?> skill = deferredBootstrapRecipeRegistration.pollFirst();
        if (skill == null) {
          break;
        }
        processed++;
        Skill<?> current = knownSkills.get(skill.getId());
        if (current != skill) {
          continue;
        }
        registerRecipesNow(skill);
        if (System.currentTimeMillis() - started > 8L) {
          break;
        }
      }

      complete = deferredBootstrapRecipeRegistration.isEmpty() && deferredRecipeTransitions.isEmpty();
      if (complete) {
        deferredBootstrapRecipeTaskScheduled = false;
      }
    }

    if (complete) {
      AdaptLog.info("Deferred recipe registration completed.");
      synchronizeRecipeBookRuntime();
      return;
    }

    if (shouldDelayRecipeRegistrationForFolia()) {
      Tasks.s(this::runDeferredBootstrapRecipeRegistrationTick, 20);
    } else {
      Tasks.s(this::runDeferredBootstrapRecipeRegistrationTick, 1);
    }
  }

  private DeferredRecipeTransition pollDeferredRecipeTransition() {
    Iterator<Map.Entry<Id, DeferredRecipeTransition>> iterator = deferredRecipeTransitions.entrySet().iterator();
    if (!iterator.hasNext()) {
      return null;
    }
    DeferredRecipeTransition transition = iterator.next().getValue();
    iterator.remove();
    return transition;
  }

  private void applyDeferredRecipeTransition(DeferredRecipeTransition transition) {
    for (Skill<?> retired : transition.retired()) {
      unregisterRecipes(retired);
    }
    Skill<?> replacement = transition.replacement();
    if (replacement == null || !replacement.isEnabled()) {
      return;
    }
    Skill<?> current = knownSkills.get(replacement.getId());
    if (current == replacement) {
      registerRecipesNow(replacement);
    }
  }

  private record AdaptationIndex(long revision, Map<Id, Adaptation<?>> byId, Set<String> namespaces) {
  }

  private record AdaptationConfigState(SimpleAdaptation<?> adaptation, boolean previouslyEnabled) {
  }

  private record Staged(SkillSource source, SkillInstantiation instantiation, boolean enabled, Skill<?> previous) {
    private void discard() {
      instantiation.rollbackRegistrations();
      ComponentHost.of(instantiation.skill()).retireComponent();
    }
  }

  private record SkillInstantiation(
      Skill<?> skill,
      List<Id> adaptations,
      AdaptationOwnerPulse.RegistrationBatch adaptationRegistrations,
      SkillOwnerPulse.RegistrationBatch skillRegistrations
  ) {
    private void commitRegistrations() {
      if (adaptationRegistrations != null) {
        adaptationRegistrations.commit();
      }
      if (skillRegistrations != null) {
        skillRegistrations.commit();
      }
    }

    private void rollbackRegistrations() {
      if (skillRegistrations != null) {
        skillRegistrations.rollback();
      }
      if (adaptationRegistrations != null) {
        adaptationRegistrations.rollback();
      }
    }
  }

  private static final class RecipeHostAccess {
    private RecipeHostAccess() {
    }

    private static void register(List<RecipeSpec> recipes) {
      for (RecipeSpec recipe : recipes) {
        register(recipe);
      }
    }

    private static void unregister(List<RecipeSpec> recipes) {
      for (RecipeSpec recipe : recipes) {
        unregister(recipe);
      }
    }

    private static void register(RecipeSpec recipe) {
      AdaptCore core = AdaptCore.current();
      if (recipe != null && core != null) {
        core.getPlatform().recipes().add(recipe);
      }
    }

    private static void unregister(RecipeSpec recipe) {
      AdaptCore core = AdaptCore.current();
      if (recipe != null && core != null) {
        core.getPlatform().recipes().unregister(recipe.key());
      }
    }
  }

  private record ReplacementConfigSnapshot(boolean skill, Id configName, String raw, File sourceFile) {
    private static ReplacementConfigSnapshot skill(Id configName, String raw, File sourceFile) {
      return new ReplacementConfigSnapshot(true, configName, raw, sourceFile);
    }

    private static ReplacementConfigSnapshot adaptation(Id configName, String raw, File sourceFile) {
      return new ReplacementConfigSnapshot(false, configName, raw, sourceFile);
    }
  }

  private record DeferredRecipeTransition(List<Skill<?>> retired, Skill<?> replacement) {
  }
}
