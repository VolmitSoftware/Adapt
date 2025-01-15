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

package com.volmit.adapt.api.data.unit;

import art.arcane.spatial.matter.slices.RawMatter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
public record Earnings(int earnings) {
    public Earnings increment() {
        if (earnings >= 127) {
            return this;
        }

        return new Earnings(earnings() + 1);
    }

    public static class EarningsMatter extends RawMatter<Earnings> {
        public EarningsMatter() {
            this(1, 1, 1);
        }

        public EarningsMatter(int width, int height, int depth) {
            super(width, height, depth, Earnings.class);
        }

        @Override
        public void writeNode(Earnings earnings, DataOutputStream dataOutputStream) throws IOException {
            dataOutputStream.writeByte(earnings.earnings());
        }

        @Override
        public Earnings readNode(DataInputStream dataInputStream) throws IOException {
            return new Earnings(dataInputStream.readByte());
        }
    }
}
