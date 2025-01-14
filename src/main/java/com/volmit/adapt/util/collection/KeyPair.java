/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.adapt.util.collection;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a keypair
 *
 * @param <K> the key type
 * @param <V> the value type
 * @author cyberpwn
 */
@Setter
@Getter
@SuppressWarnings("hiding")
public class KeyPair<K, V> {
    private K k;
    private V v;

    /**
     * Create a keypair
     *
     * @param k the key
     * @param v the value
     */
    public KeyPair(K k, V v) {
        this.k = k;
        this.v = v;
    }

}
