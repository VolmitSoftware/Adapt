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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public class UnarmedPower extends SimpleAdaptation<UnarmedPower.Config> {
  private static final String SLOT_POWER = "power";
  private static final long REFRESH_DURATION_TICKS = 180L;

  public UnarmedPower() {
    super("unarmed-power");
    registerConfiguration(Config.class);
    setIcon(Material.IRON_INGOT);
    setInterval(4444);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_power_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_power_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_power_500", "unarmed.power.unarmed-kills", 500, 400);
    registerMilestone("challenge_unarmed_power_5k", "unarmed.power.unarmed-kills", 5000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getUnarmedDamage(level), 0), 1);
  }

  @Override
  public void onTick() {
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      Player player = adaptPlayer.getPlayer();
      withPlayerThread(player, () -> reconcile(player));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerItemHeldEvent e) {
    Player p = e.getPlayer();
    reconcileWith(p, p.getInventory().getItem(e.getNewSlot()), p.getInventory().getItemInOffHand());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerSwapHandItemsEvent e) {
    reconcileWith(e.getPlayer(), e.getMainHandItem(), e.getOffHandItem());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(InventoryCloseEvent e) {
    if (e.getPlayer() instanceof Player p) {
      reconcile(p);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerDropItemEvent e) {
    reconcileNextTick(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityPickupItemEvent e) {
    if (e.getEntity() instanceof Player p) {
      reconcileNextTick(p);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerItemBreakEvent e) {
    reconcileNextTick(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerRespawnEvent e) {
    reconcileNextTick(e.getPlayer());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    reconcile(e.getPlayer());
  }

  @EventHandler
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null) {
      return;
    }

    Player p = attack.attacker();
    ItemStack mainHand = p.getInventory().getItemInMainHand();
    ItemStack offHand = p.getInventory().getItemInOffHand();
    if (isTool(mainHand) || isTool(offHand)) {
      return;
    }
    double factor = getLevelPercent(attack.level());

    if (factor <= 0) {
      return;
    }
    reconcileWith(p, mainHand, offHand);
    xp(p, 0.321 * factor * e.getDamage(), "unarmed-hit");
    if (factor >= 0.5) {
      fx(e.getEntity(), FxPriority.COMBAT)
          .particle(Particle.CRIT, 3, 0, 1.0D, 0, 0.1D, 0.0D)
          .sound(Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.35F, (float) (0.9D + (factor * 0.3D)));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    if (!(e.getEntity() instanceof LivingEntity victim)) {
      return;
    }
    if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent dmg
        && dmg.getDamager() instanceof Player p
        && hasActiveAdaptation(p)
        && !isTool(p.getInventory().getItemInMainHand())
        && !isTool(p.getInventory().getItemInOffHand())) {
      addStat(p, "unarmed.power.unarmed-kills", 1);
      fx(victim, FxPriority.COMBAT)
          .particle(Particle.SOUL, 5, 0, 0.4D, 0, 0.2D, 0.03D)
          .sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.4F, 1.3F);
    }
  }

  private void reconcile(Player p) {
    if (p == null || !p.isOnline()) {
      return;
    }
    reconcileWith(p, p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand());
  }

  private void reconcileWith(Player p, ItemStack mainHand, ItemStack offHand) {
    if (p == null || !p.isOnline() || getLevel(p) <= 0) {
      return;
    }

    int level = getActiveLevel(p);
    double scalar = powerScalar(getLevelPercent(level), getConfig().damageFactor);
    boolean bareHanded = !isTool(mainHand) && !isTool(offHand);
    if (shouldApplyPower(level, bareHanded, scalar)) {
      AdaptAttributeService.get().applyTimed(p, getName(), SLOT_POWER, Attributes.ATTACK_DAMAGE, scalar, AttributeModifier.Operation.MULTIPLY_SCALAR_1, REFRESH_DURATION_TICKS);
    } else {
      AdaptAttributeService.get().remove(p, getName(), SLOT_POWER, Attributes.ATTACK_DAMAGE);
    }
  }

  private void reconcileNextTick(Player p) {
    J.runEntity(p, () -> reconcile(p), 1);
  }

  private double getUnarmedDamage(int level) {
    return powerScalar(getLevelPercent(level), getConfig().damageFactor);
  }

  static double powerScalar(double levelPercent, double damageFactor) {
    return levelPercent * damageFactor;
  }

  static boolean shouldApplyPower(int activeLevel, boolean bareHanded, double scalar) {
    return activeLevel > 0 && bareHanded && scalar != 0D;
  }


  @ConfigDescription("Improved base unarmed damage.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Damage Factor for the Unarmed Power adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damageFactor = 2.57;

    public Config() {
      baseCost = 3;
      costFactor = 0.425;
      maxLevel = 7;
      initialCost = 6;
    }
  }
}
