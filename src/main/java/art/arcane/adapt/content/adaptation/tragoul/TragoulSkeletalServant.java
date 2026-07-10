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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.minion.MinionBurden;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.version.IAttribute;
import art.arcane.adapt.api.version.Version;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.entity.StackExclusion;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TragoulSkeletalServant extends SimpleAdaptation<TragoulSkeletalServant.Config> {
  private static final int HARD_MAX_SERVANTS_PER_OWNER = 16;
  private static final int MAX_IMMEDIATE_RETARGETS = 4;
  private static final int MAX_LOCAL_TARGET_CANDIDATES = 8;
  private static final int MIN_RETARGET_INTERVAL_TICKS = 10;
  private static final int MAX_RETARGET_SEARCHES_PER_TICK = 64;
  private static final int TARGET_ASSIGNMENT_DELAY_TICKS = 2;
  private static final int OWNER_HANDOFF_TIMEOUT_TICKS = 10;
  private static final long THREAT_REFRESH_MILLIS = 500L;
  private static final long RETARGET_BUDGET_COUNT_MASK = 0xFFFFL;
  private static final double HARD_MAX_TARGET_SEARCH_RADIUS = 24D;
  private static final NamespacedKey SERVANT_KEY = NamespacedKey.fromString("adapt:tragoul_servant_owner");
  private static final NamespacedKey PLAGUE_OWNER_KEY = NamespacedKey.fromString("adapt:tragoul_plague_owner");
  private static final NamespacedKey PLAGUE_GENERATION_KEY = NamespacedKey.fromString("adapt:tragoul_plague_generation");
  private static final NamespacedKey PLAGUE_STAMP_KEY = NamespacedKey.fromString("adapt:tragoul_plague_stamp");

  private static final Material[][] HELMETS = {
      {Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET},
      {Material.CHAINMAIL_HELMET, Material.IRON_HELMET},
      {Material.IRON_HELMET, Material.DIAMOND_HELMET}
  };
  private static final Material[][] CHESTPLATES = {
      {Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE},
      {Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE},
      {Material.IRON_CHESTPLATE, Material.DIAMOND_CHESTPLATE}
  };
  private static final Material[][] LEGGINGS = {
      {Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS},
      {Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS},
      {Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS}
  };
  private static final Material[][] BOOTS = {
      {Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS},
      {Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS},
      {Material.IRON_BOOTS, Material.DIAMOND_BOOTS}
  };
  private static final Material[][] SWORDS = {
      {Material.WOODEN_SWORD, Material.STONE_SWORD},
      {Material.IRON_SWORD, Material.IRON_SWORD},
      {Material.IRON_SWORD, Material.DIAMOND_SWORD}
  };
  private static final Material[] BOW_POOL = {Material.BOW};

  private static final Color SERVANT_BONE = Color.fromRGB(230, 225, 205);
  private final Map<UUID, CopyOnWriteArrayList<Skeleton>> servants = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> servantOwners = new ConcurrentHashMap<>();
  private final Map<Skeleton, UUID> servantOwnersByEntity = new ConcurrentHashMap<>();
  private final Map<Skeleton, UUID> servantIdsByEntity = new ConcurrentHashMap<>();
  private final Map<UUID, TargetRequest> latestTargetRequests = new ConcurrentHashMap<>();
  private final Set<UUID> activeTargetRequests = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Long> pendingLocalRetargets = new ConcurrentHashMap<>();
  private final Cooldowns cooldowns = cooldowns();
  private final MinionBurden burden = MinionBurden.get();
  private final AtomicLong localRetargetSequence = new AtomicLong();
  private final AtomicLong retargetBudgetState = new AtomicLong();
  private final Map<UUID, Threat> threats = new ConcurrentHashMap<>();
  private final Map<UUID, Long> servantThornsCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Long> servantCurseCooldowns = new ConcurrentHashMap<>();
  private volatile PerkRefs perkRefs;

  public TragoulSkeletalServant() {
    super("tragoul-skeletal-servant");
    registerConfiguration(Config.class);
    setIcon(Material.SKELETON_SKULL);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_tragoul_servant_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SKELETON_SKULL)
            .key("challenge_tragoul_servant_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_tragoul_servant_50", "tragoul.skeletal-servant.servants-summoned", 50, 400);
    registerMilestone("challenge_tragoul_servant_500", "tragoul.skeletal-servant.servants-summoned", 500, 1500);
  }

  public static boolean isServant(Entity entity) {
    return entity.getPersistentDataContainer().has(SERVANT_KEY, PersistentDataType.STRING);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("tragoul.skeletal_servant.lore1"));
    v.addLore(C.GREEN + "+ " + getServantCap(level) + C.GRAY + " " + Localizer.dLocalize("tragoul.skeletal_servant.lore5"));
    v.addLore(C.GREEN + "+ " + Form.duration(getDurationTicks(level) * 50D, 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.skeletal_servant.lore2"));
    v.addLore(C.YELLOW + "* " + getBoneCost(level) + C.GRAY + " " + Localizer.dLocalize("tragoul.skeletal_servant.lore3"));
    v.addLore(C.YELLOW + "* " + Form.duration((double) getCooldownMillis(level), 1) + C.GRAY + " " + Localizer.dLocalize("tragoul.skeletal_servant.lore4"));
    v.addLore(C.GRAY + Localizer.dLocalize("tragoul.skeletal_servant.lore6"));
    if (getConfig().healthCostEnabled && getConfig().healthCostPerMinion > 0) {
      statLore(v, C.RED, "* ", Form.f(getConfig().healthCostPerMinion, 1), 7);
    }
  }

  @EventHandler
  public void on(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    Action action = e.getAction();
    if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
      return;
    }

    if (e.getHand() != EquipmentSlot.HAND || e.getItem() == null || e.getMaterial() != Material.BONE) {
      return;
    }

    withAdaptedPlayer(p, e, () -> {
      int level = getActiveLevel(p);
      if (level <= 0) {
        return;
      }

      UUID id = p.getUniqueId();
      long now = System.currentTimeMillis();
      if (!cooldowns.isReady(id, getCooldownMillis(level))) {
        sfx(p.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 0.8F, 0.8F);
        return;
      }

      CopyOnWriteArrayList<Skeleton> list = servants.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>());
      int cap = getServantCap(level);
      if (list.size() >= cap && !getConfig().replaceOldestAtCap) {
        sfx(p.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 0.8F, 1.2F);
        return;
      }

      int boneCost = getBoneCost(level);
      if (p.getGameMode() != GameMode.CREATIVE) {
        if (!p.getInventory().containsAtLeast(new ItemStack(Material.BONE), boneCost)) {
          sfx(p.getLocation(), Sound.BLOCK_CONDUIT_DEACTIVATE, 0.8F, 0.6F);
          return;
        }
        p.getInventory().removeItem(new ItemStack(Material.BONE, boneCost));
      }

      while (list.size() >= cap) {
        Skeleton oldest = list.remove(0);
        UUID oldestId = servantIdsByEntity.get(oldest);
        if (oldestId == null) {
          if (!J.runEntity(oldest, () -> removeServantOwned(id, oldest, true))) {
            burden.unregister(id, oldest);
          }
          continue;
        }
        servantOwners.remove(oldestId);
        servantOwnersByEntity.remove(oldest);
        servantIdsByEntity.remove(oldest);
        if (!J.runEntity(oldest, () -> removeServantOwned(id, oldest, true))) {
          forgetServant(id, oldest, oldestId);
        }
      }

      cooldowns.mark(id);
      int durationTicks = getDurationTicks(level);
      ThreadLocalRandom random = ThreadLocalRandom.current();
      Skeleton servant = p.getWorld().spawn(p.getLocation(), Skeleton.class, s -> {
        StackExclusion.exclude(s);
        s.getPersistentDataContainer().set(SERVANT_KEY, PersistentDataType.STRING, id.toString());
        s.setPersistent(false);
        s.setRemoveWhenFarAway(false);
        s.setShouldBurnInDay(false);
        applyServantAttributes(s, level);
        equipServant(s, level, random);
      });
      list.add(servant);
      UUID servantId = servant.getUniqueId();
      servantOwners.put(servantId, id);
      servantOwnersByEntity.put(servant, id);
      servantIdsByEntity.put(servant, servantId);
      burden.configure(getConfig().healthCostEnabled ? getConfig().healthCostPerMinion : 0, getConfig().minimumOwnerMaxHealth);
      burden.register(p, servant);
      LivingEntity priorityTarget = resolvePriorityTargetOwned(id, p, now);
      if (priorityTarget != null) {
        servant.setTarget(priorityTarget);
      }
      scheduleServantPulse(servant, id, now + (durationTicks * 50L));
      J.runEntity(servant, () -> removeServantOwned(id, servant, true), durationTicks);

      playSummonRitual(servant.getLocation());
      addStat(p, "tragoul.skeletal-servant.servants-summoned", 1);
      xp(p, getConfig().xpPerSummon);
    });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityTargetLivingEntityEvent e) {
    if (!(e.getEntity() instanceof Skeleton skeleton)) {
      return;
    }

    LivingEntity target = e.getTarget();
    if (target == null) {
      return;
    }

    UUID ownerId = resolveServantOwnerOwned(skeleton);
    if (ownerId == null) {
      return;
    }

    if (target instanceof Player player) {
      if (!isPriorityTarget(ownerId, player)) {
        e.setCancelled(true);
        if (skeleton.getTarget() instanceof Player) {
          skeleton.setTarget(null);
        }
      }
      return;
    }

    if (target instanceof Skeleton other && servantOwnersByEntity.containsKey(other)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player victim)) {
      return;
    }

    ServantDamager servant = resolveServantDamager(e.getDamager());
    if (servant == null) {
      return;
    }

    UUID ownerId = servant.ownerId();
    if (victim.getUniqueId().equals(ownerId) || !isPriorityTarget(ownerId, victim)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCombatPerks(EntityDamageByEntityEvent e) {
    Entity entity = e.getEntity();
    if (entity instanceof Skeleton skeleton) {
      UUID ownerId = resolveServantOwnerOwned(skeleton);
      if (ownerId != null) {
        handleServantHit(e, skeleton, ownerId);
        return;
      }
    }

    if (entity instanceof Player ownerCandidate) {
      handleOwnerDamaged(e, ownerCandidate);
    }

    if (!(entity instanceof LivingEntity victim)) {
      return;
    }

    ServantDamager servant = resolveServantDamager(e.getDamager());
    if (servant != null && servant.servant() != victim) {
      handleServantDealtDamage(e, servant, victim);
    }

    handleOwnerAttack(e, victim);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (!(e.getEntity() instanceof Skeleton skeleton)) {
      return;
    }

    UUID ownerId = resolveServantOwnerOwned(skeleton);
    if (ownerId == null) {
      return;
    }

    e.getDrops().clear();
    e.setDroppedExp(0);
    servantThornsCooldowns.remove(skeleton.getUniqueId());
    removeServantRef(ownerId, skeleton);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(EntitiesUnloadEvent event) {
    for (Entity entity : event.getEntities()) {
      if (!(entity instanceof Skeleton skeleton)) {
        continue;
      }
      UUID ownerId = resolveServantOwnerOwned(skeleton);
      if (ownerId == null) {
        continue;
      }
      servantThornsCooldowns.remove(skeleton.getUniqueId());
      removeServantRef(ownerId, skeleton);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onServantKill(EntityDeathEvent e) {
    LivingEntity victim = e.getEntity();
    if (!(victim instanceof Monster)) {
      return;
    }

    if (victim.getPersistentDataContainer().has(SERVANT_KEY, PersistentDataType.STRING)) {
      return;
    }

    if (!(victim.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent)) {
      return;
    }

    ServantDamager servant = resolveServantDamager(damageEvent.getDamager());
    if (servant == null) {
      return;
    }

    Player owner = Bukkit.getPlayer(servant.ownerId());
    if (owner == null) {
      return;
    }

    TragoulCorpseExplosion nova = perks().nova();
    if (nova != null) {
      TragoulCorpseExplosion.detonateServantKill(nova, owner, victim);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    dismissPack(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    dismissPack(e.getEntity().getUniqueId());
  }

  private void dismissPack(UUID id) {
    threats.remove(id);
    latestTargetRequests.remove(id);
    activeTargetRequests.remove(id);
    servantCurseCooldowns.remove(id);
    CopyOnWriteArrayList<Skeleton> list = servants.remove(id);
    if (list == null) {
      return;
    }

    for (Skeleton servant : list) {
      UUID servantId = servantIdsByEntity.get(servant);
      if (servantId == null) {
        if (!J.runEntity(servant, () -> removeServantOwned(id, servant, false))) {
          burden.unregister(id, servant);
        }
        continue;
      }
      servantOwners.remove(servantId);
      servantOwnersByEntity.remove(servant);
      servantIdsByEntity.remove(servant);
      servantThornsCooldowns.remove(servantId);
      pendingLocalRetargets.remove(servantId);
      if (!J.runEntity(servant, () -> removeServantOwned(id, servant, false))) {
        forgetServant(id, servant, servantId);
      }
    }
  }

  private void handleOwnerDamaged(EntityDamageByEntityEvent e, Player owner) {
    LivingEntity attacker = resolveLivingDamager(e.getDamager());
    if (attacker == null || attacker == owner) {
      return;
    }

    assignPackTarget(owner, attacker);
  }

  private void handleOwnerAttack(EntityDamageByEntityEvent e, LivingEntity victim) {
    Player owner = null;
    if (e.getDamager() instanceof Player player) {
      owner = player;
    } else if (e.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
      owner = shooter;
    }

    if (owner == null || owner == victim) {
      return;
    }

    assignPackTarget(owner, victim);
  }

  private void assignPackTarget(Player owner, LivingEntity target) {
    UUID ownerId = owner.getUniqueId();
    CopyOnWriteArrayList<Skeleton> pack = servants.get(ownerId);
    if (pack == null || pack.isEmpty()) {
      return;
    }
    Threat current = threats.get(ownerId);
    TargetRequest queued = latestTargetRequests.get(ownerId);
    long now = System.currentTimeMillis();
    if (queued == null && current != null && current.target() == target) {
      current.refreshStamp(now);
      requestThreatRefresh(current, now);
      return;
    }

    TargetRequest request = new TargetRequest(target, now);
    latestTargetRequests.put(ownerId, request);
    if (activeTargetRequests.add(ownerId)) {
      scheduleTargetRequest(ownerId, owner);
    }
  }

  private void scheduleTargetRequest(UUID ownerId, Player owner) {
    if (!J.runEntity(owner, () -> startTargetRequestOwned(ownerId, owner), TARGET_ASSIGNMENT_DELAY_TICKS)) {
      latestTargetRequests.remove(ownerId);
      activeTargetRequests.remove(ownerId);
    }
  }

  private void startTargetRequestOwned(UUID ownerId, Player owner) {
    TargetRequest request = latestTargetRequests.get(ownerId);
    if (request == null || !owner.isOnline() || getLevel(owner) <= 0) {
      finishTargetRequestOwned(ownerId, owner, request);
      return;
    }

    LivingEntity target = request.target();
    if (!J.runEntity(target, () -> {
      TargetSnapshot snapshot = captureTargetOwned(ownerId, target);
      if (!J.runEntity(owner, () -> completeTargetRequestOwned(ownerId, owner, request, snapshot))) {
        latestTargetRequests.remove(ownerId, request);
        activeTargetRequests.remove(ownerId);
      }
    })) {
      finishTargetRequestOwned(ownerId, owner, request);
      return;
    }
    J.runEntity(owner, () -> expireTargetRequestOwned(ownerId, owner, request), OWNER_HANDOFF_TIMEOUT_TICKS);
  }

  private void completeTargetRequestOwned(UUID ownerId, Player owner, TargetRequest request, TargetSnapshot snapshot) {
    if (latestTargetRequests.get(ownerId) != request) {
      if (latestTargetRequests.containsKey(ownerId)) {
        scheduleTargetRequest(ownerId, owner);
      } else {
        activeTargetRequests.remove(ownerId);
      }
      return;
    }

    if (snapshot != null && owner.isOnline() && getLevel(owner) > 0 && canDamageSnapshotOwned(owner, snapshot)) {
      publishThreatOwned(ownerId, owner, snapshot, request.requestedAt());
    }
    finishTargetRequestOwned(ownerId, owner, request);
  }

  private void expireTargetRequestOwned(UUID ownerId, Player owner, TargetRequest request) {
    if (latestTargetRequests.get(ownerId) == request) {
      finishTargetRequestOwned(ownerId, owner, request);
    }
  }

  private void finishTargetRequestOwned(UUID ownerId, Player owner, TargetRequest completed) {
    if (completed != null) {
      latestTargetRequests.remove(ownerId, completed);
    }
    if (latestTargetRequests.containsKey(ownerId)) {
      scheduleTargetRequest(ownerId, owner);
      return;
    }

    activeTargetRequests.remove(ownerId);
    if (latestTargetRequests.containsKey(ownerId) && activeTargetRequests.add(ownerId)) {
      scheduleTargetRequest(ownerId, owner);
    }
  }

  private void publishThreatOwned(UUID ownerId, Player owner, TargetSnapshot snapshot, long stamp) {
    Threat previous = threats.get(ownerId);
    boolean newTarget = previous == null || previous.target() != snapshot.entity();
    Threat current;
    if (newTarget) {
      current = new Threat(ownerId, owner, snapshot, stamp);
      threats.put(ownerId, current);
    } else {
      previous.update(snapshot, stamp);
      current = previous;
    }

    if (!newTarget) {
      return;
    }

    CopyOnWriteArrayList<Skeleton> list = servants.get(ownerId);
    if (list != null) {
      int dispatched = 0;
      for (Skeleton servant : list) {
        if (dispatched >= MAX_IMMEDIATE_RETARGETS) {
          break;
        }
        J.runEntity(servant, () -> assignPriorityTargetOwned(servant, current, true));
        dispatched++;
      }
    }
    fx(owner.getLocation(), FxPriority.COMBAT).sound(Sound.ENTITY_SKELETON_AMBIENT, 0.7F, 0.5F);
  }

  private void handleServantHit(EntityDamageByEntityEvent e, Skeleton servant, UUID ownerId) {
    LivingEntity attacker = resolveLivingDamager(e.getDamager());
    if (attacker == null || attacker == servant) {
      return;
    }

    if (attacker instanceof Skeleton other && servantOwnersByEntity.containsKey(other)) {
      return;
    }

    Player owner = Bukkit.getPlayer(ownerId);
    if (owner == null) {
      return;
    }

    long now = System.currentTimeMillis();
    UUID servantId = servant.getUniqueId();
    if (!J.runEntity(attacker, () -> {
      TargetSnapshot snapshot = captureTargetOwned(ownerId, attacker);
      if (snapshot != null) {
        J.runEntity(owner, () -> prepareDefensePerksOwned(owner, servant, servantId, snapshot, now));
      }
    })) {
      servantThornsCooldowns.remove(servantId);
    }
  }

  private void prepareDefensePerksOwned(Player owner, Skeleton servant, UUID servantId, TargetSnapshot attacker, long now) {
    if (!owner.isOnline() || !canDamageSnapshotOwned(owner, attacker)) {
      return;
    }

    Threat threat = threats.get(owner.getUniqueId());
    if (threat != null && threat.target() == attacker.entity() && now - threat.stamp() < getThreatWindowMillis()) {
      threat.update(attacker, now);
    }

    PerkRefs refs = perks();
    boolean thornsApplied = false;
    double reflectedDamage = 0D;
    TragoulThorns thorns = refs.thorns();
    Long thornsUntil = servantThornsCooldowns.get(servantId);
    if (thorns != null && (thornsUntil == null || thornsUntil <= now)) {
      int level = thorns.getActiveLevel(owner);
      if (level > 0) {
        servantThornsCooldowns.put(servantId, now + 1500L);
        reflectedDamage = thorns.getConfig().damageMultiplierPerLevel * level;
        thornsApplied = reflectedDamage > 0D;
      }
    }

    boolean frailtyApplied = false;
    int frailtyDuration = 0;
    int weaknessAmplifier = 0;
    boolean slowness = false;
    int slownessAmplifier = 0;
    TragoulCurseOfFrailty frailty = refs.frailty();
    Long curseUntil = servantCurseCooldowns.get(attacker.entityId());
    if (frailty != null && (curseUntil == null || curseUntil <= now)) {
      int level = frailty.getActiveLevel(owner);
      if (level > 0) {
        TragoulCurseOfFrailty.Config config = frailty.getConfig();
        servantCurseCooldowns.put(attacker.entityId(), now + config.perAttackerCooldownMillis);
        double levelPercent = frailty.getLevelPercent(level);
        frailtyDuration = Math.max(40, (int) Math.round(config.curseDurationTicksBase + (levelPercent * config.curseDurationTicksFactor)));
        weaknessAmplifier = levelPercent >= 0.8D ? 1 : 0;
        slowness = levelPercent >= config.slownessUnlockPercent;
        slownessAmplifier = config.slownessAmplifier;
        frailtyApplied = true;
      }
    }

    if (!thornsApplied && !frailtyApplied) {
      return;
    }

    DefenseEffect effect = new DefenseEffect(owner, reflectedDamage, thornsApplied, frailtyApplied,
        frailtyDuration, weaknessAmplifier, slowness, slownessAmplifier);
    J.runEntity(attacker.entity(), () -> applyDefenseEffectOwned(attacker, effect));
    J.runEntity(servant, () -> playDefenseEffectOwned(servant, servantId, effect));
  }

  private void applyDefenseEffectOwned(TargetSnapshot attacker, DefenseEffect effect) {
    LivingEntity target = attacker.entity();
    if (!target.isValid() || target.isDead()) {
      return;
    }

    if (effect.thornsApplied()) {
      target.damage(effect.reflectedDamage(), effect.owner());
    }
    if (effect.frailtyApplied()) {
      target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, effect.frailtyDuration(), effect.weaknessAmplifier(), true, true, true));
      if (effect.slowness()) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, effect.frailtyDuration(), effect.slownessAmplifier(), true, true, true));
      }
    }
  }

  private void playDefenseEffectOwned(Skeleton servant, UUID servantId, DefenseEffect effect) {
    if (!servant.isValid() || servant.isDead() || !servantOwners.containsKey(servantId)) {
      return;
    }
    Location location = servant.getLocation().add(0, 1.0D, 0);
    if (effect.thornsApplied()) {
      fx(location, FxPriority.TRAIL).particle(Particle.CRIT, 2, 0, 0, 0, 0.15D, 0.02D);
    }
    if (effect.frailtyApplied()) {
      fx(location, FxPriority.TRAIL).particle(Particle.WARPED_SPORE, 2, 0, 0, 0, 0.15D, 0.02D);
    }
  }

  private void handleServantDealtDamage(EntityDamageByEntityEvent event, ServantDamager servant, LivingEntity victim) {
    Player owner = Bukkit.getPlayer(servant.ownerId());
    if (owner == null) {
      return;
    }

    EntityDamageEvent.DamageCause cause = event.getCause();
    double finalDamage = event.getFinalDamage();
    J.runEntity(victim, () -> {
      TargetSnapshot snapshot = captureTargetOwned(servant.ownerId(), victim);
      if (snapshot != null) {
        J.runEntity(owner, () -> prepareOffensePerksOwned(owner, servant.servant(), snapshot, cause, finalDamage));
      }
    });
  }

  private void prepareOffensePerksOwned(Player owner, Skeleton servant, TargetSnapshot victim,
                                        EntityDamageEvent.DamageCause cause, double finalDamage) {
    if (!owner.isOnline() || !canDamageSnapshotOwned(owner, victim)) {
      return;
    }

    PerkRefs refs = perks();
    double heal = 0D;
    TragoulSoulSiphon siphon = refs.siphon();
    if (siphon != null && (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
        || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
      int level = siphon.getActiveLevel(owner);
      if (level > 0) {
        TragoulSoulSiphon.Config config = siphon.getConfig();
        double levelPercent = siphon.getLevelPercent(level);
        double percent = Math.max(0D, config.healPercentBase + (levelPercent * config.healPercentFactor));
        double cap = Math.max(0.5D, config.healCapPerSecondBase + (levelPercent * config.healCapPerSecondFactor));
        heal = Math.min(cap, finalDamage * percent);
      }
    }

    boolean plague = false;
    TragoulPlagueBearer plagueBearer = refs.plague();
    if (plagueBearer != null && victim.monster() && victim.afflicted()) {
      plague = plagueBearer.getActiveLevel(owner) > 0;
    }

    if (heal <= 0D && !plague) {
      return;
    }

    OffenseEffect effect = new OffenseEffect(owner.getUniqueId(), heal, plague);
    J.runEntity(servant, () -> applyOffenseToServantOwned(servant, effect));
    if (plague) {
      J.runEntity(victim.entity(), () -> applyPlagueOwned(victim, effect.ownerId()));
    }
  }

  private void applyOffenseToServantOwned(Skeleton servant, OffenseEffect effect) {
    if (!servantOwnersByEntity.containsKey(servant) || !servant.isValid() || servant.isDead()) {
      return;
    }

    if (effect.heal() > 0D) {
      IAttribute attribute = Version.get().getAttribute(servant, Attributes.GENERIC_MAX_HEALTH);
      double maxHealth = attribute == null ? 20D : attribute.getValue();
      double currentHealth = servant.getHealth();
      double newHealth = Math.min(maxHealth, currentHealth + effect.heal());
      if (newHealth > currentHealth) {
        servant.setHealth(newHealth);
        fx(servant.getLocation().add(0, 1.2D, 0), FxPriority.TRAIL).particle(Particle.SOUL, 4, 0, 0, 0, 0.2D, 0.02D);
      }
    }
    if (effect.plague()) {
      fx(servant.getLocation().add(0, 1.0D, 0), FxPriority.TRAIL).particle(Particle.SPORE_BLOSSOM_AIR, 2, 0, 0, 0, 0.15D, 0.02D);
    }
  }

  private void applyPlagueOwned(TargetSnapshot victim, UUID ownerId) {
    LivingEntity target = victim.entity();
    if (!(target instanceof Monster monster) || !monster.isValid() || monster.isDead()) {
      return;
    }

    PersistentDataContainer pdc = monster.getPersistentDataContainer();
    pdc.set(PLAGUE_OWNER_KEY, PersistentDataType.STRING, ownerId.toString());
    pdc.set(PLAGUE_STAMP_KEY, PersistentDataType.LONG, System.currentTimeMillis());
    if (!pdc.has(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER)) {
      pdc.set(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER, 0);
    }
  }

  private void scheduleServantPulse(Skeleton servant, UUID ownerId, long expiresAt) {
    if (!J.runEntity(servant, () -> {
      UUID servantId = servant.getUniqueId();
      if (!ownerId.equals(servantOwners.get(servantId))) {
        return;
      }

      if (!servant.isValid() || servant.isDead() || System.currentTimeMillis() >= expiresAt) {
        removeServantOwned(ownerId, servant, true);
        return;
      }

      retargetOwned(servant, servantId, ownerId);
      scheduleServantPulse(servant, ownerId, expiresAt);
    }, getRetargetIntervalTicks())) {
      UUID servantId = servant.getUniqueId();
      forgetServant(ownerId, servant, servantId);
    }
  }

  private void retargetOwned(Skeleton servant, UUID servantId, UUID ownerId) {
    long now = System.currentTimeMillis();
    Threat threat = threats.get(ownerId);
    if (threat != null && now - threat.stamp() < getThreatWindowMillis()) {
      requestThreatRefresh(threat, now);
      if (assignPriorityTargetOwned(servant, threat, false)) {
        return;
      }
    }

    LivingEntity current = servant.getTarget();
    if (current instanceof Monster && !isTrackedServant(current)) {
      return;
    }
    if (current != null) {
      servant.setTarget(null);
    }

    if (!tryAcquireRetargetSearch()) {
      return;
    }

    double range = getTargetSearchRadius();
    int desiredCandidate = Math.floorMod(servantId.hashCode() + (int) (now / 500L), MAX_LOCAL_TARGET_CANDIDATES);
    Monster first = null;
    Monster candidate = null;
    int candidateIndex = 0;
    for (Entity entity : servant.getNearbyEntities(range, range, range)) {
      if (!(entity instanceof Monster monster) || isTrackedServant(monster)) {
        continue;
      }
      if (first == null) {
        first = monster;
      }
      if (candidateIndex == desiredCandidate) {
        candidate = monster;
        break;
      }
      candidateIndex++;
      if (candidateIndex >= MAX_LOCAL_TARGET_CANDIDATES) {
        break;
      }
    }

    if (candidate == null) {
      candidate = first;
    }
    if (candidate == null) {
      return;
    }

    long token = localRetargetSequence.incrementAndGet();
    if (pendingLocalRetargets.putIfAbsent(servantId, token) != null) {
      return;
    }
    Monster selected = candidate;
    if (!J.runEntity(selected, () -> {
      TargetSnapshot snapshot = captureTargetOwned(ownerId, selected);
      Player owner = Bukkit.getPlayer(ownerId);
      if (snapshot == null || owner == null
          || !J.runEntity(owner, () -> completeLocalRetargetOwnerOwned(owner, servant, servantId, token, snapshot))) {
        pendingLocalRetargets.remove(servantId, token);
      }
    })) {
      pendingLocalRetargets.remove(servantId, token);
      return;
    }
    J.runEntity(servant, () -> pendingLocalRetargets.remove(servantId, token), OWNER_HANDOFF_TIMEOUT_TICKS);
  }

  private void completeLocalRetargetOwnerOwned(Player owner, Skeleton servant, UUID servantId, long token, TargetSnapshot target) {
    if (!owner.isOnline() || getLevel(owner) <= 0 || !canDamageSnapshotOwned(owner, target)) {
      pendingLocalRetargets.remove(servantId, token);
      return;
    }

    UUID ownerId = owner.getUniqueId();
    if (!J.runEntity(servant, () -> completeLocalRetargetServantOwned(servant, servantId, token, ownerId, target))) {
      pendingLocalRetargets.remove(servantId, token);
    }
  }

  private void completeLocalRetargetServantOwned(Skeleton servant, UUID servantId, long token,
                                                 UUID ownerId, TargetSnapshot target) {
    try {
      if (!Long.valueOf(token).equals(pendingLocalRetargets.get(servantId))) {
        return;
      }
      if (!ownerId.equals(servantOwners.get(servantId)) || !servant.isValid() || servant.isDead()) {
        return;
      }

      Threat threat = threats.get(ownerId);
      if (threat != null && System.currentTimeMillis() - threat.stamp() < getThreatWindowMillis()
          && assignPriorityTargetOwned(servant, threat, false)) {
        return;
      }

      LivingEntity current = servant.getTarget();
      if (current instanceof Monster && !isTrackedServant(current)) {
        return;
      }
      if (current != null) {
        servant.setTarget(null);
      }
      if (servant.getWorld().getUID().equals(target.worldId())) {
        servant.setTarget(target.entity());
      }
    } finally {
      pendingLocalRetargets.remove(servantId, token);
    }
  }

  private boolean assignPriorityTargetOwned(Skeleton servant, Threat threat, boolean showFx) {
    if (threats.get(threat.ownerId()) != threat || System.currentTimeMillis() - threat.stamp() >= getThreatWindowMillis()) {
      return false;
    }

    TargetSnapshot snapshot = threat.snapshot();
    if (!servant.getWorld().getUID().equals(snapshot.worldId())) {
      return false;
    }

    if (servant.getTarget() != snapshot.entity()) {
      servant.setTarget(snapshot.entity());
    }
    if (showFx) {
      fx(servant.getLocation().add(0, 1.0D, 0), FxPriority.COMBAT).particle(Particle.SCULK_SOUL, 3, 0, 0, 0, 0.2D, 0.02D);
    }
    return true;
  }

  private void requestThreatRefresh(Threat threat, long now) {
    if (!threat.beginRefresh(now)) {
      return;
    }

    LivingEntity target = threat.target();
    if (!J.runEntity(target, () -> {
      TargetSnapshot snapshot = captureTargetOwned(threat.ownerId(), target);
      Player owner = threat.owner();
      if (!J.runEntity(owner, () -> completeThreatRefreshOwnerOwned(threat, snapshot))) {
        threat.finishRefresh(System.currentTimeMillis());
        threats.remove(threat.ownerId(), threat);
      }
    })) {
      threat.finishRefresh(now);
      threats.remove(threat.ownerId(), threat);
    }
  }

  private void completeThreatRefreshOwnerOwned(Threat threat, TargetSnapshot snapshot) {
    long now = System.currentTimeMillis();
    try {
      if (threats.get(threat.ownerId()) != threat) {
        return;
      }
      Player owner = threat.owner();
      if (snapshot == null || !owner.isOnline() || getLevel(owner) <= 0 || !canDamageSnapshotOwned(owner, snapshot)) {
        threats.remove(threat.ownerId(), threat);
        return;
      }
      threat.updateSnapshot(snapshot);
    } finally {
      threat.finishRefresh(now);
    }
  }

  private TargetSnapshot captureTargetOwned(UUID ownerId, LivingEntity target) {
    if (!target.isValid() || target.isDead()) {
      return null;
    }

    UUID targetId = target.getUniqueId();
    boolean protectedFriendly = ownerId.equals(targetId)
        || (target instanceof ArmorStand stand && stand.isMarker())
        || target.isInvulnerable()
        || target.hasMetadata("NPC")
        || isServant(target);
    if (!protectedFriendly && target instanceof Tameable tameable && tameable.isTamed()) {
      AnimalTamer tamer = tameable.getOwner();
      protectedFriendly = tamer != null && ownerId.equals(tamer.getUniqueId());
    }

    boolean afflicted = target.hasPotionEffect(PotionEffectType.POISON)
        || target.hasPotionEffect(PotionEffectType.WITHER);
    Location location = target.getLocation();
    return new TargetSnapshot(target, targetId, location.getWorld().getUID(), location,
        target instanceof Player, target instanceof Monster, protectedFriendly, afflicted);
  }

  private boolean canDamageSnapshotOwned(Player owner, TargetSnapshot target) {
    if (target.protectedFriendly() || owner.getUniqueId().equals(target.entityId())) {
      return false;
    }
    return target.player() ? canPVP(owner, target.location()) : canPVE(owner, target.location());
  }

  private void removeServantOwned(UUID ownerId, Skeleton servant, boolean showFx) {
    UUID servantId = servant.getUniqueId();
    forgetServant(ownerId, servant, servantId);
    if (servant.isValid() && !servant.isDead()) {
      if (showFx) {
        fx(servant.getLocation().add(0, 1.0D, 0), FxPriority.TRANSITION)
            .particle(Particle.SOUL, 4, 0, 0.4D, 0, 0.3D, 0.03D)
            .dustBurst(SERVANT_BONE, 4, 0.3D, 1.0F)
            .chord(Sound.ENTITY_SKELETON_DEATH, 0.7F, 1.3F, Sound.BLOCK_BONE_BLOCK_BREAK, 0.4F, 0.9F);
      }
      servant.remove();
    }
  }

  private void forgetServant(UUID ownerId, Skeleton servant, UUID servantId) {
    servantOwners.remove(servantId, ownerId);
    servantOwnersByEntity.remove(servant, ownerId);
    servantIdsByEntity.remove(servant, servantId);
    servantThornsCooldowns.remove(servantId);
    pendingLocalRetargets.remove(servantId);
    CopyOnWriteArrayList<Skeleton> list = servants.get(ownerId);
    if (list != null) {
      list.remove(servant);
      if (list.isEmpty()) {
        servants.remove(ownerId, list);
      }
    }
    burden.unregister(ownerId, servant);
  }

  private void playSummonRitual(Location spawn) {
    timeline(spawn)
        .duration(12)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          if (tick < 4) {
            fx.dustRing(SERVANT_BONE, 1.2 - (0.2 * tick), 12, 0.1F);
          } else {
            double rise = (tick - 4) * 0.15;
            fx.particle(Particle.SOUL, 3, 0, 0.5 + rise, 0, 0.25, 0.03);
            fx.particle(Particle.SCULK_SOUL, 2, 0, 0.5 + rise, 0, 0.2, 0.02);
          }
          if (tick == 0) {
            fx.sound(Sound.BLOCK_BONE_BLOCK_PLACE, 0.6F, 0.7F);
          } else if (tick == 11) {
            fx.sound(Sound.ENTITY_SKELETON_AMBIENT, 0.9F, 0.7F);
          }
        })
        .start();
  }

  private void removeServantRef(UUID ownerId, Skeleton servant) {
    forgetServant(ownerId, servant, servant.getUniqueId());
  }

  private LivingEntity resolvePriorityTargetOwned(UUID ownerId, Player owner, long now) {
    Threat threat = threats.get(ownerId);
    if (threat == null || now - threat.stamp() >= getThreatWindowMillis()) {
      return null;
    }

    TargetSnapshot target = threat.snapshot();
    if (!target.worldId().equals(owner.getWorld().getUID()) || !canDamageSnapshotOwned(owner, target)) {
      return null;
    }
    requestThreatRefresh(threat, now);
    return target.entity();
  }

  private boolean isPriorityTarget(UUID ownerId, LivingEntity candidate) {
    Threat threat = threats.get(ownerId);
    return threat != null
        && threat.target() == candidate
        && System.currentTimeMillis() - threat.stamp() < getThreatWindowMillis();
  }

  private void applyServantAttributes(Skeleton servant, int level) {
    IAttribute maxHealth = Version.get().getAttribute(servant, Attributes.GENERIC_MAX_HEALTH);
    if (maxHealth != null) {
      maxHealth.setBaseValue(maxHealth.getBaseValue() + (level * getConfig().healthBonusPerLevel));
      servant.setHealth(maxHealth.getValue());
    }

    IAttribute attack = Version.get().getAttribute(servant, Attributes.GENERIC_ATTACK_DAMAGE);
    if (attack != null) {
      attack.setBaseValue(attack.getBaseValue() + (level * getConfig().attackBonusPerLevel));
    }
  }

  private void equipServant(Skeleton servant, int level, ThreadLocalRandom random) {
    EntityEquipment equipment = servant.getEquipment();
    if (equipment == null) {
      return;
    }

    int tier = getGearTier(level);
    double pieceChance = getConfig().gearChancePerPiece;
    double enchantChance = tier == 0 ? 0 : Math.max(0, getConfig().enchantChanceBase + (getLevelPercent(level) * getConfig().enchantChanceFactor));
    if (random.nextDouble() < pieceChance) {
      equipment.setHelmet(rollPiece(random, HELMETS[tier], enchantChance, Enchantment.PROTECTION));
    }
    if (random.nextDouble() < pieceChance) {
      equipment.setChestplate(rollPiece(random, CHESTPLATES[tier], enchantChance, Enchantment.PROTECTION));
    }
    if (random.nextDouble() < pieceChance) {
      equipment.setLeggings(rollPiece(random, LEGGINGS[tier], enchantChance, Enchantment.PROTECTION));
    }
    if (random.nextDouble() < pieceChance) {
      equipment.setBoots(rollPiece(random, BOOTS[tier], enchantChance, Enchantment.PROTECTION));
    }

    ItemStack weapon = random.nextDouble() < getConfig().bowChance
        ? rollPiece(random, BOW_POOL, enchantChance, Enchantment.POWER)
        : rollPiece(random, SWORDS[tier], enchantChance, Enchantment.SHARPNESS);
    equipment.setItemInMainHand(weapon);

    equipment.setHelmetDropChance(0f);
    equipment.setChestplateDropChance(0f);
    equipment.setLeggingsDropChance(0f);
    equipment.setBootsDropChance(0f);
    equipment.setItemInMainHandDropChance(0f);
    equipment.setItemInOffHandDropChance(0f);
  }

  private ItemStack rollPiece(ThreadLocalRandom random, Material[] pool, double enchantChance, Enchantment enchantment) {
    Material material = pool[random.nextInt(pool.length)];
    ItemStack item = new ItemStack(material);
    if (enchantChance > 0 && random.nextDouble() < enchantChance) {
      item.addEnchantment(enchantment, 1 + random.nextInt(2));
    }
    return item;
  }

  private int getGearTier(int level) {
    return switch (Math.min(Math.max(level, 1), 5)) {
      case 1, 2 -> 0;
      case 3, 4 -> 1;
      default -> 2;
    };
  }

  private PerkRefs perks() {
    PerkRefs local = perkRefs;
    if (local != null) {
      return local;
    }

    if (getSkill() == null) {
      return new PerkRefs(null, null, null, null, null);
    }

    TragoulThorns thornsRef = null;
    TragoulSoulSiphon siphonRef = null;
    TragoulCurseOfFrailty frailtyRef = null;
    TragoulCorpseExplosion novaRef = null;
    TragoulPlagueBearer plagueRef = null;
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (adaptation instanceof TragoulThorns found) {
        thornsRef = found;
      } else if (adaptation instanceof TragoulSoulSiphon found) {
        siphonRef = found;
      } else if (adaptation instanceof TragoulCurseOfFrailty found) {
        frailtyRef = found;
      } else if (adaptation instanceof TragoulCorpseExplosion found) {
        novaRef = found;
      } else if (adaptation instanceof TragoulPlagueBearer found) {
        plagueRef = found;
      }
    }

    local = new PerkRefs(thornsRef, siphonRef, frailtyRef, novaRef, plagueRef);
    perkRefs = local;
    return local;
  }

  private static LivingEntity resolveLivingDamager(Entity damager) {
    if (damager instanceof LivingEntity living) {
      return living;
    }

    if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
      return shooter;
    }

    return null;
  }

  private ServantDamager resolveServantDamager(Entity damager) {
    Entity source = damager;
    if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
      source = shooter;
    }

    if (source instanceof Skeleton skeleton) {
      UUID ownerId = servantOwnersByEntity.get(skeleton);
      if (ownerId != null) {
        return new ServantDamager(skeleton, ownerId);
      }
    }

    return null;
  }

  private UUID resolveServantOwnerOwned(Skeleton servant) {
    UUID ownerId = servantOwnersByEntity.get(servant);
    if (ownerId != null) {
      return ownerId;
    }

    String raw = servant.getPersistentDataContainer().get(SERVANT_KEY, PersistentDataType.STRING);
    if (raw == null) {
      return null;
    }
    ownerId = UUID.fromString(raw);
    UUID servantId = servant.getUniqueId();
    servantOwners.put(servantId, ownerId);
    servantOwnersByEntity.put(servant, ownerId);
    servantIdsByEntity.put(servant, servantId);
    return ownerId;
  }

  private boolean isTrackedServant(LivingEntity entity) {
    return entity instanceof Skeleton skeleton && servantOwnersByEntity.containsKey(skeleton);
  }

  private boolean tryAcquireRetargetSearch() {
    long epoch = System.currentTimeMillis() / 50L;
    while (true) {
      long state = retargetBudgetState.get();
      long stateEpoch = state >>> 16;
      long count = state & RETARGET_BUDGET_COUNT_MASK;
      long next;
      if (stateEpoch != epoch) {
        next = (epoch << 16) | 1L;
      } else {
        if (count >= MAX_RETARGET_SEARCHES_PER_TICK) {
          return false;
        }
        next = state + 1L;
      }
      if (retargetBudgetState.compareAndSet(state, next)) {
        return true;
      }
    }
  }

  private int getServantCap(int level) {
    int configured = Math.max(1, (int) Math.round(level * getConfig().servantCapPerLevel));
    return Math.min(HARD_MAX_SERVANTS_PER_OWNER, configured);
  }

  private int getBoneCost(int level) {
    return Math.max(1, (int) Math.round(getConfig().boneCostBase - (getLevelPercent(level) * getConfig().boneCostReduction)));
  }

  private int getDurationTicks(int level) {
    return Math.max(100, (int) Math.round(getConfig().durationTicksBase + (getLevelPercent(level) * getConfig().durationTicksFactor)));
  }

  private long getCooldownMillis(int level) {
    return Math.max(1000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  private int getRetargetIntervalTicks() {
    return Math.max(MIN_RETARGET_INTERVAL_TICKS, getConfig().retargetIntervalTicks);
  }

  private double getTargetSearchRadius() {
    return Math.min(HARD_MAX_TARGET_SEARCH_RADIUS, Math.max(1D, getConfig().targetSearchRadius));
  }

  private long getThreatWindowMillis() {
    return Math.max(0L, getConfig().playerThreatWindowMillis);
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    servantCurseCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    servantThornsCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    long window = getThreatWindowMillis();
    threats.entrySet().removeIf(entry -> now - entry.getValue().stamp() > window);
  }

  private static final class Threat {
    private final UUID ownerId;
    private final Player owner;
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private volatile TargetSnapshot snapshot;
    private volatile long stamp;
    private volatile long nextRefreshAt;

    private Threat(UUID ownerId, Player owner, TargetSnapshot snapshot, long stamp) {
      this.ownerId = ownerId;
      this.owner = owner;
      this.snapshot = snapshot;
      this.stamp = stamp;
      this.nextRefreshAt = stamp + THREAT_REFRESH_MILLIS;
    }

    private UUID ownerId() {
      return ownerId;
    }

    private Player owner() {
      return owner;
    }

    private LivingEntity target() {
      return snapshot.entity();
    }

    private TargetSnapshot snapshot() {
      return snapshot;
    }

    private long stamp() {
      return stamp;
    }

    private void refreshStamp(long refreshedAt) {
      stamp = refreshedAt;
    }

    private void update(TargetSnapshot refreshed, long refreshedAt) {
      snapshot = refreshed;
      stamp = refreshedAt;
    }

    private void updateSnapshot(TargetSnapshot refreshed) {
      snapshot = refreshed;
    }

    private boolean beginRefresh(long now) {
      return now >= nextRefreshAt && refreshPending.compareAndSet(false, true);
    }

    private void finishRefresh(long now) {
      nextRefreshAt = now + THREAT_REFRESH_MILLIS;
      refreshPending.set(false);
    }
  }

  private record TargetRequest(LivingEntity target, long requestedAt) {
  }

  private record TargetSnapshot(LivingEntity entity, UUID entityId, UUID worldId, Location location,
                                boolean player, boolean monster, boolean protectedFriendly, boolean afflicted) {
  }

  private record ServantDamager(Skeleton servant, UUID ownerId) {
  }

  private record DefenseEffect(Player owner, double reflectedDamage, boolean thornsApplied,
                               boolean frailtyApplied, int frailtyDuration, int weaknessAmplifier,
                               boolean slowness, int slownessAmplifier) {
  }

  private record OffenseEffect(UUID ownerId, double heal, boolean plague) {
  }

  private record PerkRefs(TragoulThorns thorns, TragoulSoulSiphon siphon,
                          TragoulCurseOfFrailty frailty,
                          TragoulCorpseExplosion nova,
                          TragoulPlagueBearer plague) {
  }

  @ConfigDescription("Sneak right-click with bones to raise a pack of temporary skeletal servants that gear up with your level, inherit your Tragoul perks, and hunt whatever you strike or whoever strikes you.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bones consumed per summon before level scaling.", impact = "Higher values make summoning more expensive at low levels.")
    double boneCostBase = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bone cost reduction granted at max level.", impact = "Higher values make summoning cheaper as the player levels.")
    double boneCostReduction = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Servant lifetime in ticks before level scaling.", impact = "Higher values keep each servant alive longer.")
    double durationTicksBase = 400;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional servant lifetime ticks granted at max level.", impact = "Higher values increase the level-scaled lifetime growth.")
    double durationTicksFactor = 800;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Summon cooldown in milliseconds before level scaling.", impact = "Higher values slow how often a servant can be summoned.")
    double cooldownMillisBase = 10000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values let high levels summon more often.")
    double cooldownMillisFactor = 9000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Living servants allowed per adaptation level, with a hard runtime ceiling of 16 per owner.", impact = "Higher values let one necromancer field a larger pack up to the server-safety ceiling.")
    double servantCapPerLevel = 1.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Replaces the oldest living servant when summoning at the cap.", impact = "False quietly refuses the summon instead of recycling the oldest servant.")
    boolean replaceOldestAtCap = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Window in milliseconds during which the entity the owner last hit or was hit by stays the pack's priority target.", impact = "Higher values keep servants hunting the marked target for longer after the last combat event.")
    long playerThreatWindowMillis = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance for each armor piece to be equipped on a freshly summoned servant.", impact = "Higher values produce more heavily armored servants.")
    double gearChancePerPiece = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base chance for an equipped piece to receive an enchantment at mid gear tiers.", impact = "Higher values enchant servant gear more often before level scaling.")
    double enchantChanceBase = 0.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional enchant chance granted at max level.", impact = "Higher values make high-level servants spawn with enchanted gear more often.")
    double enchantChanceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Chance a servant spawns with a bow instead of a sword.", impact = "Higher values produce more ranged servants.")
    double bowChance = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus max health granted to servants per adaptation level.", impact = "Higher values make servants tankier as the owner levels.")
    double healthBonusPerLevel = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus attack damage granted to servants per adaptation level.", impact = "Higher values make servants hit harder as the owner levels.")
    double attackBonusPerLevel = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks between servant retarget pulses, clamped to at least 10 ticks.", impact = "Lower values retarget faster but cannot exceed the server-safety cadence.")
    int retargetIntervalTicks = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius the servant scans for hostile mobs to attack, capped at 24 blocks.", impact = "Higher values let the servant acquire targets further away up to the bounded search radius.")
    double targetSearchRadius = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per servant summon.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerSummon = 30;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables the owner max health upkeep while servants are alive.", impact = "False lets the necromancer field servants with no health cost.")
    boolean healthCostEnabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Max health removed from the owner per living servant.", impact = "Higher values make maintaining a large pack of servants more punishing.")
    double healthCostPerMinion = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest max health the servant upkeep can reduce the owner to.", impact = "Higher values guarantee the owner keeps more health while servants are active.")
    double minimumOwnerMaxHealth = 4.0;

    public Config() {
      baseCost = 5;
      costFactor = 0.75;
      initialCost = 5;
    }
  }
}
