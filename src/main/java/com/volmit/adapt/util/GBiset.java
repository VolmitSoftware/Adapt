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

package com.volmit.adapt.util;


import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * A Biset
 *
 * @param <A> the first object type
 * @param <B> the second object type
 * @author cyberpwn
 */
@Setter
@Getter
@SuppressWarnings("hiding")
public class GBiset<A, B> implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * -- GETTER --
     *  Get the object of the type A
     * <p>
     *
     * -- SETTER --
     *  Set the first object
     *
     */
    private A a;
    /**
     * -- GETTER --
     *  Get the second object
     * <p>
     *
     * -- SETTER --
     *  Set the second object
     */
    private B b;

    /**
     * Create a new Biset
     *
     * @param a the first object
     * @param b the second object
     */
    public GBiset(A a, B b) {
        this.a = a;
        this.b = b;
    }

}
