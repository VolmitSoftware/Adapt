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

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.PotionEffectTypes;
import art.arcane.volmlib.util.inventorygui.Element;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PickaxeStoneSkin extends SimpleAdaptation<PickaxeStoneSkin.Config> {
  private static final Set<Material> STONE_BLOCKS = EnumSet.of(
      Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
      Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF,
      Material.CALCITE, Material.ANDESITE, Material.DIORITE, Material.GRANITE);
  private final Map<UUID, StackState> stacks = new ConcurrentHashMap<>();

  public PickaxeStoneSkin() {
    super("pickaxe-stone-skin");
    registerConfiguration(PickaxeStoneSkin.Config.class);
    setDescription(Localizer.dLocalize("pickaxe.stone_skin.description"));
    setDisplayName(Localizer.dLocalize("pickaxe.stone_skin.name"));
    setIcon(Material.STONE);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(5377);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.STONE)
        .key("challenge_pickaxe_stoneskin_10k")
        .title(Localizer.dLocalize("advancement.challenge_pickaxe_stoneskin_10k.title"))
        .description(Localizer.dLocalize("advancement.challenge_pickaxe_stoneskin_10k.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_pickaxe_stoneskin_10k", "pickaxe.stone-skin.stacks-gained", 10000, 500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("pickaxe.stone_skin.lore1"));
    v.addLore(C.GREEN + "" + getTierCap(level) + C.GRAY + " " + Localizer.dLocalize("pickaxe.stone_skin.lore2"));
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
      getPlayer(p).getData().addStat("pickaxe.stone-skin.stacks-gained", 1);
    }

    int tier = Math.min(next / blocksPerStack, tierCap);
    if (tier <= 0) {
      return;
    }

    p.addPotionEffect(new PotionEffect(PotionEffectTypes.DAMAGE_RESISTANCE, getConfig().effectDurationTicks, tier - 1, false, false, true));
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    stacks.remove(e.getPlayer().getUniqueId());
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public void onTick() {
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  private record StackState(int stacks, long expiresAt) {
  }

  @NoArgsConstructor
  @ConfigDescription("Breaking stone-type blocks builds short-lived stacking damage resistance.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.55;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Stone blocks that must be broken to gain one resistance tier.", impact = "Higher values make resistance tiers slower to build.")
    int blocksPerStack = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds before built stacks expire without mining.", impact = "Higher values keep stacks alive longer between breaks.")
    long stackDurationMs = 6000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Duration in ticks of the applied resistance effect.", impact = "Higher values keep the resistance active longer after each break.")
    int effectDurationTicks = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum resistance amplifier this adaptation can reach.", impact = "Higher values allow stronger damage reduction at max stacks.")
    int maxAmplifier = 3;
  }
}
