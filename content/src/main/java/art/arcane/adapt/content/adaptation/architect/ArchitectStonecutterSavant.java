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
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.CraftingStation;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

public class ArchitectStonecutterSavant extends SimpleAdaptation<ArchitectStonecutterSavant.Config> {
  public static final Id ID = Id.adapt("architect/stonecutter_savant");
  public static final PlayerPreference<CommonPreferences.Toggle> OFFHAND = CommonPreferences.toggle("offhand", Text.key("adapt.architectstonecuttersavant.preferences.preference_offhand"), CommonPreferences.Toggle.OFF);

  private static final int STONECUTTER_LEVELS = 1;
  private static final Id STONECUTTER = Id.minecraft("stonecutter");

  public ArchitectStonecutterSavant() {
    super(ID);
    registerConfiguration(ArchitectStonecutterSavant.Config.class);
    setIcon(STONECUTTER);
    setInterval(24420);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("stonecutter"))
        .key("challenge_architect_stonecutter_savant_50")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("stonecutter"))
            .key("challenge_architect_stonecutter_savant_500")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_stonecutter_savant_50", "architect.stonecutter-savant.uses", 50, 300);
    registerMilestone("challenge_architect_stonecutter_savant_500", "architect.stonecutter-savant.uses", 500, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, OFFHAND);
  }


  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.architect.stonecutter_savant.lore1").resolve());
    v.addLore(TextFormat.YELLOW + (getConfig().requireOffhand ? Text.key("adapt.architect.stonecutter_savant.lore3") : Text.key("adapt.architect.stonecutter_savant.lore2")).resolve());
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    InteractAction action = e.action();
    if (action != InteractAction.LEFT_CLICK_AIR && action != InteractAction.LEFT_CLICK_BLOCK) {
      return;
    }

    if (e.hand() != null && e.hand() != Hand.MAIN_HAND) {
      return;
    }

    PlayerRef p = e.player();
    if (!p.isSneaking()) {
      return;
    }

    PlayerInventoryRef inventory = p.inventory();
    ItemRef hand = inventory.mainHand();
    if (ItemKinds.isItem(hand) && !hand.isEmpty()) {
      return;
    }

    if (!hasStonecutter(inventory) || (preferenceEnabled(p, OFFHAND) && !STONECUTTER.equals(inventory.offHand().type()))) {
      return;
    }
    if (resolveInteractContext(p, p.location(), PlayerRef::isSneaking) == null) {
      return;
    }

    withPlayerThread(p, () -> {
      p.openStation(CraftingStation.STONECUTTER);
      fx(p.location(), FxPriority.GAMEPLAY)
          .helix(Particles.ENCHANT, 0.5D, 1.6D, 12, 0)
          .chord(Sounds.BLOCK_GRINDSTONE_USE, 0.8f, 1.4f, Sounds.BLOCK_STONE_PLACE, 0.4f, 0.8f);
      addStat(p, "architect.stonecutter-savant.uses", 1);
      xp(p, getConfig().xpPerUse);
    });
  }

  private boolean hasStonecutter(PlayerInventoryRef inventory) {
    if (STONECUTTER.equals(inventory.offHand().type())) {
      return true;
    }

    return !getConfig().requireOffhand && inventory.contains(STONECUTTER, 1);
  }

  @ConfigDescription("Sneak-punch the air with an empty hand while carrying a stonecutter to open it anywhere.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Requires the stonecutter item to be in the offhand specifically.", impact = "True only accepts a stonecutter held in the offhand; false accepts a stonecutter anywhere in the inventory.")
    boolean requireOffhand = false;
    @ConfigDoc(value = "Adaptation xp granted per stonecutter opened.", impact = "Higher values speed up adaptation progression from uses.")
    double xpPerUse = 2;

    public Config() {
      initialCost = 1;
      baseCost = 1;
      costFactor = 1;
      maxLevel = STONECUTTER_LEVELS;
    }
  }
}
