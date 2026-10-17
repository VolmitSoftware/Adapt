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

package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.skill.SimpleSkill;
import art.arcane.adapt.api.skill.SkillConfig;
import art.arcane.adapt.localization.SkillPresentation;
import art.arcane.adapt.localization.catalog.SkillMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.config.ConfigDoc;
import lombok.NoArgsConstructor;
import org.bukkit.Material;

public class SkillGolemancy extends SimpleSkill<SkillGolemancy.Config> {
  public SkillGolemancy() {
    super("golemancy", SkillPresentation.of(SkillMessages.GOLEMANCY_NAME, SkillMessages.GOLEMANCY_ICON, SkillMessages.GOLEMANCY_DESCRIPTION));
    registerConfiguration(Config.class);
    setColor(C.DARK_AQUA);
    setInterval(3620);
    setIcon(Material.CARVED_PUMPKIN);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CARVED_PUMPKIN)
        .key("challenge_golemancy_built_10")
        .model(CustomModel.get(Material.CARVED_PUMPKIN, "advancement", "golemancy", "challenge_golemancy_built_10"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.IRON_BLOCK)
            .key("challenge_golemancy_built_100")
            .model(CustomModel.get(Material.IRON_BLOCK, "advancement", "golemancy", "challenge_golemancy_built_100"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @NoArgsConstructor
  protected static class Config extends SkillConfig {
    @ConfigDoc(value = "Enables or disables the Golemancy skill.", impact = "Set to false to disable behavior without uninstalling files.")
    boolean enabled = true;
    @ConfigDoc(value = "Legacy ampersand color code used for Golemancy in menus and text.", impact = "Changes only how the skill name is colored.")
    String skillColor = "&3";
    @ConfigDoc(value = "Skill XP paid for building a snow golem.", impact = "Higher values reward snow golem builds more; 0 pays nothing.")
    double buildSnowXp = 40;
    @ConfigDoc(value = "Skill XP paid for building a copper golem.", impact = "Higher values reward copper golem builds more; 0 pays nothing.")
    double buildCopperXp = 90;
    @ConfigDoc(value = "Skill XP paid for building an iron golem.", impact = "Higher values reward iron golem builds more; 0 pays nothing.")
    double buildIronXp = 180;
    @ConfigDoc(value = "Milliseconds before one player earns build XP again for the same golem body.", impact = "Higher values limit repeated build XP; the builder record is still written during the cooldown.")
    long buildXpCooldownMillis = 20000;
    @ConfigDoc(value = "Milliseconds a placed carved pumpkin or jack o'lantern and a golem spawn may be apart and still credit the builder.", impact = "Higher values tolerate more delay between the head placement and the spawn.")
    long buildAttributionWindowMillis = 250;
    @ConfigDoc(value = "Blocks between the placed head block and the spawned golem for the build to credit the player.", impact = "Higher values match builds farther from the head block.")
    double buildAttributionRadius = 3.0;
    @ConfigDoc(value = "Blocks from a golem within which players earn witness XP when it kills a hostile mob.", impact = "Higher values let players farther away share in golem kills.")
    double witnessRadius = 16.0;
    @ConfigDoc(value = "Fraction of the golem kill value paid to each witnessing player.", impact = "Higher values pay more witness XP per kill.")
    double witnessXpMultiplier = 0.35;
    @ConfigDoc(value = "Milliseconds before one player earns witness XP again from the same golem.", impact = "Higher values limit witness XP from a single busy golem.")
    long witnessCooldownMillis = 6000;
    @ConfigDoc(value = "Most witness XP one player can earn in any rolling 60 minutes.", impact = "Higher values allow more passive witness XP per hour.")
    double witnessHourlyCap = 600;
    @ConfigDoc(value = "Base skill XP value of a golem kill before the victim's health is added.", impact = "Higher values raise the value of every golem kill.")
    double golemKillXpBase = 15;
    @ConfigDoc(value = "Skill XP added to a golem kill value per point of the victim's maximum health.", impact = "Higher values make tougher victims worth more.")
    double golemKillMaxHealthMultiplier = 1.0;
    @ConfigDoc(value = "Skill XP per point of health restored when repairing an iron golem that hostile mobs damaged.", impact = "Higher values pay more for repairs; damage from players never counts.")
    double repairXpPerHealth = 2.0;
    @ConfigDoc(value = "Skill XP paid for scraping one oxidation stage off a copper golem with an axe.", impact = "Higher values reward copper golem care more.")
    double copperScrapeXp = 60;
    @ConfigDoc(value = "Skill XP paid the first time you wax a copper golem with honeycomb.", impact = "Higher values reward waxing more; each golem pays once.")
    double copperWaxXp = 30;
    @ConfigDoc(value = "Multiplier applied to Golemancy XP from mobs that came from a spawner.", impact = "Lower values make spawner farms pay less; 1.0 pays the same as natural mobs.")
    double spawnerMobXpMultiplier = 0.3;
    @ConfigDoc(value = "Knowledge paid by the golem building challenges.", impact = "Higher values pay more knowledge; the 100 builds challenge pays five times this value.")
    double challengeBuiltReward = 500;
  }
}
