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
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;

import java.util.Map;
import java.util.UUID;

public class UnarmedIronFists extends SimpleAdaptation<UnarmedIronFists.Config> {
  private final Map<UUID, Long> lastPunchedBlock = playerState();

  public UnarmedIronFists() {
    super("unarmed-iron-fists");
    registerConfiguration(Config.class);
    setIcon(Material.ANVIL);
    setInterval(4622);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.IRON_INGOT)
        .key("challenge_unarmed_iron_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.DIAMOND)
            .key("challenge_unarmed_iron_10k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_unarmed_iron_1k", "unarmed.iron-fists.iron-hits", 1000, 400);
    registerMilestone("challenge_unarmed_iron_10k", "unarmed.iron-fists.iron-hits", 10000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getDamageBonus(level)), 1);
    statLore(v, getHasteAmplifier(level) + 1, 2);
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
    addStat(p, "unarmed.iron-fists.iron-hits", 1);
    double levelPercent = getLevelPercent(attack.level());
    if (levelPercent >= 0.4) {
      fx(e.getEntity(), FxPriority.COMBAT)
          .particle(Particle.CRIT, 3, 0, 1.0D, 0, 0.15D, 0.0D)
          .particle(Particle.ELECTRIC_SPARK, 1, 0, 1.0D, 0, 0.1D, 0.0D)
          .sound(Sound.BLOCK_ANVIL_LAND, 0.3F, (float) (1.4D + (levelPercent * 0.3D)));
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    Player p = e.getPlayer();
    if (isItem(p.getInventory().getItemInMainHand())) {
      return;
    }

    Block block = e.getBlock();
    float hardness = block.getType().getHardness();
    if (hardness < 0 || hardness > getConfig().softBlockMaxHardness) {
      return;
    }

    art.arcane.adapt.api.adaptation.Adaptation.BlockActionContext context = resolveInteractContext(p, block.getLocation());
    if (context == null) {
      return;
    }

    p.addPotionEffect(new PotionEffect(PotionEffectTypes.FAST_DIGGING, getConfig().hasteDurationTicks, getHasteAmplifier(context.level()), false, false, true));

    long key = blockKey(block.getX(), block.getY(), block.getZ());
    Long last = lastPunchedBlock.get(p.getUniqueId());
    if (last == null || last != key) {
      lastPunchedBlock.put(p.getUniqueId(), key);
      fx(block.getLocation().add(0.5, 0.5, 0.5), FxPriority.GAMEPLAY)
          .particle(Particles.BLOCK_CRACK, 4, 0, 0, 0, 0.1D, 0.02D, block.getBlockData())
          .particle(Particle.WAX_ON, 2, 0, 0, 0, 0.1D, 0.0D)
          .sound(Sound.BLOCK_STONE_HIT, 0.35F, 0.8F);
    }
  }

  private static long blockKey(int x, int y, int z) {
    return ((long) x * 73856093L) ^ ((long) y * 19349663L) ^ ((long) z * 83492791L);
  }

  private double getDamageBonus(int level) {
    return getConfig().damageBase + (getLevelPercent(level) * getConfig().damageFactor);
  }

  private int getHasteAmplifier(int level) {
    return Math.max(0, (int) Math.round(getLevelPercent(level) * getConfig().hasteAmplifierFactor));
  }


  @ConfigDescription("Bare fists hit harder and punch through soft blocks faster.")
  protected static class Config extends AdaptationConfig {
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

    public Config() {
      baseCost = 3;
      initialCost = 4;
    }
  }
}
