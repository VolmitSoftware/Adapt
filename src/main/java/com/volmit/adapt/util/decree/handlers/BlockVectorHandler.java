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

package com.volmit.adapt.util.decree.handlers;

import com.volmit.adapt.util.Form;
import com.volmit.adapt.util.M;
import com.volmit.adapt.util.VolmitSender;
import com.volmit.adapt.util.collection.KList;
import com.volmit.adapt.util.decree.DecreeContext;
import com.volmit.adapt.util.decree.DecreeParameterHandler;
import com.volmit.adapt.util.decree.DecreeSystem;
import com.volmit.adapt.util.decree.exceptions.DecreeParsingException;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;

import java.util.List;
import java.util.Objects;

public class BlockVectorHandler implements DecreeParameterHandler<BlockVector> {
    @Override
    public KList<BlockVector> getPossibilities() {
        KList<BlockVector> vx = new KList<>();
        VolmitSender s = DecreeContext.get();

        if (s.isPlayer()) {
            vx.add(s.player().getLocation().toVector().toBlockVector());
        }

        return vx;
    }

    @Override
    public String toString(BlockVector v) {
        if (v.getY() == 0) {
            return Form.f(v.getBlockX(), 2) + "," + Form.f(v.getBlockZ(), 2);
        }

        return Form.f(v.getBlockX(), 2) + "," + Form.f(v.getBlockY(), 2) + "," + Form.f(v.getBlockZ(), 2);
    }

    @Override
    public BlockVector parse(String in, boolean force) throws DecreeParsingException {
        try {
            if (in.contains(",")) {
                String[] comp = in.split("\\Q,\\E");

                if (comp.length == 2) {
                    return new BlockVector(Integer.parseInt(comp[0].trim()), 0, Integer.parseInt(comp[1].trim()));
                } else if (comp.length == 3) {
                    return new BlockVector(Integer.parseInt(comp[0].trim()),
                            Integer.parseInt(comp[1].trim()),
                            Integer.parseInt(comp[2].trim()));
                } else {
                    throw new DecreeParsingException("Could not parse components for vector. You have " + comp.length + " components. Expected 2 or 3.");
                }
            } else if (in.equalsIgnoreCase("here") || in.equalsIgnoreCase("me") || in.equalsIgnoreCase("self")) {
                if (!DecreeContext.get().isPlayer()) {
                    throw new DecreeParsingException("You cannot specify me,self,here as a console.");
                }

                return DecreeContext.get().player().getLocation().toVector().toBlockVector();
            } else if (in.equalsIgnoreCase("look") || in.equalsIgnoreCase("cursor") || in.equalsIgnoreCase("crosshair")) {
                if (!DecreeContext.get().isPlayer()) {
                    throw new DecreeParsingException("You cannot specify look,cursor,crosshair as a console.");
                }

                return Objects.requireNonNull(DecreeContext.get().player().getTargetBlockExact(256, FluidCollisionMode.NEVER)).getLocation().toVector().toBlockVector();
            } else if (in.trim().toLowerCase().startsWith("player:")) {
                String v = in.trim().split("\\Q:\\E")[1];


                List<?> px = Objects.requireNonNull(DecreeSystem.getHandler(Player.class)).getPossibilities(v);

                if (px != null && px.isNotEmpty()) {
                    return ((Player) px.get(0)).getLocation().toVector().toBlockVector();
                } else if (px == null || px.isEmpty()) {
                    throw new DecreeParsingException("Cannot find player: " + v);
                }
            }
        } catch (Throwable e) {
            throw new DecreeParsingException("Unable to get Vector for \"" + in + "\" because of an uncaught exception: " + e);
        }

        return null;
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(BlockVector.class);
    }

    @Override
    public String getRandomDefault() {
        return M.r(0.5) ? "0,0" : "0,0,0";
    }
}
