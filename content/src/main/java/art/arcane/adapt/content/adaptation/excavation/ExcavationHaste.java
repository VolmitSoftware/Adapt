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

package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.PlatformNeed;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDamageEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.List;

public class ExcavationHaste extends SimpleAdaptation<ExcavationHaste.Config> {
  public static final Id ID = Id.adapt("excavation/haste");
  public static final PlayerPreference<CommonPreferences.Scale> TIER = CommonPreferences.scale("tier", Text.key("adapt.excavation.preferences.excavationhaste.tier"));

  private final ExcavationHasteBoost haste = new ExcavationHasteBoost(this, playerState(), playerState());

  public ExcavationHaste() {
    super(ID);
    needs(PlatformNeed.attribute(Attributes.BLOCK_BREAK_SPEED));
    registerConfiguration(ExcavationHaste.Config.class);
    setIcon(Id.minecraft("golden_pickaxe"));
    setInterval(4388);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("iron_shovel"))
        .key("challenge_excavation_haste_5k")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("diamond_shovel"))
            .key("challenge_excavation_haste_50k")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_excavation_haste_5k", "excavation.haste.blocks-while-hasted", 5000, 400);
    registerMilestone("challenge_excavation_haste_50k", "excavation.haste.blocks-while-hasted", 50000, 1500);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    AdaptAttributeService.get().removeAll(player.player(), getId().toString());
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TIER);
  }

  @Override
  public void addStats(int level, StatLines v) {
    v.addLore(TextFormat.GREEN + Text.key("adapt.excavation.haste.lore1").resolve());
    statLore(v, TextFormat.GREEN, "", level, 2);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockDamageEvent e) {
    PlayerRef p = e.player();
    BlockActionContext context = resolveInteractContext(p, e.block().location());
    if (context == null) {
      return;
    }

    int level = Math.max(1, (int) Math.floor(context.level() * preference(p, TIER).multiplier()));
    haste.apply(p, e.block(), level, getHasteDurationTicks());
    addStat(p, "excavation.haste.blocks-while-hasted", 1);
  }

  private int getHasteDurationTicks() {
    return Math.max(40, Math.min(20 * 30, getConfig().hasteDurationTicks));
  }

  static void onsetFx(Adaptation<?> owner, Loc eye) {
    Fx.now(owner, eye, FxPriority.AMBIENT)
        .helix(Particles.ENCHANT, 0.5D, 1.4D, 6, 0)
        .sound(Sounds.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
  }

  static void tierUpFx(Adaptation<?> owner, Loc center) {
    Fx.now(owner, center, FxPriority.TRANSITION)
        .ring(Particles.WAX_ON, 0.6D, 8, 0.2D)
        .chord(Sounds.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.2f, Sounds.BLOCK_NOTE_BLOCK_BELL, 0.4f, 1.5f);
  }

  @ConfigDescription("Gain Haste while excavating blocks.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Duration in ticks that Hasty Excavator remains active after mining begins.", impact = "Longer durations prevent mining progress from repeatedly changing speed during slow block breaks and are clamped between 40 and 600 ticks.")
    int hasteDurationTicks = 100;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      maxLevel = 3;
      initialCost = 1;
    }
  }
}
