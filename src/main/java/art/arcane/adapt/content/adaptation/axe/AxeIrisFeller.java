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

import org.bukkit.event.player.PlayerToggleSneakEvent;
import java.util.Map;
import art.arcane.adapt.api.world.AdaptPlayer;
import java.util.List;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.AxeMessages;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.content.integration.iris.IrisTreeFellerLink;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

import static art.arcane.adapt.util.data.Metadata.VEIN_MINED;

public class AxeIrisFeller extends SimpleAdaptation<AxeIrisFeller.Config> {
  private final Map<UUID, IrisFellerRunHooks> activeRuns = playerState();
  private final Cooldowns activationCooldown = cooldowns();

  public AxeIrisFeller() {
    super("axe-iris-feller");
    registerConfiguration(AxeIrisFeller.Config.class);
    setLocalizationKey("axe.iris_feller");
    setIcon(Material.NETHERITE_AXE);
    setInterval(6127);
  }

  static int durabilityPreservationChance(int level) {
    return switch (Math.max(1, Math.min(level, 3))) {
      case 1 -> 0;
      case 2 -> 25;
      case 3 -> 75;
      default -> throw new IllegalStateException("Unexpected Iris Feller level");
    };
  }

  @EventHandler(ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent event) {
    if (event.isSneaking()) {
      IrisFellerRunHooks run = activeRuns.remove(event.getPlayer().getUniqueId());
      if (run != null) {
        run.cancelled = true;
      }
    }
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    IrisFellerRunHooks run = activeRuns.remove(player.getPlayer().getUniqueId());
    if (run != null) {
      run.cancelled = true;
    }
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, AxePreferences.LATCH, AxePreferences.RESERVE, AxePreferences.WORK);
  }

  public void addStats(int level, Element element) {
    statLore(element, C.GREEN, "", durabilityPreservationChance(level) + "%", 1);
    element.addLore(C.ITALIC + AdaptLanguage.text(AxeMessages.IRIS_FELLER_LORE2));
    element.addLore(C.ITALIC + AdaptLanguage.text(AxeMessages.IRIS_FELLER_LORE3));
    statLore(element, C.YELLOW, "* ", getHungerCost(), 4);
    statLore(element, C.YELLOW, "* ", Form.duration(getCooldownMillis(), 1), 5);
    element.addLore(C.ITALIC + AdaptLanguage.text(AxeMessages.IRIS_FELLER_LORE6));
    element.addLore(C.ITALIC + AdaptLanguage.text(AxeMessages.IRIS_FELLER_LORE7));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void on(BlockBreakEvent event) {
    if (event.isCancelled()) {
      return;
    }

    Block block = event.getBlock();
    if (VEIN_MINED.get(block) || IrisTreeFellerLink.isManagedBreak(event)) {
      return;
    }

    Player player = event.getPlayer();
    if (!meetsTrigger(player) || !IrisTreeFellerLink.isTreeBlock(block)) {
      return;
    }

    Adaptation.BlockActionContext context = resolveBlockBreakContext(player, block.getLocation(), null, true);
    if (context == null) {
      return;
    }

    int level = context.level();
    UUID playerId = player.getUniqueId();
    long cooldownMillis = getCooldownMillis();
    if (!activationCooldown.isReady(playerId, cooldownMillis)) {
      return;
    }

    int hungerCost = getHungerCost();
    if (!preference(player, AxePreferences.RESERVE).permits(player.getFoodLevel() - hungerCost)) {
      return;
    }

    IrisTreeFellerLink.tryFell(
        event,
        durabilityPreservationChance(level),
        new IrisFellerRunHooks(new RunCost(player, playerId, hungerCost, cooldownMillis))
    );
  }

  @Override
  public boolean isEnabled() {
    return IrisTreeFellerLink.isAvailable() && super.isEnabled();
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    loadedConfig.hungerCost = Math.max(0, Math.min(20, loadedConfig.hungerCost));
    loadedConfig.cooldownSeconds = Math.max(0, loadedConfig.cooldownSeconds);
  }

  @Override
  protected boolean shouldCanonicalizeConfigOnLoad() {
    return true;
  }

  private boolean meetsTrigger(Player player) {
    ItemStack tool = player.getInventory().getItemInMainHand();
    return player.isSneaking() && isAxe(tool);
  }

  private int getHungerCost() {
    return Math.max(0, Math.min(20, getConfig().hungerCost));
  }

  private long getCooldownMillis() {
    return cooldownMillis(getConfig().cooldownSeconds);
  }

  static long cooldownMillis(int cooldownSeconds) {
    return Math.max(0L, (long) cooldownSeconds * 1000L);
  }

  @ConfigDescription("Sneak to fell an Iris tree, then keep sneaking with the original axe held while each successfully eroded log consumes hunger and the accepted run starts a cooldown.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Hunger points reserved for each log and consumed only after that log is successfully eroded.", impact = "Higher values make the run stop sooner when hunger cannot fund the next log; failed removals clear the reservation without changing food and 0 disables the cost.")
    int hungerCost = 2;
    @ConfigDoc(value = "Cooldown in seconds after Iris accepts a tree-felling request.", impact = "Higher values space accepted tree-felling activations farther apart; 0 disables the cooldown.")
    int cooldownSeconds = 30;

    public Config() {
      baseCost = 3;
      costFactor = 0.95;
      maxLevel = 3;
      initialCost = 4;
    }
  }

  private final class IrisFellerRunHooks implements IrisTreeFellerLink.RunHooks {
    private final RunCost cost;
    private int reservedHunger;
    private int committedLogs;
    private boolean cancelled;
    private final boolean latched;

    private IrisFellerRunHooks(RunCost cost) {
      this.cost = cost;
      latched = preferenceEnabled(cost.player(), AxePreferences.LATCH);
    }

    @Override
    public void onActivationAccepted() {
      activeRuns.put(cost.playerId(), this);
      if (cost.cooldownMillis() > 0L) {
        activationCooldown.mark(cost.playerId());
      }
    }

    @Override
    public boolean requiresSneaking() {
      return !latched;
    }

    @Override
    public boolean reserveLogCost() {
      if (cancelled || activeRuns.get(cost.playerId()) != this || getActiveLevel(cost.player()) <= 0
          || (preference(cost.player(), AxePreferences.WORK) != CommonPreferences.Scale.FULL
          && committedLogs >= (preference(cost.player(), AxePreferences.WORK) == CommonPreferences.Scale.HALF ? 128 : 64))
          || !preference(cost.player(), AxePreferences.RESERVE).permits(cost.player().getFoodLevel() - cost.hungerCost())
          || reservedHunger > 0) {
        return false;
      }
      if (cost.hungerCost() == 0) {
        return true;
      }

      if (cost.player().getFoodLevel() < cost.hungerCost()) {
        return false;
      }

      reservedHunger = cost.hungerCost();
      return true;
    }

    @Override
    public void commitLogCost() {
      committedLogs++;
      if (reservedHunger == 0) {
        return;
      }

      cost.player().setFoodLevel(Math.max(0, cost.player().getFoodLevel() - reservedHunger));
      reservedHunger = 0;
    }

    @Override
    public void refundLogCost() {
      reservedHunger = 0;
    }
  }

  private record RunCost(Player player, UUID playerId, int hungerCost, long cooldownMillis) {
  }
}
