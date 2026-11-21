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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.fx.ViewerGlowPort;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformSupport;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.EntityMarkerPort;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.viewer.GlowColor;
import art.arcane.adapt.platform.world.RayHit;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TamingAlphasCommand extends SimpleAdaptation<TamingAlphasCommand.Config> {
  private static final int HARD_MAX_PETS = 24;
  private static final int FOCUS_REFRESH_TICKS = 10;
  private static final Id BONE = Id.minecraft("bone");
  private static final Set<Id> COMBAT_PETS = Set.of(Id.minecraft("wolf"), Id.minecraft("cat"), Id.minecraft("llama"),
      Id.minecraft("trader_llama"));
  private final Cooldowns commandCd = cooldowns();
  private final Map<UUID, FocusState> activeFocuses = new ConcurrentHashMap<>();
  private final Map<UUID, GlowState> targetGlows = new ConcurrentHashMap<>();
  private final AtomicLong commandSequence = new AtomicLong();
  private final AtomicBoolean closed = new AtomicBoolean(false);

  public TamingAlphasCommand() {
    super("tame-alphas-command");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.alphas_command");
    setIcon(Id.minecraft("bone"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("bone"))
        .key("challenge_taming_command_250")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(PlatformSupport.itemOr(Id.minecraft("wolf_armor"), Id.minecraft("iron_horse_armor")))
            .key("challenge_taming_command_2500")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_command_250", "taming.alphas-command.commands", 250, 400);
    registerMilestone("challenge_taming_command_2500", "taming.alphas-command.commands", 2500, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    clearTargetGlow(player.getPlayer());
    clearOwnerFocuses(player.getPlayer().uuid());
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.PETS, TamingPreferences.TARGETS, TamingPreferences.BONES);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getCommandRange(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getFocusTicks(level) * 50D, 1), 2);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.damager() instanceof PlayerRef p) || !p.isSneaking()) {
      return;
    }

    if (!BONE.equals(p.inventory().mainHand().type())) {
      return;
    }

    if (!(e.entity() instanceof LivingRef target) || !activateCommand(p, target)) {
      return;
    }

    e.setCancelled(true);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(PlayerInteractEvent e) {
    if (!isCommandGesture(e.action(), e.hand())) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking() || !BONE.equals(p.inventory().mainHand().type())) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    LivingRef target = raycastTarget(p, getCommandRange(level));
    if (target == null || !activateCommand(p, target)) {
      return;
    }

    e.setCancelled(true);
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID ownerId = e.player().uuid();
    clearTargetGlow(e.player());
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
      PlayerRef viewer = glow.viewer();
      if (viewer.isOnline()) {
        Tasks.runEntity(viewer, () -> unsetGlowOwned(viewer, glow));
      }
    }
    ViewerGlowPort.current().clearLayer(ViewerGlowPort.Layer.TAMING_ALPHAS_COMMAND);

    List<FocusState> focuses = new ArrayList<>(activeFocuses.values());
    activeFocuses.clear();
    for (FocusState focus : focuses) {
      Tasks.runEntity(focus.pet(), () -> clearFocusOwned(focus));
    }
    super.unregister();
  }

  private boolean activateCommand(PlayerRef p, LivingRef target) {
    if (closed.get() || !isEligibleTarget(p, target) || !preference(p, TamingPreferences.TARGETS).accepts(target)
        || !preference(p, TamingPreferences.BONES).permits(p.inventory().mainHand().amount(), 1)) {
      return false;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return false;
    }

    UUID playerId = p.uuid();
    if (!commandCd.isReady(playerId, getConfig().commandCooldownMillis)) {
      return false;
    }

    double range = getCommandRange(level);
    List<MobRef> pets = collectPets(p, range, target);
    if (pets.isEmpty()) {
      return false;
    }

    int focusTicks = getFocusTicks(level);
    int amplifier = Math.max(0, getConfig().focusSpeedAmplifier);
    long generation = commandSequence.incrementAndGet();
    int scheduled = 0;
    for (MobRef pet : pets) {
      if (Tasks.runEntity(pet, () -> focusPet(pet, p, playerId, target, focusTicks, amplifier, generation))) {
        scheduled++;
      }
    }
    if (scheduled <= 0) {
      return false;
    }

    commandCd.mark(playerId, getConfig().commandCooldownMillis);
    AbilityGlyphs.duration(playerId, this, System.currentTimeMillis(), focusTicks * 50L);
    consumeOneBone(p);
    showTargetGlow(p, target, focusTicks, generation);
    Loc origin = p.location().add(0, 1, 0);
    fx(origin, FxPriority.COMBAT)
        .ring(Particles.VILLAGER_HAPPY, 1.2D, 12, 0.3D)
        .chord(Sounds.ENTITY_WOLF_GROWL, 0.8F, 0.9F, Sounds.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.7F);
    addStat(p, "taming.alphas-command.commands", 1);
    xp(p, getConfig().xpPerCommand);
    return true;
  }

  private LivingRef raycastTarget(PlayerRef p, double range) {
    Loc eye = p.eyeLocation();
    RayHit hit = p.world().rayTrace(
        eye.position(),
        eye.direction(),
        range,
        false,
        0.6D,
        entity -> entity instanceof LivingRef living
            && !living.uuid().equals(p.uuid())
            && !living.has(EntityTrait.ARMOR_STAND)
            && living.isValid()
            && !living.isDead()
            && !isOwnPet(p, living)
    );
    return hit != null && hit.entity() instanceof LivingRef target ? target : null;
  }

  private boolean isEligibleTarget(PlayerRef p, LivingRef target) {
    if (target.uuid().equals(p.uuid()) || target.has(EntityTrait.ARMOR_STAND) || !target.isValid() || target.isDead()
        || isOwnPet(p, target)) {
      return false;
    }
    if (target instanceof PlayerRef player && !AdaptCore.platform().viewers().canSee(p, player)) {
      return false;
    }
    return canDamageTarget(p, target);
  }

  private List<MobRef> collectPets(PlayerRef owner, double range, LivingRef target) {
    UUID ownerId = owner.uuid();
    int limit = getPetLimit();
    List<MobRef> pets = new ArrayList<>(limit);
    for (EntityRef entity : owner.nearbyEntities(range, range, range)) {
      if (entity.uuid().equals(target.uuid())) {
        continue;
      }
      if (entity instanceof MobRef mob && isCombatCapableTameable(mob)
          && preference(owner, TamingPreferences.PETS).accepts(mob.type()) && animals().isTamed(mob) && mob.isValid() && !mob.isDead() && isOwnedBy(mob, ownerId)) {
        pets.add(mob);
        if (pets.size() >= limit) {
          break;
        }
      }
    }
    return pets;
  }

  private void focusPet(MobRef pet, PlayerRef owner, UUID ownerId, LivingRef target, int focusTicks, int amplifier,
                        long generation) {
    if (!pet.isValid() || pet.isDead() || !pet.has(EntityTrait.TAMEABLE) || !animals().isTamed(pet)
        || !isOwnedBy(pet, ownerId)) {
      return;
    }

    UUID petId = pet.uuid();
    FocusState previous = activeFocuses.get(petId);
    boolean restoreSitting = previous != null
        ? previous.restoreSitting()
        : animals().isSitting(pet);
    FocusState focus = new FocusState(petId, pet, owner, ownerId, target, generation, restoreSitting);
    activeFocuses.put(petId, focus);
    if (animals().isSitting(pet)) {
      animals().setSitting(pet, false);
    }
    pet.setTarget(target);
    applyFocusBuffs(pet, focusTicks, amplifier);
    fx(pet, FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 4, 0.25D)
        .sound(Sounds.ENTITY_WOLF_GROWL, 0.6F, 1.2F);
    scheduleFocusRefresh(focus, focusTicks);
  }

  private void applyFocusBuffs(MobRef pet, int focusTicks, int amplifier) {
    if (focusTicks <= 0) {
      return;
    }
    AdaptAttributeService service = AdaptAttributeService.get();
    service.applyTimed(pet, getName(), "damage", Attributes.ATTACK_DAMAGE, focusDamageBonus(amplifier),
        AttributeOperation.ADD_VALUE, focusTicks);
    service.applyTimed(pet, getName(), "speed", Attributes.MOVEMENT_SPEED, focusSpeedScalar(amplifier),
        AttributeOperation.ADD_MULTIPLIED_TOTAL, focusTicks);
  }

  private void scheduleFocusRefresh(FocusState focus, int remainingTicks) {
    int delay = Math.min(FOCUS_REFRESH_TICKS, remainingTicks);
    if (delay <= 0 || !Tasks.runEntity(focus.pet(), () -> refreshFocusOwned(focus, remainingTicks - delay), delay)) {
      if (activeFocuses.remove(focus.petId(), focus)) {
        clearFocusOwned(focus);
      }
    }
  }

  private void refreshFocusOwned(FocusState focus, int remainingTicks) {
    if (!isCurrentFocus(focus)) {
      return;
    }
    if (closed.get() || remainingTicks <= 0 || !isPetFocusValid(focus)) {
      stopFocusOwned(focus);
      return;
    }

    if (!Tasks.runEntity(focus.target(), () -> validateFocusTargetOwned(focus, remainingTicks))) {
      stopFocusOwned(focus);
    }
  }

  private void validateFocusTargetOwned(FocusState focus, int remainingTicks) {
    LivingRef target = focus.target();
    if (!isCurrentFocus(focus)) {
      return;
    }
    if (!target.isValid() || target.isDead()) {
      stopFocusFromForeignThread(focus);
      return;
    }
    TargetPolicySnapshot snapshot = new TargetPolicySnapshot(
        target.location(),
        target instanceof PlayerRef,
        isProtectedFocusTarget(target, focus.ownerId())
    );
    if (!Tasks.runEntity(focus.owner(), () -> validateFocusPolicyOwned(focus, remainingTicks, snapshot))) {
      activeFocuses.remove(focus.petId(), focus);
    }
  }

  private void validateFocusPolicyOwned(FocusState focus, int remainingTicks, TargetPolicySnapshot snapshot) {
    PlayerRef owner = focus.owner();
    if (!isCurrentFocus(focus) || closed.get() || !owner.isOnline()
        || !owner.uuid().equals(focus.ownerId()) || getActiveLevel(owner) <= 0
        || snapshot.protectedFriendly() || !canAffectFocusTarget(owner, snapshot)) {
      stopFocusFromForeignThread(focus);
      return;
    }
    if (!Tasks.runEntity(focus.pet(), () -> applyFocusRefreshOwned(focus, remainingTicks))) {
      activeFocuses.remove(focus.petId(), focus);
    }
  }

  private boolean canAffectFocusTarget(PlayerRef owner, TargetPolicySnapshot snapshot) {
    if (snapshot.location().world() == null || !owner.world().equals(snapshot.location().world())) {
      return false;
    }
    return snapshot.player()
        ? canPVP(owner, snapshot.location())
        : canPVE(owner, snapshot.location());
  }

  private boolean isProtectedFocusTarget(LivingRef target, UUID ownerId) {
    if (target.isInvulnerable() || AdaptPorts.get(EntityMarkerPort.class, EntityMarkerPort.NONE).marked(target, "NPC")
        || MinionIdentity.isMinion(target)) {
      return true;
    }
    if (target.has(EntityTrait.TAMEABLE) && animals().isTamed(target)) {
      return isOwnedBy(target, ownerId);
    }
    return false;
  }

  private void applyFocusRefreshOwned(FocusState focus, int remainingTicks) {
    MobRef pet = focus.pet();
    if (!isCurrentFocus(focus) || closed.get() || !isPetFocusValid(focus)) {
      stopFocusOwned(focus);
      return;
    }
    if (animals().isSitting(pet)) {
      animals().setSitting(pet, false);
    }
    if (!isTargeting(pet, focus.target())) {
      pet.setTarget(focus.target());
    }
    scheduleFocusRefresh(focus, remainingTicks);
  }

  private boolean isPetFocusValid(FocusState focus) {
    MobRef pet = focus.pet();
    if (!pet.isValid() || pet.isDead() || !pet.has(EntityTrait.TAMEABLE) || !animals().isTamed(pet)) {
      return false;
    }
    return isOwnedBy(pet, focus.ownerId());
  }

  private static boolean isTargeting(MobRef pet, LivingRef target) {
    LivingRef current = pet.target();
    return current != null && current.uuid().equals(target.uuid());
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
    if (!Tasks.runEntity(focus.pet(), () -> stopFocusOwned(focus))) {
      activeFocuses.remove(focus.petId(), focus);
    }
  }

  private void clearFocusOwned(FocusState focus) {
    MobRef pet = focus.pet();
    AdaptAttributeService.get().removeAll(pet, getName());
    if (isTargeting(pet, focus.target())) {
      pet.setTarget(null);
    }
    if (focus.restoreSitting()) {
      animals().setSitting(pet, true);
    }
  }

  private void clearOwnerFocuses(UUID ownerId) {
    AbilityGlyphs.clear(ownerId, this, HudGlyph.DURATION);
    for (FocusState focus : new ArrayList<>(activeFocuses.values())) {
      if (focus.ownerId().equals(ownerId) && activeFocuses.remove(focus.petId(), focus)) {
        Tasks.runEntity(focus.pet(), () -> clearFocusOwned(focus));
      }
    }
  }

  private void consumeOneBone(PlayerRef p) {
    ItemRef bone = p.inventory().mainHand();
    if (!BONE.equals(bone.type()) || bone.amount() <= 0) {
      return;
    }

    int remaining = boneAmountAfterSuccessfulCommand(p.gameMode(), bone.amount());
    if (remaining == bone.amount()) {
      return;
    }
    if (remaining <= 0) {
      p.inventory().setMainHand(AdaptCore.platform().host().items().empty());
      return;
    }
    bone.setAmount(remaining);
    p.inventory().setMainHand(bone);
  }

  private void showTargetGlow(PlayerRef viewer, LivingRef target, int focusTicks, long generation) {
    ViewerGlowPort glowCoordinator = ViewerGlowPort.current();
    if (glowCoordinator == null || !glowCoordinator.isAvailable()) {
      return;
    }

    UUID viewerId = viewer.uuid();
    GlowState glow = new GlowState(viewer, target.uuid(), target.entityId(), generation);
    GlowState previous = targetGlows.put(viewerId, glow);
    if (previous != null
        && (!previous.targetId().equals(glow.targetId())
        || previous.runtimeEntityId() != glow.runtimeEntityId())) {
      unsetGlowOwned(viewer, previous);
    }

    if (!glowCoordinator.set(
        ViewerGlowPort.Layer.TAMING_ALPHAS_COMMAND,
        target,
        viewer,
        GlowColor.RED
    )) {
      targetGlows.remove(viewerId, glow);
      return;
    }
    if (!Tasks.runEntity(viewer, () -> expireTargetGlowOwned(viewer, glow), focusTicks)) {
      expireTargetGlowOwned(viewer, glow);
    }
  }

  private void expireTargetGlowOwned(PlayerRef viewer, GlowState glow) {
    UUID viewerId = viewer.uuid();
    GlowState current = targetGlows.get(viewerId);
    if (current != null && current.generation() == glow.generation() && targetGlows.remove(viewerId, current)) {
      unsetGlowOwned(viewer, current);
    }
  }

  private void clearTargetGlow(PlayerRef viewer) {
    GlowState glow = targetGlows.remove(viewer.uuid());
    if (glow != null) {
      unsetGlowOwned(viewer, glow);
    }
  }

  private void unsetGlowOwned(PlayerRef viewer, GlowState glow) {
    ViewerGlowPort glowCoordinator = ViewerGlowPort.current();
    if (glowCoordinator == null) {
      return;
    }
    glowCoordinator.unset(
        ViewerGlowPort.Layer.TAMING_ALPHAS_COMMAND,
        glow.targetId(),
        glow.runtimeEntityId(),
        viewer
    );
  }

  private boolean isOwnPet(PlayerRef p, LivingRef target) {
    return target.has(EntityTrait.TAMEABLE) && animals().isTamed(target) && isOwnedBy(target, p.uuid());
  }

  private boolean isOwnedBy(EntityRef tameable, UUID ownerId) {
    UUID owner = tameable.tameOwner();
    return owner != null && ownerId.equals(owner);
  }

  private static AnimalOps animals() {
    return AdaptCore.platform().host().animals();
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

  static boolean isCommandGesture(InteractAction action, Hand hand) {
    return hand == Hand.MAIN_HAND && (action == InteractAction.LEFT_CLICK_AIR || action == InteractAction.LEFT_CLICK_BLOCK);
  }

  static boolean isCombatCapableTameable(EntityRef entity) {
    return entity instanceof MobRef && entity.has(EntityTrait.TAMEABLE) && COMBAT_PETS.contains(entity.type());
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

  private record FocusState(UUID petId, MobRef pet, PlayerRef owner, UUID ownerId, LivingRef target, long generation,
                            boolean restoreSitting) {
  }

  private record TargetPolicySnapshot(Loc location, boolean player, boolean protectedFriendly) {
  }

  private record GlowState(PlayerRef viewer, UUID targetId, int runtimeEntityId, long generation) {
  }
}
