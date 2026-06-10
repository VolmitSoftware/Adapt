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

package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import lombok.NoArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ArchitectStonecutterSavant extends SimpleAdaptation<ArchitectStonecutterSavant.Config> {
  private final Map<UUID, Long> cooldowns;

  public ArchitectStonecutterSavant() {
    super("architect-stonecutter-savant");
    registerConfiguration(ArchitectStonecutterSavant.Config.class);
    setDescription(Localizer.dLocalize("architect.stonecutter_savant.description"));
    setDisplayName(Localizer.dLocalize("architect.stonecutter_savant.name"));
    setIcon(Material.STONECUTTER);
    setInterval(24420);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    cooldowns = new ConcurrentHashMap<>();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.STONECUTTER)
        .key("challenge_architect_stonecutter_savant_50")
        .title(Localizer.dLocalize("advancement.challenge_architect_stonecutter_savant_50.title"))
        .description(Localizer.dLocalize("advancement.challenge_architect_stonecutter_savant_50.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.STONECUTTER)
            .key("challenge_architect_stonecutter_savant_500")
            .title(Localizer.dLocalize("advancement.challenge_architect_stonecutter_savant_500.title"))
            .description(Localizer.dLocalize("advancement.challenge_architect_stonecutter_savant_500.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_architect_stonecutter_savant_50", "architect.stonecutter-savant.uses", 50, 300);
    registerMilestone("challenge_architect_stonecutter_savant_500", "architect.stonecutter-savant.uses", 500, 1000);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + Localizer.dLocalize("architect.stonecutter_savant.lore1"));
    v.addLore(C.GREEN + "" + (getCooldownMillis(getLevelPercent(level)) / 1000) + C.GRAY + " " + Localizer.dLocalize("architect.stonecutter_savant.lore2"));
    v.addLore(C.YELLOW + Localizer.dLocalize(getConfig().requireOffhand ? "architect.stonecutter_savant.lore4" : "architect.stonecutter_savant.lore3"));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerInteractEvent e) {
    Action action = e.getAction();
    if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) {
      return;
    }

    if (e.getHand() != null && e.getHand() != EquipmentSlot.HAND) {
      return;
    }

    Player p = e.getPlayer();
    if (!p.isSneaking()) {
      return;
    }

    PlayerInventory inventory = p.getInventory();
    ItemStack hand = inventory.getItemInMainHand();
    if (isItem(hand) && hand.getType() != Material.AIR) {
      return;
    }

    if (!hasStonecutter(inventory)) {
      return;
    }

    Adaptation.BlockActionContext context = resolveInteractContext(p, p.getLocation(), Player::isSneaking);
    if (context == null) {
      return;
    }

    UUID id = p.getUniqueId();
    long now = M.ms();
    Long until = cooldowns.get(id);
    if (until != null && now < until) {
      SoundPlayer sp = SoundPlayer.of(p);
      sp.play(p.getLocation(), Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.3f, 0.7f);
      return;
    }

    cooldowns.put(id, now + getCooldownMillis(getLevelPercent(context.level())));
    withPlayerThread(p, () -> {
      p.openStonecutter(null, true);
      SoundPlayer sp = SoundPlayer.of(p);
      sp.play(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.8f, 1.4f);
      getPlayer(p).getData().addStat("architect.stonecutter-savant.uses", 1);
      xp(p, getConfig().xpPerUse);
    });
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    cooldowns.remove(e.getPlayer().getUniqueId());
  }

  private boolean hasStonecutter(PlayerInventory inventory) {
    if (getConfig().requireOffhand) {
      return inventory.getItemInOffHand().getType() == Material.STONECUTTER;
    }

    return inventory.contains(Material.STONECUTTER);
  }

  private long getCooldownMillis(double factor) {
    return (long) Math.max(1000, M.lerp(getConfig().maxCooldownSeconds, getConfig().minCooldownSeconds, factor) * 1000D);
  }

  @Override
  public void onTick() {
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }

  @NoArgsConstructor
  @ConfigDescription("Sneak-punch the air with an empty hand while carrying a stonecutter to open it anywhere.")
  protected static class Config {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Keeps this adaptation permanently active once learned.", impact = "True removes the normal learn/unlearn flow and treats it as always learned.")
    boolean permanent = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Requires the stonecutter item to be in the offhand specifically.", impact = "True only accepts a stonecutter held in the offhand; false accepts a stonecutter anywhere in the inventory.")
    boolean requireOffhand = false;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
    int baseCost = 4;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
    int initialCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
    double costFactor = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum level a player can reach for this adaptation.", impact = "Higher values allow more levels; lower values cap progression sooner.")
    int maxLevel = 5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in seconds at level 0 progression.", impact = "Higher values make low-level players wait longer between uses.")
    double maxCooldownSeconds = 60;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown in seconds at maximum level progression.", impact = "Lower values let max-level players open the stonecutter more often.")
    double minCooldownSeconds = 10;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Adaptation xp granted per stonecutter opened.", impact = "Higher values speed up adaptation progression from uses.")
    double xpPerUse = 2;
  }
}
