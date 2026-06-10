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

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;

public class UnarmedIronFists extends SimpleAdaptation<UnarmedIronFists.Config> {
  public UnarmedIronFists() {
    super("unarmed-iron-fists");
    registerConfiguration(Config.class);
    setDescription(Localizer.dLocalize("unarmed.iron_fists.description"));
    setDisplayName(Localizer.dLocalize("unarmed.iron_fists.name"));
    setIcon(Material.ANVIL);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(4622);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_iron_1k")
        .title(Localizer.dLocalize("advancement.challenge_unarmed_iron_1k.title"))
        .description(Localizer.dLocalize("advancement.challenge_unarmed_iron_1k.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_iron_10k")
            .title(Localizer.dLocalize("advancement.challenge_unarmed_iron_10k.title"))
            .description(Localizer.dLocalize("advancement.challenge_unarmed_iron_10k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_iron_1k", "unarmed.iron-fists.iron-hits", 1000, 400);
    registerMilestone("challenge_unarmed_iron_10k", "unarmed.iron-fists.iron-hits", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.f(getDamageBonus(level)) + C.GRAY + " " + Localizer.dLocalize("unarmed.iron_fists.lore1"));
    v.addLore(C.GREEN + "+ " + (getHasteAmplifier(level) + 1) + C.GRAY + " " + Localizer.dLocalize("unarmed.iron_fists.lore2"));
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

    e.setDamage(e.getDamage() + getDamageBonus(attack.level()));
    xp(p, getConfig().xpPerHit);
    getPlayer(p).getData().addStat("unarmed.iron-fists.iron-hits", 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    Player p = e.getPlayer();
    if (isItem(p.getInventory().getItemInMainHand())) {
      return;
    }

    float hardness = e.getBlock().getType().getHardness();
    if (hardness < 0 || hardness > getConfig().softBlockMaxHardness) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    p.addPotionEffect(new PotionEffect(PotionEffectTypes.FAST_DIGGING, getConfig().hasteDurationTicks, getHasteAmplifier(context.level()), false, false, true));
  }

  private double getDamageBonus(int level) {
    return getConfig().damageBase + (getLevelPercent(level) * getConfig().damageFactor);
  }

  private int getHasteAmplifier(int level) {
    return Math.max(0, (int) Math.round(getLevelPercent(level) * getConfig().hasteAmplifierFactor));
  }

  @Override
  public void onTick() {

  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Bare fists hit harder and punch through soft blocks faster.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base flat bare-hand damage bonus at level 1.", impact = "Higher values make every bare-hand hit stronger.")
    double damageBase = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional flat damage bonus granted at max level.", impact = "Higher values make bare-hand hits stronger as levels increase.")
    double damageFactor = 2.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum block hardness still considered a soft block.", impact = "Higher values let the punch-haste apply to tougher blocks.")
    double softBlockMaxHardness = 0.8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Haste duration in ticks while punching soft blocks.", impact = "Higher values keep the dig-speed buff active longer.")
    int hasteDurationTicks = 25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Haste amplifier granted at max level while punching soft blocks.", impact = "Higher values speed up bare-hand block breaking.")
    double hasteAmplifierFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per bare-hand hit.", impact = "Higher values speed up unarmed skill progression from hits.")
    double xpPerHit = 2.4;
  }
}
