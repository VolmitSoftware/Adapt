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

package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.DeathSaveCooldown;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.damage.PlayerDeathSave;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RiftMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class RiftVoidSkin extends SimpleAdaptation<RiftVoidSkin.Config> implements PlayerDeathSave {
  public static final PlayerPreference<Reserve> RESERVE = new PlayerPreference<>(Reserve.class,
      new PlayerPreference.Definition<>("reserve", RiftMessages.RIFTVOIDSKIN_PREFERENCE_RESERVE, Reserve.NONE, List.of(
          new PlayerPreference.Choice<>(Reserve.NONE, RiftMessages.RIFTVOIDSKIN_PREFERENCE_RESERVE_NONE, Id.minecraft("ender_pearl"), 1),
          new PlayerPreference.Choice<>(Reserve.ONE, RiftMessages.RIFTVOIDSKIN_PREFERENCE_RESERVE_ONE, Id.minecraft("ender_eye"), 1),
          new PlayerPreference.Choice<>(Reserve.FOUR, RiftMessages.RIFTVOIDSKIN_PREFERENCE_RESERVE_FOUR, Id.minecraft("ender_chest"), 1))));
  public static final PlayerPreference<Causes> CAUSES = new PlayerPreference<>(Causes.class,
      new PlayerPreference.Definition<>("causes", RiftMessages.RIFTVOIDSKIN_PREFERENCE_CAUSES, Causes.ALL, List.of(
          new PlayerPreference.Choice<>(Causes.ALL, RiftMessages.RIFTVOIDSKIN_PREFERENCE_CAUSES_ALL, Id.minecraft("totem_of_undying"), 1),
          new PlayerPreference.Choice<>(Causes.COMBAT, RiftMessages.RIFTVOIDSKIN_PREFERENCE_CAUSES_COMBAT, Id.minecraft("iron_sword"), 1),
          new PlayerPreference.Choice<>(Causes.ENVIRONMENT, RiftMessages.RIFTVOIDSKIN_PREFERENCE_CAUSES_ENVIRONMENT, Id.minecraft("lava_bucket"), 1))));
  public static final PlayerPreference<CommonPreferences.Toggle> SPAWN = CommonPreferences.toggle("spawn-fallback", RiftMessages.RIFTVOIDSKIN_PREFERENCE_SPAWN, CommonPreferences.Toggle.ON);

  private static final double HARD_MAX_SEARCH_RADIUS = 16D;
  private static final int SAFE_SAMPLES = 32;
  private static final Id RESISTANCE = Id.minecraft("resistance");
  private static final Id ENDER_PEARL = Id.minecraft("ender_pearl");
  private static final Set<Id> HAZARDS = Set.of(Id.minecraft("lava"), Id.minecraft("fire"), Id.minecraft("soul_fire"),
      Id.minecraft("campfire"), Id.minecraft("soul_campfire"), Id.minecraft("magma_block"), Id.minecraft("cactus"),
      Id.minecraft("powder_snow"), Id.minecraft("sweet_berry_bush"), Id.minecraft("wither_rose"));

  private final Map<UUID, VoidEscape> pendingEscapes = new ConcurrentHashMap<>();
  private final DataKey<String> pearlReservationKey;
  private final AtomicBoolean acceptingEscapes = new AtomicBoolean(true);

  public RiftVoidSkin() {
    super("rift-void-skin");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("echo_shard"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ender_pearl"))
        .key("challenge_rift_void_skin_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("echo_shard"))
            .key("challenge_rift_void_skin_500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_rift_void_skin_50", "rift.void-skin.escapes", 50, 400);
    registerMilestone("challenge_rift_void_skin_500", "rift.void-skin.escapes", 500, 1500);
    pearlReservationKey = DataKey.adapt("rift_void_skin_reserved_pearl", DataCodecs.STRING);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, CommonPreferences.DEATH_SAVE_PRIORITY, RESERVE, CAUSES, SPAWN);
  }

  @Override
  public int deathSavePriority(PlayerRef player) {
    return preference(player, CommonPreferences.DEATH_SAVE_PRIORITY).ordinal();
  }

  @Override
  public int defaultOrder() {
    return 2;
  }

  @Override
  public boolean protectsFromDamage(EntityDamageEvent event) {
    return event.entity() instanceof PlayerRef player
        && isLethalDamage(player.health(), event.finalDamage())
        && pendingEscapes.containsKey(player.uuid());
  }

  @Override
  protected void onRuntimeActivated() {
    acceptingEscapes.set(true);
    for (PlayerRef player : AdaptCore.platform().host().onlinePlayers()) {
      Tasks.runEntity(player, () -> restoreStrandedReservation(player));
    }
  }

  @Override
  public void unregister() {
    acceptingEscapes.set(false);
    List<VoidEscape> operations = new ArrayList<>(pendingEscapes.values());
    pendingEscapes.clear();
    for (VoidEscape operation : operations) {
      PlayerRef player = AdaptCore.platform().host().player(operation.playerId());
      if (player != null) {
        Tasks.runEntity(player, () -> refundReservation(
            player,
            operation.reservation(),
            AbilityRefundReason.ADAPTATION_DISABLED
        ));
      }
    }
    super.unregister();
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + "+ " + AdaptLanguage.text(RiftMessages.VOID_SKIN_LORE1));
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownMillis(level), 1), 2);
    v.addLore(C.RED + "* " + AdaptLanguage.text(RiftMessages.VOID_SKIN_LORE3));
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    tryPreventDeath(e);
  }

  @Override
  public void tryPreventDeath(EntityDamageEvent e) {
    if (e.isCancelled() || !(e.entity() instanceof PlayerRef p)) {
      return;
    }

    if (!isLethalDamage(p.health(), e.finalDamage())) {
      return;
    }

    Causes causes = preference(p, CAUSES);
    boolean combat = e instanceof EntityDamageByEntityEvent;
    if ((causes == Causes.COMBAT && !combat) || (causes == Causes.ENVIRONMENT && combat)) {
      return;
    }
    int level = getActiveLevel(p);
    if (level <= 0 || !acceptingEscapes.get()) {
      return;
    }

    UUID id = p.uuid();
    if (pendingEscapes.containsKey(id)) {
      e.setCancelled(true);
      return;
    }
    long cooldownMillis = getCooldownMillis(level);
    long now = System.currentTimeMillis();
    long remainingCooldown = DeathSaveCooldown.VOID_SKIN.remaining(p, now);
    if (remainingCooldown > 0L) {
      AdaptLog.verbose(() -> "Void Skin skipped " + id + ": "
          + remainingCooldown + "ms cooldown left.");
      return;
    }

    if (!hasPlainPearl(p)) {
      AdaptLog.verbose(() -> "Void Skin skipped " + id + ": no plain ender pearl in inventory.");
      return;
    }

    Loc destination = findSafeSpot(p);
    if (destination == null) {
      AdaptLog.verbose(() -> "Void Skin skipped " + id + ": no safe spot and no usable world spawn.");
      return;
    }

    Loc origin = p.location();
    destination = destination.withRotation(origin.yaw(), origin.pitch());
    PearlReservation reservation = reservePlainPearl(p);
    if (reservation == null) {
      AdaptLog.verbose(() -> "Void Skin skipped " + id + ": pearl cost was refused.");
      return;
    }

    if (!DeathSaveCooldown.VOID_SKIN.activate(p, now, cooldownMillis)) {
      refundReservation(p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      return;
    }
    AbilityGlyphs.cooldown(id, this, now, cooldownMillis);
    VoidEscape operation = new VoidEscape(id, origin, level, reservation);
    pendingEscapes.put(id, operation);
    // Survive first. The promise is that lethal damage never kills while the
    // pearl is banked, so the kill is cancelled before the teleport gets a
    // chance to fail to even start.
    cancelLethalDamage(e, true);

    CompletableFuture<Boolean> teleport;
    try {
      teleport = p.teleport(destination, TeleportCause.PLUGIN);
    } catch (RuntimeException exception) {
      pendingEscapes.remove(id, operation);
      refundReservation(p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      AdaptLog.error(exception);
      AdaptLog.error("Void Skin could not start an escape teleport for " + id + ".");
      return;
    }

    if (teleport == null) {
      pendingEscapes.remove(id, operation);
      refundReservation(p, reservation, AbilityRefundReason.ACTIVATION_FAILED);
      AdaptLog.error("Void Skin got no escape teleport future for " + id + ".");
      return;
    }

    teleport.whenComplete((Boolean success, Throwable failure) -> finishEscape(p, operation, success, failure));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    VoidEscape operation = pendingEscapes.remove(id);
    if (operation != null) {
      refundReservation(e.player(), operation.reservation(), AbilityRefundReason.PLAYER_LEFT);
    }
  }

  @Handle
  public void on(PlayerJoinEvent e) {
    restoreStrandedReservation(e.player());
  }

  private long getCooldownMillis(int level) {
    long value = getConfig().cooldownBaseMillis - ((long) Math.max(0, level - 1) * getConfig().cooldownReductionPerLevelMillis);
    return Math.max(getConfig().minimumCooldownMillis, value);
  }

  private int getResistanceTicks(int level) {
    return Math.max(20, getConfig().resistanceTicksBase + (level * getConfig().resistanceTicksPerLevel));
  }

  private int getResistanceAmplifier() {
    return Math.max(0, Math.min(4, getConfig().resistanceAmplifier));
  }

  private void finishEscape(PlayerRef p, VoidEscape operation, Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptLog.error(failure);
      AdaptLog.error("Void Skin escape teleport failed for " + operation.playerId() + ".");
    }

    if (!Tasks.runEntity(p, () -> {
      if (!pendingEscapes.remove(operation.playerId(), operation)) {
        return;
      }
      if (failure != null || !Boolean.TRUE.equals(success) || !p.isOnline()) {
        refundReservation(p, operation.reservation(), AbilityRefundReason.ACTIVATION_FAILED);
        return;
      }

      if (!settleReservation(p, operation.reservation())) {
        return;
      }
      p.setFallDistance(0f);
      p.addEffect(new EffectInstance(RESISTANCE,
          getResistanceTicks(operation.level()), getResistanceAmplifier(), true, false, false));
      Loc actualDestination = p.location();
      fx(operation.origin(), FxPriority.TRANSITION)
          .dome(Particles.ENCHANTMENT_TABLE, 1.1, 16)
          .particle(Particles.WITCH, 10, 0, 1.0, 0, 0.4, 0.02)
          .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.8f, Sounds.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 1.2f);
      fx(actualDestination, FxPriority.TRANSITION)
          .ring(Particles.END_ROD, 0.8, 12, 0.1)
          .particle(Particles.REVERSE_PORTAL, 8, 0, 1.0, 0, 0.3, 0.04)
          .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.3f, Sounds.ITEM_ARMOR_EQUIP_NETHERITE, 0.5f, 1.1f);
      addStat(p, "rift.void-skin.escapes", 1);
      addStat(p, "rift.teleports", 1);
      xp(p, getConfig().xpOnEscape, "rift:void-skin:escape");
    })) {
      if (pendingEscapes.remove(operation.playerId(), operation)
          && operation.reservation().resolved().compareAndSet(false, true)) {
        refundCost(operation.reservation().charge().activationId(), AbilityRefundReason.ACTIVATION_FAILED);
        AdaptLog.error("Void Skin stranded an escape for " + operation.playerId()
            + "; the reserved pearl returns on rejoin.");
      }
    }
  }

  private Loc findSafeSpot(PlayerRef p) {
    Loc origin = p.location();
    double maxRadius = Math.max(3D, Math.min(HARD_MAX_SEARCH_RADIUS, getConfig().searchRadius));
    double minRadius = Math.min(maxRadius - 1D, Math.max(2D, getConfig().minRadius));
    Loc best = null;
    double bestDistance = -1D;
    for (int i = 0; i < SAFE_SAMPLES; i++) {
      double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2D);
      double distance = minRadius + ThreadLocalRandom.current().nextDouble(maxRadius - minRadius);
      double dx = Math.cos(angle) * distance;
      double dz = Math.sin(angle) * distance;
      Loc feet = origin.add(dx, 0, dz);
      if (!Tasks.isOwnedByCurrentRegion(feet)) {
        continue;
      }
      Loc resolved = resolveStandable(feet.world().blockAt(feet), 4);
      if (resolved == null) {
        continue;
      }
      double resolvedDistance = resolved.distanceSquared(origin);
      if (resolvedDistance > bestDistance) {
        bestDistance = resolvedDistance;
        best = resolved;
      }
    }
    return best != null ? best : preferenceEnabled(p, SPAWN) ? worldSpawnFallback(p.world()) : null;
  }

  private Loc resolveStandable(BlockRef base, int vertical) {
    for (int dy = 0; dy <= vertical; dy++) {
      BlockRef up = base.relative(0, dy, 0);
      if (isStandable(up)) {
        return up.location().add(0.5, 0, 0.5);
      }
      BlockRef down = base.relative(0, -dy, 0);
      if (isStandable(down)) {
        return down.location().add(0.5, 0, 0.5);
      }
    }
    return null;
  }

  private boolean isStandable(BlockRef feet) {
    BlockRef head = feet.relative(BlockFace.UP);
    BlockRef ground = feet.relative(BlockFace.DOWN);
    return feet.isPassable()
        && head.isPassable()
        && ground.isSolid()
        && !isHazard(ground.type())
        && !isHazard(feet.type())
        && !isHazard(head.type());
  }

  private boolean isHazard(Id material) {
    return HAZARDS.contains(material);
  }

  private boolean hasPlainPearl(PlayerRef p) {
    int pearls = 0;
    int reserve = switch (preference(p, RESERVE)) { case NONE -> 0; case ONE -> 1; case FOUR -> 4; };
    for (ItemRef stack : p.inventory().contents()) {
      if (RiftPearls.isPlainPearl(stack)) {
        pearls += stack.amount();
      }
    }
    return pearls > reserve;
  }

  private PearlReservation reservePlainPearl(PlayerRef p) {
    if (!hasPlainPearl(p)) {
      return null;
    }

    String token = UUID.randomUUID().toString();
    AtomicBoolean defaultConsumed = new AtomicBoolean();
    ItemRef unit = AdaptCore.platform().host().items().create(ENDER_PEARL, 1);
    AbilityCharge charge = payItemCostDeferred(p, "pearl", unit, 1, () -> {
      PlayerInventoryRef inventory = p.inventory();
      List<ItemRef> contents = inventory.contents();
      for (int slot = 0; slot < contents.size(); slot++) {
        ItemRef stack = contents.get(slot);
        if (RiftPearls.isPlainPearl(stack)) {
          p.data().set(pearlReservationKey, token);
          if (stack.amount() - 1 <= 0) {
            inventory.clear(slot);
          } else {
            inventory.setItem(slot, stack.copyWithAmount(stack.amount() - 1));
          }
          defaultConsumed.set(true);
          return true;
        }
      }

      return false;
    });
    return charge.allowed()
        ? new PearlReservation(charge, token, defaultConsumed.get(), new AtomicBoolean())
        : null;
  }

  private void refundReservation(PlayerRef p, PearlReservation reservation, AbilityRefundReason reason) {
    if (!reservation.resolved().compareAndSet(false, true)) {
      return;
    }
    refundCost(reservation.charge().activationId(), reason);
    if (!reservation.defaultConsumed() || !ownsReservation(p, reservation.token())) {
      return;
    }

    try {
      returnPearl(p);
      clearReservation(p, reservation.token());
    } catch (Throwable error) {
      AdaptLog.error("Void Skin could not return a reserved ender pearl to " + p.uuid() + ".");
      AdaptLog.error(error);
    }
  }

  private boolean settleReservation(PlayerRef player, PearlReservation reservation) {
    if (!reservation.resolved().compareAndSet(false, true)) {
      return false;
    }
    if (reservation.defaultConsumed() && !ownsReservation(player, reservation.token())) {
      refundCost(reservation.charge().activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return false;
    }
    boolean settled = settleCost(reservation.charge().activationId());
    if (!acceptSettlement(
        reservation.defaultConsumed(),
        reservation.charge().defaultCostSuppressed(),
        settled
    )) {
      refundCost(reservation.charge().activationId(), AbilityRefundReason.ACTIVATION_FAILED);
      return false;
    }
    if (reservation.defaultConsumed()) {
      clearReservation(player, reservation.token());
    }
    return true;
  }

  static boolean acceptSettlement(boolean defaultConsumed, boolean defaultCostSuppressed, boolean settled) {
    return defaultConsumed || !defaultCostSuppressed || settled;
  }

  private void restoreStrandedReservation(PlayerRef player) {
    String token = player.data().get(pearlReservationKey);
    if (token == null) {
      return;
    }
    try {
      returnPearl(player);
      clearReservation(player, token);
    } catch (Throwable error) {
      AdaptLog.error("Void Skin could not recover a reserved ender pearl for " + player.uuid() + ".");
      AdaptLog.error(error);
    }
  }

  private void returnPearl(PlayerRef player) {
    for (ItemRef leftover : player.inventory().addItem(AdaptCore.platform().host().items().create(ENDER_PEARL, 1))) {
      player.world().dropItemNaturally(player.location().position(), leftover);
    }
  }

  private boolean ownsReservation(PlayerRef player, String token) {
    return token.equals(player.data().get(pearlReservationKey));
  }

  private void clearReservation(PlayerRef player, String token) {
    if (ownsReservation(player, token)) {
      player.data().remove(pearlReservationKey);
    }
  }

  static boolean isLethalDamage(double health, double finalDamage) {
    return Double.isFinite(health)
        && Double.isFinite(finalDamage)
        && health > 0D
        && finalDamage >= health;
  }

  static boolean cancelLethalDamage(EntityDamageEvent event, boolean escapeReserved) {
    if (!escapeReserved) {
      return false;
    }
    event.setCancelled(true);
    return true;
  }

  static Loc worldSpawnFallback(WorldRef currentWorld) {
    if (currentWorld == null) {
      return null;
    }
    Loc spawn = currentWorld.spawnLocation();
    if (spawn == null || !currentWorld.equals(spawn.world())) {
      return null;
    }
    return new Loc(spawn.world(), spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
  }

  @ConfigDescription("Any lethal damage blinks you to a nearby safe spot, or the current world's spawn when none exists, with brief resistance for one ender pearl.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base cooldown in milliseconds between escapes.", impact = "Higher values force longer waits between escapes.")
    long cooldownBaseMillis = 120000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds per adaptation level beyond the first.", impact = "Higher values make leveling shorten the cooldown faster.")
    long cooldownReductionPerLevelMillis = 18000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lowest possible cooldown in milliseconds regardless of level.", impact = "Higher values keep a floor under cooldown reduction.")
    long minimumCooldownMillis = 45000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base resistance duration in ticks applied after an escape.", impact = "Higher values keep the resistance buff up longer.")
    int resistanceTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra resistance ticks per adaptation level.", impact = "Higher values extend the resistance buff as you level.")
    int resistanceTicksPerLevel = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Resistance amplifier applied after an escape.", impact = "Higher values reduce incoming damage more during the buff.")
    int resistanceAmplifier = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum horizontal search radius in blocks for a safe blink spot.", impact = "Higher values let the escape reach further, up to a hard 16-block limit.")
    double searchRadius = 9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum horizontal blink distance in blocks.", impact = "Higher values push the escape further from where you were hit.")
    double minRadius = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Skill XP granted when an escape triggers.", impact = "Higher values grant more skill XP per escape.")
    double xpOnEscape = 40;

    public Config() {
      baseCost = 8;
      costFactor = 0.4;
      maxLevel = 4;
      initialCost = 6;
    }
  }

  private record PearlReservation(AbilityCharge charge, String token, boolean defaultConsumed, AtomicBoolean resolved) {
  }

  private record VoidEscape(UUID playerId, Loc origin, int level, PearlReservation reservation) {
  }

  public enum Reserve { NONE, ONE, FOUR }

  public enum Causes { ALL, COMBAT, ENVIRONMENT }
}
