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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.ability.MinionKind;
import art.arcane.adapt.api.ability.Minions;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.integration.Integrations;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.player.SkillProgress;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.service.ServiceRegistration;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudGlyph;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.ServantFrame;
import art.arcane.adapt.content.minion.MinionTargeting;
import art.arcane.adapt.content.minion.ServantOrder;
import art.arcane.adapt.content.minion.ServantPack;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.event.world.ChunkEntitiesEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TragoulSkeletalServant extends SimpleAdaptation<TragoulSkeletalServant.Config> implements ServantPack {
  public static final Id ID = Id.adapt("tragoul/skeletal_servant");
  private static final int HARD_MAX_SERVANTS_PER_OWNER = 16;
  private static final String GOLEMANCY_SKILL = "golemancy";
  private static final int MAX_IMMEDIATE_RETARGETS = 4;
  private static final int MAX_LOCAL_TARGET_CANDIDATES = 8;
  private static final int MIN_RETARGET_INTERVAL_TICKS = 10;
  private static final int MAX_RETARGET_SEARCHES_PER_TICK = 64;
  private static final int TARGET_ASSIGNMENT_DELAY_TICKS = 2;
  private static final int OWNER_HANDOFF_TIMEOUT_TICKS = 10;
  private static final long THREAT_REFRESH_MILLIS = 500L;
  private static final long RETARGET_BUDGET_COUNT_MASK = 0xFFFFL;
  private static final double HARD_MAX_TARGET_SEARCH_RADIUS = 24D;
  private static final DataKey<String> SERVANT_KEY = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);
  private static final DataKey<String> PLAGUE_OWNER_KEY = DataKey.adapt("tragoul_plague_owner", DataCodecs.STRING);
  private static final DataKey<Integer> PLAGUE_GENERATION_KEY = DataKey.adapt("tragoul_plague_generation", DataCodecs.INT);
  private static final DataKey<Long> PLAGUE_STAMP_KEY = DataKey.adapt("tragoul_plague_stamp", DataCodecs.LONG);
  private static final Id SKELETON = Id.minecraft("skeleton");
  private static final Id BONE = Id.minecraft("bone");
  private static final Id POISON = Id.minecraft("poison");
  private static final Id WITHER = Id.minecraft("wither");
  private static final Id WEAKNESS = Id.minecraft("weakness");
  private static final Id SLOWNESS = Id.minecraft("slowness");
  private static final Id PROTECTION = Id.minecraft("protection");
  private static final Id POWER = Id.minecraft("power");
  private static final Id SHARPNESS = Id.minecraft("sharpness");
  public static final MinionKind MINION_KIND = new ServantMinionKind();

  private static final Id[][] HELMETS = {
      {Id.minecraft("leather_helmet"), Id.minecraft("chainmail_helmet")},
      {Id.minecraft("chainmail_helmet"), Id.minecraft("iron_helmet")},
      {Id.minecraft("iron_helmet"), Id.minecraft("diamond_helmet")}
  };
  private static final Id[][] CHESTPLATES = {
      {Id.minecraft("leather_chestplate"), Id.minecraft("chainmail_chestplate")},
      {Id.minecraft("chainmail_chestplate"), Id.minecraft("iron_chestplate")},
      {Id.minecraft("iron_chestplate"), Id.minecraft("diamond_chestplate")}
  };
  private static final Id[][] LEGGINGS = {
      {Id.minecraft("leather_leggings"), Id.minecraft("chainmail_leggings")},
      {Id.minecraft("chainmail_leggings"), Id.minecraft("iron_leggings")},
      {Id.minecraft("iron_leggings"), Id.minecraft("diamond_leggings")}
  };
  private static final Id[][] BOOTS = {
      {Id.minecraft("leather_boots"), Id.minecraft("chainmail_boots")},
      {Id.minecraft("chainmail_boots"), Id.minecraft("iron_boots")},
      {Id.minecraft("iron_boots"), Id.minecraft("diamond_boots")}
  };
  private static final Id[][] SWORDS = {
      {Id.minecraft("wooden_sword"), Id.minecraft("stone_sword")},
      {Id.minecraft("iron_sword"), Id.minecraft("iron_sword")},
      {Id.minecraft("iron_sword"), Id.minecraft("diamond_sword")}
  };
  private static final Id[] BOW_POOL = {Id.minecraft("bow")};

  private static final Rgb SERVANT_BONE = new Rgb(230, 225, 205);
  private final Map<UUID, CopyOnWriteArrayList<MobRef>> servants = new ConcurrentHashMap<>();
  private final Map<UUID, UUID> servantOwners = new ConcurrentHashMap<>();
  private final Map<MobRef, UUID> servantOwnersByEntity = new ConcurrentHashMap<>();
  private final Map<MobRef, UUID> servantIdsByEntity = new ConcurrentHashMap<>();
  private final Map<UUID, ServantRecord> records = new ConcurrentHashMap<>();
  private final Map<UUID, TargetRequest> latestTargetRequests = new ConcurrentHashMap<>();
  private final Set<UUID> activeTargetRequests = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Long> pendingLocalRetargets = new ConcurrentHashMap<>();
  private final Map<UUID, TragoulPreferences.Stance> stances = new ConcurrentHashMap<>();
  private final Cooldowns cooldowns = cooldowns();
  private final Map<UUID, Integer> packCaps = playerState();
  private final AtomicLong localRetargetSequence = new AtomicLong();
  private final AtomicLong retargetBudgetState = new AtomicLong();
  private final Map<UUID, Threat> threats = new ConcurrentHashMap<>();
  private final Map<UUID, Long> servantThornsCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Long> servantCurseCooldowns = new ConcurrentHashMap<>();
  private volatile PerkRefs perkRefs;
  private final ServiceRegistration minionKind = Minions.register(MINION_KIND);

  public TragoulSkeletalServant() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("skeleton_skull"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_tragoul_servant_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("skeleton_skull"))
            .key("challenge_tragoul_servant_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_servant_50", "tragoul.skeletal-servant.servants-summoned", 50, 400);
    registerMilestone("challenge_tragoul_servant_500", "tragoul.skeletal-servant.servants-summoned", 500, 1500);
  }

  public static boolean isServant(EntityRef entity) {
    return entity.data().has(SERVANT_KEY);
  }

  public static UUID getServantOwnerId(EntityRef entity) {
    String raw = entity.data().get(SERVANT_KEY);
    if (raw == null) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  @Override
  public void onLevelChanged(PlayerRef player, int level) {
    if (level <= 0) {
      UUID owner = player.uuid();
      dismissPack(owner);
      GolemancyRuntime.registry().ifPresent(registry -> registry.revokeBones(owner));
    }
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    PlayerRef owner = player.player();
    UUID id = owner.uuid();
    stances.put(id, preference(owner, TragoulPreferences.STANCE));
    if (getActiveLevel(owner) <= 0) {
      dismissPack(id);
      return;
    }
    threats.remove(id);
    latestTargetRequests.remove(id);
    CopyOnWriteArrayList<MobRef> pack = servants.get(id);
    if (pack != null) {
      for (MobRef servant : pack) { AdaptApi.scheduler().runEntity(servant, () -> servant.setTarget(null)); }
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.REPLACE, TragoulPreferences.STANCE, TragoulPreferences.BONES);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.tragoul.skeletal_servant.lore1").resolve());
    statLore(v, getServantCap(level), 5);
    statLore(v, Format.duration(getDurationTicks(level) * 50D, 1), 2);
    statLore(v, TextFormat.YELLOW, "* ", getBoneCost(level), 3);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration((double) getCooldownMillis(level), 1), 4);
    v.addLore(TextFormat.GRAY + Text.key("adapt.tragoul.skeletal_servant.lore6").resolve());
    if (getConfig().healthCostEnabled && getConfig().healthCostPerMinion > 0) {
      statLore(v, TextFormat.RED, "* ", Format.f(getConfig().healthCostPerMinion, 1), 7);
    }
  }

  @Override
  public void unregister() {
    super.unregister();
    minionKind.withdraw();
  }

  @Override
  public List<MobRef> servants(UUID owner) {
    CopyOnWriteArrayList<MobRef> pack = servants.get(owner);
    return pack == null ? List.of() : List.copyOf(pack);
  }

  @Override
  public long expiresAt(MobRef servant) {
    ServantRecord record = records.get(servant.uuid());
    return record == null ? 0L : record.expiresAt();
  }

  @Override
  public ServantOrder order(MobRef servant) {
    ServantRecord record = records.get(servant.uuid());
    return record == null ? ServantOrder.FREE : record.order();
  }

  @Override
  public boolean order(MobRef servant, ServantOrder order) {
    ServantRecord record = records.get(servant.uuid());
    if (record == null) {
      return false;
    }
    record.order(Objects.requireNonNull(order));
    return true;
  }

  static int golemancyCapBonus(int golemancyLevel, int everyLevels, int maxBonus) {
    if (golemancyLevel <= 0 || everyLevels <= 0 || maxBonus <= 0) {
      return 0;
    }
    return Math.min(maxBonus, golemancyLevel / everyLevels);
  }

  static int servantCap(int levelCap, int golemancyBonus) {
    return Math.min(HARD_MAX_SERVANTS_PER_OWNER, levelCap + Math.max(0, golemancyBonus));
  }

  static int golemancyLevel(Skill<?> golemancy, @Nullable SkillProgress line) {
    if (golemancy == null || !golemancy.isEnabled() || line == null) {
      return 0;
    }
    return line.level();
  }

  static double golemancyDurationMultiplier(int golemancyLevel, double perLevel, double maxBonus) {
    if (golemancyLevel <= 0 || !Double.isFinite(perLevel) || !Double.isFinite(maxBonus) || perLevel <= 0D || maxBonus <= 0D) {
      return 1D;
    }
    return 1D + Math.min(maxBonus, golemancyLevel * perLevel);
  }

  static boolean isAlliedTarget(MobRef servant, LivingRef target, boolean trackedServant) {
    return trackedServant || Minions.sameOwner(servant, target);
  }

  public static boolean allowsSummonInteraction(PlayerInteractEvent event) {
    if (event.useItem() == UseResult.DENY) {
      return false;
    }
    return event.action() == InteractAction.RIGHT_CLICK_AIR
        || event.action() == InteractAction.RIGHT_CLICK_BLOCK && event.useBlock() != UseResult.DENY;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    PlayerRef p = e.player();
    if (!p.isSneaking()) {
      return;
    }

    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.RIGHT_CLICK_BLOCK) {
      return;
    }

    if (e.hand() != Hand.MAIN_HAND || e.item() == null || e.item().isEmpty() || !BONE.equals(e.item().type())) {
      return;
    }

    withAdaptedPlayer(p, () -> {
      if (!allowsSummonInteraction(e)) {
        return;
      }
      int level = getActiveLevel(p);
      if (level <= 0 || raisedByUpgrade(p)) {
        return;
      }

      UUID id = p.uuid();
      stances.put(id, preference(p, TragoulPreferences.STANCE));
      if (!cooldowns.isReady(id, getCooldownMillis(level))) {
        sfx(p.location(), Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.8F, 0.8F);
        return;
      }

      CopyOnWriteArrayList<MobRef> list = servants.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>());
      int golemancyLevel = golemancyLevel(p);
      int cap = getServantCap(level, golemancyLevel);
      int room = cap - ServantFrame.legion(id);
      if (room <= 0 || (list.size() >= room
          && (!getConfig().replaceOldestAtCap || !preferenceEnabled(p, TragoulPreferences.REPLACE)))) {
        sfx(p.location(), Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.8F, 1.2F);
        return;
      }

      int boneCost = getBoneCost(level);
      if (p.gameMode() != GameMode.CREATIVE) {
        if (!p.inventory().containsAtLeast(bone(1), preference(p, TragoulPreferences.BONES).required(boneCost))) {
          sfx(p.location(), Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.8F, 0.6F);
          return;
        }
        if (!payItemCost(p, "summon", bone(1), boneCost, () -> {
          p.inventory().removeSimilar(bone(1), boneCost);
          return true;
        })) {
          sfx(p.location(), Sounds.BLOCK_CONDUIT_DEACTIVATE, 0.8F, 0.6F);
          return;
        }
      }

      while (list.size() >= room) {
        MobRef oldest = list.remove(0);
        UUID oldestId = servantIdsByEntity.get(oldest);
        if (oldestId == null) {
          if (!AdaptApi.scheduler().runEntity(oldest, () -> removeServantOwned(id, oldest, true))) {
            Minions.releaseBurden(id, oldest);
          }
          continue;
        }
        servantOwners.remove(oldestId);
        servantOwnersByEntity.remove(oldest);
        servantIdsByEntity.remove(oldest);
        if (!AdaptApi.scheduler().runEntity(oldest, () -> removeServantOwned(id, oldest, true))) {
          forgetServant(id, oldest, oldestId);
        }
      }

      cooldowns.mark(id, getCooldownMillis(level));
      packCaps.put(id, cap);
      if (raise(p, p.location(), level, getDurationTicks(level, golemancyLevel)) == null) {
        return;
      }
      addStat(p, "tragoul.skeletal-servant.servants-summoned", 1);
      xp(p, getConfig().xpPerSummon);
    });
  }

  /** An upgrade that raises servants by hand in place of this adaptation's own summon. */
  public interface HandRaiser {
    boolean raisesByHand(PlayerRef player);
  }

  private boolean raisedByUpgrade(PlayerRef p) {
    if (getSkill() == null) {
      return false;
    }
    for (Adaptation<?> upgrade : getSkill().getUpgrades()) {
      if (upgrade instanceof HandRaiser raiser && raiser.raisesByHand(p)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Raises one servant for {@code owner} at {@code at}, armed and as strong as this adaptation at {@code level}, for
   * {@code durationTicks}. It joins the owner's pack and counts toward it, follows their stance and drops nothing when
   * it dies. Null when nothing rose. Call on the owner's thread.
   */
  public @Nullable MobRef raise(PlayerRef owner, Loc at, int level, int durationTicks) {
    UUID id = owner.uuid();
    stances.put(id, preference(owner, TragoulPreferences.STANCE));
    ThreadLocalRandom random = ThreadLocalRandom.current();
    EntityRef spawned = at.world().spawn(SKELETON, at, (EntityRef created) -> {
      Integrations.excludeFromStacking(created);
      created.data().set(SERVANT_KEY, id.toString());
      created.setPersistent(false);
      if (created instanceof MobRef s) {
        s.setRemoveWhenFarAway(false);
        s.setBurnsInDaylight(false);
        equipServant(s, level, random);
      }
    });
    if (!(spawned instanceof MobRef servant)) {
      return null;
    }
    long now = System.currentTimeMillis();
    applyServantAttributes(servant, level);
    track(owner, servant, now + (durationTicks * 50L), durationTicks);
    ServantFrame.frame().ifPresent(frame -> frame.summoned(owner, servant));
    LivingRef priorityTarget = resolvePriorityTargetOwned(id, owner, now);
    if (priorityTarget != null) {
      servant.setTarget(priorityTarget);
    }
    playSummonRitual(servant.location());
    return servant;
  }

  @Override
  public boolean surrender(MobRef servant) {
    UUID ownerId = getServantOwnerId(servant);
    if (ownerId == null) {
      return false;
    }
    servant.data().remove(SERVANT_KEY);
    forgetServant(ownerId, servant, servant.uuid());
    return true;
  }

  @Override
  public void crumble(MobRef servant) {
    crumbleFx(servant);
    servant.remove();
  }

  void track(PlayerRef owner, MobRef servant, long expiresAt, int durationTicks) {
    UUID ownerId = owner.uuid();
    UUID servantId = servant.uuid();
    servants.computeIfAbsent(ownerId, key -> new CopyOnWriteArrayList<>()).add(servant);
    records.put(servantId, new ServantRecord(expiresAt));
    servantOwners.put(servantId, ownerId);
    servantOwnersByEntity.put(servant, ownerId);
    servantIdsByEntity.put(servant, servantId);
    configureBurden(getConfig());
    Minions.burden(owner, servant);
    scheduleServantPulse(servant, ownerId, expiresAt);
    showPack(ownerId);
    AdaptApi.scheduler().runEntity(servant, () -> removeServantOwned(ownerId, servant, true), durationTicks);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityTargetEvent e) {
    MobRef skeleton = asSkeleton(e.entity());
    if (skeleton == null) {
      return;
    }

    LivingRef target = e.target();
    if (target == null) {
      return;
    }

    UUID ownerId = resolveServantOwnerOwned(skeleton);
    if (ownerId == null) {
      return;
    }

    ServantOrder order = order(skeleton);
    long now = System.currentTimeMillis();
    switch (ServantPulse.gate(order, e.reason(), target, now)) {
      case COMMANDED -> {
        return;
      }
      case REFUSED -> {
        e.setCancelled(true);
        return;
      }
      case USUAL -> {
      }
    }

    TragoulPreferences.Stance stance = stances.getOrDefault(ownerId, TragoulPreferences.Stance.ASSIST);
    if (stance == TragoulPreferences.Stance.PASSIVE
        || (stance == TragoulPreferences.Stance.DEFEND && !isPriorityTarget(ownerId, target))) {
      e.setCancelled(true);
      releaseTarget(skeleton, order, now);
      return;
    }
    if (target instanceof PlayerRef player) {
      if (!isPriorityTarget(ownerId, player)) {
        e.setCancelled(true);
        if (skeleton.target() instanceof PlayerRef) {
          releaseTarget(skeleton, order, now);
        }
      }
      return;
    }

    if (isAlliedTarget(skeleton, target, isTrackedServant(target))) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.LOWEST)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof PlayerRef victim)) {
      return;
    }

    ServantDamager servant = resolveServantDamager(e.damager());
    if (servant == null) {
      return;
    }

    UUID ownerId = servant.ownerId();
    if (victim.uuid().equals(ownerId) || (!isPriorityTarget(ownerId, victim) && !isCommandedTarget(servant.servant(), victim))) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void onCombatPerks(EntityDamageByEntityEvent e) {
    EntityRef entity = e.entity();
    MobRef skeleton = asSkeleton(entity);
    if (skeleton != null) {
      UUID ownerId = resolveServantOwnerOwned(skeleton);
      if (ownerId != null) {
        handleServantHit(e, skeleton, ownerId);
        return;
      }
    }

    if (entity instanceof PlayerRef ownerCandidate) {
      handleOwnerDamaged(e, ownerCandidate);
    }

    if (!(entity instanceof LivingRef victim)) {
      return;
    }

    ServantDamager servant = resolveServantDamager(e.damager());
    if (servant != null && !servant.servant().equals(victim)) {
      handleServantDealtDamage(e, servant, victim);
    }

    handleOwnerAttack(e, victim);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDeathEvent e) {
    MobRef skeleton = asSkeleton(e.entity());
    if (skeleton == null) {
      return;
    }

    UUID ownerId = resolveServantOwnerOwned(skeleton);
    if (ownerId == null) {
      return;
    }

    e.drops().clear();
    e.setDroppedExp(0);
    servantThornsCooldowns.remove(skeleton.uuid());
    removeServantRef(ownerId, skeleton);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(ChunkEntitiesEvent event) {
    if (event.phase() != ChunkEntitiesEvent.Phase.UNLOAD) {
      return;
    }
    for (EntityRef entity : event.entities()) {
      MobRef skeleton = asSkeleton(entity);
      if (skeleton == null) {
        continue;
      }
      UUID ownerId = resolveServantOwnerOwned(skeleton);
      if (ownerId == null) {
        continue;
      }
      servantThornsCooldowns.remove(skeleton.uuid());
      removeServantRef(ownerId, skeleton);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void onServantKill(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    if (!victim.has(EntityTrait.MONSTER)) {
      return;
    }

    if (victim.data().has(SERVANT_KEY)) {
      return;
    }

    LastDamage lastDamage = victim.lastDamage();
    if (lastDamage == null || !lastDamage.byEntity()) {
      return;
    }

    ServantDamager servant = resolveServantDamager(lastDamage.damager());
    if (servant == null) {
      return;
    }

    PlayerRef owner = AdaptApi.platform().host().player(servant.ownerId());
    if (owner == null) {
      return;
    }

    TragoulCorpseExplosion nova = perks().nova();
    if (nova != null) {
      TragoulCorpseExplosion.detonateServantKill(nova, owner, victim);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    dismissPack(e.player().uuid());
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerDeathEvent e) {
    dismissPack(e.player().uuid());
  }

  private void dismissPack(UUID id) {
    threats.remove(id);
    latestTargetRequests.remove(id);
    activeTargetRequests.remove(id);
    servantCurseCooldowns.remove(id);
    CopyOnWriteArrayList<MobRef> list = servants.remove(id);
    showPack(id);
    if (list == null) {
      return;
    }

    for (MobRef servant : list) {
      UUID servantId = servantIdsByEntity.get(servant);
      if (servantId == null) {
        if (!AdaptApi.scheduler().runEntity(servant, () -> removeServantOwned(id, servant, false))) {
          Minions.releaseBurden(id, servant);
        }
        continue;
      }
      servantOwners.remove(servantId);
      servantOwnersByEntity.remove(servant);
      servantIdsByEntity.remove(servant);
      servantThornsCooldowns.remove(servantId);
      pendingLocalRetargets.remove(servantId);
      if (!AdaptApi.scheduler().runEntity(servant, () -> removeServantOwned(id, servant, false))) {
        forgetServant(id, servant, servantId);
      }
    }
  }

  private void handleOwnerDamaged(EntityDamageByEntityEvent e, PlayerRef owner) {
    LivingRef attacker = resolveLivingDamager(e.damager());
    if (attacker == null || attacker.equals(owner) || preference(owner, TragoulPreferences.STANCE) == TragoulPreferences.Stance.PASSIVE) {
      return;
    }

    assignPackTarget(owner, attacker);
  }

  private void handleOwnerAttack(EntityDamageByEntityEvent e, LivingRef victim) {
    PlayerRef owner = null;
    if (e.damager() instanceof PlayerRef player) {
      owner = player;
    } else if (e.damager() instanceof ProjectileRef projectile && projectile.shooter() instanceof PlayerRef shooter) {
      owner = shooter;
    }

    if (owner == null || owner.equals(victim) || preference(owner, TragoulPreferences.STANCE) != TragoulPreferences.Stance.ASSIST) {
      return;
    }

    assignPackTarget(owner, victim);
  }

  private void assignPackTarget(PlayerRef owner, LivingRef target) {
    UUID ownerId = owner.uuid();
    CopyOnWriteArrayList<MobRef> pack = servants.get(ownerId);
    if (pack == null || pack.isEmpty()) {
      return;
    }
    Threat current = threats.get(ownerId);
    TargetRequest queued = latestTargetRequests.get(ownerId);
    long now = System.currentTimeMillis();
    if (queued == null && current != null && current.target().equals(target)) {
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

  private void scheduleTargetRequest(UUID ownerId, PlayerRef owner) {
    if (!AdaptApi.scheduler().runEntity(owner, () -> startTargetRequestOwned(ownerId, owner), TARGET_ASSIGNMENT_DELAY_TICKS)) {
      latestTargetRequests.remove(ownerId);
      activeTargetRequests.remove(ownerId);
    }
  }

  private void startTargetRequestOwned(UUID ownerId, PlayerRef owner) {
    TargetRequest request = latestTargetRequests.get(ownerId);
    if (request == null || !owner.isOnline() || getActiveLevel(owner) <= 0) {
      finishTargetRequestOwned(ownerId, owner, request);
      return;
    }

    LivingRef target = request.target();
    if (!AdaptApi.scheduler().runEntity(target, () -> {
      TargetSnapshot snapshot = captureTargetOwned(owner, target);
      if (!AdaptApi.scheduler().runEntity(owner, () -> completeTargetRequestOwned(ownerId, owner, request, snapshot))) {
        latestTargetRequests.remove(ownerId, request);
        activeTargetRequests.remove(ownerId);
      }
    })) {
      finishTargetRequestOwned(ownerId, owner, request);
      return;
    }
    AdaptApi.scheduler().runEntity(owner, () -> expireTargetRequestOwned(ownerId, owner, request), OWNER_HANDOFF_TIMEOUT_TICKS);
  }

  private void completeTargetRequestOwned(UUID ownerId, PlayerRef owner, TargetRequest request, TargetSnapshot snapshot) {
    if (latestTargetRequests.get(ownerId) != request) {
      if (latestTargetRequests.containsKey(ownerId)) {
        scheduleTargetRequest(ownerId, owner);
      } else {
        activeTargetRequests.remove(ownerId);
      }
      return;
    }

    if (snapshot != null && owner.isOnline() && getActiveLevel(owner) > 0 && canDamageSnapshotOwned(owner, snapshot)) {
      publishThreatOwned(ownerId, owner, snapshot, request.requestedAt());
    }
    finishTargetRequestOwned(ownerId, owner, request);
  }

  private void expireTargetRequestOwned(UUID ownerId, PlayerRef owner, TargetRequest request) {
    if (latestTargetRequests.get(ownerId) == request) {
      finishTargetRequestOwned(ownerId, owner, request);
    }
  }

  private void finishTargetRequestOwned(UUID ownerId, PlayerRef owner, TargetRequest completed) {
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

  private void publishThreatOwned(UUID ownerId, PlayerRef owner, TargetSnapshot snapshot, long stamp) {
    Threat previous = threats.get(ownerId);
    boolean newTarget = previous == null || !previous.target().equals(snapshot.entity());
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

    CopyOnWriteArrayList<MobRef> list = servants.get(ownerId);
    if (list != null) {
      int dispatched = 0;
      for (MobRef servant : list) {
        if (dispatched >= MAX_IMMEDIATE_RETARGETS) {
          break;
        }
        AdaptApi.scheduler().runEntity(servant, () -> assignPriorityTargetOwned(servant, current, true));
        dispatched++;
      }
    }
    fx(owner.location(), FxPriority.COMBAT).sound(Sounds.ENTITY_SKELETON_AMBIENT, 0.7F, 0.5F);
  }

  private void handleServantHit(EntityDamageByEntityEvent e, MobRef servant, UUID ownerId) {
    LivingRef attacker = resolveLivingDamager(e.damager());
    if (attacker == null || attacker.equals(servant)) {
      return;
    }

    if (isTrackedServant(attacker)) {
      return;
    }

    PlayerRef owner = AdaptApi.platform().host().player(ownerId);
    if (owner == null) {
      return;
    }

    long now = System.currentTimeMillis();
    UUID servantId = servant.uuid();
    if (!AdaptApi.scheduler().runEntity(attacker, () -> {
      TargetSnapshot snapshot = captureTargetOwned(owner, attacker);
      if (snapshot != null) {
        AdaptApi.scheduler().runEntity(owner, () -> prepareDefensePerksOwned(owner, servant, servantId, snapshot, now));
      }
    })) {
      servantThornsCooldowns.remove(servantId);
    }
  }

  private void prepareDefensePerksOwned(PlayerRef owner, MobRef servant, UUID servantId, TargetSnapshot attacker, long now) {
    if (!owner.isOnline() || !canDamageSnapshotOwned(owner, attacker)) {
      return;
    }

    Threat threat = threats.get(owner.uuid());
    if (threat != null && threat.target().equals(attacker.entity()) && now - threat.stamp() < getThreatWindowMillis()) {
      threat.update(attacker, now);
    }

    PerkRefs refs = perks();
    boolean thornsApplied = false;
    double reflectedDamage = 0D;
    TragoulThorns thorns = refs.thorns();
    Long thornsUntil = servantThornsCooldowns.get(servantId);
    if (thorns != null && (!(thorns.getConfig().ignorePassiveMobs || thorns.preferenceEnabled(owner, TragoulPreferences.PASSIVE)) || !attacker.passiveOrNeutral())
        && (thornsUntil == null || thornsUntil <= now)) {
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
    AdaptApi.scheduler().runEntity(attacker.entity(), () -> applyDefenseEffectOwned(attacker, effect));
    AdaptApi.scheduler().runEntity(servant, () -> playDefenseEffectOwned(servant, servantId, effect));
  }

  private void applyDefenseEffectOwned(TargetSnapshot attacker, DefenseEffect effect) {
    LivingRef target = attacker.entity();
    if (!target.isValid() || target.isDead()) {
      return;
    }

    TragoulThorns thorns = perks().thorns();
    if (effect.thornsApplied() && thorns != null
        && AdaptationDamageTargets.allows(target, thorns.getConfig().ignorePassiveMobs)) {
      TragoulReactiveDamage.apply(() -> target.damage(effect.reflectedDamage(), effect.owner()));
    }
    if (effect.frailtyApplied()) {
      target.addEffect(EffectInstance.of(WEAKNESS, effect.frailtyDuration(), effect.weaknessAmplifier()).withAmbient(true));
      if (effect.slowness()) {
        target.addEffect(EffectInstance.of(SLOWNESS, effect.frailtyDuration(), effect.slownessAmplifier()).withAmbient(true));
      }
    }
  }

  private void playDefenseEffectOwned(MobRef servant, UUID servantId, DefenseEffect effect) {
    if (!servant.isValid() || servant.isDead() || !servantOwners.containsKey(servantId)) {
      return;
    }
    Loc location = servant.location().add(0, 1.0D, 0);
    if (effect.thornsApplied()) {
      fx(location, FxPriority.TRAIL).particle(Particles.CRIT, 2, 0, 0, 0, 0.15D, 0.02D);
    }
    if (effect.frailtyApplied()) {
      fx(location, FxPriority.TRAIL).particle(Particles.WARPED_SPORE, 2, 0, 0, 0, 0.15D, 0.02D);
    }
  }

  private void handleServantDealtDamage(EntityDamageByEntityEvent event, ServantDamager servant, LivingRef victim) {
    ServantFrame.frame().ifPresent(frame -> frame.struck(servant.servant(), victim));
    PlayerRef owner = AdaptApi.platform().host().player(servant.ownerId());
    if (owner == null) {
      return;
    }

    double finalDamage = event.finalDamage();
    AdaptApi.scheduler().runEntity(victim, () -> {
      TargetSnapshot snapshot = captureTargetOwned(owner, victim);
      if (snapshot != null) {
        AdaptApi.scheduler().runEntity(owner, () -> prepareOffensePerksOwned(owner, servant.servant(), snapshot, finalDamage));
      }
    });
  }

  private void prepareOffensePerksOwned(PlayerRef owner, MobRef servant, TargetSnapshot victim,
                                        double finalDamage) {
    if (!owner.isOnline() || !canDamageSnapshotOwned(owner, victim)) {
      return;
    }

    PerkRefs refs = perks();
    double heal = 0D;
    TragoulSoulSiphon siphon = refs.siphon();
    if (siphon != null) {
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
    if (plagueBearer != null && victim.mob() && victim.afflicted()) {
      plague = plagueBearer.getActiveLevel(owner) > 0;
    }

    if (heal <= 0D && !plague) {
      return;
    }

    OffenseEffect effect = new OffenseEffect(owner.uuid(), heal, plague);
    AdaptApi.scheduler().runEntity(servant, () -> applyOffenseToServantOwned(servant, effect));
    if (plague) {
      AdaptApi.scheduler().runEntity(victim.entity(), () -> applyPlagueOwned(victim, effect.ownerId()));
    }
  }

  private void applyOffenseToServantOwned(MobRef servant, OffenseEffect effect) {
    if (!servantOwnersByEntity.containsKey(servant) || !servant.isValid() || servant.isDead()) {
      return;
    }

    if (effect.heal() > 0D) {
      double attributeMaximum = servant.attributeValue(Attributes.MAX_HEALTH);
      double maxHealth = Double.isNaN(attributeMaximum) ? 20D : attributeMaximum;
      double currentHealth = servant.health();
      double newHealth = Math.min(maxHealth, currentHealth + effect.heal());
      if (newHealth > currentHealth) {
        servant.setHealth(newHealth);
        fx(servant.location().add(0, 1.2D, 0), FxPriority.TRAIL).particle(Particles.SOUL, 4, 0, 0, 0, 0.2D, 0.02D);
      }
    }
    if (effect.plague()) {
      fx(servant.location().add(0, 1.0D, 0), FxPriority.TRAIL).particle(Particles.SPORE_BLOSSOM_AIR, 2, 0, 0, 0, 0.15D, 0.02D);
    }
  }

  private void applyPlagueOwned(TargetSnapshot victim, UUID ownerId) {
    LivingRef target = victim.entity();
    if (!(target instanceof MobRef mob) || !mob.isValid() || mob.isDead()) {
      return;
    }

    DataHolder pdc = mob.data();
    pdc.set(PLAGUE_OWNER_KEY, ownerId.toString());
    pdc.set(PLAGUE_STAMP_KEY, System.currentTimeMillis());
    if (!pdc.has(PLAGUE_GENERATION_KEY)) {
      pdc.set(PLAGUE_GENERATION_KEY, 0);
    }
  }

  private void scheduleServantPulse(MobRef servant, UUID ownerId, long expiresAt) {
    if (!AdaptApi.scheduler().runEntity(servant, () -> {
      UUID servantId = servant.uuid();
      if (!ownerId.equals(servantOwners.get(servantId))) {
        return;
      }

      if (!servant.isValid() || servant.isDead() || System.currentTimeMillis() >= expiresAt) {
        removeServantOwned(ownerId, servant, true);
        return;
      }

      pulseOwned(servant, servantId, ownerId);
      scheduleServantPulse(servant, ownerId, expiresAt);
    }, getRetargetIntervalTicks())) {
      UUID servantId = servant.uuid();
      forgetServant(ownerId, servant, servantId);
    }
  }

  private void pulseOwned(MobRef servant, UUID servantId, UUID ownerId) {
    ServantRecord record = records.get(servantId);
    ServantOrder order = record == null ? ServantOrder.FREE : record.order();
    if (order.isFree()) {
      retargetOwned(servant, servantId, ownerId);
      return;
    }
    LivingRef commanded = order.target();
    boolean holds = order.commandHolds(System.currentTimeMillis(), commanded != null && reachable(servant, commanded));
    if (order.commanded() && !holds) {
      order = record.release(order);
      if (commanded != null && commanded.equals(servant.target())) {
        servant.setTarget(null);
      }
    }
    Loc here = servant.location();
    Loc ownerAt = order.mode() == ServantOrder.Mode.FOLLOW ? sightOwner(record, ownerId) : null;
    Loc anchor = order.anchor();
    LivingRef current = servant.target();
    ServantPulse.Senses senses = new ServantPulse.Senses(holds, distanceSquared(here, ownerAt), distanceSquared(here, anchor),
        current != null && current.isValid() && !current.isDead(), GolemancyRuntime.brainSettings().leashRadius());
    switch (ServantPulse.step(order, senses)) {
      case HUNT -> retargetOwned(servant, servantId, ownerId);
      case ATTACK -> {
        if (!commanded.equals(servant.target())) {
          servant.setTarget(commanded);
        }
      }
      case FOLLOW -> {
        servant.setTarget(null);
        servant.pathTo(ownerAt, ServantPulse.PATH_SPEED);
      }
      case RETURN -> servant.pathTo(anchor, ServantPulse.PATH_SPEED);
      case STAND -> {
      }
    }
  }

  private Loc sightOwner(ServantRecord record, UUID ownerId) {
    PlayerRef owner = AdaptApi.platform().host().player(ownerId);
    if (owner == null) {
      return null;
    }
    if (AdaptApi.scheduler().owns(owner)) {
      return owner.location();
    }
    if (record.lookOut() && !AdaptApi.scheduler().runEntity(owner, () -> record.sighted(owner.location()))) {
      record.lookFailed();
    }
    return record.sighting();
  }

  private static boolean reachable(MobRef servant, LivingRef target) {
    return target.isValid() && !target.isDead() && servant.world().equals(target.world());
  }

  private static double distanceSquared(Loc from, Loc to) {
    if (to == null || !from.world().equals(to.world())) {
      return Double.NaN;
    }
    return from.distanceSquared(to);
  }

  private boolean isCommandedTarget(MobRef servant, LivingRef victim) {
    return ServantPulse.commanded(order(servant), victim, System.currentTimeMillis());
  }

  private static void releaseTarget(MobRef servant, ServantOrder order, long now) {
    if (!ServantPulse.commanded(order, servant.target(), now)) {
      servant.setTarget(null);
    }
  }

  private void retargetOwned(MobRef servant, UUID servantId, UUID ownerId) {
    long now = System.currentTimeMillis();
    Threat threat = threats.get(ownerId);
    if (threat != null && now - threat.stamp() < getThreatWindowMillis()) {
      requestThreatRefresh(threat, now);
      if (assignPriorityTargetOwned(servant, threat, false)) {
        return;
      }
    }

    LivingRef current = servant.target();
    if (current != null && current.has(EntityTrait.MONSTER) && !isTrackedServant(current)) {
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
    LivingRef first = null;
    LivingRef candidate = null;
    int candidateIndex = 0;
    for (EntityRef entity : servant.nearbyEntities(range, range, range)) {
      if (!(entity instanceof LivingRef monster) || !monster.has(EntityTrait.MONSTER) || isTrackedServant(monster)) {
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
    LivingRef selected = candidate;
    if (!AdaptApi.scheduler().runEntity(selected, () -> {
      PlayerRef owner = AdaptApi.platform().host().player(ownerId);
      TargetSnapshot snapshot = owner == null ? null : captureTargetOwned(owner, selected);
      if (snapshot == null || !AdaptApi.scheduler().runEntity(owner, () -> completeLocalRetargetOwnerOwned(owner, servant, servantId, token, snapshot))) {
        pendingLocalRetargets.remove(servantId, token);
      }
    })) {
      pendingLocalRetargets.remove(servantId, token);
      return;
    }
    AdaptApi.scheduler().runEntity(servant, () -> pendingLocalRetargets.remove(servantId, token), OWNER_HANDOFF_TIMEOUT_TICKS);
  }

  private void completeLocalRetargetOwnerOwned(PlayerRef owner, MobRef servant, UUID servantId, long token, TargetSnapshot target) {
    if (!owner.isOnline() || getActiveLevel(owner) <= 0 || !canDamageSnapshotOwned(owner, target)) {
      pendingLocalRetargets.remove(servantId, token);
      return;
    }

    UUID ownerId = owner.uuid();
    if (!AdaptApi.scheduler().runEntity(servant, () -> completeLocalRetargetServantOwned(servant, servantId, token, ownerId, target))) {
      pendingLocalRetargets.remove(servantId, token);
    }
  }

  private void completeLocalRetargetServantOwned(MobRef servant, UUID servantId, long token,
                                                 UUID ownerId, TargetSnapshot target) {
    try {
      if (!Long.valueOf(token).equals(pendingLocalRetargets.get(servantId))) {
        return;
      }
      if (!ownerId.equals(servantOwners.get(servantId)) || !servant.isValid() || servant.isDead()
          || !ServantPulse.takesPriority(order(servant))) {
        return;
      }

      Threat threat = threats.get(ownerId);
      if (threat != null && System.currentTimeMillis() - threat.stamp() < getThreatWindowMillis()
          && assignPriorityTargetOwned(servant, threat, false)) {
        return;
      }

      LivingRef current = servant.target();
      if (current != null && current.has(EntityTrait.MONSTER) && !isTrackedServant(current)) {
        return;
      }
      if (current != null) {
        servant.setTarget(null);
      }
      if (servant.world().equals(target.world())) {
        servant.setTarget(target.entity());
      }
    } finally {
      pendingLocalRetargets.remove(servantId, token);
    }
  }

  private boolean assignPriorityTargetOwned(MobRef servant, Threat threat, boolean showFx) {
    if (!ServantPulse.takesPriority(order(servant))) {
      return false;
    }
    if (threats.get(threat.ownerId()) != threat || System.currentTimeMillis() - threat.stamp() >= getThreatWindowMillis()) {
      return false;
    }

    TargetSnapshot snapshot = threat.snapshot();
    if (!servant.world().equals(snapshot.world())) {
      return false;
    }

    if (!snapshot.entity().equals(servant.target())) {
      servant.setTarget(snapshot.entity());
    }
    if (showFx) {
      fx(servant.location().add(0, 1.0D, 0), FxPriority.COMBAT).particle(Particles.SCULK_SOUL, 3, 0, 0, 0, 0.2D, 0.02D);
    }
    return true;
  }

  private void requestThreatRefresh(Threat threat, long now) {
    if (!threat.beginRefresh(now)) {
      return;
    }

    LivingRef target = threat.target();
    if (!AdaptApi.scheduler().runEntity(target, () -> {
      PlayerRef owner = threat.owner();
      TargetSnapshot snapshot = captureTargetOwned(owner, target);
      if (!AdaptApi.scheduler().runEntity(owner, () -> completeThreatRefreshOwnerOwned(threat, snapshot))) {
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
      PlayerRef owner = threat.owner();
      if (snapshot == null || !owner.isOnline() || getActiveLevel(owner) <= 0 || !canDamageSnapshotOwned(owner, snapshot)) {
        threats.remove(threat.ownerId(), threat);
        return;
      }
      threat.updateSnapshot(snapshot);
    } finally {
      threat.finishRefresh(now);
    }
  }

  private TargetSnapshot captureTargetOwned(PlayerRef owner, LivingRef target) {
    MinionTargeting.Judged judged = MinionTargeting.captureJudgedOwned(this, owner, target);
    if (judged == null) {
      return null;
    }

    boolean afflicted = target.hasEffect(POISON)
        || target.hasEffect(WITHER);
    return new TargetSnapshot(judged, target instanceof MobRef, afflicted,
        !AdaptationDamageTargets.allows(target, true));
  }

  private boolean canDamageSnapshotOwned(PlayerRef owner, TargetSnapshot target) {
    if (preference(owner, TragoulPreferences.STANCE) == TragoulPreferences.Stance.PASSIVE
        || (preference(owner, TragoulPreferences.STANCE) == TragoulPreferences.Stance.DEFEND && !isDefenseTarget(owner.uuid(), target.entity()))) {
      return false;
    }
    return MinionTargeting.canDamageJudgedOwned(this, owner, target.judged());
  }

  private void removeServantOwned(UUID ownerId, MobRef servant, boolean showFx) {
    UUID servantId = servant.uuid();
    forgetServant(ownerId, servant, servantId);
    if (isServant(servant) && servant.isValid() && !servant.isDead()) {
      AdaptAttributeService.get().clearAllAdapt(servant);
      if (showFx) {
        crumbleFx(servant);
      }
      servant.remove();
    }
  }

  void crumbleFx(MobRef servant) {
    fx(servant.location().add(0, 1.0D, 0), FxPriority.TRANSITION)
        .particle(Particles.SOUL, 4, 0, 0.4D, 0, 0.3D, 0.03D)
        .dustBurst(SERVANT_BONE, 4, 0.3D, 1.0F)
        .chord(Sounds.ENTITY_SKELETON_DEATH, 0.7F, 1.3F, Sounds.BLOCK_BONE_BLOCK_BREAK, 0.4F, 0.9F);
  }

  private static void configureBurden(Config config) {
    Minions.setBurden(config.healthCostEnabled ? config.healthCostPerMinion : 0, config.minimumOwnerMaxHealth);
  }

  private void forgetServant(UUID ownerId, MobRef servant, UUID servantId) {
    servantOwners.remove(servantId, ownerId);
    servantOwnersByEntity.remove(servant, ownerId);
    servantIdsByEntity.remove(servant, servantId);
    servantThornsCooldowns.remove(servantId);
    pendingLocalRetargets.remove(servantId);
    records.remove(servantId);
    CopyOnWriteArrayList<MobRef> list = servants.get(ownerId);
    if (list != null) {
      list.remove(servant);
      if (list.isEmpty()) {
        servants.remove(ownerId, list);
      }
    }
    Minions.releaseBurden(ownerId, servant);
    showPack(ownerId);
  }

  /** One lit pip per living servant out of the owner's cap; gone with the last servant. */
  private void showPack(UUID ownerId) {
    if (!HudStatus.capable(ownerId)) {
      return;
    }
    CopyOnWriteArrayList<MobRef> pack = servants.get(ownerId);
    int count = pack == null ? 0 : pack.size();
    if (count == 0) {
      HudStatus.clear(ownerId, this, HudGlyph.CHARGES);
      return;
    }
    HudStatus.charges(ownerId, this, count, Math.max(count, packCaps.getOrDefault(ownerId, count)));
  }

  private void playSummonRitual(Loc spawn) {
    timeline(spawn)
        .duration(12)
        .priority(FxPriority.GAMEPLAY)
        .cullRadius(32)
        .frame((fx, tick, progress) -> {
          if (tick < 4) {
            fx.dustRing(SERVANT_BONE, 1.2 - (0.2 * tick), 12, 0.1F);
          } else {
            double rise = (tick - 4) * 0.15;
            fx.particle(Particles.SOUL, 3, 0, 0.5 + rise, 0, 0.25, 0.03);
            fx.particle(Particles.SCULK_SOUL, 2, 0, 0.5 + rise, 0, 0.2, 0.02);
          }
          if (tick == 0) {
            fx.sound(Sounds.BLOCK_BONE_BLOCK_PLACE, 0.6F, 0.7F);
          } else if (tick == 11) {
            fx.sound(Sounds.ENTITY_SKELETON_AMBIENT, 0.9F, 0.7F);
          }
        })
        .start();
  }

  private void removeServantRef(UUID ownerId, MobRef servant) {
    forgetServant(ownerId, servant, servant.uuid());
  }

  private LivingRef resolvePriorityTargetOwned(UUID ownerId, PlayerRef owner, long now) {
    Threat threat = threats.get(ownerId);
    if (threat == null || now - threat.stamp() >= getThreatWindowMillis()) {
      return null;
    }

    TargetSnapshot target = threat.snapshot();
    if (!target.world().equals(owner.world()) || !canDamageSnapshotOwned(owner, target)) {
      return null;
    }
    requestThreatRefresh(threat, now);
    return target.entity();
  }

  private boolean isDefenseTarget(UUID ownerId, LivingRef candidate) {
    TargetRequest request = latestTargetRequests.get(ownerId);
    return isPriorityTarget(ownerId, candidate) || (request != null && request.target().equals(candidate));
  }

  private boolean isPriorityTarget(UUID ownerId, LivingRef candidate) {
    Threat threat = threats.get(ownerId);
    return threat != null
        && threat.target().equals(candidate)
        && System.currentTimeMillis() - threat.stamp() < getThreatWindowMillis();
  }

  private void applyServantAttributes(MobRef servant, int level) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.apply(servant, getId().toString(), "health", Attributes.MAX_HEALTH,
        servantAttributeBonus(level, getConfig().healthBonusPerLevel), AttributeOperation.ADD_VALUE);
    attributes.apply(servant, getId().toString(), "attack", Attributes.ATTACK_DAMAGE,
        servantAttributeBonus(level, getConfig().attackBonusPerLevel), AttributeOperation.ADD_VALUE);
    double maxHealth = servant.attributeValue(Attributes.MAX_HEALTH);
    if (!Double.isNaN(maxHealth)) {
      servant.setHealth(maxHealth);
    }
  }

  static double servantAttributeBonus(int level, double bonusPerLevel) {
    return level * bonusPerLevel;
  }

  private void equipServant(MobRef servant, int level, ThreadLocalRandom random) {
    int tier = getGearTier(level);
    double pieceChance = getConfig().gearChancePerPiece;
    double enchantChance = tier == 0 ? 0 : Math.max(0, getConfig().enchantChanceBase + (getLevelPercent(level) * getConfig().enchantChanceFactor));
    if (random.nextDouble() < pieceChance) {
      servant.setEquipment(EquipmentSlot.HEAD, rollPiece(random, HELMETS[tier], enchantChance, PROTECTION));
    }
    if (random.nextDouble() < pieceChance) {
      servant.setEquipment(EquipmentSlot.CHEST, rollPiece(random, CHESTPLATES[tier], enchantChance, PROTECTION));
    }
    if (random.nextDouble() < pieceChance) {
      servant.setEquipment(EquipmentSlot.LEGS, rollPiece(random, LEGGINGS[tier], enchantChance, PROTECTION));
    }
    if (random.nextDouble() < pieceChance) {
      servant.setEquipment(EquipmentSlot.FEET, rollPiece(random, BOOTS[tier], enchantChance, PROTECTION));
    }

    ItemRef weapon = random.nextDouble() < getConfig().bowChance
        ? rollPiece(random, BOW_POOL, enchantChance, POWER)
        : rollPiece(random, SWORDS[tier], enchantChance, SHARPNESS);
    servant.setEquipment(EquipmentSlot.MAIN_HAND, weapon);

    servant.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
    servant.setEquipmentDropChance(EquipmentSlot.CHEST, 0f);
    servant.setEquipmentDropChance(EquipmentSlot.LEGS, 0f);
    servant.setEquipmentDropChance(EquipmentSlot.FEET, 0f);
    servant.setEquipmentDropChance(EquipmentSlot.MAIN_HAND, 0f);
    servant.setEquipmentDropChance(EquipmentSlot.OFF_HAND, 0f);
  }

  private ItemRef rollPiece(ThreadLocalRandom random, Id[] pool, double enchantChance, Id enchantment) {
    Id material = pool[random.nextInt(pool.length)];
    ItemRef item = AdaptApi.platform().host().items().create(material);
    if (enchantChance > 0 && random.nextDouble() < enchantChance) {
      item.setEnchantment(enchantment, 1 + random.nextInt(2));
    }
    return item;
  }

  private static ItemRef bone(int amount) {
    return AdaptApi.platform().host().items().create(BONE, amount);
  }

  private static MobRef asSkeleton(EntityRef entity) {
    return entity instanceof MobRef mob && SKELETON.equals(mob.type()) ? mob : null;
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

  private static LivingRef resolveLivingDamager(EntityRef damager) {
    if (damager instanceof LivingRef living) {
      return living;
    }

    if (damager instanceof ProjectileRef projectile && projectile.shooter() instanceof LivingRef shooter) {
      return shooter;
    }

    return null;
  }

  private ServantDamager resolveServantDamager(EntityRef damager) {
    EntityRef source = damager;
    if (source instanceof ProjectileRef projectile && projectile.shooter() != null) {
      source = projectile.shooter();
    }

    MobRef skeleton = asSkeleton(source);
    if (skeleton != null) {
      UUID ownerId = servantOwnersByEntity.get(skeleton);
      if (ownerId != null) {
        return new ServantDamager(skeleton, ownerId);
      }
    }

    return null;
  }

  private UUID resolveServantOwnerOwned(MobRef servant) {
    UUID ownerId = servantOwnersByEntity.get(servant);
    if (ownerId != null) {
      return ownerId;
    }

    ownerId = getServantOwnerId(servant);
    if (ownerId == null) {
      return null;
    }
    UUID servantId = servant.uuid();
    servantOwners.put(servantId, ownerId);
    servantOwnersByEntity.put(servant, ownerId);
    servantIdsByEntity.put(servant, servantId);
    return ownerId;
  }

  private boolean isTrackedServant(LivingRef entity) {
    MobRef skeleton = asSkeleton(entity);
    return skeleton != null && servantOwnersByEntity.containsKey(skeleton);
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

  private int getServantCap(int level, int golemancyLevel) {
    Config config = getConfig();
    return servantCap(getServantCap(level), golemancyCapBonus(golemancyLevel, config.golemancyCapBonusEveryLevels, config.golemancyCapBonusMax));
  }

  private int getDurationTicks(int level, int golemancyLevel) {
    Config config = getConfig();
    return (int) Math.round(getDurationTicks(level)
        * golemancyDurationMultiplier(golemancyLevel, config.golemancyDurationBonusPerLevel, config.golemancyDurationBonusMax));
  }

  private int golemancyLevel(PlayerRef player) {
    PlayerProfile adaptPlayer = getPlayer(player);
    Id golemancy = Id.parseAdapt(GOLEMANCY_SKILL);
    SkillProgress line = adaptPlayer == null ? null : adaptPlayer.skill(golemancy);
    return golemancyLevel(AdaptApi.content().skill(golemancy).orElse(null), line);
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
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    configureBurden(newConfig);
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
    private final PlayerRef owner;
    private final AtomicBoolean refreshPending = new AtomicBoolean();
    private volatile TargetSnapshot snapshot;
    private volatile long stamp;
    private volatile long nextRefreshAt;

    private Threat(UUID ownerId, PlayerRef owner, TargetSnapshot snapshot, long stamp) {
      this.ownerId = ownerId;
      this.owner = owner;
      this.snapshot = snapshot;
      this.stamp = stamp;
      this.nextRefreshAt = stamp + THREAT_REFRESH_MILLIS;
    }

    private UUID ownerId() {
      return ownerId;
    }

    private PlayerRef owner() {
      return owner;
    }

    private LivingRef target() {
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

  private record TargetRequest(LivingRef target, long requestedAt) {
  }

  private record TargetSnapshot(MinionTargeting.Judged judged, boolean mob, boolean afflicted,
                                boolean passiveOrNeutral) {
    private LivingRef entity() {
      return judged.candidate().entity();
    }

    private UUID entityId() {
      return judged.candidate().entityId();
    }

    private WorldRef world() {
      return judged.candidate().location().world();
    }
  }

  private record ServantDamager(MobRef servant, UUID ownerId) {
  }

  private record DefenseEffect(PlayerRef owner, double reflectedDamage, boolean thornsApplied,
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

  private static final class ServantRecord {
    private final long expiresAt;
    private final AtomicReference<ServantOrder> order = new AtomicReference<>(ServantOrder.FREE);
    private final AtomicBoolean lookPending = new AtomicBoolean();
    private volatile Loc sighting;

    private ServantRecord(long expiresAt) {
      this.expiresAt = expiresAt;
    }

    private long expiresAt() {
      return expiresAt;
    }

    private ServantOrder order() {
      return order.get();
    }

    private void order(ServantOrder next) {
      order.set(next);
    }

    private ServantOrder release(ServantOrder expected) {
      ServantOrder released = expected.released();
      return order.compareAndSet(expected, released) ? released : order.get();
    }

    private boolean lookOut() {
      return lookPending.compareAndSet(false, true);
    }

    private void lookFailed() {
      lookPending.set(false);
    }

    private void sighted(Loc at) {
      sighting = at;
      lookPending.set(false);
    }

    private Loc sighting() {
      return sighting;
    }
  }

  private static final class ServantMinionKind implements MinionKind {
    @Override
    public boolean matches(EntityRef entity) {
      return isServant(entity);
    }

    @Override
    public UUID ownerOf(EntityRef entity) {
      return getServantOwnerId(entity);
    }
  }

  @ConfigDescription("Sneak right-click with bones to raise a pack of temporary skeletal servants that gear up with your level, inherit your Tragoul perks, and hunt whatever you strike or whoever strikes you.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Bones consumed per summon before level scaling.", impact = "Higher values make summoning more expensive at low levels.")
    double boneCostBase = 8;
    @ConfigDoc(value = "Bone cost reduction granted at max level.", impact = "Higher values make summoning cheaper as the player levels.")
    double boneCostReduction = 5;
    @ConfigDoc(value = "Servant lifetime in ticks before level scaling.", impact = "Higher values keep each servant alive longer.")
    double durationTicksBase = 400;
    @ConfigDoc(value = "Additional servant lifetime ticks granted at max level.", impact = "Higher values increase the level-scaled lifetime growth.")
    double durationTicksFactor = 800;
    @ConfigDoc(value = "Summon cooldown in milliseconds before level scaling.", impact = "Higher values slow how often a servant can be summoned.")
    double cooldownMillisBase = 10000;
    @ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values let high levels summon more often.")
    double cooldownMillisFactor = 9000;
    @ConfigDoc(value = "Living servants allowed per adaptation level, with a hard runtime ceiling of 16 per owner.", impact = "Higher values let one necromancer field a larger pack up to the server-safety ceiling.")
    double servantCapPerLevel = 1.0;
    @ConfigDoc(value = "Replaces the oldest living servant when summoning at the cap.", impact = "False quietly refuses the summon instead of recycling the oldest servant.")
    boolean replaceOldestAtCap = true;
    @ConfigDoc(value = "Window in milliseconds during which the entity the owner last hit or was hit by stays the pack's priority target.", impact = "Higher values keep servants hunting the marked target for longer after the last combat event.")
    long playerThreatWindowMillis = 5000;
    @ConfigDoc(value = "Chance for each armor piece to be equipped on a freshly summoned servant.", impact = "Higher values produce more heavily armored servants.")
    double gearChancePerPiece = 0.55;
    @ConfigDoc(value = "Base chance for an equipped piece to receive an enchantment at mid gear tiers.", impact = "Higher values enchant servant gear more often before level scaling.")
    double enchantChanceBase = 0.0;
    @ConfigDoc(value = "Additional enchant chance granted at max level.", impact = "Higher values make high-level servants spawn with enchanted gear more often.")
    double enchantChanceFactor = 0.45;
    @ConfigDoc(value = "Chance a servant spawns with a bow instead of a sword.", impact = "Higher values produce more ranged servants.")
    double bowChance = 0.3;
    @ConfigDoc(value = "Bonus max health granted to servants per adaptation level.", impact = "Higher values make servants tankier as the owner levels.")
    double healthBonusPerLevel = 3.0;
    @ConfigDoc(value = "Bonus attack damage granted to servants per adaptation level.", impact = "Higher values make servants hit harder as the owner levels.")
    double attackBonusPerLevel = 1.0;
    @ConfigDoc(value = "Ticks between servant retarget pulses, clamped to at least 10 ticks.", impact = "Lower values retarget faster but cannot exceed the server-safety cadence.")
    int retargetIntervalTicks = 20;
    @ConfigDoc(value = "Radius the servant scans for hostile mobs to attack, capped at 24 blocks.", impact = "Higher values let the servant acquire targets further away up to the bounded search radius.")
    double targetSearchRadius = 12;
    @ConfigDoc(value = "XP granted per servant summon.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerSummon = 30;
    @ConfigDoc(value = "Enables the owner max health upkeep while servants are alive.", impact = "False lets the necromancer field servants with no health cost.")
    boolean healthCostEnabled = true;
    @ConfigDoc(value = "Max health removed from the owner per living servant.", impact = "Higher values make maintaining a large pack of servants more punishing.")
    double healthCostPerMinion = 2.0;
    @ConfigDoc(value = "Lowest max health the servant upkeep can reduce the owner to.", impact = "Higher values guarantee the owner keeps more health while servants are active.")
    double minimumOwnerMaxHealth = 4.0;
    @ConfigDoc(value = "Golemancy skill levels needed for each extra living servant on top of the level cap.", impact = "Lower values give Golemancers extra servants sooner; 0 turns the bonus off.")
    int golemancyCapBonusEveryLevels = 20;
    @ConfigDoc(value = "Most extra living servants that Golemancy skill levels can add, still within the hard ceiling of 16 per owner.", impact = "Higher values let high-level Golemancers field larger packs.")
    int golemancyCapBonusMax = 2;
    @ConfigDoc(value = "Fraction added to servant lifetime per Golemancy skill level. 0.01 is 1% longer per level.", impact = "Higher values keep a Golemancer's servants alive longer.")
    double golemancyDurationBonusPerLevel = 0.01;
    @ConfigDoc(value = "Most extra servant lifetime that Golemancy skill levels can add, as a fraction. 0.5 is 50% longer.", impact = "Higher values raise the lifetime ceiling for high-level Golemancers.")
    double golemancyDurationBonusMax = 0.5;

    public Config() {
      baseCost = 5;
      costFactor = 0.6;
      initialCost = 3;
    }
  }
}
