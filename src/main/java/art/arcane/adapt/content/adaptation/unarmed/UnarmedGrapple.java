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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Boss;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;

public class UnarmedGrapple extends SimpleAdaptation<UnarmedGrapple.Config> {
  private final Map<UUID, GrabState> grabs = playerState();
  private final Cooldowns grappleCooldown = cooldowns();

  public UnarmedGrapple() {
    super("unarmed-grapple");
    registerConfiguration(Config.class);
    setIcon(Material.LEAD);
    setInterval(2750);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_grapple_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_grapple_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_grapple_100", "unarmed.grapple.hurled-mobs", 100, 400);
    registerMilestone("challenge_unarmed_grapple_1k", "unarmed.grapple.hurled-mobs", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getForce(level)), 1);
    statLore(v, C.YELLOW, "* ", Form.duration((double) getCooldownMillis(level), 1), 2);
    v.addLore(C.GRAY + Localizer.dLocalize("unarmed.grapple.lore3"));
    statLore(v, C.RED, "- ", Form.f(getConfig().exhaustionPerThrow, 1), 4);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    Player p = attack.attacker();
    if (isTool(p.getInventory().getItemInMainHand()) || isTool(p.getInventory().getItemInOffHand())) {
      return;
    }

    long now = System.currentTimeMillis();
    GrabState state = grabs.remove(p.getUniqueId());
    if (state != null && now - state.grabbedAtMillis <= getConfig().grabTimeoutMillis) {
      hurl(p, state.target, attack.level());
      return;
    }

    if (!p.isSneaking()) {
      return;
    }

    if (!grappleCooldown.isReady(p.getUniqueId(), getCooldownMillis(attack.level()))) {
      return;
    }

    if (!(attack.target() instanceof LivingEntity victim)) {
      return;
    }

    if (victim instanceof Player && !getConfig().allowGrapplePlayers) {
      return;
    }

    if (victim instanceof Boss) {
      return;
    }

    grabs.put(p.getUniqueId(), new GrabState(victim, now));
    playGrab(p, victim);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    if (e.isSneaking()) {
      return;
    }

    Player p = e.getPlayer();
    GrabState state = grabs.remove(p.getUniqueId());
    if (state == null) {
      return;
    }

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

  private void hurl(Player p, LivingEntity target, int level) {
    if (!target.isValid() || target.isDead() || target.getWorld() != p.getWorld()) {
      return;
    }

    double maxRange = getConfig().maxHurlRange;
    if (target.getLocation().distanceSquared(p.getLocation()) > maxRange * maxRange) {
      return;
    }

    grappleCooldown.mark(p.getUniqueId());
    p.setExhaustion(p.getExhaustion() + (float) getConfig().exhaustionPerThrow);
    Vector velocity = p.getLocation().getDirection().normalize().multiply(getForce(level)).setY(getConfig().upwardBoost + (getLevelPercent(level) * getConfig().upwardBoostFactor));
    J.runEntity(target, () -> {
      if (target.isValid() && !target.isDead()) {
        target.setVelocity(velocity);
      }
    });

    playHurl(p, target, velocity);
    xp(p, getConfig().xpPerHurl, "grapple");
    addStat(p, "unarmed.grapple.hurled-mobs", 1);
  }

  private void playGrab(Player p, LivingEntity victim) {
    Location mob = victim.getLocation().add(0, 1, 0);
    fx(mob, FxPriority.COMBAT)
        .particle(Particle.CRIT, 6, 0, 0, 0, 0.3D, 0.05D)
        .chord(Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0F, 0.7F, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.4F, 0.8F);
    fx(p.getEyeLocation(), FxPriority.TRANSITION)
        .line(Particle.CRIT, mob.getX(), mob.getY(), mob.getZ(), 8);
  }

  private void playHurl(Player p, LivingEntity target, Vector velocity) {
    fx(target.getLocation().add(0, 0.8D, 0), FxPriority.COMBAT)
        .particle(Particle.CLOUD, 8, 0, 0, 0, 0.25D, 0.05D)
        .trail(Particle.CRIT, velocity.getX(), velocity.getY(), velocity.getZ(), 1.6D, 6)
        .sound(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9F, 1.2F);
    timeline(p)
        .duration(3)
        .priority(FxPriority.COMBAT)
        .frame((fx, tick, progress) -> fx.sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9F, (float) (0.9D + (progress * 0.4D))))
        .start();
    timeline(target)
        .duration(6)
        .priority(FxPriority.TRAIL)
        .frame((fx, tick, progress) -> fx.particle(Particle.CRIT, 1, 0, 0.2D, 0, 0.05D, 0.0D))
        .start();
  }

  private double getForce(int level) {
    return getConfig().forceBase + (getLevelPercent(level) * getConfig().forceFactor);
  }

  private long getCooldownMillis(int level) {
    return Math.max(1000L, (long) Math.round(getConfig().cooldownMillisBase - (getLevelPercent(level) * getConfig().cooldownMillisFactor)));
  }

  @Override
  public void onTick() {
    long now = System.currentTimeMillis();
    grabs.values().removeIf(state -> now - state.grabbedAtMillis > getConfig().grabTimeoutMillis || !state.target.isValid() || state.target.isDead());
  }

  @ConfigDescription("Sneak-punch a mob to grab it, then hurl it where you look.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Allows grappling other players, not just mobs.", impact = "True lets sneak-punches grab players in PVP.")
    boolean allowGrapplePlayers = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base hurl force at level 1.", impact = "Higher values throw grabbed mobs further.")
    double forceBase = 0.9;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional hurl force granted at max level.", impact = "Higher values throw grabbed mobs further as levels increase.")
    double forceFactor = 1.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base upward component added to the hurl velocity.", impact = "Higher values arc thrown mobs higher.")
    double upwardBoost = 0.2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional upward hurl component granted at max level.", impact = "Higher values arc thrown mobs higher as levels increase.")
    double upwardBoostFactor = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum distance in blocks a grabbed mob can be hurled from.", impact = "Higher values let the grab persist over larger gaps.")
    double maxHurlRange = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds before an unused grab expires.", impact = "Higher values keep the grab primed for longer.")
    long grabTimeoutMillis = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base grapple cooldown in milliseconds at level 1.", impact = "Higher values make the ability usable less often.")
    double cooldownMillisBase = 9000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown reduction in milliseconds granted at max level.", impact = "Higher values make the ability recharge faster as levels increase.")
    double cooldownMillisFactor = 5000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Exhaustion added to the player per hurled mob.", impact = "Higher values drain saturation and food faster with each throw. Set to 0 to disable the exhaustion cost.")
    double exhaustionPerThrow = 2.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per hurled mob.", impact = "Higher values speed up unarmed skill progression from grapples.")
    double xpPerHurl = 32;

    public Config() {
      baseCost = 5;
      costFactor = 0.65;
      initialCost = 6;
    }
  }

  private static class GrabState {
    private final LivingEntity target;
    private final long grabbedAtMillis;

    private GrabState(LivingEntity target, long grabbedAtMillis) {
      this.target = target;
      this.grabbedAtMillis = grabbedAtMillis;
    }
  }
}
