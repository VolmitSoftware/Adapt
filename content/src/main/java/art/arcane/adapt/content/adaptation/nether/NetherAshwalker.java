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

package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByBlockEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;

public class NetherAshwalker extends SimpleAdaptation<NetherAshwalker.Config> {
  public static final Id ID = Id.adapt("nether/ashwalker");
  private static final Id HOT_FLOOR = Id.minecraft("hot_floor");
  private static final Id SOUL_FIRE = Id.minecraft("soul_fire");
  private static final Id CAMPFIRE = Id.minecraft("campfire");
  private static final Id SOUL_CAMPFIRE = Id.minecraft("soul_campfire");

  public NetherAshwalker() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(CAMPFIRE);
    setInterval(4000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("campfire"))
        .key("challenge_nether_ash_200")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("soul_campfire"))
            .key("challenge_nether_ash_5k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_ash_200", "nether.ashwalker.damage-negated", 200, 300);
    registerMilestone("challenge_nether_ash_5k", "nether.ashwalker.damage-negated", 5000, 1000);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, "100%", 1);
    statLore(v, coversCampfire(level, getConfig().campfireUnlockLevel) ? "100%" : "-", 2);
    statLore(v, coversSoulFire(level, getMaxLevel()) ? Format.pc(getConfig().soulFireReduction, 0) : "-", 3);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityDamageEvent e) {
    if (!(e.entity() instanceof PlayerRef p)) {
      return;
    }

    int level = getActiveLevel(p);
    if (level <= 0) {
      return;
    }

    DamageCause cause = e.cause();
    if (isMagmaDamage(e)) {
      negate(p, e, "magma");
      return;
    }

    if (isCampfireDamage(cause)) {
      if (coversCampfire(level, getConfig().campfireUnlockLevel)) {
        negate(p, e, "campfire");
      }
      return;
    }

    if (!isFireDamage(cause)) {
      return;
    }

    Id source = fireSource(e, p);
    if (source == null) {
      return;
    }

    if (isCampfire(source) && coversCampfire(level, getConfig().campfireUnlockLevel)) {
      negate(p, e, "campfire");
      return;
    }

    if (source.equals(SOUL_FIRE) && coversSoulFire(level, getMaxLevel())) {
      reduceSoulFire(p, e);
    }
  }

  private void reduceSoulFire(PlayerRef p, EntityDamageEvent e) {
    double before = e.damage();
    double reduction = soulFireReduction(getConfig().soulFireReduction);
    e.setDamage(Math.max(0D, before * (1D - reduction)));
    p.setFireTicks(Math.min(p.fireTicks(), Math.max(0, getConfig().soulFireMaxFireTicks)));
    double prevented = Math.max(0D, before - e.damage());
    int reduced = (int) Math.round(prevented);
    if (reduced > 0) {
      addStat(p, "nether.ashwalker.damage-negated", reduced);
    }
    xp(p, prevented * getConfig().xpPerNegatedDamage);
    fx(p.location().add(0D, 1.0D, 0D), FxPriority.COMBAT)
        .particle(Particles.SOUL_FIRE_FLAME, 4, 0D, 0.3D, 0D, 0.2D, 0.0D)
        .particle(Particles.SMOKE, 3, 0D, 0.2D, 0D, 0.2D, 0.0D)
        .sound(Sounds.BLOCK_SOUL_SAND_STEP, 0.4F, 0.8F);
  }

  private void negate(PlayerRef p, EntityDamageEvent e, String flavor) {
    double before = NetherAshGuard.negate(this, p, e, flavor.equals("magma"),
        immunitySoundVolume(getConfig().immunitySoundVolume));
    int prevented = (int) Math.round(Math.max(1D, before));
    addStat(p, "nether.ashwalker.damage-negated", prevented);
    xp(p, Math.max(before, 1D) * getConfig().xpPerNegatedDamage);
  }

  static void fullyNegateDamage(EntityDamageEvent event) {
    event.setDamage(0D);
    event.setCancelled(true);
  }

  static float immunitySoundVolume(double configured) {
    if (!Double.isFinite(configured)) {
      return 0F;
    }
    return (float) Math.max(0D, Math.min(1D, configured));
  }

  static double soulFireReduction(double configured) {
    if (!Double.isFinite(configured)) {
      return 0D;
    }
    return Math.max(0D, Math.min(1D, configured));
  }

  private Id fireSource(EntityDamageEvent event, PlayerRef p) {
    if (event instanceof EntityDamageByBlockEvent byBlock && byBlock.damager() != null) {
      Id eventSource = classifyFireBlock(byBlock.damager());
      if (eventSource != null) {
        return eventSource;
      }
    }

    Loc loc = p.location();
    BlockRef feet = p.world().blockAt(loc);
    BlockRef below = p.world().blockAt(loc.add(0D, -1D, 0D));
    Id fromFeet = classifyFireBlock(feet);
    if (fromFeet != null) {
      return fromFeet;
    }

    return classifyFireBlock(below);
  }

  private Id classifyFireBlock(BlockRef b) {
    Id t = b.type();
    if (t.equals(SOUL_FIRE)) {
      return SOUL_FIRE;
    }
    if (isCampfire(t) && "true".equals(b.state().property("lit"))) {
      return t;
    }

    return null;
  }

  public static boolean isMagmaDamage(EntityDamageEvent event) {
    return HOT_FLOOR.equals(event.damageType());
  }

  static boolean isFireDamage(DamageCause cause) {
    return cause == DamageCause.FIRE || cause == DamageCause.FIRE_TICK;
  }

  static boolean isCampfire(Id material) {
    return material.equals(CAMPFIRE) || material.equals(SOUL_CAMPFIRE);
  }

  static boolean coversCampfire(int level, int unlockLevel) {
    return level >= unlockLevel;
  }

  public static boolean isCampfireDamage(DamageCause cause) {
    return cause == DamageCause.CAMPFIRE;
  }

  static boolean coversSoulFire(int level, int maxLevel) {
    return level >= maxLevel;
  }


  static void ashFx(Adaptation<?> owner, PlayerRef p, boolean magma, float soundVolume) {
    FxEmitter feedback = Fx.now(owner, p.location(), FxPriority.TRANSITION)
        .particle(Particles.SMOKE, 4, 0.2D, 0.1D, 0.2D, 0.2D, 0.01D)
        .particle(Particles.ASH, 3, 0.2D, 0.1D, 0.2D, 0.1D, 0.0D);
    if (soundVolume > 0F) {
      feedback.sound(Sounds.BLOCK_FIRE_EXTINGUISH, soundVolume, magma ? 1.4F : 1.0F);
    }
  }

  @ConfigDescription("Ignore magma-block and campfire burns, and shrug off most soul-fire damage at mastery.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Campfire Unlock Level for the Nether Ashwalker adaptation.", impact = "Higher values require more levels before campfire immunity activates.")
    int campfireUnlockLevel = 2;
    @ConfigDoc(value = "Controls Soul Fire Reduction for the Nether Ashwalker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double soulFireReduction = 0.8;
    @ConfigDoc(value = "Controls Soul Fire Max Fire Ticks for the Nether Ashwalker adaptation.", impact = "Lower values shorten how long soul fire keeps you burning.")
    int soulFireMaxFireTicks = 20;
    @ConfigDoc(value = "Volume of Ashwalker's extinguish sound after fully cancelling magma or campfire damage.", impact = "Set to 0 to keep immunity silent, or raise up to 1 for audible proc feedback.")
    double immunitySoundVolume = 0;
    @ConfigDoc(value = "Controls Xp Per Negated Damage for the Nether Ashwalker adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerNegatedDamage = 3;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }
}
