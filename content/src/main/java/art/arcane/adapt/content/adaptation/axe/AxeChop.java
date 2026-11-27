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

package art.arcane.adapt.content.adaptation.axe;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.List;

public class AxeChop extends SimpleAdaptation<AxeChop.Config> {
  public static final Id ID = Id.adapt("axe/chop");

  public AxeChop() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_axe"));
    setInterval(6911);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_axe"))
        .key("challenge_axe_chop_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_axe"))
            .key("challenge_axe_chop_2500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("netherite_axe"))
        .key("challenge_axe_chop_one_swing")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_axe_chop_100", "axe.chop.trees-felled", 100, 400);
    registerMilestone("challenge_axe_chop_2500", "axe.chop.trees-felled", 2500, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.SNEAK, AxePreferences.WORK);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, level, 1);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getCooldownTime(getLevelPercent(level)) * 50D, 1), 2);
    statLore(v, TextFormat.RED, "- ", getDamagePerBlock(getLevelPercent(level)), 3);
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_BLOCK && action != InteractAction.RIGHT_CLICK_AIR) {
      return;
    }

    if (e.hand() != null && e.hand() != Hand.MAIN_HAND) {
      return;
    }
    if ((action == InteractAction.RIGHT_CLICK_BLOCK && e.useBlock() == UseResult.DENY)
        || (action == InteractAction.RIGHT_CLICK_AIR && e.useItem() == UseResult.DENY)) {
      return;
    }

    PlayerRef p = e.player();
    if (action == InteractAction.RIGHT_CLICK_AIR && AdaptApi.scheduler().isRegionized()) {
      return;
    }
    if (p.cooldownTicks(p.inventory().mainHand().type()) > 0) {
      return;
    }

    if ((preferenceEnabled(p, AxePreferences.SNEAK) && !p.isSneaking()) || !ItemKinds.isAxe(p.inventory().mainHand()) || !hasActiveAdaptation(p)) {
      return;
    }

    BlockRef target = action == InteractAction.RIGHT_CLICK_BLOCK ? e.clickedBlock() : p.targetBlock(5);
    if (target == null) {
      return;
    }

    if (!canBlockBreak(p, target.location())) {
      return;
    }

    BlockStateRef b = target.state();
    if (ItemKinds.isLog(b.type())) {
      e.setCancelled(true);
      Loc chopLoc = target.location().add(0.5D, 1.0D, 0.5D);
      timeline(chopLoc)
          .duration(4)
          .priority(FxPriority.GAMEPLAY)
          .cullRadius(24)
          .frame((fx, tick, progress) -> {
            fx.ring(Particles.CRIT, 0.6D - (0.55D * progress), 8, 0.0D);
            if (tick == 0) {
              fx.chord(Sounds.ITEM_AXE_STRIP, 1.25F, 0.6F, Sounds.ITEM_AXE_WAX_OFF, 0.4F, 1.4F);
            }
          })
          .start();
      int logsChopped = 0;
      for (int i = 0; i < Math.max(1, (int) Math.floor(getActiveLevel(p) * preference(p, AxePreferences.WORK).multiplier())); i++) {
        if (AxeFelling.chopTop(this, target, getRange(getLevel(p)), p)) {
          logsChopped++;
          p.setCooldown(p.inventory().mainHand().type(), getCooldownTime(getLevelPercent(p)));
          damageHand(p, getDamagePerBlock(getLevelPercent(p)));
        }
      }
      if (logsChopped > 0) {
        HudStatus.cooldown(p, this, getCooldownTime(getLevelPercent(p)) * 50L);
        addStat(p, "axe.chop.trees-felled", 1);
        int felledLogs = logsChopped;
        int steps = Math.min(8, Math.max(3, felledLogs));
        Loc base = target.location().add(0.5D, 0.0D, 0.5D);
        timeline(base)
            .duration(steps)
            .priority(FxPriority.TRANSITION)
            .cullRadius(24)
            .frame((fx, tick, progress) -> {
              fx.particle(Particles.blockCrack(b), 3, 0.0D, progress * felledLogs, 0.0D, 0.15D, 0.02D);
              if ((tick & 1) == 0) {
                fx.sound(Sounds.ITEM_AXE_STRIP, 0.6F, (float) (0.9D + (0.5D * progress)));
              }
            })
            .start();
        if (logsChopped >= getConfig().maxLevel && grantOnce(p, "challenge_axe_chop_one_swing")) {
          fx(p.location().add(0.0D, 1.0D, 0.0D), FxPriority.TRANSITION)
              .column(Particles.TOTEM, 20, 2.0D)
              .chord(Sounds.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.6F);
        }
      }
    }
  }

  private int getRange(int level) {
    return level * getConfig().rangeLevelMultiplier;
  }

  private int getCooldownTime(double levelPercent) {
    return (int) (getConfig().cooldownTicksBase + (getConfig().cooldownTicksInverseLevelMultiplier * ((1D - levelPercent))));
  }

  private int getDamagePerBlock(double levelPercent) {
    return (int) (getConfig().damagePerBlockBase + (getConfig().damagePerBlockInverseLevelMultiplier * ((1D - levelPercent))));
  }

  @ConfigDescription("Chop down trees by right-clicking the base log.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Controls Range Level Multiplier for the Axe Chop adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    int rangeLevelMultiplier = 5;
    @ConfigDoc(value = "Controls Cooldown Ticks Base for the Axe Chop adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 15;
    @ConfigDoc(value = "Controls Cooldown Ticks Inverse Level Multiplier for the Axe Chop adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksInverseLevelMultiplier = 16;
    @ConfigDoc(value = "Controls Damage Per Block Base for the Axe Chop adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerBlockBase = 1;
    @ConfigDoc(value = "Controls Damage Per Block Inverse Level Multiplier for the Axe Chop adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double damagePerBlockInverseLevelMultiplier = 4;

    public Config() {
      initialCost = 3;
      baseCost = 5;
      costFactor = 0.6;
    }
  }
}
