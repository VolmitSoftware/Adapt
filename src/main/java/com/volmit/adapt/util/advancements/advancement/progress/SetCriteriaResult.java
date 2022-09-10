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

package com.volmit.adapt.util.advancements.advancement.progress;

import com.volmit.adapt.util.advancements.advancement.criteria.CriteriaType;

/**
 * Represents the Result to an Operation where Criteria is set
 *
 * @author Axel
 */
public enum SetCriteriaResult {

    /**
     * Operations with this Result did not lead to any changes
     */
    UNCHANGED,

    /**
     * Operations with this Result did lead to changes, but did not lead to the Advancement being completed
     */
    CHANGED,

    /**
     * Operations with this Result did lead to the Advancement being completed
     */
    COMPLETED,

    /**
     * Operations with this Result could not be processed because the {@link CriteriaType} did not match
     */
    INVALID,

}