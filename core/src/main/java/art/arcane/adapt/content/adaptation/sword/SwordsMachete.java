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
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.protection.ProtectionProbes;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class SwordsMachete extends SimpleAdaptation<SwordsMachete.Config> {
  private static final Set<Id> FOLIAGE = Set.of(
      Id.minecraft("tall_grass"), Id.minecraft("cactus"), Id.minecraft("sugar_cane"), Id.minecraft("carrots"),
      Id.minecraft("potatoes"), Id.minecraft("nether_wart"), Id.minecraft("fern"), Id.minecraft("large_fern"),
      Id.minecraft("vine"), Id.minecraft("rose_bush"), Id.minecraft("wither_rose"), Id.minecraft("acacia_leaves"),
      Id.minecraft("birch_leaves"), Id.minecraft("dark_oak_leaves"), Id.minecraft("jungle_leaves"),
      Id.minecraft("oak_leaves"), Id.minecraft("spruce_leaves"), Id.minecraft("brown_mushroom"),
      Id.minecraft("red_mushroom"), Id.minecraft("dead_bush"), Id.minecraft("dandelion"),
      Id.minecraft("tall_seagrass"), Id.minecraft("seagrass"), Id.minecraft("white_tulip"), Id.minecraft("red_tulip"),
      Id.minecraft("pink_tulip"), Id.minecraft("orange_tulip"), Id.minecraft("lily_of_the_valley"),
      Id.minecraft("allium"), Id.minecraft("azure_bluet"), Id.minecraft("sunflower"), Id.minecraft("cornflower"),
      Id.minecraft("chorus_flower"), Id.minecraft("bamboo"), Id.minecraft("bamboo_sapling"), Id.minecraft("lilac"),
      Id.minecraft("peony"), Id.minecraft("lily_pad"), Id.minecraft("cocoa"), Id.minecraft("mangrove_leaves"),
      Id.minecraft("short_grass"), Id.minecraft("grass"));

  public SwordsMachete() {
    super("sword-machete");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_sword"));
    setInterval(5234);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_sword"))
        .key("challenge_swords_machete_2500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_sword"))
            .key("challenge_swords_machete_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_swords_machete_2500", "swords.machete.foliage-cut", 2500, 300);
    registerMilestone("challenge_swords_machete_25k", "swords.machete.foliage-cut", 25000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, SwordPreferences.SNEAK, SwordPreferences.FOLIAGE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getRadius(level), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1), 2);
    statLore(v, C.RED, "- ", getDamagePerBlock(getLevelPercent(level)), 3);
  }

  public double getRadius(int level) {
    return (getLevelPercent(level) * getConfig().radiusFactor) + getConfig().radiusBase;
  }

  @Handle(ignoreCancelled = false)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if ((action != InteractAction.LEFT_CLICK_AIR && action != InteractAction.LEFT_CLICK_BLOCK) || e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    ItemRef is = e.item();
    if ((preferenceEnabled(p, SwordPreferences.SNEAK) && !p.isSneaking()) || is == null || is.isEmpty() || !isSword(is)
        || p.hasCooldown(is.type()) || !hasActiveAdaptation(p)) {
      return;
    }

    int dmg = 0;
    Loc ctr = p.eyeLocation().add(p.location().direction().multiply(2.25)).add(0, -0.5, 0);

    int lvl = getLevel(p);
    int vertical = (int) Math.floor(getRadius(lvl));
    int horizontal = (int) Math.round(getRadius(lvl));
    WorldRef world = ctr.world();
    int cx = ctr.blockX();
    int cy = ctr.blockY();
    int cz = ctr.blockZ();
    for (int x = cx - horizontal; x <= cx + horizontal; x++) {
      for (int y = cy - vertical; y <= cy + vertical; y++) {
        for (int z = cz - horizontal; z <= cz + horizontal; z++) {
          BlockRef i = world.blockAt(x, y, z);
          if (!M.r((getLevelPercent(lvl) * 2.8) / (i.location().distanceSquared(ctr)))) {
            continue;
          }
          if (!FOLIAGE.contains(i.type()) || !preference(p, SwordPreferences.FOLIAGE).accepts(i.type())) {
            continue;
          }
          if (!canBlockBreak(p, i.location())) {
            continue;
          }
          if (ProtectionProbes.callBlockBreak(p, i)) {
            dmg += 1;
            Tasks.runAt(i.location(), () -> {
              i.breakNaturally(null);
              sfx(i.location(), Sounds.BLOCK_GRASS_BREAK, 0.4F, (float) (ThreadLocalRandom.current().nextDouble() * 1.85D));
            }, RNG.r.i(0, Math.max(1, (getMaxLevel() - lvl * 2) + 1)));
          }
        }
      }
    }

    if (dmg > 0) {
      int cooldownTicks = getCooldownTime(getLevelPercent(lvl));
      p.setCooldown(is.type(), cooldownTicks);
      AbilityGlyphs.cooldown(p, this, cooldownTicks * 50L);
      Loc tip = p.eyeLocation().add(p.location().direction().multiply(1.6D));
      float sweepPitch = (float) (ThreadLocalRandom.current().nextDouble() / 2D) + 0.65F;
      fx(tip, FxPriority.COMBAT)
          .particle(Particles.CRIT, Math.min(8, dmg + 4), 0, 0, 0, 0.25D, 0.05D)
          .particle(Particles.WAX_ON, 2, 0, 0, 0, 0.05D, 0)
          .chord(Sounds.ENTITY_PLAYER_ATTACK_SWEEP, 1F, sweepPitch, Sounds.ITEM_AXE_STRIP, 0.35F, 1.4F);
      damageHand(p, dmg * getDamagePerBlock(getLevelPercent(lvl)));
      xp(p, dmg * 11.25, "foliage-cut");
      addStat(p, "swords.machete.foliage-cut", dmg);
    }
  }

  private int getCooldownTime(double levelPercent) {
    return (int) (((int) ((1D - levelPercent) * getConfig().cooldownTicksSlowest)) + getConfig().cooldownTicksBase);
  }

  private int getDamagePerBlock(double levelPercent) {
    return (int) (getConfig().toolDamageBase + (getConfig().toolDamageInverseLevelFactor * ((1D - levelPercent))));
  }


  @ConfigDescription("Cut through foliage with ease using a sword.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 0.6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 2.36;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Slowest for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksSlowest = 35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Tool Damage Base for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double toolDamageBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Tool Damage Inverse Level Factor for the Swords Machete adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double toolDamageInverseLevelFactor = 5;

    public Config() {
      baseCost = 1;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }
}
