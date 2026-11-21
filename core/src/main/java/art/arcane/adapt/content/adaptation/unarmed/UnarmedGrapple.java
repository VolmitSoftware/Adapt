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

package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.UnarmedMessages;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerToggleSneakEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UnarmedGrapple extends SimpleAdaptation<UnarmedGrapple.Config> {
  private final Map<UUID, GrabState> grabs = playerState();
  private final Map<UUID, Long> pendingHurls = playerState();
  private final Cooldowns grappleCooldown = cooldowns();

  public UnarmedGrapple() {
    super("unarmed-grapple");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("lead"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_grapple_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_unarmed_grapple_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_grapple_100", "unarmed.grapple.hurled-mobs", 100, 400);
    registerMilestone("challenge_unarmed_grapple_1k", "unarmed.grapple.hurled-mobs", 1000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (grabs.remove(player.getPlayer().uuid()) != null) {
      AbilityGlyphs.clear(player.getPlayer().uuid(), this, HudGlyph.DURATION);
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, UnarmedPreferences.TARGETS, UnarmedPreferences.RELEASE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getForce(level)), 1);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getCooldownMillis(level), 1), 2);
    v.addLore(C.GRAY + AdaptLanguage.text(UnarmedMessages.GRAPPLE_LORE3));
    statLore(v, C.RED, "- ", Form.f(getConfig().exhaustionPerThrow, 1), 4);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    PlayerRef p = attack.attacker();
    if (isTool(p.inventory().mainHand()) || isTool(p.inventory().offHand())) {
      return;
    }

    long now = System.currentTimeMillis();
    GrabState state = grabs.remove(p.uuid());
    if (state != null) {
      AbilityGlyphs.clear(p.uuid(), this, HudGlyph.DURATION);
    }
    if (state != null && now - state.grabbedAtMillis <= getConfig().grabTimeoutMillis) {
      hurl(p, state.target, attack.level());
      return;
    }

    if (!p.isSneaking()) {
      return;
    }

    if (!grappleCooldown.isReady(p.uuid(), getCooldownMillis(attack.level()))) {
      return;
    }

    if (!(attack.target() instanceof LivingRef victim)) {
      return;
    }

    if (victim.has(EntityTrait.BOSS) || !preference(p, UnarmedPreferences.TARGETS).accepts(victim)) {
      return;
    }

    grabs.put(p.uuid(), new GrabState(victim, now));
    AbilityGlyphs.duration(p.uuid(), this, now, getConfig().grabTimeoutMillis);
    playGrab(p, victim);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerToggleSneakEvent e) {
    if (e.isSneaking() || preference(e.player(), UnarmedPreferences.RELEASE) == UnarmedPreferences.Release.NEXT_HIT) {
      return;
    }

    PlayerRef p = e.player();
    GrabState state = grabs.remove(p.uuid());
    if (state == null) {
      return;
    }
    AbilityGlyphs.clear(p.uuid(), this, HudGlyph.DURATION);

    long now = System.currentTimeMillis();
    if (now - state.grabbedAtMillis > getConfig().grabTimeoutMillis) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    hurl(p, state.target, level);
  }

  private void hurl(PlayerRef p, LivingRef target, int level) {
    UUID playerId = p.uuid();
    long now = System.currentTimeMillis();
    if (pendingHurls.getOrDefault(playerId, 0L) > now) {
      return;
    }
    pendingHurls.put(playerId, now + 1000L);

    Loc playerLocation = p.location();
    double maxRange = getConfig().maxHurlRange;
    double maxRangeSquared = maxRange * maxRange;
    Vec3 velocity = playerLocation.direction().normalize().multiply(getForce(level))
        .withY(getConfig().upwardBoost + (getLevelPercent(level) * getConfig().upwardBoostFactor));
    HurlRequest request = new HurlRequest(p, target, playerLocation, velocity, maxRangeSquared, playerId);
    boolean scheduled = Tasks.runEntity(target, () -> prepareHurlTargetOwned(request));
    if (!scheduled) {
      pendingHurls.remove(playerId);
    }
  }

  private void prepareHurlTargetOwned(HurlRequest request) {
    if (!isHurlTargetValid(request)) {
      pendingHurls.remove(request.playerId());
      return;
    }

    if (!(request.target() instanceof PlayerRef)) {
      applyHurlTargetOwned(request);
      return;
    }

    Loc targetLocation = request.target().location();
    if (!Tasks.runEntity(request.player(), () -> authorizePlayerHurlOwnerOwned(request, targetLocation))) {
      pendingHurls.remove(request.playerId());
    }
  }

  private void authorizePlayerHurlOwnerOwned(HurlRequest request, Loc targetLocation) {
    if (!request.player().isOnline() || !canPVP(request.player(), targetLocation)) {
      pendingHurls.remove(request.playerId());
      return;
    }

    if (!Tasks.runEntity(request.target(), () -> applyHurlTargetOwned(request))) {
      pendingHurls.remove(request.playerId());
    }
  }

  private void applyHurlTargetOwned(HurlRequest request) {
    if (!isHurlTargetValid(request)) {
      pendingHurls.remove(request.playerId());
      return;
    }

    request.target().setVelocity(request.velocity());
    playTargetHurlFx(request.target(), request.velocity());
    if (!Tasks.runEntity(request.player(), () -> completeHurl(request.player()))) {
      pendingHurls.remove(request.playerId());
    }
  }

  private boolean isHurlTargetValid(HurlRequest request) {
    if (!request.target().isValid() || request.target().isDead()) {
      return false;
    }
    Loc targetLocation = request.target().location();
    return targetLocation.sameWorld(request.playerLocation())
        && targetLocation.distanceSquared(request.playerLocation()) <= request.maxRangeSquared();
  }

  private void playGrab(PlayerRef p, LivingRef victim) {
    Loc mob = victim.location().add(0, 1, 0);
    fx(mob, FxPriority.COMBAT)
        .particle(Particles.CRIT, 6, 0, 0, 0, 0.3D, 0.05D)
        .chord(Sounds.ITEM_ARMOR_EQUIP_LEATHER, 1.0F, 0.7F, Sounds.BLOCK_NOTE_BLOCK_BASEDRUM, 0.4F, 0.8F);
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .line(Particles.CRIT, mob.x(), mob.y(), mob.z(), 8);
  }

  private void playTargetHurlFx(LivingRef target, Vec3 velocity) {
    fx(target.location().add(0, 0.8D, 0), FxPriority.COMBAT)
        .particle(Particles.CLOUD, 8, 0, 0, 0, 0.25D, 0.05D)
        .trail(Particles.CRIT, velocity.x(), velocity.y(), velocity.z(), 1.6D, 6)
        .sound(Sounds.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9F, 1.2F);
    timeline(target)
        .duration(6)
        .priority(FxPriority.TRAIL)
        .frame((fx, tick, progress) -> fx.particle(Particles.CRIT, 1, 0, 0.2D, 0, 0.05D, 0.0D))
        .start();
  }

  private void completeHurl(PlayerRef player) {
    pendingHurls.remove(player.uuid());
    if (!player.isOnline()) {
      return;
    }

    grappleCooldown.mark(player.uuid(), getCooldownMillis(getActiveLevel(player)));
    player.setExhaustion(player.exhaustion() + (float) getConfig().exhaustionPerThrow);
    timeline(player)
        .duration(3)
        .priority(FxPriority.COMBAT)
        .frame((fx, tick, progress) -> fx.sound(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, (float) (0.9D + (progress * 0.4D))))
        .start();
    xp(player, getConfig().xpPerHurl, "grapple");
    addStat(player, "unarmed.grapple.hurled-mobs", 1);
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private long getCooldownMillis(int level) {
    return Math.max(1000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @ConfigDescription("Sneak-punch a target to grab it, then hurl it where you look.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base hurl force at level 1.", impact = "Higher values throw grabbed targets further.")
    double forceBase = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional hurl force granted at max level.", impact = "Higher values throw grabbed targets further as levels increase.")
    double forceFactor = 1.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base upward component added to the hurl velocity.", impact = "Higher values arc thrown mobs higher.")
    double upwardBoost = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional upward hurl component granted at max level.", impact = "Higher values arc thrown mobs higher as levels increase.")
    double upwardBoostFactor = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum distance in blocks a grabbed target can be hurled from.", impact = "Higher values let the grab persist over larger gaps.")
    double maxHurlRange = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds before an unused grab expires.", impact = "Higher values keep the grab primed for longer.")
    long grabTimeoutMillis = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base grapple cooldown in milliseconds at level 1.", impact = "Higher values make the ability usable less often.")
    double cooldownMillisBase = 9000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values make the ability recharge faster as levels increase.")
    double cooldownMillisFactor = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Exhaustion added to the player per hurled mob.", impact = "Higher values drain saturation and food faster with each throw. Set to 0 to disable the exhaustion cost.")
    double exhaustionPerThrow = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per hurled target.", impact = "Higher values speed up unarmed skill progression from grapples.")
    double xpPerHurl = 32;

    public Config() {
      baseCost = 5;
      costFactor = 0.65;
      initialCost = 6;
    }
  }

  private static class GrabState {
    private final LivingRef target;
    private final long grabbedAtMillis;

    private GrabState(LivingRef target, long grabbedAtMillis) {
      this.target = target;
      this.grabbedAtMillis = grabbedAtMillis;
    }
  }

  private record HurlRequest(PlayerRef player, LivingRef target, Loc playerLocation, Vec3 velocity,
                             double maxRangeSquared, UUID playerId) {
  }
}
