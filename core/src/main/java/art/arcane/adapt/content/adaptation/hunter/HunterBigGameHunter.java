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

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.golem.GolemKillCredit;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class HunterBigGameHunter extends SimpleAdaptation<HunterBigGameHunter.Config> {
  private static final Set<Id> BIG_GAME = Set.of(
      Id.minecraft("ravager"),
      Id.minecraft("iron_golem"),
      Id.minecraft("warden"),
      Id.minecraft("wither"),
      Id.minecraft("ender_dragon"),
      Id.minecraft("elder_guardian"));

  public HunterBigGameHunter() {
    super("hunter-big-game");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("netherite_sword"));
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("ravager_spawn_egg"))
        .key("challenge_hunter_big_game_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("nether_star"))
            .key("challenge_hunter_big_game_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_hunter_big_game_100", "hunter.big-game.big-game-slain", 100, 600);
    registerMilestone("challenge_hunter_big_game_1k", "hunter.big-game.big-game-slain", 1000, 2000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getBonusDamage(level), 0), 1);
    statLore(v, C.YELLOW, "+ ", Form.pc(getExtraDropChance(level), 0), 2);
  }

  static boolean isBigGame(Id type) {
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

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDamageByEntityEvent e) {
    AttackContext attack = resolveAttackContext(e);
    if (attack == null || !isBigGame(attack.target().type())) {
      return;
    }

    PlayerRef p = attack.attacker();
    double bonus = getBonusDamage(attack.level());
    if (bonus <= 0) {
      return;
    }

    e.setDamage(e.damage() * (1.0D + bonus));
    EntityRef target = attack.target();
    fx(target.location().add(0, target.height() * 0.5D, 0), FxPriority.COMBAT)
        .burst(Particles.CRIT_MAGIC, 4, 0.25D)
        .sound(Sounds.ENTITY_PLAYER_ATTACK_STRONG, 0.5F, 0.7F);
  }

  @Handle(priority = EventPriority.NORMAL)
  public void on(EntityDeathEvent e) {
    PlayerRef killer = e.killer();
    LivingRef victim = e.entity();
    if (killer == null || !isBigGame(victim.type())
        || GolemKillCredit.isGolemKill(victim)
        || !hasActiveAdaptation(killer) || !canDamageTarget(killer, victim)) {
      return;
    }

    int level = getActiveLevel(killer);
    double chance = getExtraDropChance(level);
    int cap = Math.max(1, getConfig().maxExtraDropsPerKill);
    List<ItemRef> bonusDrops = new ArrayList<>();
    for (ItemRef drop : new ArrayList<>(e.drops())) {
      if (bonusDrops.size() >= cap) {
        break;
      }
      if (drop != null && !drop.isEmpty() && ThreadLocalRandom.current().nextDouble() <= chance) {
        bonusDrops.add(drop.copy());
      }
    }
    e.drops().addAll(bonusDrops);

    addStat(killer, "hunter.big-game.big-game-slain", 1);
    xp(killer, getConfig().xpPerBigGameKill, "hunter:big-game:" + victim.type().path());

    Loc center = victim.location().add(0, victim.height() * 0.5D, 0);
    timeline(center)
        .duration(6)
        .priority(FxPriority.TRANSITION)
        .cullRadius(40)
        .frame((f, tick, progress) -> {
          f.dustRing(1.0D + (2.5D * progress), 16, 1.3F);
          f.particle(Particles.GLOW, 4, 0, 0.5D, 0, 0.4D, 0);
          if (tick == 0) {
            f.chord(Sounds.ENTITY_PLAYER_LEVELUP, 0.5F, 0.9F, Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.6F, 1.4F);
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
