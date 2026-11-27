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

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UnarmedShockwaveClap extends SimpleAdaptation<UnarmedShockwaveClap.Config> {
  public static final Id ID = Id.adapt("unarmed/shockwave_clap");
  private static final Rgb WAVE_COLOR = new Rgb(255, 255, 255);
  private final Map<UUID, Long> nextClapAt = playerState();
  private final Cooldowns failCue = cooldowns();
  private final UnarmedShockwave shockwave = new UnarmedShockwave(this, (PlayerRef player, List<LivingRef> hits) -> {
    xp(player, getConfig().xpPerTargetHit * hits.size());
    addStat(player, "unarmed.shockwave-clap.mobs-clapped", hits.size());
  });

  public UnarmedShockwaveClap() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("note_block"));
    setInterval(5230);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_ingot"))
        .key("challenge_unarmed_clap_250")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond"))
            .key("challenge_unarmed_clap_2500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_unarmed_clap_250", "unarmed.shockwave-clap.mobs-clapped", 250, 400);
    registerMilestone("challenge_unarmed_clap_2500", "unarmed.shockwave-clap.mobs-clapped", 2500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, UnarmedPreferences.AIRBORNE, UnarmedPreferences.HUNGER, UnarmedPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getRange(level), 1), 1);
    statLore(v, Format.f(getForce(level), 1), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration((double) getCooldownMillis(level), 1), 3);
    statLore(v, TextFormat.RED, "- ", getConfig().hungerCost, 4);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.LEFT_CLICK_AIR && action != InteractAction.LEFT_CLICK_BLOCK) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking() || (preferenceEnabled(p, UnarmedPreferences.AIRBORNE) && p.isOnGround())) {
      return;
    }

    if (ItemKinds.isTool(p.inventory().mainHand()) || ItemKinds.isTool(p.inventory().offHand())) {
      return;
    }

    UUID id = p.uuid();
    long now = System.currentTimeMillis();
    Long next = nextClapAt.get(id);
    if (next != null && now < next) {
      playBlocked(p);
      return;
    }

    int hungerCost = getConfig().hungerCost;
    if (!preference(p, UnarmedPreferences.HUNGER).permits(p.foodLevel(), hungerCost)) {
      playBlocked(p);
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, p.location());
    if (context == null) {
      return;
    }

    int level = context.level();
    long cooldownMillis = getCooldownMillis(level);
    nextClapAt.put(id, now + cooldownMillis);
    HudStatus.cooldown(id, this, now, cooldownMillis);
    p.setFoodLevel(Math.max(0, p.foodLevel() - hungerCost));

    Loc origin = p.location();
    int candidateLimit = UnarmedShockwave.candidateLimit(getConfig().maxCandidatesPerActivation);
    shockwave.release(new UnarmedShockwave.Wave(p, origin, origin.direction().normalize(), getRange(level),
        getForce(level), getUpwardForce(level), 0D, getConfig().coneDotThreshold, candidateLimit,
        UnarmedShockwave.affectedLimit(getConfig().maxAffectedPerActivation, candidateLimit),
        UnarmedShockwave.targetFxLimit(getConfig().maxTargetFxPerActivation), Set.of(id)));
    addStat(p, "unarmed.shockwave-clap.claps", 1);
  }

  static void waveFx(Adaptation<?> owner, Loc origin, Vec3 look, double range) {
    Fx.now(owner, origin, FxPriority.COMBAT)
        .chord(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.6F, Sounds.BLOCK_BELL_RESONATE, 0.7F, 1.4F, Sounds.ENTITY_GENERIC_EXPLODE, 0.4F, 0.7F);
    Loc center = origin.add(look.multiply(0.6)).add(0, 1.0D, 0);
    Fx.now(owner, center, FxPriority.COMBAT)
        .particle(Particles.EXPLOSION, 1, 0, 0, 0, 0.1D, 0.02D)
        .particle(Particles.SWEEP_ATTACK, 1, 0, 0, 0, 0, 0);
    int ringPoints = Math.min(28, 12 + (int) (range * 2));
    FxTimeline.at(owner, center)
        .duration(5)
        .priority(FxPriority.TRANSITION)
        .cullRadius(Math.min(48.0D, (range * 2.0D) + 8.0D))
        .frame((fx, tick, progress) -> fx.dustRing(WAVE_COLOR, 0.4D + (range * progress), ringPoints, 1.0F))
        .start();
  }

  static void impactFx(Adaptation<?> owner, Loc targetLocation) {
    Fx.now(owner, targetLocation.add(0, 1, 0), FxPriority.COMBAT)
        .particle(Particles.CLOUD, 3, 0, 0, 0, 0.15D, 0.03D);
  }

  private void playBlocked(PlayerRef p) {
    if (!failCue.isReady(p.uuid(), 700L)) {
      return;
    }

    failCue.mark(p.uuid());
    fx(p.eyeLocation(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 2, 0, 0, 0, 0.05D, 0.01D)
        .sound(Sounds.BLOCK_NOTE_BLOCK_HAT, 0.25F, 0.5F);
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private double getUpwardForce(int level) {
    return getConfig().upwardForceBase + (getLevelPercent(level) * getConfig().upwardForceFactor);
  }

  private long getCooldownMillis(int level) {
    return Math.max(1000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @ConfigDescription("Sneak and punch the air to clap a shockwave that knocks back enemies in a cone.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base shockwave range in blocks at level 1.", impact = "Higher values let the clap reach more distant enemies.")
    double rangeBase = 3.5;
    @ConfigDoc(value = "Additional shockwave range granted at max level.", impact = "Higher values extend the clap reach as levels increase.")
    double rangeFactor = 3;
    @ConfigDoc(value = "Base knockback force at level 1.", impact = "Higher values launch enemies further.")
    double forceBase = 0.8;
    @ConfigDoc(value = "Additional knockback force granted at max level.", impact = "Higher values launch enemies further as levels increase.")
    double forceFactor = 1.2;
    @ConfigDoc(value = "Base upward knockback component at level 1.", impact = "Higher values pop enemies into the air more.")
    double upwardForceBase = 0.25;
    @ConfigDoc(value = "Additional upward knockback granted at max level.", impact = "Higher values pop enemies higher as levels increase.")
    double upwardForceFactor = 0.2;
    @ConfigDoc(value = "Look-direction dot threshold that defines the cone width.", impact = "Lower values widen the cone; higher values narrow it.")
    double coneDotThreshold = 0.45;
    @ConfigDoc(value = "Base clap cooldown in milliseconds at level 1.", impact = "Higher values make the ability usable less often.")
    double cooldownMillisBase = 10000;
    @ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values make the ability recharge faster as levels increase.")
    double cooldownMillisFactor = 6000;
    @ConfigDoc(value = "Hunger points consumed per shockwave clap.", impact = "Higher values drain more food per clap; activation fails when food is below the cost. Set to 0 to disable the hunger cost.")
    int hungerCost = 2;
    @ConfigDoc(value = "XP granted per enemy knocked back by a clap.", impact = "Higher values speed up unarmed skill progression from claps.")
    double xpPerTargetHit = 14;
    @ConfigDoc(value = "Maximum living targets inspected by one shockwave clap.", impact = "Lower values reduce dense-entity scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @ConfigDoc(value = "Maximum living targets knocked back by one shockwave clap.", impact = "Lower values cap combat work and rewards in dense groups; values are clamped to an absolute maximum of 16.")
    int maxAffectedPerActivation = 12;
    @ConfigDoc(value = "Maximum knocked-back targets that receive individual cloud particles.", impact = "Lower values reduce particle dispatch while preserving the main shockwave; values are clamped to an absolute maximum of 12.")
    int maxTargetFxPerActivation = 8;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
