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

package com.volmit.adapt.content.block;

import art.arcane.spatial.matter.slices.RawMatter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public class ScaffoldMatter extends RawMatter<ScaffoldMatter.ScaffoldData> {
    public ScaffoldMatter(int width, int height, int depth) {
        super(width, height, depth, ScaffoldData.class);
    }

    @Override
    public void writeNode(ScaffoldData scaffoldData, DataOutputStream dos) throws IOException {
        dos.writeLong(scaffoldData.getUuid().getMostSignificantBits());
        dos.writeLong(scaffoldData.getUuid().getLeastSignificantBits());
        dos.writeLong(scaffoldData.getTime());
    }

    @Override
    public ScaffoldData readNode(DataInputStream din) throws IOException {
        return ScaffoldData.builder()
                .uuid(new UUID(din.readLong(), din.readLong()))
                .time(din.readLong())
                .build();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ScaffoldData {
        private UUID uuid;
        @Builder.Default
        private long time = 0;
    }
}
