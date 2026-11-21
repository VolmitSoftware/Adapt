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

package art.arcane.adapt.content.adaptation.agility;

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
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.AgilityMessages;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgilityAirDash extends SimpleAdaptation<AgilityAirDash.Config> {
  public static final PlayerPreference<Trigger> TRIGGER = new PlayerPreference<>(Trigger.class,
      new PlayerPreference.Definition<>("trigger", AgilityMessages.AGILITYAIRDASH_PREFERENCE_TRIGGER, Trigger.ANY, List.of(
          new PlayerPreference.Choice<>(Trigger.ANY, AgilityMessages.AGILITYAIRDASH_PREFERENCE_TRIGGER_ANY, Id.minecraft("feather"), 1),
          new PlayerPreference.Choice<>(Trigger.SNEAK, AgilityMessages.AGILITYAIRDASH_PREFERENCE_TRIGGER_SNEAK, Id.minecraft("leather_boots"), 1),
          new PlayerPreference.Choice<>(Trigger.EMPTY_HAND, AgilityMessages.AGILITYAIRDASH_PREFERENCE_TRIGGER_EMPTY_HAND, Id.minecraft("white_stained_glass_pane"), 1))));
  public static final PlayerPreference<CommonPreferences.Scale> SPEED = CommonPreferences.scale("speed", AgilityMessages.AGILITYAIRDASH_PREFERENCE_SPEED);

  private final Cooldowns debounce = cooldowns();
  private final Map<UUID, Boolean> sprintJumpActive = playerState();
  private final Map<UUID, Boolean> wasOnGround = playerState();
  private final Map<UUID, Integer> chargesUsed = playerState();

  public AgilityAirDash() {
    super("agility-air-dash");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("phantom_membrane"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("phantom_membrane"))
        .key("challenge_agility_air_dash_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("elytra"))
            .key("challenge_agility_air_dash_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_agility_air_dash_500", "agility.air-dash.dashes", 500, 400);
    registerMilestone("challenge_agility_air_dash_5k", "agility.air-dash.dashes", 5000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TRIGGER, SPEED);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getDashForce(level) * 20D, 1), 1);
    statLore(v, getMaxCharges(level), 2);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerMoveEvent e) {
    PlayerRef p = e.player();
    UUID id = p.uuid();
    boolean groundNow = p.isOnGround();
    Boolean previousGround = wasOnGround.put(id, groundNow);
    boolean groundBefore = previousGround == null ? groundNow : previousGround;

    if (groundNow) {
      if (!sprintJumpActive.isEmpty()) {
        sprintJumpActive.remove(id);
      }
      if (!chargesUsed.isEmpty() && chargesUsed.remove(id) != null) {
        AbilityGlyphs.clear(id, this, HudGlyph.CHARGES);
      }
      return;
    }

    if (groundBefore && p.isSprinting()) {
      sprintJumpActive.put(id, true);
      chargesUsed.put(id, 0);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (e.action() != InteractAction.LEFT_CLICK_AIR) {
      return;
    }

    PlayerRef p = e.player();
    withAdaptedPlayer(p, () -> attemptDash(p));
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    UUID id = e.player().uuid();
    sprintJumpActive.remove(id);
    wasOnGround.remove(id);
    chargesUsed.remove(id);
  }

  private void attemptDash(PlayerRef p) {
    if (!isDashEligible(p) || (preference(p, TRIGGER) == Trigger.SNEAK && !p.isSneaking())
        || (preference(p, TRIGGER) == Trigger.EMPTY_HAND && !p.inventory().mainHand().isEmpty())) {
      return;
    }

    UUID id = p.uuid();
    if (!sprintJumpActive.getOrDefault(id, false)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    int maxCharges = getMaxCharges(level);
    if (chargesUsed.getOrDefault(id, 0) >= maxCharges) {
      return;
    }

    if (!debounce.isReady(id, Math.max(0L, getConfig().debounceMillis))) {
      return;
    }

    if (p.foodLevel() <= 0) {
      return;
    }

    Vec3 direction = p.location().direction().withY(0);
    if (direction.lengthSquared() <= 1.0E-4D) {
      return;
    }

    direction = direction.normalize();
    debounce.mark(id);
    int used = chargesUsed.merge(id, 1, Integer::sum);
    AbilityGlyphs.charges(id, this, Math.max(0, maxCharges - used), maxCharges);
    applyHungerCost(p, getConfig().hungerCost);

    double force = getDashForce(level) * preference(p, SPEED).multiplier();
    Vec3 current = p.velocity();
    double liftedY = Math.max(current.y(), -0.15D) + getConfig().upwardLift;
    p.setVelocity(new Vec3(direction.x() * force, liftedY, direction.z() * force));
    p.setFallDistance(0F);

    fx(p.location(), FxPriority.GAMEPLAY)
        .trail(Particles.CLOUD, -direction.x(), 0.05D, -direction.z(), 1.2D, 8)
        .ring(Particles.CRIT, 0.6D, 8, 0.1D)
        .chord(Sounds.ENTITY_PHANTOM_FLAP, 0.6F, 1.4F, Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 1.6F);

    addStat(p, "agility.air-dash.dashes", 1);
    xp(p, getConfig().xpPerDash);
  }

  private void applyHungerCost(PlayerRef p, double cost) {
    double saturation = p.saturation();
    if (saturation >= cost) {
      p.setSaturation((float) (saturation - cost));
      return;
    }

    p.setSaturation(0F);
    int foodCost = (int) Math.ceil(cost - saturation);
    p.setFoodLevel(Math.max(0, p.foodLevel() - foodCost));
  }

  private boolean isDashEligible(PlayerRef p) {
    GameMode mode = p.gameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isOnGround()
        && !p.isFlying()
        && !p.isGliding()
        && !p.isSwimming()
        && !p.isClimbing()
        && p.vehicle() == null;
  }

  private double getDashForce(int level) {
    return getConfig().dashForceBase + (getLevelPercent(level) * getConfig().dashForceFactor);
  }

  private int getMaxCharges(int level) {
    return level >= getMaxLevel() ? Math.max(2, getConfig().maxLevelCharges) : 1;
  }

  @ConfigDescription("Left-click air after a sprint-jump to dash forward with pure horizontal velocity.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base dash velocity in blocks per tick before level scaling.", impact = "Higher values throw the dash further; lower values keep it tame.")
    double dashForceBase = 0.85;
    @ConfigDoc(value = "Additional dash velocity in blocks per tick granted at max level.", impact = "Higher values widen the dash-force gain from leveling.")
    double dashForceFactor = 0.6;
    @ConfigDoc(value = "Small upward velocity added on dash to preserve airtime.", impact = "Higher values keep the dash airborne longer; lower values stay flatter.")
    double upwardLift = 0.12;
    @ConfigDoc(value = "Number of dash charges available at max level per sprint-jump.", impact = "Higher values allow more mid-air dashes before landing at max level.")
    int maxLevelCharges = 2;
    @ConfigDoc(value = "Minimum milliseconds between dash inputs to debounce double clicks.", impact = "Higher values ignore rapid repeat clicks; lower values react faster.")
    long debounceMillis = 250;
    @ConfigDoc(value = "Saturation/hunger cost paid per dash.", impact = "Higher values make dashing drain stamina faster.")
    double hungerCost = 2;
    @ConfigDoc(value = "Experience granted per successful dash.", impact = "Higher values reward dashing with more skill xp.")
    double xpPerDash = 3;

    public Config() {
      baseCost = 3;
      costFactor = 0.55;
      maxLevel = 4;
      initialCost = 5;
    }
  }

  public enum Trigger { ANY, SNEAK, EMPTY_HAND }
}
