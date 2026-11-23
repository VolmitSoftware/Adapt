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

import art.arcane.adapt.AdaptCore;
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
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.ArchitectMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockBreakEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;

import java.util.List;

public class ArchitectGlass extends SimpleAdaptation<ArchitectGlass.Config> {
  public static final PlayerPreference<Materials> MATERIALS = new PlayerPreference<>(Materials.class,
      new PlayerPreference.Definition<>("materials", ArchitectMessages.ARCHITECTGLASS_PREFERENCE_MATERIALS, Materials.ALL, List.of(
          new PlayerPreference.Choice<>(Materials.ALL, ArchitectMessages.ARCHITECTGLASS_PREFERENCE_MATERIALS_ALL, Id.minecraft("glass"), 1),
          new PlayerPreference.Choice<>(Materials.CLEAR, ArchitectMessages.ARCHITECTGLASS_PREFERENCE_MATERIALS_CLEAR, Id.minecraft("glass_pane"), 1),
          new PlayerPreference.Choice<>(Materials.STAINED, ArchitectMessages.ARCHITECTGLASS_PREFERENCE_MATERIALS_STAINED, Id.minecraft("blue_stained_glass"), 1))));

  public ArchitectGlass() {
    super("architect-glass");
    registerConfiguration(ArchitectGlass.Config.class);
    setIcon(Id.minecraft("glass"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("glass"))
        .key("challenge_architect_glass_200")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("glass"))
            .key("challenge_architect_glass_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_architect_glass_200", "architect.glass.blocks-recovered", 200, 300);
    registerMilestone("challenge_architect_glass_5k", "architect.glass.blocks-recovered", 5000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, MATERIALS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(C.GREEN + AdaptLanguage.text(ArchitectMessages.GLASS_LORE1));
  }


  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockBreakEvent e) {
    PlayerRef p = e.player();
    withAdaptedPlayer(p, e, () -> {
      BlockRef block = e.block();
      String materialName = block.type().path();
      Materials selection = preference(p, MATERIALS);
      if ((selection == Materials.CLEAR && materialName.contains("stained"))
          || (selection == Materials.STAINED && !materialName.contains("stained"))) {
        return;
      }
      ItemRef hand = p.inventory().mainHand();
      if (hand.isEmpty() || !isTool(hand)) {
        if (!canBlockBreak(p, block.location())) {
          return;
        }
        if (materialName.contains("glass") && !materialName.contains("tinted_glass")) {
          block.world().dropItemNaturally(block.location().position(),
              AdaptCore.platform().host().items().create(block.type(), 1));
          fx(block.location().add(0.5, 0.5, 0.5), FxPriority.COMBAT)
              .dustRing(new Rgb(180, 230, 255), 0.4D, 12, 0.8F)
              .burst(Particles.REVERSE_PORTAL, 6, 0.2D)
              .chord(Sounds.BLOCK_LARGE_AMETHYST_BUD_BREAK, 0.7f, 1.0f, Sounds.BLOCK_GLASS_BREAK, 0.4f, 1.8f, Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 2.0f);
          block.breakNaturally(null);
          addStat(p, "architect.glass.blocks-recovered", 1);
        }
      }
    });
  }



  @ConfigDescription("Silk-touch glass blocks when breaking them with an empty hand.")
  protected static class Config extends AdaptationConfig {
    public Config() {
      permanent = true;
      baseCost = 2;
      costFactor = 1;
      maxLevel = 1;
      initialCost = 2;
    }
  }

  public enum Materials { ALL, CLEAR, STAINED }
}
