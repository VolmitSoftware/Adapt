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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.NetherMessages;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.Environment;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class NetherNetherrackMason extends SimpleAdaptation<NetherNetherrackMason.Config> {
  public static final PlayerPreference<CommonPreferences.Toggle> MINING = CommonPreferences.toggle("mining", NetherMessages.NETHERNETHERRACKMASON_PREFERENCE_MINING, CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> DROPS = CommonPreferences.toggle("drops", NetherMessages.NETHERNETHERRACKMASON_PREFERENCE_DROPS, CommonPreferences.Toggle.ON);

  private static final Set<Id> MASON_BLOCKS = Set.of(Id.minecraft("netherrack"), Id.minecraft("basalt"),
      Id.minecraft("polished_basalt"), Id.minecraft("smooth_basalt"), Id.minecraft("blackstone"),
      Id.minecraft("polished_blackstone"), Id.minecraft("gilded_blackstone"), Id.minecraft("chiseled_polished_blackstone"),
      Id.minecraft("polished_blackstone_bricks"), Id.minecraft("cracked_polished_blackstone_bricks"));

  public NetherNetherrackMason() {
    super("nether-netherrack-mason");
    needs(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED));
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("blackstone"));
    setInterval(1500);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("netherrack"))
        .key("challenge_nether_mason_1k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("gilded_blackstone"))
            .key("challenge_nether_mason_25k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_nether_mason_1k", "nether.netherrack-mason.blocks-mined", 1000, 300);
    registerMilestone("challenge_nether_mason_25k", "nether.netherrack-mason.blocks-mined", 25000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MINING, DROPS);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    AdaptAttributeService.get().removeAll(player.getPlayer(), getName());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, getHasteTier(level), 1);
    statLore(v, Form.pc(getBonusDropChance(level), 0), 2);
  }

  @Handle(priority = EventPriority.HIGH)
  public void on(BlockDamageEvent e) {
    if (!preferenceEnabled(e.player(), MINING)) {
      return;
    }
    PlayerRef p = e.player();
    withAdaptedPlayer(p, e, () -> {
      if (!isNether(p) || !isMasonBlock(e.block().type())) {
        return;
      }

      long now = System.currentTimeMillis();
      if (getStorageLong(p, "masonHasteNext", 0L) > now && getStorageLong(p, "masonHasteUntil", 0L) > now) {
        return;
      }

      int hasteDurationTicks = getConfig().hasteDurationTicks;
      if (hasteDurationTicks <= 0) {
        return;
      }

      int level = getActiveLevel(p);
      AdaptAttributeService.get().applyTimed(p, getName(), "haste", Attributes.BLOCK_BREAK_SPEED, breakSpeedBonus(getHasteTier(level)), AttributeOperation.ADD_MULTIPLIED_TOTAL, hasteDurationTicks);
      setStorage(p, "masonHasteNext", now + getConfig().hasteRefreshMillis);
      setStorage(p, "masonHasteUntil", now + (hasteDurationTicks * 50L));
      AbilityGlyphs.duration(p.uuid(), this, now, hasteDurationTicks * 50L);
    });
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    if (!preferenceEnabled(e.player(), DROPS)) {
      return;
    }
    PlayerRef p = e.player();
    Id material = e.block().type();
    if (!isMasonBlock(material)) {
      return;
    }

    Loc dropAt = e.block().location().add(0.5D, 0.5D, 0.5D);
    withAdaptedPlayer(p, e, () -> {
      if (!isNether(p)) {
        return;
      }

      int level = getActiveLevel(p);
      addStat(p, "nether.netherrack-mason.blocks-mined", 1);
      xp(p, getConfig().xpPerBlock);

      if (ThreadLocalRandom.current().nextDouble() > getBonusDropChance(level)) {
        return;
      }

      ItemRef bonus = bonusDropFor(material);
      Tasks.runAt(dropAt, () -> {
        if (dropAt.world() != null) {
          dropAt.world().dropItemNaturally(dropAt.position(), bonus);
        }
      });
      xp(p, getConfig().xpOnBonusDrop);
      fx(dropAt, FxPriority.GAMEPLAY)
          .burst(Particles.LAVA, 3, 0.25D)
          .particle(Particles.CRIT, 4, 0D, 0.1D, 0D, 0.2D, 0.1D)
          .sound(Sounds.BLOCK_NETHERRACK_BREAK, 0.6F, 1.3F);
    });
  }

  private ItemRef bonusDropFor(Id mined) {
    ItemOps items = AdaptCore.platform().host().items();
    if (ThreadLocalRandom.current().nextDouble() < getConfig().premiumDropChance) {
      return switch (ThreadLocalRandom.current().nextInt(4)) {
        case 0 -> items.create(Id.minecraft("gold_nugget"), 1 + ThreadLocalRandom.current().nextInt(2));
        case 1 -> items.create(Id.minecraft("quartz"), 1);
        case 2 -> items.create(Id.minecraft("iron_nugget"), 1 + ThreadLocalRandom.current().nextInt(3));
        default -> items.create(Id.minecraft("nether_brick"), 1);
      };
    }

    return items.create(mined, 1);
  }

  private boolean isNether(PlayerRef p) {
    return p.world().environment() == Environment.NETHER;
  }

  private boolean isMasonBlock(Id type) {
    return MASON_BLOCKS.contains(type);
  }

  private int getHasteTier(int level) {
    return hasteTier(getLevelPercent(level), getConfig().hasteTierBase, getConfig().hasteTierFactor);
  }

  private double getBonusDropChance(int level) {
    return bonusDropChance(getLevelPercent(level), getConfig().bonusDropChanceBase, getConfig().bonusDropChanceFactor, getConfig().maxBonusDropChance);
  }

  static int hasteTier(double levelPercent, double base, double factor) {
    return Math.max(1, (int) Math.round(base + (levelPercent * factor)));
  }

  static double breakSpeedBonus(int tier) {
    return 0.20D * Math.max(1, tier);
  }

  static double bonusDropChance(double levelPercent, double base, double factor, double cap) {
    return Math.min(cap, base + (levelPercent * factor));
  }


  @ConfigDescription("Mine netherrack, basalt, and blackstone faster in the Nether with occasional bonus drops.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Tier Base for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hasteTierBase = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Tier Factor for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double hasteTierFactor = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Duration Ticks for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int hasteDurationTicks = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Haste Refresh Millis for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    long hasteRefreshMillis = 4000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Drop Chance Base for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDropChanceBase = 0.08;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Bonus Drop Chance Factor for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double bonusDropChanceFactor = 0.35;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Max Bonus Drop Chance for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double maxBonusDropChance = 0.4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Premium Drop Chance for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double premiumDropChance = 0.25;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Block for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerBlock = 1.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Bonus Drop for the Nether Netherrack Mason adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnBonusDrop = 5;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 4;
      initialCost = 1;
    }
  }
}
