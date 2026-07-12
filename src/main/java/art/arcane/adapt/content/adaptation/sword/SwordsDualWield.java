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

package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class SwordsDualWield extends SimpleAdaptation<SwordsDualWield.Config> {
  private static final Color GOLD = Color.fromRGB(0xFFD54A);
  private final Cooldowns fxThrottle = cooldowns();

  public SwordsDualWield() {
    super("sword-dual-wield");
    registerConfiguration(Config.class);
    setIcon(Material.GOLDEN_SWORD);
    setInterval(1800);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_SWORD)
        .key("challenge_swords_dual_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND_SWORD)
            .key("challenge_swords_dual_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_swords_dual_1k", "swords.dual-wield.bonus-damage", 1000, 400);
    registerMilestone("challenge_swords_dual_25k", "swords.dual-wield.bonus-damage", 25000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getSameMultiplier(level), 0), 1);
    statLore(v, Form.pc(getMixedMultiplier(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    art.arcane.adapt.api.adaptation.Adaptation.MeleeContext combat = resolveMeleeContext(e);
    if (combat == null) {
      return;
    }

    Player p = combat.attacker();
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();
    if (!isSword(main) || !isSword(off)) {
      return;
    }

    boolean sameWeapon = main.getType() == off.getType();
    double multiplier = sameWeapon ? getSameMultiplier(combat.level()) : getMixedMultiplier(combat.level());
    double originalDamage = e.getDamage();
    e.setDamage(originalDamage * multiplier);
    double bonusDamage = e.getDamage() - originalDamage;
    xp(p, e.getDamage() * getConfig().xpPerDamage);
    addStat(p, "swords.dual-wield.bonus-damage", bonusDamage);

    if (fxThrottle.isReady(p.getUniqueId(), 250L)) {
      fxThrottle.mark(p.getUniqueId());
      Location center = p.getLocation().add(0, 1, 0);
      FxEmitter twin = fx(center, FxPriority.AMBIENT).particle(Particle.CRIT, 6, 0, 0, 0, 0.25D, 0.05D);
      if (sameWeapon) {
        twin.dustBurst(GOLD, 2, 0.25D, 0.9F)
            .particle(Particle.WAX_ON, 1, 0, 0, 0, 0.05D, 0)
            .chord(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35F, 1.5F, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25F, 1.6F);
      } else {
        twin.sound(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35F, 1.2F);
      }
    }
  }

  private double getSameMultiplier(int level) {
    return getConfig().sameWeaponBase + (getLevelPercent(level) * getConfig().sameWeaponFactor);
  }

  private double getMixedMultiplier(int level) {
    return getConfig().mixedWeaponBase + (getLevelPercent(level) * getConfig().mixedWeaponFactor);
  }


  @ConfigDescription("Wield a sword in both hands for increased damage output.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Same Weapon Base for the Swords Dual Wield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sameWeaponBase = 1.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Same Weapon Factor for the Swords Dual Wield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double sameWeaponFactor = 0.43;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mixed Weapon Base for the Swords Dual Wield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mixedWeaponBase = 1.06;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Mixed Weapon Factor for the Swords Dual Wield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double mixedWeaponFactor = 0.28;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Damage for the Swords Dual Wield adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerDamage = 2.0;

    public Config() {
      baseCost = 5;
      costFactor = 0.7;
      initialCost = 5;
    }
  }
}
