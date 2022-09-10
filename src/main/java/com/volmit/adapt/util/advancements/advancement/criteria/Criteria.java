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

package com.volmit.adapt.util.advancements.advancement.criteria;

import com.volmit.adapt.util.advancements.CrazyAdvancementsAPI;
import net.minecraft.advancements.Criterion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Represents the Criteria that is required for an Advancement
 *
 * @author Axel
 */
public class Criteria {

    private final CriteriaType type;

    private final int requiredNumber;

    private final String[] actionNames;
    private final String[][] requirements;

    private final HashMap<String, Criterion> criteria = new HashMap<>();

    /**
     * Constructor for creating {@link CriteriaType} NUMBER which will require a certain number
     *
     * @param requiredNumber The required number
     */
    public Criteria(int requiredNumber) {
        this.type = CriteriaType.NUMBER;
        this.requiredNumber = requiredNumber;
        this.actionNames = new String[requiredNumber];

        for (int i = 0; i < requiredNumber; i++) {
            criteria.put("" + i, CrazyAdvancementsAPI.CRITERION);
            actionNames[i] = "" + i;
        }

        ArrayList<String[]> fixedRequirements = new ArrayList<>();
        for (String name : criteria.keySet()) {
            fixedRequirements.add(new String[]{name});
        }
        this.requirements = Arrays.stream(fixedRequirements.toArray()).toArray(String[][]::new);
    }

    /**
     * Constructor for creating {@link CriteriaType} LIST which will require a list of actions that need to be completed<br>
     * For further details see <a href="https://minecraft.fandom.com/wiki/Advancement/JSON_format">Advancement/JSON Format</a> on the Minecraft Wiki
     *
     * @param actionNames  The names of all occuring actions
     * @param requirements The definition of which and how actions are required (AND grouping of OR groups)
     */
    public Criteria(String[] actionNames, String[][] requirements) {
        this.type = CriteriaType.LIST;
        this.requiredNumber = -1;
        this.actionNames = actionNames;
        this.requirements = requirements;

        for (String action : actionNames) {
            criteria.put(action, CrazyAdvancementsAPI.CRITERION);
        }
    }

    /**
     * Gets the Criteria type
     *
     * @return The Criteria type
     */
    public CriteriaType getType() {
        return type;
    }

    /**
     * Gets the required Number (only applies for {@link CriteriaType} NUMBER)
     *
     * @return The Required Number
     */
    public int getRequiredNumber() {
        return requiredNumber;
    }

    /**
     * Gets the Action Names (auto-generated when using {@link CriteriaType} NUMBER)
     *
     * @return The Actions
     */
    public String[] getActionNames() {
        return actionNames;
    }

    /**
     * Gets the Requirements (auto-generated when using {@link CriteriaType} NUMBER)
     *
     * @return The Requirementsn
     */
    public String[][] getRequirements() {
        return requirements;
    }

    /**
     * Gets the generated Criteria
     *
     * @return The generated Criteria
     */
    public HashMap<String, Criterion> getCriteria() {
        return new HashMap<String, Criterion>(criteria);
    }


}