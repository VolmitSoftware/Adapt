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

package art.arcane.adapt.content.adaptation.hunter;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class HunterBigGameHunter extends SimpleAdaptation<HunterBigGameHunter.Config> {
  private static final Set<EntityType> BIG_GAME = EnumSet.of(
      EntityType.RAVAGER,
      EntityType.IRON_GOLEM,
      EntityType.WARDEN,
      EntityType.WITHER,
      EntityType.ENDER_DRAGON,
      EntityType.ELDER_GUARDIAN);

  public HunterBigGameHunter() {
    super("hunter-big-game");
    registerConfiguration(Config.class);
    setIcon(Material.NETHERITE_SWORD);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RAVAGER_SPAWN_EGG)
        .key("challenge_hunter_big_game_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.NETHER_STAR)
            .key("challenge_hunter_big_game_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_hunter_big_game_100", "hunter.big-game.big-game-slain", 100, 600);
    registerMilestone("challenge_hunter_big_game_1k", "hunter.big-game.big-game-slain", 1000, 2000);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.pc(getBonusDamage(level), 0), 1);
    statLore(v, C.YELLOW, "+ ", Form.pc(getExtraDropChance(level), 0), 2);
  }

  static boolean isBigGame(EntityType type) {
    return BIG_GAME.contains(type);
  }

  static double bonusDamage(double levelPercent, double base, double factor) {
    return base + (levelPercent * factor);
  }

  static double extraDropChance(double levelPercent, double base, double factor, double max) {
    return Math.min(max, base + (levelPercent * factor));
  }

  double getBonusDamage(int level) {
    return bonusDamage(getLevelPercent(level), getConfig().bonusDamageBase, getConfig().bonusDamageFactor);
  }

  double getExtraDropChance(int level) {
    return extraDropChance(getLevelPercent(level), getConfig().extraDropChanceBase, getConfig().extraDropChanceFactor, getConfig().maxExtraDropChance);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    Adaptation.AttackContext attack = resolveAttackContext(e);
    if (attack == null || !isBigGame(attack.target().getType())) {
      return;
    }

    Player p = attack.attacker();
    double bonus = getBonusDamage(attack.level());
    if (bonus <= 0) {
      return;
    }

    e.setDamage(e.getDamage() * (1.0D + bonus));
    Entity target = attack.target();
    fx(target.getLocation().add(0, target.getHeight() * 0.5D, 0), FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 4, 0.25D)
        .sound(Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.5F, 0.7F);
  }

  @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    Player killer = e.getEntity().getKiller();
    if (killer == null || !isBigGame(e.getEntity().getType()) || !hasActiveAdaptation(killer) || !canDamageTarget(killer, e.getEntity())) {
      return;
    }

    int level = getActiveLevel(killer);
    double chance = getExtraDropChance(level);
    int cap = Math.max(1, getConfig().maxExtraDropsPerKill);
    List<ItemStack> bonusDrops = new ArrayList<>();
    for (ItemStack drop : new ArrayList<>(e.getDrops())) {
      if (bonusDrops.size() >= cap) {
        break;
      }
      if (drop != null && !drop.getType().isAir() && ThreadLocalRandom.current().nextDouble() <= chance) {
        bonusDrops.add(drop.clone());
      }
    }
    e.getDrops().addAll(bonusDrops);

    addStat(killer, "hunter.big-game.big-game-slain", 1);
    xp(killer, getConfig().xpPerBigGameKill, "hunter:big-game:" + e.getEntity().getType().name().toLowerCase(java.util.Locale.ROOT));

    Location center = e.getEntity().getLocation().add(0, e.getEntity().getHeight() * 0.5D, 0);
    timeline(center)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(40)
        .frame((f, tick, progress) -> {
          f.dustRing(1.0D + (2.5D * progress), 16, 1.3F);
          f.particle(Particle.GLOW, 4, 0, 0.5D, 0, 0.4D, 0);
          if (tick == 0) {
            f.chord(Sound.ENTITY_PLAYER_LEVELUP, 0.5F, 0.9F, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6F, 1.4F);
          }
        })
        .start();
  }

  @ConfigDescription("Deal bonus damage to and reap extra drops from large and boss-class mobs.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base bonus damage fraction versus big game for the Hunter Big Game Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Bonus damage fraction gained across levels for the Hunter Big Game Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDamageFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base per-drop duplication chance versus big game for the Hunter Big Game Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double extraDropChanceBase = 0.15;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Per-drop duplication chance gained across levels for the Hunter Big Game Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double extraDropChanceFactor = 0.45;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum per-drop duplication chance for the Hunter Big Game Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxExtraDropChance = 0.75;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum bonus drops duplicated from one big-game kill for the Hunter Big Game Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int maxExtraDropsPerKill = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Xp granted per big-game kill for the Hunter Big Game Hunter adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBigGameKill = 45;

    public Config() {
      baseCost = 6;
      costFactor = 0.6;
      initialCost = 7;
    }
  }
}
