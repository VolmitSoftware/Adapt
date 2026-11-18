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

import art.arcane.adapt.AdaptCore;
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
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class HunterTrophySkinner extends SimpleAdaptation<HunterTrophySkinner.Config> {
  public HunterTrophySkinner() {
    super("hunter-trophy-skinner");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("zombie_head"));
    setInterval(2000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("skeleton_skull"))
        .key("challenge_hunter_trophy_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("zombie_head"))
            .key("challenge_hunter_trophy_heads_100")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_hunter_trophy_50", "hunter.trophy-skinner.trophies-collected", 50, 400);
    registerMilestone("challenge_hunter_trophy_heads_100", "hunter.trophy-skinner.heads-collected", 100, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.pc(getDropChance(level), 0), 1);
    statLore(v, Form.pc(getHeadChance(level), 0), 2);
    statLore(v, C.YELLOW, "* ", Form.f(getMinimumRange(level), 1), 3);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(EntityDeathEvent e) {
    PlayerRef killer = e.killer();
    LivingRef victim = e.entity();
    if (killer == null || GolemKillCredit.isGolemKill(victim)
        || !hasActiveAdaptation(killer) || victim instanceof PlayerRef || !canDamageTarget(killer, victim)) {
      return;
    }

    int level = getActiveLevel(killer);
    PrecisionContext precision = readPrecisionContext(e, killer, level);
    if (!precision.precise()) {
      return;
    }

    Loc corpse = victim.location().add(0, victim.height() * 0.5D, 0);
    float markPitch = precision.projectileKill() ? 2.0F : 1.4F;
    timeline(corpse)
        .duration(2)
        .priority(FxPriority.COMBAT)
        .cullRadius(32)
        .frame((f, tick, progress) -> {
          f.ring(Particles.CRIT_MAGIC, 0.6D - (0.5D * progress), 6, 0.0D);
          if (tick == 0) {
            f.chord(Sounds.BLOCK_NOTE_BLOCK_PLING, 0.7F, markPitch, Sounds.BLOCK_NOTE_BLOCK_HAT, 0.4F, 1.9F);
          }
        })
        .start();

    DropOutcome outcome = decideDrops(
        ThreadLocalRandom.current().nextDouble(),
        ThreadLocalRandom.current().nextDouble(),
        getDropChance(level),
        getHeadChance(level)
    );
    if (!outcome.trophy() && !outcome.head()) {
      return;
    }

    if (outcome.trophy()) {
      ItemRef trophy = buildTrophyDrop(victim.type(), level, precision.projectileKill());
      if (trophy != null) {
        e.drops().add(trophy);
        addStat(killer, "hunter.trophy-skinner.trophies-collected", 1);
        xp(killer, getConfig().xpPerTrophy);
        fx(corpse, FxPriority.COMBAT)
            .particle(Particles.VILLAGER_HAPPY, 8, 0, 0.2D, 0, 0.3D, 0)
            .chord(Sounds.ENTITY_WOLF_SHAKE, 0.55F, 1.35F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4F, 1.8F);
      }
    }

    if (outcome.head()) {
      ItemRef head = buildHeadDrop(victim.type());
      if (head != null) {
        e.drops().add(head);
        addStat(killer, "hunter.trophy-skinner.heads-collected", 1);
        timeline(corpse)
            .duration(3)
            .priority(FxPriority.TRANSITION)
            .cullRadius(32)
            .frame((f, tick, progress) -> {
              f.column(Particles.TOTEM, 12, 1.4D);
              f.particle(Particles.GLOW, 4, 0, 0.4D, 0, 0.3D, 0);
              if (tick == 0) {
                f.chord(Sounds.ENTITY_PLAYER_LEVELUP, 0.4F, 1.2F, Sounds.BLOCK_NOTE_BLOCK_CHIME, 0.6F, 1.5F);
              }
            })
            .start();
      }
    }
  }

  static DropOutcome decideDrops(double trophyRoll, double headRoll, double trophyChance, double headChance) {
    return new DropOutcome(trophyRoll <= trophyChance, headRoll <= headChance);
  }

  private PrecisionContext readPrecisionContext(EntityDeathEvent e, PlayerRef killer, int level) {
    boolean projectileKill = false;
    double range = 0;
    if (e.directDamager() instanceof ProjectileRef projectile) {
      UUID shooter = projectile.shooterId();
      if (shooter != null && shooter.equals(killer.uuid())) {
        projectileKill = true;
      }
    }

    Loc killerLocation = killer.location();
    Loc victimLocation = e.entity().location();
    if (killerLocation.sameWorld(victimLocation)) {
      range = killerLocation.distance(victimLocation);
    }

    boolean rangedPrecision = projectileKill && range >= getMinimumRange(level);
    boolean stealthPrecision = killer.isSneaking();
    return new PrecisionContext(projectileKill, rangedPrecision || stealthPrecision);
  }

  private ItemRef buildTrophyDrop(Id type, int level, boolean projectileKill) {
    String material = switch (type.isMinecraft() ? type.path() : "") {
      case "creeper" -> "gunpowder";
      case "skeleton", "bogged", "wither_skeleton", "stray" -> "bone";
      case "zombie", "zombified_piglin", "husk", "drowned" -> "rotten_flesh";
      case "spider", "cave_spider" -> "string";
      case "blaze" -> "blaze_powder";
      case "enderman" -> "ender_pearl";
      case "witch" -> "redstone";
      case "piglin", "piglin_brute", "hoglin", "zoglin" -> "porkchop";
      default -> "leather";
    };

    int amount = Math.max(1, (int) Math.round(getConfig().trophyAmountBase + (getLevelPercent(level) * getConfig().trophyAmountFactor)));
    if (projectileKill) {
      amount += 1;
    }

    return AdaptCore.platform().host().items().create(Id.minecraft(material), Math.min(8, amount));
  }

  private ItemRef buildHeadDrop(Id type) {
    String material = switch (type.isMinecraft() ? type.path() : "") {
      case "creeper" -> "creeper_head";
      case "skeleton", "stray", "bogged" -> "skeleton_skull";
      case "wither_skeleton" -> "wither_skeleton_skull";
      case "zombie", "husk", "drowned", "zombified_piglin" -> "zombie_head";
      case "piglin", "piglin_brute" -> "piglin_head";
      default -> null;
    };

    return material == null ? null : AdaptCore.platform().host().items().create(Id.minecraft(material));
  }

  private double getDropChance(int level) {
    return Math.min(getConfig().maxDropChance, getConfig().dropChanceBase + (getLevelPercent(level) * getConfig().dropChanceFactor));
  }

  private double getHeadChance(int level) {
    return Math.min(getConfig().maxHeadChance, getConfig().headChanceBase + (getLevelPercent(level) * getConfig().headChanceFactor));
  }

  private double getMinimumRange(int level) {
    return Math.max(4, getConfig().minimumRangeBase - (getLevelPercent(level) * getConfig().minimumRangeFactor));
  }


  @ConfigDescription("Precision kills can grant bonus trophy drops and occasional heads from elite targets.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceBase = 0.14;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Drop Chance Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double dropChanceFactor = 0.3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Maximum Drop Chance for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxDropChance = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Head Chance Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double headChanceBase = 0.015;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Head Chance Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double headChanceFactor = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Maximum Head Chance for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxHeadChance = 0.12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Trophy Amount Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double trophyAmountBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Trophy Amount Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double trophyAmountFactor = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Range Base for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumRangeBase = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Minimum Range Factor for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minimumRangeFactor = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls XP Per Trophy for the Hunter Trophy Skinner adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerTrophy = 16;

    public Config() {
      baseCost = 5;
      costFactor = 0.8;
      initialCost = 5;
    }
  }

  private record PrecisionContext(boolean projectileKill, boolean precise) {
  }

  record DropOutcome(boolean trophy, boolean head) {
  }
}
