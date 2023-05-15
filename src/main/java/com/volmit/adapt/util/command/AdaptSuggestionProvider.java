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

package com.volmit.adapt.util.command;

import com.volmit.adapt.api.skill.Skill;
import com.volmit.adapt.api.skill.SkillRegistry;
import io.github.mqzn.commands.base.SuggestionProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AdaptSuggestionProvider implements SuggestionProvider {
    private final List<String> suggestions = new ArrayList<>();

    @Override
    public @NotNull List<String> suggestions() {
        suggestions.add("[all]");
        suggestions.add("[random]");
        for (Skill<?> skill : SkillRegistry.skills.sortV()) {
            suggestions.add(skill.getName());
        }
        return suggestions;
    }
}