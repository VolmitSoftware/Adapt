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

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.PickaxeMessages;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.PotionEffect;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PickaxeStoneSkin extends SimpleAdaptation<PickaxeStoneSkin.Config> {
  private static final Set<Material> STONE_BLOCKS = EnumSet.of(
      Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
      Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF,
      Material.CALCITE, Material.ANDESITE, Material.DIORITE, Material.GRANITE);
  private final Map<UUID, StackState> stacks = playerState();

  public PickaxeStoneSkin() {
    super("pickaxe-stone-skin");
    registerConfiguration(PickaxeStoneSkin.Config.class);
    setIcon(Material.STONE);
    setInterval(5377);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.STONE)
        .key("challenge_pickaxe_stoneskin_10k")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .build());
    registerMilestone("challenge_pickaxe_stoneskin_10k", "pickaxe.stone-skin.stacks-gained", 10000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(PickaxeMessages.STONE_SKIN_LORE1));
    statLore(v, C.GREEN, "", getTierCap(level), 2);
  }

  private int getTierCap(int level) {
    return Math.min(level, getConfig().maxAmplifier + 1);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockBreakEvent e) {
    if (!STONE_BLOCKS.contains(e.getBlock().getType())) {
      return;
    }

    Player p = e.getPlayer();
    if (!isPickaxe(p.getInventory().getItemInMainHand())) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(p, e.getBlock().getLocation());
    if (context == null) {
      return;
    }

    long now = System.currentTimeMillis();
    UUID id = p.getUniqueId();
    StackState state = stacks.get(id);
    int current = state == null || now > state.expiresAt() ? 0 : state.stacks();
    int blocksPerStack = Math.max(1, getConfig().blocksPerStack);
    int tierCap = getTierCap(context.level());
    int next = Math.min(current + 1, tierCap * blocksPerStack);
    stacks.put(id, new StackState(next, now + getConfig().stackDurationMs));
    if (next > current) {
      addStat(p, "pickaxe.stone-skin.stacks-gained", 1);
    }

    int tier = Math.min(next / blocksPerStack, tierCap);
    if (tier <= 0) {
      return;
    }

    int prevTier = Math.min(current / blocksPerStack, tierCap);
    if (tier > prevTier) {
      fx(p.getLocation().add(0, 1, 0), FxPriority.TRANSITION)
          .dome(Particle.CLOUD, 1.2D, Math.min(20, tier * 4))
          .sound(Sound.BLOCK_STONE_PLACE, 0.5f, 0.8f + ((tier - 1) * 0.2f));
      if (tier >= tierCap) {
        fx(p.getLocation(), FxPriority.TRANSITION)
            .dustRing(0.9D, 12, 1.0F)
            .sound(Sound.BLOCK_ANCIENT_DEBRIS_PLACE, 0.4f, 0.9f);
      }
    }

    p.addPotionEffect(new PotionEffect(PotionEffectTypes.DAMAGE_RESISTANCE, getConfig().effectDurationTicks, tier - 1, false, false, true));
  }


  private record StackState(int stacks, long expiresAt) {
  }

  @ConfigDescription("Breaking stone-type blocks builds short-lived stacking damage resistance.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Stone blocks that must be broken to gain one resistance tier.", impact = "Higher values make resistance tiers slower to build.")
    int blocksPerStack = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds before built stacks expire without mining.", impact = "Higher values keep stacks alive longer between breaks.")
    long stackDurationMs = 6000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the applied resistance effect.", impact = "Higher values keep the resistance active longer after each break.")
    int effectDurationTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum resistance amplifier this adaptation can reach.", impact = "Higher values allow stronger damage reduction at max stacks.")
    int maxAmplifier = 3;

    public Config() {
      baseCost = 5;
      costFactor = 0.55;
      maxLevel = 4;
      initialCost = 4;
    }
  }
}
