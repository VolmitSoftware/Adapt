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

package com.volmit.adapt.content.gui;

import com.volmit.adapt.Adapt;
import com.volmit.adapt.AdaptConfig;
import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.world.AdaptPlayer;
import com.volmit.adapt.api.world.PlayerAdaptation;
import com.volmit.adapt.api.world.PlayerSkillLine;
import com.volmit.adapt.api.xp.XP;
import com.volmit.adapt.util.*;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SkillsGui {
    public static void open(Player player) {
        Window w = new UIWindow(player);
        w.setTag("/");
        w.setDecorator((window, position, row) -> new UIElement("bg")
                .setName(" ")
                .setMaterial(new MaterialBlock(Material.BLACK_STAINED_GLASS_PANE)));

        AdaptPlayer adaptPlayer = Adapt.instance.getAdaptServer().getPlayer(player);
        int ind = 0;
        if (adaptPlayer == null) {
            Adapt.error("Failed to open skills gui for " + player.getName() + " because they are not Online, Were Kicked, Or are a fake player.");
            return;
        }

        if (adaptPlayer.getData().getSkillLines().size() > 0) {
            for (PlayerSkillLine i : adaptPlayer.getData().getSkillLines().sortV()) {
                if (i.getRawSkill(adaptPlayer).hasBlacklistPermission(adaptPlayer.getPlayer(), i.getRawSkill(adaptPlayer)) || i.getLevel() < 0) {
                    continue;
                }
                int pos = w.getPosition(ind);
                int row = w.getRow(ind);
                int adaptationLevel = 0;
                for (PlayerAdaptation adaptation : i.getAdaptations().sortV()) {
                    adaptationLevel = adaptation.getLevel();
                }
                Skill<?> sk = Adapt.instance.getAdaptServer().getSkillRegistry().getSkill(i.getLine());
                w.setElement(pos, row, new UIElement("skill-" + sk.getName())
                        .setMaterial(new MaterialBlock(sk.getIcon()))
                        .setName(sk.getDisplayName(i.getLevel()))
                        .setProgress(1D)
                        .addLore(C.ITALIC + "" + C.GRAY + sk.getDescription())
                        .addLore(C.UNDERLINE + "" + C.WHITE + i.getKnowledge() + C.RESET + " " + C.GRAY + Localizer.dLocalize("snippets", "gui", "knowledge"))
                        .addLore(C.ITALIC + "" + C.GRAY + Localizer.dLocalize("snippets", "gui", "powerused") + " " + C.DARK_GREEN + adaptationLevel)
                        .onLeftClick((e) -> sk.openGui(player)));
                ind++;
            }

            if (AdaptConfig.get().isUnlearnAllButton()) {
                int unlearnAllPos = w.getResolution().getWidth() - 1;
                int unlearnAllRow = w.getViewportHeight() - 1;
                if (w.getElement(unlearnAllPos, unlearnAllRow) != null) unlearnAllRow++;
                w.setElement(unlearnAllPos, unlearnAllRow, new UIElement("unlearn-all")
                        .setMaterial(new MaterialBlock(Material.BARRIER))
                        .setName("" + C.RESET + C.GRAY + Localizer.dLocalize("snippets", "gui", "unlearnall")
                                + (AdaptConfig.get().isHardcoreNoRefunds()
                                ? " " + C.DARK_RED + "" + C.BOLD + Localizer.dLocalize("snippets", "adaptmenu", "norefunds")
                                : ""))
                        .onLeftClick((e) -> {
                            Adapt.instance.getAdaptServer().getSkillRegistry().getSkills().forEach(skill -> skill.getAdaptations().forEach(adaptation -> adaptation.unlearn(player, 1, false)));
                            for (Player players : player.getWorld().getPlayers()) {
                                players.playSound(player.getLocation(), Sound.BLOCK_NETHER_GOLD_ORE_PLACE, 0.7f, 1.355f);
                                players.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 0.755f);
                            }
                            w.close();
                            if (AdaptConfig.get().getLearnUnlearnButtonDelayTicks() != 0) {
                                player.sendTitle(" ", C.GRAY + Localizer.dLocalize("snippets", "gui", "unlearnedall"), 1, 5, 11);
                            }
                            J.s(() -> open(player), AdaptConfig.get().getLearnUnlearnButtonDelayTicks());
                        }));
            }

            w.setTitle(Localizer.dLocalize("snippets", "gui", "level") + " " + (int) XP.getLevelForXp(adaptPlayer.getData().getMasterXp()) + " (" + adaptPlayer.getData().getUsedPower() + "/" + adaptPlayer.getData().getMaxPower() + " " + Localizer.dLocalize("snippets", "gui", "powerused") + ")");
            w.open();
            w.onClosed((e) -> Adapt.instance.getGuiLeftovers().remove(player.getUniqueId().toString()));
            Adapt.instance.getGuiLeftovers().put(player.getUniqueId().toString(), w);
        }
    }
}
