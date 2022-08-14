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

package com.volmit.adapt.content.matter;

import art.arcane.spatial.matter.slices.RawMatter;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public class BrewingStandOwnerMatter extends RawMatter<BrewingStandOwner> {
        public BrewingStandOwnerMatter(int w, int h, int d)
        {
            super(w,h,d,BrewingStandOwner.class);
        }

        public BrewingStandOwnerMatter()
        {
            this(1,1,1);
        }

        @Override
        public void writeNode(BrewingStandOwner brewingStandOwner, DataOutputStream dataOutputStream) throws IOException {
            dataOutputStream.writeLong(brewingStandOwner.getOwner().getMostSignificantBits());
            dataOutputStream.writeLong(brewingStandOwner.getOwner().getLeastSignificantBits());
        }

        @Override
        public BrewingStandOwner readNode(DataInputStream dataInputStream) throws IOException {
            return new BrewingStandOwner(new UUID(dataInputStream.readLong(), dataInputStream.readLong()));
        }
    }