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
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class AgilityAirDash extends SimpleAdaptation<AgilityAirDash.Config> {
  private final Cooldowns debounce = cooldowns();
  private final Map<UUID, Boolean> sprintJumpActive = playerState();
  private final Map<UUID, Boolean> wasOnGround = playerState();
  private final Map<UUID, Integer> chargesUsed = playerState();

  public AgilityAirDash() {
    super("agility-air-dash");
    registerConfiguration(Config.class);
    setIcon(Material.PHANTOM_MEMBRANE);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.PHANTOM_MEMBRANE)
        .key("challenge_agility_air_dash_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.ELYTRA)
            .key("challenge_agility_air_dash_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_agility_air_dash_500", "agility.air-dash.dashes", 500, 400);
    registerMilestone("challenge_agility_air_dash_5k", "agility.air-dash.dashes", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDashForce(level) * 20D, 1), 1);
    statLore(v, getMaxCharges(level), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    boolean groundNow = p.isOnGround();
    Boolean previousGround = wasOnGround.put(id, groundNow);
    boolean groundBefore = previousGround == null ? groundNow : previousGround;

    if (groundNow) {
      if (!sprintJumpActive.isEmpty()) {
        sprintJumpActive.remove(id);
      }
      if (!chargesUsed.isEmpty()) {
        chargesUsed.remove(id);
      }
      return;
    }

    if (groundBefore && p.isSprinting()) {
      sprintJumpActive.put(id, true);
      chargesUsed.put(id, 0);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    if (e.getAction() != Action.LEFT_CLICK_AIR) {
      return;
    }

    Player p = e.getPlayer();
    withAdaptedPlayer(p, () -> attemptDash(p));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    sprintJumpActive.remove(id);
    wasOnGround.remove(id);
    chargesUsed.remove(id);
  }

  private void attemptDash(Player p) {
    if (!isDashEligible(p)) {
      return;
    }

    UUID id = p.getUniqueId();
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

    if (p.getFoodLevel() <= 0) {
      return;
    }

    Vector direction = p.getLocation().getDirection().clone().setY(0);
    if (direction.lengthSquared() <= 1.0E-4D) {
      return;
    }

    direction.normalize();
    debounce.mark(id);
    chargesUsed.merge(id, 1, Integer::sum);
    applyHungerCost(p, getConfig().hungerCost);

    double force = getDashForce(level);
    Vector current = p.getVelocity();
    double liftedY = Math.max(current.getY(), -0.15D) + getConfig().upwardLift;
    p.setVelocity(new Vector(direction.getX() * force, liftedY, direction.getZ() * force));
    p.setFallDistance(0F);

    fx(p.getLocation(), FxPriority.GAMEPLAY)
        .trail(Particle.CLOUD, -direction.getX(), 0.05D, -direction.getZ(), 1.2D, 8)
        .ring(Particle.CRIT, 0.6D, 8, 0.1D)
        .chord(Sound.ENTITY_PHANTOM_FLAP, 0.6F, 1.4F, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5F, 1.6F);

    addStat(p, "agility.air-dash.dashes", 1);
    xp(p, getConfig().xpPerDash);
  }

  private void applyHungerCost(Player p, double cost) {
    double saturation = p.getSaturation();
    if (saturation >= cost) {
      p.setSaturation((float) (saturation - cost));
      return;
    }

    p.setSaturation(0F);
    int foodCost = (int) Math.ceil(cost - saturation);
    p.setFoodLevel(Math.max(0, p.getFoodLevel() - foodCost));
  }

  private boolean isDashEligible(Player p) {
    GameMode mode = p.getGameMode();
    if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
      return false;
    }

    return !p.isOnGround()
        && !p.isFlying()
        && !p.isGliding()
        && !p.isSwimming()
        && !p.isClimbing()
        && p.getVehicle() == null;
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
}
