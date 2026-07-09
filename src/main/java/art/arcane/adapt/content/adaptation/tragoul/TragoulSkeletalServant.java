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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
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
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class TragoulSkeletalServant extends SimpleAdaptation<TragoulSkeletalServant.Config> {
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
  private final Cooldowns cooldowns = cooldowns();
  private final MinionBurden burden = MinionBurden.get();

  public static boolean isServant(org.bukkit.entity.Entity entity) {
    return entity.getPersistentDataContainer().has(SERVANT_KEY, PersistentDataType.STRING);
  }
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
      list.removeIf(servant -> !servant.isValid() || servant.isDead());
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
        J.runEntity(oldest, () -> despawnServant(oldest));
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
      burden.configure(getConfig().healthCostEnabled ? getConfig().healthCostPerMinion : 0, getConfig().minimumOwnerMaxHealth);
      burden.register(p, servant);
      LivingEntity priorityTarget = resolvePriorityTarget(id, p, now);
      if (priorityTarget != null) {
        servant.setTarget(priorityTarget);
      }
      scheduleServantPulse(servant, id, now + (durationTicks * 50L));
      J.runEntity(servant, () -> despawnServant(servant), durationTicks);

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

    String ownerRaw = skeleton.getPersistentDataContainer().get(SERVANT_KEY, PersistentDataType.STRING);
    if (ownerRaw == null) {
      return;
    }

    if (target instanceof Player player) {
      UUID ownerId = UUID.fromString(ownerRaw);
      if (player.getUniqueId().equals(ownerId) || !isPriorityTarget(ownerId, player)) {
        e.setCancelled(true);
        if (skeleton.getTarget() instanceof Player) {
          skeleton.setTarget(null);
        }
      }
      return;
    }

    if (target instanceof Skeleton other && other.getPersistentDataContainer().has(SERVANT_KEY, PersistentDataType.STRING)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Player victim)) {
      return;
    }

    Skeleton servant = resolveServantDamager(e.getDamager());
    if (servant == null) {
      return;
    }

    String ownerRaw = servant.getPersistentDataContainer().get(SERVANT_KEY, PersistentDataType.STRING);
    if (ownerRaw == null) {
      return;
    }

    UUID ownerId = UUID.fromString(ownerRaw);
    if (victim.getUniqueId().equals(ownerId) || !isPriorityTarget(ownerId, victim)) {
      e.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onCombatPerks(EntityDamageByEntityEvent e) {
    Entity entity = e.getEntity();
    if (entity instanceof Skeleton skeleton) {
      String ownerRaw = skeleton.getPersistentDataContainer().get(SERVANT_KEY, PersistentDataType.STRING);
      if (ownerRaw != null) {
        handleServantHit(e, skeleton, ownerRaw);
        return;
      }
    }

    if (entity instanceof Player ownerCandidate) {
      handleOwnerDamaged(e, ownerCandidate);
    }

    if (!(entity instanceof LivingEntity victim)) {
      return;
    }

    Skeleton servant = resolveServantDamager(e.getDamager());
    if (servant != null && servant != victim) {
      handleServantDealtDamage(e, servant, victim);
    }

    handleOwnerAttack(e, victim);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (!(e.getEntity() instanceof Skeleton skeleton)) {
      return;
    }

    String ownerRaw = skeleton.getPersistentDataContainer().get(SERVANT_KEY, PersistentDataType.STRING);
    if (ownerRaw == null) {
      return;
    }

    e.getDrops().clear();
    e.setDroppedExp(0);
    servantThornsCooldowns.remove(skeleton.getUniqueId());
    removeServantRef(UUID.fromString(ownerRaw), skeleton);
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

    Skeleton servant = resolveServantDamager(damageEvent.getDamager());
    if (servant == null) {
      return;
    }

    String ownerRaw = servant.getPersistentDataContainer().get(SERVANT_KEY, PersistentDataType.STRING);
    if (ownerRaw == null) {
      return;
    }

    Player owner = Bukkit.getPlayer(UUID.fromString(ownerRaw));
    if (owner == null || !owner.isOnline()) {
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
    servantCurseCooldowns.remove(id);
    CopyOnWriteArrayList<Skeleton> list = servants.remove(id);
    if (list == null) {
      return;
    }

    for (Skeleton servant : list) {
      servantThornsCooldowns.remove(servant.getUniqueId());
      J.runEntity(servant, () -> {
        if (servant.isValid() && !servant.isDead()) {
          servant.remove();
        }
      });
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
    if (target.getPersistentDataContainer().has(SERVANT_KEY, PersistentDataType.STRING)) {
      return;
    }

    if (getLevel(owner) <= 0) {
      return;
    }

    if (!canDamageTarget(owner, target)) {
      return;
    }

    UUID ownerId = owner.getUniqueId();
    Threat previous = threats.get(ownerId);
    boolean newTarget = previous == null || !previous.targetId().equals(target.getUniqueId());
    threats.put(ownerId, new Threat(target.getUniqueId(), System.currentTimeMillis()));
    CopyOnWriteArrayList<Skeleton> list = servants.get(ownerId);
    if (list == null || list.isEmpty()) {
      return;
    }

    for (Skeleton servant : list) {
      J.runEntity(servant, () -> {
        if (servant.isValid() && !servant.isDead() && target.isValid() && !target.isDead() && servant.getWorld() == target.getWorld()) {
          servant.setTarget(target);
          if (newTarget) {
            fx(servant.getLocation().add(0, 1.0, 0), FxPriority.COMBAT).particle(Particle.SCULK_SOUL, 3, 0, 0, 0, 0.2, 0.02);
          }
        }
      });
    }
    if (newTarget) {
      fx(owner.getLocation(), FxPriority.COMBAT).sound(Sound.ENTITY_SKELETON_AMBIENT, 0.7F, 0.5F);
    }
  }

  private void handleServantHit(EntityDamageByEntityEvent e, Skeleton servant, String ownerRaw) {
    LivingEntity attacker = resolveLivingDamager(e.getDamager());
    if (attacker == null || attacker == servant) {
      return;
    }

    if (attacker instanceof Skeleton other && other.getPersistentDataContainer().has(SERVANT_KEY, PersistentDataType.STRING)) {
      return;
    }

    UUID ownerId = UUID.fromString(ownerRaw);
    if (attacker instanceof Player player && player.getUniqueId().equals(ownerId)) {
      return;
    }

    Player owner = Bukkit.getPlayer(ownerId);
    if (owner == null || !owner.isOnline()) {
      return;
    }

    long now = System.currentTimeMillis();
    Threat threat = threats.get(ownerId);
    if (threat != null && threat.targetId().equals(attacker.getUniqueId()) && now - threat.stamp() < getConfig().playerThreatWindowMillis) {
      threats.put(ownerId, new Threat(attacker.getUniqueId(), now));
    }

    PerkRefs refs = perks();
    applyThorns(refs.thorns(), owner, servant, attacker, now);
    applyFrailty(refs.frailty(), servant, owner, attacker, now);
  }

  private void handleServantDealtDamage(EntityDamageByEntityEvent e, Skeleton servant, LivingEntity victim) {
    String ownerRaw = servant.getPersistentDataContainer().get(SERVANT_KEY, PersistentDataType.STRING);
    if (ownerRaw == null) {
      return;
    }

    Player owner = Bukkit.getPlayer(UUID.fromString(ownerRaw));
    if (owner == null || !owner.isOnline()) {
      return;
    }

    PerkRefs refs = perks();
    applySoulSiphon(refs.siphon(), owner, servant, victim, e);
    applyPlagueMark(refs.plague(), servant, owner, victim);
  }

  private void applyThorns(TragoulThorns thorns, Player owner, Skeleton servant, LivingEntity attacker, long now) {
    if (thorns == null) {
      return;
    }

    Long until = servantThornsCooldowns.get(servant.getUniqueId());
    if (until != null && until > now) {
      return;
    }

    int level = thorns.getActiveLevel(owner);
    if (level <= 0 || !canDamageTarget(owner, attacker)) {
      return;
    }

    servantThornsCooldowns.put(servant.getUniqueId(), now + 1500L);
    fx(servant.getLocation().add(0, 1.0, 0), FxPriority.TRAIL).particle(Particle.CRIT, 2, 0, 0, 0, 0.15, 0.02);
    double reflected = thorns.getConfig().damageMultiplierPerLevel * level;
    J.runEntity(attacker, () -> {
      if (attacker.isValid() && !attacker.isDead()) {
        attacker.damage(reflected, owner);
      }
    });
  }

  private void applyFrailty(TragoulCurseOfFrailty frailty, Skeleton servant, Player owner, LivingEntity attacker, long now) {
    if (frailty == null) {
      return;
    }

    Long until = servantCurseCooldowns.get(attacker.getUniqueId());
    if (until != null && until > now) {
      return;
    }

    int level = frailty.getActiveLevel(owner);
    if (level <= 0 || !canDamageTarget(owner, attacker)) {
      return;
    }

    TragoulCurseOfFrailty.Config curseConfig = frailty.getConfig();
    servantCurseCooldowns.put(attacker.getUniqueId(), now + curseConfig.perAttackerCooldownMillis);
    fx(servant.getLocation().add(0, 1.0, 0), FxPriority.TRAIL).particle(Particle.WARPED_SPORE, 2, 0, 0, 0, 0.15, 0.02);
    double levelPercent = frailty.getLevelPercent(level);
    int duration = Math.max(40, (int) Math.round(curseConfig.curseDurationTicksBase + (levelPercent * curseConfig.curseDurationTicksFactor)));
    int weaknessAmplifier = levelPercent >= 0.8 ? 1 : 0;
    boolean slowness = levelPercent >= curseConfig.slownessUnlockPercent;
    int slownessAmplifier = curseConfig.slownessAmplifier;
    J.runEntity(attacker, () -> {
      if (!attacker.isValid() || attacker.isDead()) {
        return;
      }

      attacker.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, weaknessAmplifier, true, true, true), true);
      if (slowness) {
        attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, slownessAmplifier, true, true, true), true);
      }
    });
  }

  private void applySoulSiphon(TragoulSoulSiphon siphon, Player owner, Skeleton servant, LivingEntity victim, EntityDamageByEntityEvent e) {
    if (siphon == null) {
      return;
    }

    EntityDamageEvent.DamageCause cause = e.getCause();
    if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
      return;
    }

    int level = siphon.getActiveLevel(owner);
    if (level <= 0 || !canDamageTarget(owner, victim)) {
      return;
    }

    TragoulSoulSiphon.Config siphonConfig = siphon.getConfig();
    double levelPercent = siphon.getLevelPercent(level);
    double percent = Math.max(0, siphonConfig.healPercentBase + (levelPercent * siphonConfig.healPercentFactor));
    double cap = Math.max(0.5, siphonConfig.healCapPerSecondBase + (levelPercent * siphonConfig.healCapPerSecondFactor));
    double heal = Math.min(cap, e.getFinalDamage() * percent);
    if (heal <= 0) {
      return;
    }

    IAttribute attribute = Version.get().getAttribute(servant, Attributes.GENERIC_MAX_HEALTH);
    double maxHealth = attribute == null ? 20D : attribute.getValue();
    double newHealth = Math.min(maxHealth, servant.getHealth() + heal);
    if (newHealth <= servant.getHealth()) {
      return;
    }

    servant.setHealth(newHealth);
    fx(servant.getLocation().add(0, 1.2, 0), FxPriority.TRAIL).particle(Particle.SOUL, 4, 0, 0, 0, 0.2, 0.02);
  }

  private void applyPlagueMark(TragoulPlagueBearer plague, Skeleton servant, Player owner, LivingEntity victim) {
    if (plague == null || !(victim instanceof Monster monster)) {
      return;
    }

    if (!monster.hasPotionEffect(PotionEffectType.POISON) && !monster.hasPotionEffect(PotionEffectType.WITHER)) {
      return;
    }

    if (plague.getActiveLevel(owner) <= 0) {
      return;
    }

    PersistentDataContainer pdc = monster.getPersistentDataContainer();
    pdc.set(PLAGUE_OWNER_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
    pdc.set(PLAGUE_STAMP_KEY, PersistentDataType.LONG, System.currentTimeMillis());
    if (!pdc.has(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER)) {
      pdc.set(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER, 0);
    }
    fx(servant.getLocation().add(0, 1.0, 0), FxPriority.TRAIL).particle(Particle.SPORE_BLOSSOM_AIR, 2, 0, 0, 0, 0.15, 0.02);
  }

  private void scheduleServantPulse(Skeleton servant, UUID ownerId, long expiresAt) {
    J.runEntity(servant, () -> {
      if (!servant.isValid() || servant.isDead() || System.currentTimeMillis() >= expiresAt) {
        removeServantRef(ownerId, servant);
        return;
      }

      retarget(servant, ownerId);
      scheduleServantPulse(servant, ownerId, expiresAt);
    }, getConfig().retargetIntervalTicks);
  }

  private void retarget(Skeleton servant, UUID ownerId) {
    Threat threat = threats.get(ownerId);
    if (threat != null && System.currentTimeMillis() - threat.stamp() < getConfig().playerThreatWindowMillis) {
      Entity candidate = Bukkit.getEntity(threat.targetId());
      if (candidate instanceof LivingEntity target && target.isValid() && !target.isDead() && target.getWorld() == servant.getWorld()) {
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null && owner.isOnline() && canDamageTarget(owner, target)) {
          if (servant.getTarget() != target) {
            servant.setTarget(target);
          }
          return;
        }
      }
    }

    LivingEntity current = servant.getTarget();
    if (current instanceof Monster && current.isValid() && !current.isDead()) {
      return;
    }

    double range = getConfig().targetSearchRadius;
    Monster nearest = null;
    double best = Double.MAX_VALUE;
    for (Entity entity : servant.getNearbyEntities(range, range, range)) {
      if (!(entity instanceof Monster monster) || monster.isDead() || !monster.isValid()) {
        continue;
      }

      if (monster.getPersistentDataContainer().has(SERVANT_KEY, PersistentDataType.STRING)) {
        continue;
      }

      double distance = monster.getLocation().distanceSquared(servant.getLocation());
      if (distance < best) {
        best = distance;
        nearest = monster;
      }
    }

    if (nearest != null) {
      servant.setTarget(nearest);
    }
  }

  private void despawnServant(Skeleton servant) {
    String ownerRaw = servant.getPersistentDataContainer().get(SERVANT_KEY, PersistentDataType.STRING);
    if (ownerRaw != null) {
      removeServantRef(UUID.fromString(ownerRaw), servant);
    }

    servantThornsCooldowns.remove(servant.getUniqueId());
    if (servant.isValid() && !servant.isDead()) {
      fx(servant.getLocation().add(0, 1.0, 0), FxPriority.TRANSITION)
          .particle(Particle.SOUL, 4, 0, 0.4, 0, 0.3, 0.03)
          .dustBurst(SERVANT_BONE, 4, 0.3, 1.0F)
          .chord(Sound.ENTITY_SKELETON_DEATH, 0.7F, 1.3F, Sound.BLOCK_BONE_BLOCK_BREAK, 0.4F, 0.9F);
      servant.remove();
    }
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
    CopyOnWriteArrayList<Skeleton> list = servants.get(ownerId);
    if (list != null) {
      list.remove(servant);
    }

    burden.unregister(ownerId, servant);
  }

  private LivingEntity resolvePriorityTarget(UUID ownerId, Player owner, long now) {
    Threat threat = threats.get(ownerId);
    if (threat == null || now - threat.stamp() >= getConfig().playerThreatWindowMillis) {
      return null;
    }

    Entity candidate = Bukkit.getEntity(threat.targetId());
    if (!(candidate instanceof LivingEntity target) || !target.isValid() || target.isDead()) {
      return null;
    }

    if (target.getWorld() != owner.getWorld()) {
      return null;
    }

    if (!canDamageTarget(owner, target)) {
      return null;
    }

    return target;
  }

  private boolean isPriorityTarget(UUID ownerId, LivingEntity candidate) {
    Threat threat = threats.get(ownerId);
    if (threat == null || !threat.targetId().equals(candidate.getUniqueId())) {
      return false;
    }

    if (System.currentTimeMillis() - threat.stamp() >= getConfig().playerThreatWindowMillis) {
      return false;
    }

    Player owner = Bukkit.getPlayer(ownerId);
    return owner != null && owner.isOnline() && canDamageTarget(owner, candidate);
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

  private static Skeleton resolveServantDamager(Entity damager) {
    Entity source = damager;
    if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
      source = shooter;
    }

    if (source instanceof Skeleton skeleton && skeleton.getPersistentDataContainer().has(SERVANT_KEY, PersistentDataType.STRING)) {
      return skeleton;
    }

    return null;
  }

  private int getServantCap(int level) {
    return Math.max(1, (int) Math.round(level * getConfig().servantCapPerLevel));
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

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    servantCurseCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    servantThornsCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    long window = getConfig().playerThreatWindowMillis;
    threats.entrySet().removeIf(entry -> now - entry.getValue().stamp() > window);
    for (CopyOnWriteArrayList<Skeleton> list : servants.values()) {
      list.removeIf(servant -> !servant.isValid() || servant.isDead());
    }
  }

  private record Threat(UUID targetId, long stamp) {
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Living servants allowed per adaptation level.", impact = "Higher values let one necromancer field a larger pack of servants.")
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
    @art.arcane.adapt.util.config.ConfigDoc(value = "Ticks between servant retarget pulses.", impact = "Lower values retarget faster but cost more scheduler work.")
    int retargetIntervalTicks = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Radius the servant scans for hostile mobs to attack.", impact = "Higher values let the servant acquire targets further away.")
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
