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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerGlowCoordinator;
import art.arcane.adapt.content.adaptation.tragoul.TragoulSkeletalServant;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.ChatColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TamingAlphasCommand extends SimpleAdaptation<TamingAlphasCommand.Config> {
  private static final int HARD_MAX_PETS = 24;
  private static final int FOCUS_REFRESH_TICKS = 10;
  private final Cooldowns commandCd = cooldowns();
  private final Map<UUID, FocusState> activeFocuses = new ConcurrentHashMap<>();
  private final Map<UUID, GlowState> targetGlows = new ConcurrentHashMap<>();
  private final AtomicLong commandSequence = new AtomicLong();
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public TamingAlphasCommand() {
    super("tame-alphas-command");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.alphas_command");
    setIcon(Material.BONE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.BONE)
        .key("challenge_taming_command_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.WOLF_ARMOR)
            .key("challenge_taming_command_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_command_250", "taming.alphas-command.commands", 250, 400);
    registerMilestone("challenge_taming_command_2500", "taming.alphas-command.commands", 2500, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getCommandRange(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getFocusTicks(level) * 50D, 1), 2);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getDamager() instanceof Player p) || !p.isSneaking()) {
      return;
    }

    if (p.getInventory().getItemInMainHand().getType() != Material.BONE) {
      return;
    }

    if (!(e.getEntity() instanceof LivingEntity target) || !activateCommand(p, target)) {
      return;
    }

    e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    if (!isCommandGesture(e.getAction(), e.getHand())) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking() || p.getInventory().getItemInMainHand().getType() != Material.BONE) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    LivingEntity target = raycastTarget(p, getCommandRange(level));
    if (target == null || !activateCommand(p, target)) {
      return;
    }

    e.setCancelled(true);
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID ownerId = e.getPlayer().getUniqueId();
    clearTargetGlow(e.getPlayer());
    clearOwnerFocuses(ownerId);
  }

  @Override
  public void unregister() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }

    List<GlowState> glows = new ArrayList<>(targetGlows.values());
    targetGlows.clear();
    for (GlowState glow : glows) {
      Player viewer = glow.viewer();
      if (viewer.isOnline()) {
        J.runEntity(viewer, () -> unsetGlowOwned(viewer, glow));
      }
    }
    Adapt.instance.getViewerGlowCoordinator().clearLayer(ViewerGlowCoordinator.Layer.TAMING_ALPHAS_COMMAND);

    List<FocusState> focuses = new ArrayList<>(activeFocuses.values());
    activeFocuses.clear();
    for (FocusState focus : focuses) {
      J.runEntity(focus.pet(), () -> clearFocusOwned(focus));
    }
    super.unregister();
  }

  private boolean activateCommand(Player p, LivingEntity target) {
    if (closed.get() || !isEligibleTarget(p, target)) {
      return false;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return false;
    }

    UUID playerId = p.getUniqueId();
    if (!commandCd.isReady(playerId, getConfig().commandCooldownMillis)) {
      return false;
    }

    double range = getCommandRange(level);
    List<Mob> pets = collectPets(p, range, target);
    if (pets.isEmpty()) {
      return false;
    }

    int focusTicks = getFocusTicks(level);
    int amplifier = Math.max(0, getConfig().focusSpeedAmplifier);
    long generation = commandSequence.incrementAndGet();
    int scheduled = 0;
    for (Mob pet : pets) {
      if (J.runEntity(pet, () -> focusPet(pet, p, playerId, target, focusTicks, amplifier, generation))) {
        scheduled++;
      }
    }
    if (scheduled <= 0) {
      return false;
    }

    commandCd.mark(playerId);
    consumeOneBone(p);
    showTargetGlow(p, target, focusTicks, generation);
    Location origin = p.getLocation().add(0, 1, 0);
    fx(origin, FxPriority.COMBAT)
        .ring(Particles.VILLAGER_HAPPY, 1.2D, 12, 0.3D)
        .chord(Sound.ENTITY_WOLF_GROWL, 0.8F, 0.9F, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.7F);
    addStat(p, "taming.alphas-command.commands", 1);
    xp(p, getConfig().xpPerCommand);
    return true;
  }

  private LivingEntity raycastTarget(Player p, double range) {
    Location eye = p.getEyeLocation();
    RayTraceResult hit = p.getWorld().rayTrace(
        eye,
        eye.getDirection(),
        range,
        FluidCollisionMode.NEVER,
        true,
        0.6D,
        entity -> entity instanceof LivingEntity living
            && living != p
            && !(living instanceof ArmorStand)
            && living.isValid()
            && !living.isDead()
            && !isOwnPet(p, living)
    );
    return hit != null && hit.getHitEntity() instanceof LivingEntity target ? target : null;
  }

  private boolean isEligibleTarget(Player p, LivingEntity target) {
    if (target == p || target instanceof ArmorStand || !target.isValid() || target.isDead() || isOwnPet(p, target)) {
      return false;
    }
    if (target instanceof Player player && !p.canSee(player)) {
      return false;
    }
    return canDamageTarget(p, target);
  }

  private List<Mob> collectPets(Player owner, double range, LivingEntity target) {
    UUID ownerId = owner.getUniqueId();
    int limit = getPetLimit();
    List<Mob> pets = new ArrayList<>(limit);
    for (Entity entity : owner.getNearbyEntities(range, range, range)) {
      if (entity == target) {
        continue;
      }
      if (entity instanceof Mob mob && mob instanceof Tameable tameable && isCombatCapableTameable(mob)
          && tameable.isTamed() && mob.isValid() && !mob.isDead() && isOwnedBy(tameable, ownerId)) {
        pets.add(mob);
        if (pets.size() >= limit) {
          break;
        }
      }
    }
    return pets;
  }

  private void focusPet(Mob pet, Player owner, UUID ownerId, LivingEntity target, int focusTicks, int amplifier,
                        long generation) {
    if (!pet.isValid() || pet.isDead() || !(pet instanceof Tameable tameable) || !tameable.isTamed()
        || !isOwnedBy(tameable, ownerId)) {
      return;
    }

    UUID petId = pet.getUniqueId();
    FocusState previous = activeFocuses.get(petId);
    boolean restoreSitting = previous != null
        ? previous.restoreSitting()
        : pet instanceof Sittable sittable && sittable.isSitting();
    FocusState focus = new FocusState(petId, pet, owner, ownerId, target, generation, restoreSitting);
    activeFocuses.put(petId, focus);
    if (pet instanceof Sittable sittable && sittable.isSitting()) {
      sittable.setSitting(false);
    }
    pet.setTarget(target);
    applyFocusBuffs(pet, focusTicks, amplifier);
    fx(pet, FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 4, 0.25D)
        .sound(Sound.ENTITY_WOLF_GROWL, 0.6F, 1.2F);
    scheduleFocusRefresh(focus, focusTicks);
  }

  private void applyFocusBuffs(Mob pet, int focusTicks, int amplifier) {
    if (focusTicks <= 0) {
      return;
    }
    AdaptAttributeService service = AdaptAttributeService.get();
    service.applyTimed(pet, getName(), "damage", Attributes.ATTACK_DAMAGE, focusDamageBonus(amplifier),
        AttributeModifier.Operation.ADD_NUMBER, focusTicks);
    service.applyTimed(pet, getName(), "speed", Attributes.MOVEMENT_SPEED, focusSpeedScalar(amplifier),
        AttributeModifier.Operation.MULTIPLY_SCALAR_1, focusTicks);
  }

  private void scheduleFocusRefresh(FocusState focus, int remainingTicks) {
    int delay = Math.min(FOCUS_REFRESH_TICKS, remainingTicks);
    if (delay <= 0 || !J.runEntity(focus.pet(), () -> refreshFocusOwned(focus, remainingTicks - delay), delay)) {
      if (activeFocuses.remove(focus.petId(), focus)) {
        clearFocusOwned(focus);
      }
    }
  }

  private void refreshFocusOwned(FocusState focus, int remainingTicks) {
    Mob pet = focus.pet();
    if (!isCurrentFocus(focus)) {
      return;
    }
    if (closed.get() || remainingTicks <= 0 || !isPetFocusValid(focus)) {
      stopFocusOwned(focus);
      return;
    }

    if (!J.runEntity(focus.target(), () -> validateFocusTargetOwned(focus, remainingTicks))) {
      stopFocusOwned(focus);
    }
  }

  private void validateFocusTargetOwned(FocusState focus, int remainingTicks) {
    LivingEntity target = focus.target();
    if (!isCurrentFocus(focus)) {
      return;
    }
    if (!target.isValid() || target.isDead()) {
      stopFocusFromForeignThread(focus);
      return;
    }
    TargetPolicySnapshot snapshot = new TargetPolicySnapshot(
        target.getLocation().clone(),
        target instanceof Player,
        isProtectedFocusTarget(target, focus.ownerId())
    );
    if (!J.runEntity(focus.owner(), () -> validateFocusPolicyOwned(focus, remainingTicks, snapshot))) {
      activeFocuses.remove(focus.petId(), focus);
    }
  }

  private void validateFocusPolicyOwned(FocusState focus, int remainingTicks, TargetPolicySnapshot snapshot) {
    Player owner = focus.owner();
    if (!isCurrentFocus(focus) || closed.get() || !owner.isOnline()
        || !owner.getUniqueId().equals(focus.ownerId()) || getActiveLevel(owner) <= 0
        || snapshot.protectedFriendly() || !canAffectFocusTarget(owner, snapshot)) {
      stopFocusFromForeignThread(focus);
      return;
    }
    if (!J.runEntity(focus.pet(), () -> applyFocusRefreshOwned(focus, remainingTicks))) {
      activeFocuses.remove(focus.petId(), focus);
    }
  }

  private boolean canAffectFocusTarget(Player owner, TargetPolicySnapshot snapshot) {
    if (snapshot.location().getWorld() == null || owner.getWorld() != snapshot.location().getWorld()) {
      return false;
    }
    return snapshot.player()
        ? canPVP(owner, snapshot.location())
        : canPVE(owner, snapshot.location());
  }

  private boolean isProtectedFocusTarget(LivingEntity target, UUID ownerId) {
    if (target.isInvulnerable() || target.hasMetadata("NPC") || TragoulSkeletalServant.isServant(target)) {
      return true;
    }
    if (target instanceof Tameable tameable && tameable.isTamed()) {
      return isOwnedBy(tameable, ownerId);
    }
    return false;
  }

  private void applyFocusRefreshOwned(FocusState focus, int remainingTicks) {
    Mob pet = focus.pet();
    if (!isCurrentFocus(focus) || closed.get() || !isPetFocusValid(focus)) {
      stopFocusOwned(focus);
      return;
    }
    if (pet instanceof Sittable sittable && sittable.isSitting()) {
      sittable.setSitting(false);
    }
    if (pet.getTarget() != focus.target()) {
      pet.setTarget(focus.target());
    }
    scheduleFocusRefresh(focus, remainingTicks);
  }

  private boolean isPetFocusValid(FocusState focus) {
    Mob pet = focus.pet();
    if (!pet.isValid() || pet.isDead() || !(pet instanceof Tameable tameable) || !tameable.isTamed()) {
      return false;
    }
    return isOwnedBy(tameable, focus.ownerId());
  }

  private boolean isCurrentFocus(FocusState focus) {
    FocusState current = activeFocuses.get(focus.petId());
    return current != null && current.generation() == focus.generation();
  }

  private void stopFocusOwned(FocusState focus) {
    if (activeFocuses.remove(focus.petId(), focus)) {
      clearFocusOwned(focus);
    }
  }

  private void stopFocusFromForeignThread(FocusState focus) {
    if (!J.runEntity(focus.pet(), () -> stopFocusOwned(focus))) {
      activeFocuses.remove(focus.petId(), focus);
    }
  }

  private void clearFocusOwned(FocusState focus) {
    Mob pet = focus.pet();
    AdaptAttributeService.get().removeAll(pet, getName());
    LivingEntity current = pet.getTarget();
    if (current == focus.target()) {
      pet.setTarget(null);
    }
    if (focus.restoreSitting() && pet instanceof Sittable sittable) {
      sittable.setSitting(true);
    }
  }

  private void clearOwnerFocuses(UUID ownerId) {
    for (FocusState focus : new ArrayList<>(activeFocuses.values())) {
      if (focus.ownerId().equals(ownerId) && activeFocuses.remove(focus.petId(), focus)) {
        J.runEntity(focus.pet(), () -> clearFocusOwned(focus));
      }
    }
  }

  private void consumeOneBone(Player p) {
    ItemStack bone = p.getInventory().getItemInMainHand();
    if (bone.getType() != Material.BONE || bone.getAmount() <= 0) {
      return;
    }

    int remaining = boneAmountAfterSuccessfulCommand(p.getGameMode(), bone.getAmount());
    if (remaining == bone.getAmount()) {
      return;
    }
    if (remaining <= 0) {
      p.getInventory().setItemInMainHand(null);
      return;
    }
    bone.setAmount(remaining);
    p.getInventory().setItemInMainHand(bone);
  }

  private void showTargetGlow(Player viewer, LivingEntity target, int focusTicks, long generation) {
    ViewerGlowCoordinator glowCoordinator = Adapt.instance.getViewerGlowCoordinator();
    if (glowCoordinator == null || !glowCoordinator.isAvailable()) {
      return;
    }

    UUID viewerId = viewer.getUniqueId();
    GlowState glow = new GlowState(viewer, target.getUniqueId(), target.getEntityId(), generation);
    GlowState previous = targetGlows.put(viewerId, glow);
    if (previous != null
        && (!previous.targetId().equals(glow.targetId())
        || previous.runtimeEntityId() != glow.runtimeEntityId())) {
      unsetGlowOwned(viewer, previous);
    }

    if (!glowCoordinator.set(
        ViewerGlowCoordinator.Layer.TAMING_ALPHAS_COMMAND,
        target,
        viewer,
        ChatColor.RED
    )) {
      targetGlows.remove(viewerId, glow);
      return;
    }
    if (!J.runEntity(viewer, () -> expireTargetGlowOwned(viewer, glow), focusTicks)) {
      expireTargetGlowOwned(viewer, glow);
    }
  }

  private void expireTargetGlowOwned(Player viewer, GlowState glow) {
    UUID viewerId = viewer.getUniqueId();
    GlowState current = targetGlows.get(viewerId);
    if (current != null && current.generation() == glow.generation() && targetGlows.remove(viewerId, current)) {
      unsetGlowOwned(viewer, current);
    }
  }

  private void clearTargetGlow(Player viewer) {
    GlowState glow = targetGlows.remove(viewer.getUniqueId());
    if (glow != null) {
      unsetGlowOwned(viewer, glow);
    }
  }

  private void unsetGlowOwned(Player viewer, GlowState glow) {
    ViewerGlowCoordinator glowCoordinator = Adapt.instance.getViewerGlowCoordinator();
    if (glowCoordinator == null) {
      return;
    }
    glowCoordinator.unset(
        ViewerGlowCoordinator.Layer.TAMING_ALPHAS_COMMAND,
        glow.targetId(),
        glow.runtimeEntityId(),
        viewer
    );
  }

  private boolean isOwnPet(Player p, LivingEntity target) {
    return target instanceof Tameable tameable && tameable.isTamed() && isOwnedBy(tameable, p.getUniqueId());
  }

  private boolean isOwnedBy(Tameable tameable, UUID ownerId) {
    AnimalTamer owner = tameable.getOwner();
    return owner != null && ownerId.equals(owner.getUniqueId());
  }

  private double getCommandRange(int level) {
    return getConfig().commandRangeBase + (getLevelPercent(level) * getConfig().commandRangeFactor);
  }

  private int getFocusTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().focusTicksBase + (getLevelPercent(level) * getConfig().focusTicksFactor)));
  }

  private int getPetLimit() {
    return Math.max(1, Math.min(getConfig().maxPets, HARD_MAX_PETS));
  }

  static boolean isCommandGesture(Action action, EquipmentSlot hand) {
    return hand == EquipmentSlot.HAND && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
  }

  static boolean isCombatCapableTameable(Entity entity) {
    return entity instanceof Mob && entity instanceof Tameable
        && (entity instanceof Wolf || entity instanceof Cat || entity instanceof Llama);
  }

  static int boneAmountAfterSuccessfulCommand(GameMode gameMode, int currentAmount) {
    return gameMode == GameMode.CREATIVE ? currentAmount : Math.max(0, currentAmount - 1);
  }

  static double focusDamageBonus(int amplifier) {
    return 3.0D * (amplifier + 1);
  }

  static double focusSpeedScalar(int amplifier) {
    return 0.2D * (amplifier + 1);
  }

  @ConfigDescription("Sneak-left-click a target while holding a bone to make your nearby pets focus it.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Command Range Base for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double commandRangeBase = 8.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Command Range Factor for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double commandRangeFactor = 12.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Focus Ticks Base for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double focusTicksBase = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Focus Ticks Factor for the Taming Alpha's Command adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double focusTicksFactor = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Amplifier of the speed and strength focus buff applied to commanded pets.", impact = "Higher values give commanded pets a stronger pursuit buff.")
    int focusSpeedAmplifier = 0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in milliseconds between commands.", impact = "Higher values reduce how often the pack can be re-commanded.")
    long commandCooldownMillis = 3000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted each time the pack is commanded.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerCommand = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum pets commanded per activation, capped internally at 24.", impact = "Lower values reduce entity scheduling in very large packs.")
    int maxPets = 12;

    public Config() {
      baseCost = 4;
      costFactor = 0.55;
      initialCost = 4;
    }
  }

  private record FocusState(UUID petId, Mob pet, Player owner, UUID ownerId, LivingEntity target, long generation,
                            boolean restoreSitting) {
  }

  private record TargetPolicySnapshot(Location location, boolean player, boolean protectedFriendly) {
  }

  private record GlowState(Player viewer, UUID targetId, int runtimeEntityId, long generation) {
  }
}
