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

package art.arcane.adapt.api.skill;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.VelocityBurstRuntime;
import art.arcane.adapt.api.advancement.AdvancementManager;
import art.arcane.adapt.api.minion.MinionBurden;
import art.arcane.adapt.api.mutation.MutationManager;
import art.arcane.adapt.api.potion.BrewingManager;
import art.arcane.adapt.api.protection.Protector;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.AdaptRecipeBook;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.content.skill.SkillAgility;
import art.arcane.adapt.content.skill.SkillArchitect;
import art.arcane.adapt.content.skill.SkillAxes;
import art.arcane.adapt.content.skill.SkillBlocking;
import art.arcane.adapt.content.skill.SkillBrewing;
import art.arcane.adapt.content.skill.SkillChronos;
import art.arcane.adapt.content.skill.SkillCrafting;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.content.skill.SkillEnchanting;
import art.arcane.adapt.content.skill.SkillExcavation;
import art.arcane.adapt.content.skill.SkillHerbalism;
import art.arcane.adapt.content.skill.SkillHunter;
import art.arcane.adapt.content.skill.SkillKinetics;
import art.arcane.adapt.content.skill.SkillNether;
import art.arcane.adapt.content.skill.SkillPickaxes;
import art.arcane.adapt.content.skill.SkillRanged;
import art.arcane.adapt.content.skill.SkillRift;
import art.arcane.adapt.content.skill.SkillSeaborne;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.content.skill.SkillSwords;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.content.skill.SkillTragOul;
import art.arcane.adapt.content.skill.SkillUnarmed;
import art.arcane.adapt.service.MutationSVC;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class SkillRegistry extends TickedObject {
  public static final KMap<String, Skill<?>> skills = new KMap<>();
  private static final long SLOW_SKILL_REG_MS = 300L;
  private static final int DEFERRED_SKILLS_PER_TICK = 2;
  private static final int STAT_RECONCILIATION_BATCH_SIZE = 32;
  private final KMap<String, Skill<?>> knownSkills = new KMap<>();
  private final KMap<String, Class<? extends Skill<?>>> skillTypes = new KMap<>();
  private final Map<NamespacedKey, AdaptRecipeBook.Unlock> adaptationRecipeIndex = new ConcurrentHashMap<>();
  private final Deque<Skill<?>> deferredBootstrapRecipeRegistration = new ArrayDeque<>();
  private final Map<String, DeferredRecipeTransition> deferredRecipeTransitions = new LinkedHashMap<>();
  private final AtomicLong catalogRevision = new AtomicLong();
  private final AtomicBoolean statReconciliationScheduled = new AtomicBoolean();
  private final AtomicLong statReconciliationRevision = new AtomicLong();
  private final AtomicBoolean unregistered = new AtomicBoolean();
  private volatile StatTrackerIndex statTrackerIndex = StatTrackerIndex.empty();
  private volatile boolean deferredBootstrapRecipeTaskScheduled;
  private volatile boolean bootstrapLoading = true;
  private volatile boolean foliaRecipeRegistrationWaitWarned;
  private volatile boolean runtimeStarted;

  public SkillRegistry() {
    super("registry", UUID.randomUUID() + "-sk", 1250);
    registerSkill(SkillAgility.class);
    registerSkill(SkillArchitect.class);
    registerSkill(SkillAxes.class);
    registerSkill(SkillBlocking.class);
    registerSkill(SkillChronos.class);
    registerSkill(SkillCrafting.class);
    registerSkill(SkillDiscovery.class);
    registerSkill(SkillEnchanting.class);
    registerSkill(SkillHerbalism.class);
    registerSkill(SkillHunter.class);
    registerSkill(SkillPickaxes.class);
    registerSkill(SkillRanged.class);
    registerSkill(SkillRift.class);
    registerSkill(SkillSeaborne.class);
    registerSkill(SkillStealth.class);
    registerSkill(SkillSwords.class);
    registerSkill(SkillTaming.class);
    registerSkill(SkillTragOul.class);
    registerSkill(SkillUnarmed.class);
    registerSkill(SkillExcavation.class);
    registerSkill(SkillBrewing.class);
    registerSkill(SkillNether.class);
    registerSkill(SkillKinetics.class);
    bootstrapLoading = false;
    rebuildStatTrackerIndex();
  }

  public synchronized void startRuntime() {
    if (runtimeStarted || unregistered.get()) {
      return;
    }
    runtimeStarted = true;
    for (Skill<?> skill : skills.v()) {
      prepareSkillRuntime(skill);
    }
    activateRuntime();
    scheduleDeferredBootstrapRecipeRegistration();
  }

  @EventHandler
  public void on(PlayerExpChangeEvent e) {
    Player p = e.getPlayer();
    if (e.getAmount() > 0) {
      getPlayer(p).boostXPToRecents(0.03, 10000);
    }
  }

  private boolean canInteract(Player player, Location targetLocation) {
    if (player == null || targetLocation == null) {
      return false;
    }

    for (Protector protector : Adapt.instance.getProtectorRegistry().getAllProtectors()) {
      if (!protector.canInteract(player, targetLocation, null)) {
        return false;
      }
    }

    return true;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Player p = e.getPlayer();

    boolean commonConditions = p.isSneaking() && e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && e.getClickedBlock() != null;
    boolean isLectern = commonConditions && e.getClickedBlock().getType().equals(Material.LECTERN);
    boolean isObserver = commonConditions && e.getClickedBlock().getType().equals(Material.OBSERVER);
    boolean allowVerticalFaces = AdaptConfig.get().adaptActivatorAllowVerticalFaces;
    boolean validActivatorFace = e.getBlockFace() != null
        && (allowVerticalFaces || (!e.getBlockFace().equals(BlockFace.UP) && !e.getBlockFace().equals(BlockFace.DOWN)));
    boolean isAdaptActivator = validActivatorFace && !p.isSneaking() && e.getAction().equals(Action.RIGHT_CLICK_BLOCK)
        && e.getClickedBlock() != null
        && canInteract(p, e.getClickedBlock().getLocation())
        && e.getClickedBlock().getType().equals(Material.valueOf(AdaptConfig.get().adaptActivatorBlock)) && (p.getInventory().getItemInMainHand().getType().equals(Material.AIR)
        || !p.getInventory().getItemInMainHand().getType().isBlock()) &&
        (p.getInventory().getItemInOffHand().getType().equals(Material.AIR) || !p.getInventory().getItemInOffHand().getType().isBlock());

    if (isAdaptActivator) {
      MutationSVC mutationService = MutationSVC.get();
      MutationManager mutationManager = mutationService == null ? null : mutationService.getManager();
      if (mutationManager != null && mutationManager.getConfig().isEnabled()) {
        mutationManager.authorizeBookshelf(p, e.getClickedBlock().getLocation());
      }
      SoundPlayer spw = SoundPlayer.of(e.getClickedBlock().getWorld());
      spw.play(e.getClickedBlock().getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 0.72f);
      spw.play(e.getClickedBlock().getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.35f, 0.755f);
      SkillsGui.open(p);
      e.setCancelled(true);
      p.getWorld().spawnParticle(Particles.CRIT_MAGIC, e.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), 25, 0, 0, 0, 1.1);
      p.getWorld().spawnParticle(Particles.ENCHANTMENT_TABLE, e.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), 12, 0, 0, 0, 1.1);
    }

    if (isLectern) {
      ItemStack it = p.getInventory().getItemInMainHand();
      if (it.getItemMeta() != null && !it.getItemMeta().getPersistentDataContainer().getKeys().isEmpty()) {
        e.setCancelled(true);
        playDebug(p);
        it.getItemMeta().getPersistentDataContainer().getKeys().forEach(k -> Bukkit.getServer().getConsoleSender().sendMessage(k + " = " + it.getItemMeta().getPersistentDataContainer().getOrDefault(k, PersistentDataType.STRING, "Not a String")));
      }
    }

    if (isObserver) {
      ItemStack it = p.getInventory().getItemInMainHand();
      if (it.getType().equals(Material.EXPERIENCE_BOTTLE)) {
        e.setCancelled(true);
        Bukkit.getServer().getConsoleSender().sendMessage("   ");
        p.setCooldown(Material.ENCHANTED_BOOK, 3);
        AdaptPlayer a = getPlayer(p);
        playDebug(p);

        String xv = a.getData().getMultiplier() - 1d > 0 ? "+" + Form.pc(a.getData().getMultiplier() - 1D) : Form.pc(a.getData().getMultiplier() - 1D);
        Bukkit.getServer().getConsoleSender().sendMessage("Global" + C.GRAY + ": " + C.GREEN + xv);

        for (XPMultiplier i : a.getData().getMultipliers()) {
          String vv = i.getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier()) : Form.pc(i.getMultiplier());
          Bukkit.getServer().getConsoleSender().sendMessage(C.GREEN + "* " + vv + C.GRAY + " for " + Form.duration(i.getGoodFor() - M.ms(), 0));
        }
        for (XPMultiplier i : Adapt.instance.getAdaptServer().getData().getMultipliers()) {
          String vv = i.getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier()) : Form.pc(i.getMultiplier());
          Bukkit.getServer().getConsoleSender().sendMessage(C.GREEN + "* " + vv + C.GRAY + " for " + Form.duration(i.getGoodFor() - M.ms(), 0));
        }

        for (PlayerSkillLine i : a.getData().getSkillLines().v()) {
          Skill<?> s = i.getRawSkill(a);
          if (s == null) {
            continue;
          }
          String v = i.getMultiplier() - a.getData().getMultiplier() > 0 ? "+" + Form.pc(i.getMultiplier() - a.getData().getMultiplier()) : Form.pc(i.getMultiplier() - a.getData().getMultiplier());
          Bukkit.getServer().getConsoleSender().sendMessage("  " + s.getDisplayName() + C.GRAY + ": " + s.getColor() + v);
          for (XPMultiplier j : i.getMultipliers()) {
            String vv = j.getMultiplier() > 0 ? "+" + Form.pc(j.getMultiplier()) : Form.pc(j.getMultiplier());
            Bukkit.getServer().getConsoleSender().sendMessage("  " + s.getShortName() + C.GRAY + " " + vv + " for " + Form.duration(j.getGoodFor() - M.ms(), 0));
          }
        }
      }
    }
  }

  private void playDebug(Player p) {
    SoundPlayer sp = SoundPlayer.of(p);
    sp.play(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
    sp.play(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.1f);
    sp.play(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.6f);
    sp.play(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);

  }

  public Skill<?> getSkill(String i) {
    if (i == null) {
      return null;
    }

    Skill<?> direct = skills.get(i);
    if (direct != null) {
      return direct;
    }

    return skills.get(normalizeSkillName(i));
  }

  public Skill<?> getAnySkill(String i) {
    if (i == null) {
      return null;
    }

    Skill<?> direct = knownSkills.get(i);
    if (direct != null) {
      return direct;
    }

    return knownSkills.get(normalizeSkillName(i));
  }

  public List<Skill<?>> getSkills() {
    return skills.v();
  }

  public List<Skill<?>> getAllSkills() {
    return new ArrayList<>(knownSkills.v());
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
    if (!AdaptConfig.get().isAdvancements()
        || Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return;
    }
    AdvancementManager manager = Adapt.instance.getManager();
    if (manager == null || !manager.isCatalogReady(catalogRevision.get())) {
      return;
    }

    statReconciliationRevision.incrementAndGet();
    if (!statReconciliationScheduled.compareAndSet(false, true)) {
      return;
    }

    J.s(this::startStatTrackerReconciliation, 1);
  }

  public synchronized void registerSkill(Class<? extends Skill<?>> skillType) {
    long started = System.currentTimeMillis();
    long instantiateStarted = started;
    Skill<?> skill = instantiateSkill(skillType);
    long instantiateMs = System.currentTimeMillis() - instantiateStarted;
    if (skill == null) {
      return;
    }

    String skillName = normalizeSkillName(skill.getName());
    skillTypes.put(skillName, skillType);
    Skill<?> previous = knownSkills.put(skillName, skill);
    if (previous != null && previous != skill) {
      unregisterRecipes(previous);
      previous.unregister();
    }

    if (!skill.isEnabled()) {
      skill.unregister();
      skills.remove(skillName);
      catalogChanged(true);
      return;
    }

    skills.put(skillName, skill);
    prepareSkillRuntime(skill);
    if (bootstrapLoading) {
      deferredBootstrapRecipeRegistration.addLast(skill);
    } else {
      registerRecipes(skill);
    }

    long totalMs = System.currentTimeMillis() - started;
    if (totalMs >= SLOW_SKILL_REG_MS || instantiateMs >= SLOW_SKILL_REG_MS) {
      Adapt.warn("Skill registration slow-path [" + skillName + "] total=" + totalMs + "ms instantiate=" + instantiateMs + "ms bootstrap=" + bootstrapLoading + ".");
    }
    catalogChanged(true);
  }

  public synchronized boolean hotReloadSkillConfig(String skillName) {
    String normalized = normalizeSkillName(skillName);
    Skill<?> loaded = knownSkills.get(normalized);
    Class<? extends Skill<?>> skillType = inferSkillType(normalized, loaded);
    if (skillType == null) {
      Adapt.verbose("No known skill type for config hotload: " + skillName);
      return false;
    }
    if (!(loaded instanceof SimpleSkill<?> simpleSkill)) {
      return replaceSkillInstance(normalized, skillType, loaded);
    }
    if (!simpleSkill.validateConfigFromDisk()) {
      return false;
    }

    List<AdaptationConfigState> adaptationStates = new ArrayList<>();
    for (Adaptation<?> adaptation : loaded.getAdaptations()) {
      if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)) {
        continue;
      }
      if (!simpleAdaptation.validateConfigFromDisk()) {
        return false;
      }
      adaptationStates.add(new AdaptationConfigState(simpleAdaptation, simpleAdaptation.isEnabled()));
    }

    boolean previouslyEnabled = loaded.isEnabled();
    if (!simpleSkill.reloadConfigFromDisk(true)) {
      return false;
    }
    if (previouslyEnabled != loaded.isEnabled()) {
      return replaceSkillInstance(normalized, skillType, loaded);
    }

    boolean adaptationLifecycleChanged = false;
    for (AdaptationConfigState state : adaptationStates) {
      if (!state.adaptation().reloadConfigFromDisk(false)) {
        return false;
      }
      adaptationLifecycleChanged |= state.previouslyEnabled() != state.adaptation().isEnabled();
    }
    if (adaptationLifecycleChanged) {
      return replaceSkillInstance(normalized, skillType, loaded);
    }

    for (AdaptationConfigState state : adaptationStates) {
      synchronizeAdaptationRecipes(loaded, state.adaptation(), state.previouslyEnabled());
    }

    catalogChanged(true);
    return true;
  }

  public synchronized boolean hotReloadAdaptationConfig(String adaptationName) {
    if (adaptationName == null || adaptationName.isBlank()) {
      return false;
    }

    for (Skill<?> skill : knownSkills.v()) {
      for (Adaptation<?> adaptation : skill.getAdaptations()) {
        if (!adaptation.getName().equalsIgnoreCase(adaptationName)) {
          continue;
        }
        if (!(adaptation instanceof SimpleAdaptation<?> simpleAdaptation)
            || !simpleAdaptation.validateConfigFromDisk()) {
          return false;
        }
        boolean previouslyEnabled = simpleAdaptation.isEnabled();
        if (!simpleAdaptation.reloadConfigFromDisk(true)) {
          return false;
        }
        if (previouslyEnabled != simpleAdaptation.isEnabled()) {
          String normalized = normalizeSkillName(skill.getName());
          Class<? extends Skill<?>> skillType = inferSkillType(normalized, skill);
          return skillType != null && replaceSkillInstance(normalized, skillType, skill);
        }
        synchronizeAdaptationRecipes(skill, simpleAdaptation, previouslyEnabled);
        catalogChanged(true);
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private Class<? extends Skill<?>> inferSkillType(String normalizedSkillName, Skill<?> loaded) {
    Class<? extends Skill<?>> skillType = skillTypes.get(normalizedSkillName);
    if (skillType != null) {
      return skillType;
    }

    if (loaded != null && Skill.class.isAssignableFrom(loaded.getClass())) {
      return (Class<? extends Skill<?>>) loaded.getClass();
    }

    return null;
  }

  private boolean replaceSkillInstance(String normalizedName, Class<? extends Skill<?>> skillType, Skill<?> previousLoaded) {
    Skill<?> replacement = instantiateSkill(skillType);
    if (replacement == null) {
      return false;
    }

    Skill<?> previousKnown = knownSkills.put(normalizedName, replacement);
    Skill<?> previousActive = replacement.isEnabled()
        ? skills.put(normalizedName, replacement)
        : skills.remove(normalizedName);
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
      previous.unregister();
    }

    if (!replacement.isEnabled()) {
      replacement.unregister();
      if (deferRecipeTransition) {
        enqueueDeferredRecipeTransition(normalizedName, retired, replacement);
      }
      catalogChanged(true);
      return true;
    }

    prepareSkillRuntime(replacement);
    if (deferRecipeTransition) {
      enqueueDeferredRecipeTransition(normalizedName, retired, replacement);
    } else {
      registerRecipesNow(replacement);
    }
    catalogChanged(true);
    return true;
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
      skill.activateRuntime();
    }
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (!adaptation.isEnabled()) {
        adaptation.unregister();
      } else if (runtimeStarted) {
        adaptation.activateRuntime();
      }
    }
    if (runtimeStarted) {
      SkillOwnerPulse.startRuntime();
      VelocityBurstRuntime.startRuntime();
      MinionBurden.startRuntime();
    }
  }

  private synchronized void enqueueDeferredRecipeTransition(String normalizedName, List<Skill<?>> retired,
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

  public boolean isKnownSkill(String skillName) {
    if (skillName == null) {
      return false;
    }

    return skillTypes.containsKey(normalizeSkillName(skillName));
  }

  public Adaptation<?> getRequiredAdaptation(Recipe recipe) {
    if (!(recipe instanceof Keyed keyed)) {
      return null;
    }

    AdaptRecipeBook.Unlock unlock = adaptationRecipeIndex.get(keyed.getKey());
    return unlock == null ? null : unlock.adaptation();
  }

  public List<AdaptRecipeBook.Unlock> getRegisteredRecipeUnlocks() {
    List<AdaptRecipeBook.Unlock> unlocks = new ArrayList<>(adaptationRecipeIndex.values());
    unlocks.sort(Comparator.comparing(unlock -> unlock.key().toString()));
    return List.copyOf(unlocks);
  }

  public synchronized boolean isRecipeRegistrationReady() {
    return !deferredBootstrapRecipeTaskScheduled
        && deferredBootstrapRecipeRegistration.isEmpty()
        && deferredRecipeTransitions.isEmpty();
  }

  private Skill<?> instantiateSkill(Class<? extends Skill<?>> skillType) {
    try {
      return skillType.getConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException |
             InvocationTargetException | NoSuchMethodException e) {
      e.printStackTrace();
      return null;
    }
  }

  private void unregisterRecipes(Skill<?> s) {
    synchronized (this) {
      deferredBootstrapRecipeRegistration.removeIf(candidate -> candidate == s);
    }
    s.getRecipes().forEach(AdaptRecipe::unregister);
    s.getAdaptations().forEach(this::unregisterAdaptationRecipes);
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
    s.getRecipes().forEach(AdaptRecipe::register);
    s.getAdaptations().forEach(adaptation -> {
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
    adaptation.getRecipes().forEach(recipe -> {
      removeAdaptationRecipeIndex(recipe, adaptation);
      recipe.unregister();
    });
    adaptation.getBrewingRecipes().forEach(recipe -> BrewingManager.unregisterRecipe(adaptation.getName(), recipe));
  }

  private void registerAdaptationRecipesNow(Adaptation<?> adaptation) {
    adaptation.getRecipes().forEach(recipe -> {
      recipe.register();
      indexAdaptationRecipe(recipe, adaptation);
    });
    adaptation.getBrewingRecipes().forEach(recipe -> BrewingManager.registerRecipe(adaptation.getName(), recipe));
  }

  private boolean shouldDelayRecipeRegistrationForFolia() {
    if (!J.isFoliaThreading()) {
      return false;
    }

    return !Bukkit.getOnlinePlayers().isEmpty();
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
    deferredBootstrapRecipeTaskScheduled = false;
    deferredBootstrapRecipeRegistration.clear();
    for (DeferredRecipeTransition transition : deferredRecipeTransitions.values()) {
      transition.retired().forEach(this::unregisterRecipes);
    }
    deferredRecipeTransitions.clear();
    for (Skill<?> i : knownSkills.v()) {
      i.unregister();
      unregisterRecipes(i);
    }
    skills.clear();
    knownSkills.clear();
    skillTypes.clear();
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
      if (reconcilePlayers) {
        synchronizeAdvancementRuntime(revision);
        synchronizeRecipeBookRuntime();
      }
    }
  }

  private void rebuildStatTrackerIndex() {
    statTrackerIndex = StatTrackerIndex.build(new ArrayList<>(knownSkills.v()));
  }

  private void synchronizeAdvancementRuntime(long revision) {
    if (Adapt.instance == null || Adapt.instance.getManager() == null) {
      return;
    }
    Adapt.instance.getManager().synchronizeCatalog(revision, this::reconcileStatTrackersForOnlinePlayers);
  }

  private void synchronizeRecipeBookRuntime() {
    if (Adapt.instance == null || Adapt.instance.getAdaptServer() == null) {
      return;
    }
    Adapt.instance.getAdaptServer().synchronizeRecipeBooksForOnlinePlayers();
  }

  private void startStatTrackerReconciliation() {
    long revision = statReconciliationRevision.get();
    List<AdaptPlayer> players = List.copyOf(Adapt.instance.getAdaptServer().getOnlineAdaptPlayerSnapshot());
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
      J.s(() -> reconcileStatTrackerBatch(players, endIndex, revision), 1);
      return;
    }

    finishStatTrackerReconciliation(revision);
  }

  private void finishStatTrackerReconciliation(long revision) {
    if (statReconciliationRevision.get() != revision) {
      J.s(this::startStatTrackerReconciliation, 1);
      return;
    }

    statReconciliationScheduled.set(false);
    if (statReconciliationRevision.get() != revision
        && statReconciliationScheduled.compareAndSet(false, true)) {
      J.s(this::startStatTrackerReconciliation, 1);
    }
  }

  private String normalizeSkillName(String raw) {
    String normalized = raw.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("[skill]-")) {
      normalized = normalized.substring("[skill]-".length());
    }

    if (normalized.equals("chrono")) {
      normalized = "chronos";
    }

    return normalized;
  }

  private void indexAdaptationRecipe(AdaptRecipe recipe, Adaptation<?> adaptation) {
    if (recipe == null || adaptation == null) {
      return;
    }

    NamespacedKey key = recipe.getNSKey();
    AdaptRecipeBook.Unlock unlock = new AdaptRecipeBook.Unlock(key, adaptation, recipe.getRequiredLevel());
    AdaptRecipeBook.Unlock previous = adaptationRecipeIndex.put(key, unlock);
    if (previous != null && previous.adaptation() != adaptation) {
      Adapt.warn("Recipe key conflict for " + key + ": " + previous.adaptation().getName() + " replaced by " + adaptation.getName());
    }
  }

  private void removeAdaptationRecipeIndex(AdaptRecipe recipe, Adaptation<?> adaptation) {
    if (recipe == null) {
      return;
    }

    NamespacedKey key = recipe.getNSKey();
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
    Adapt.info("Deferring recipe registration for " + deferredCount + " skills.");
    J.s(this::runDeferredBootstrapRecipeRegistrationTick, 1);
  }

  private void runDeferredBootstrapRecipeRegistrationTick() {
    if (!isRuntimeRegistered() || !deferredBootstrapRecipeTaskScheduled) {
      return;
    }
    if (shouldDelayRecipeRegistrationForFolia()) {
      if (!foliaRecipeRegistrationWaitWarned) {
        foliaRecipeRegistrationWaitWarned = true;
        Adapt.warn("Delaying Adapt recipe registration on Folia while players are online to avoid unsafe recipe reload races. Recipes will register automatically when the server has no online players.");
      }
      J.s(this::runDeferredBootstrapRecipeRegistrationTick, 20);
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
        Skill<?> current = knownSkills.get(normalizeSkillName(skill.getName()));
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
      Adapt.info("Deferred recipe registration completed.");
      synchronizeRecipeBookRuntime();
      return;
    }

    if (shouldDelayRecipeRegistrationForFolia()) {
      J.s(this::runDeferredBootstrapRecipeRegistrationTick, 20);
    } else {
      J.s(this::runDeferredBootstrapRecipeRegistrationTick, 1);
    }
  }

  private DeferredRecipeTransition pollDeferredRecipeTransition() {
    Iterator<Map.Entry<String, DeferredRecipeTransition>> iterator = deferredRecipeTransitions.entrySet().iterator();
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
    Skill<?> current = knownSkills.get(normalizeSkillName(replacement.getName()));
    if (current == replacement) {
      registerRecipesNow(replacement);
    }
  }

  private record AdaptationConfigState(SimpleAdaptation<?> adaptation, boolean previouslyEnabled) {
  }

  private record DeferredRecipeTransition(List<Skill<?>> retired, Skill<?> replacement) {
  }
}
