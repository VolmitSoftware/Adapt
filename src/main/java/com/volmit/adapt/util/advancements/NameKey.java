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

package com.volmit.adapt.util.advancements;

import net.minecraft.resources.MinecraftKey;

import java.util.Objects;

/**
 * Represents a Unique Name
 *
 * @author Axel
 */
public class NameKey {
    private final String namespace;
    private final String key;

    private transient MinecraftKey mcKey;

    /**
     * Constructor for creating a NameKey
     *
     * @param namespace The namespace, choose something representing your plugin/project/subproject
     * @param key       The Unique key inside your namespace
     */
    public NameKey(String namespace, String key) {
        this.namespace = namespace.toLowerCase();
        this.key = key.toLowerCase();
    }

    /**
     * Constructor for creating a NameKey
     *
     * @param key The key inside the default namespace "minecraft" or a NameSpacedKey seperated by a colon
     */
    public NameKey(String key) {
        String[] split = key.split(":");
        if (split.length < 2) {
            this.namespace = "minecraft";
            this.key = key.toLowerCase();
        } else {
            this.namespace = split[0].toLowerCase();
            this.key = key.replaceFirst(split[0] + ":", "").toLowerCase();
        }
    }

    /**
     * Generates a {@link NameKey}
     *
     * @param from The MinecraftKey to generate from
     */
    public NameKey(MinecraftKey from) {
        this.namespace = from.b().toLowerCase();
        this.key = from.a().toLowerCase();
    }

    /**
     * Gets the namespace
     *
     * @return The namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Gets the key
     *
     * @return The key
     */
    public String getKey() {
        return key;
    }

    /**
     * Compares to another key
     *
     * @param anotherNameKey NameKey to compare to
     * @return true if both NameKeys match each other
     */
    public boolean isSimilar(NameKey anotherNameKey) {
        return namespace.equals(anotherNameKey.getNamespace()) && key.equals(anotherNameKey.getKey());
    }

    /**
     * Gets the MinecraftKey equivalent of this NameKey
     *
     * @return A {@link MinecraftKey} representation of this NameKey
     */
    public MinecraftKey getMinecraftKey() {
        if (mcKey == null) mcKey = new MinecraftKey(namespace, key);
        return mcKey;
    }

    @Override
    public boolean equals(Object obj) {
        return isSimilar((NameKey) obj);
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key);
    }

}