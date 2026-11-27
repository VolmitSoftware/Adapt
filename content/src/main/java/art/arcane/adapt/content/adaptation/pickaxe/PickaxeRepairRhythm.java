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

package art.arcane.adapt.content.adaptation.pickaxe;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.item.ItemKinds;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.event.player.PlayerItemDamageEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.concurrent.ThreadLocalRandom;

public class PickaxeRepairRhythm extends SimpleAdaptation<PickaxeRepairRhythm.Config> {
  public static final Id ID = Id.adapt("pickaxe/repair_rhythm");
  private final PickaxeRepair repair = new PickaxeRepair(playerState(), this::completeRepair);

  public PickaxeRepairRhythm() {
    super(ID);
    registerConfiguration(PickaxeRepairRhythm.Config.class);
    setIcon(Id.minecraft("experience_bottle"));
    setInterval(7561);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("experience_bottle"))
        .key("challenge_pickaxe_rhythm_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_rhythm_5k", "pickaxe.repair-rhythm.durability-restored", 5000, 500);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.pickaxe.repair_rhythm.lore1").resolve());
    statLore(v, Format.pc(getRepairChance(level), 0), 2);
  }

  private double getRepairChance(int level) {
    return Math.min(getConfig().maxChance, getConfig().chanceBase + (level * getConfig().chancePerLevel));
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    ItemRef hand = p.inventory().mainHand();
    if (!ItemKinds.isPickaxe(hand)) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.block().location());
    if (context == null) {
      return;
    }

    int damage = hand.damage();
    if (damage <= 0) {
      return;
    }

    if (!(ThreadLocalRandom.current().nextDouble() < getRepairChance(context.level()))) {
      return;
    }

    int min = Math.max(1, getConfig().restoreMin);
    int max = Math.max(min, getConfig().restoreMax);
    int restore = min == max ? min : min + ThreadLocalRandom.current().nextInt((max - min) + 1);
    repair.queue(p, hand, restore);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerItemDamageEvent e) {
    repair.onItemDamage(e);
  }

  private void completeRepair(PlayerRef p, int restore) {
    addStat(p, "pickaxe.repair-rhythm.durability-restored", restore);
    fx(p.location().add(0, 1.2, 0), FxPriority.AMBIENT)
        .particle(Particles.WAX_ON, 2, 0, 0.2, 0, 0.15, 0.02)
        .particle(Particles.ENCHANT, 1, 0, 0.3, 0, 0.1, 0.05)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, restore >= 2 ? 1.95f : 1.8f);
  }

  @ConfigDescription("Sustained mining has a chance to restore pickaxe durability per broken block.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base chance per broken block to restore durability.", impact = "Higher values trigger repairs more often at every level.")
    double chanceBase = 0.05;
    @ConfigDoc(value = "Additional repair chance gained per adaptation level.", impact = "Higher values trigger repairs more often at higher levels.")
    double chancePerLevel = 0.06;
    @ConfigDoc(value = "Maximum total repair chance per broken block.", impact = "Higher values allow more frequent repairs at max level.")
    double maxChance = 0.5;
    @ConfigDoc(value = "Minimum durability restored per repair proc.", impact = "Higher values restore more durability per proc.")
    int restoreMin = 1;
    @ConfigDoc(value = "Maximum durability restored per repair proc.", impact = "Higher values restore more durability per proc.")
    int restoreMax = 2;

    public Config() {
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }
}
