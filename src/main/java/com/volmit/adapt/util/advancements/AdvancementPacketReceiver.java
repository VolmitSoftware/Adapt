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

import com.volmit.adapt.util.advancements.event.AdvancementScreenCloseEvent;
import com.volmit.adapt.util.advancements.event.AdvancementTabChangeEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayInAdvancements;
import net.minecraft.network.protocol.game.PacketPlayInAdvancements.Status;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

public class AdvancementPacketReceiver {

    private static HashMap<String, ChannelHandler> handlers = new HashMap<>();
    private static Field channelField;

    {
        for (Field f : NetworkManager.class.getDeclaredFields()) {
            if (f.getType().isAssignableFrom(Channel.class)) {
                channelField = f;
                channelField.setAccessible(true);
                break;
            }
        }
    }

    public ChannelHandler listen(final Player p, final PacketReceivingHandler handler) {
        Channel ch = getNettyChannel(p);
        ChannelPipeline pipe = ch.pipeline();

        ChannelHandler handle = new MessageToMessageDecoder<Packet<?>>() {
            @Override
            protected void decode(ChannelHandlerContext chc, Packet<?> packet, List<Object> out) throws Exception {

                if (packet instanceof PacketPlayInAdvancements) {
                    if (!handler.handle(p, (PacketPlayInAdvancements) packet)) {
                        out.add(packet);
                    }
                    return;
                }

                out.add(packet);
            }
        };
        pipe.addAfter("decoder", "endercentral_crazy_advancements_listener_" + handler.hashCode(), handle);


        return handle;
    }

    public Channel getNettyChannel(Player p) {
        NetworkManager manager = ((CraftPlayer) p).getHandle().b.b;
        Channel channel = null;
        try {
            channel = (Channel) channelField.get(manager);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return channel;
    }

    public boolean close(Player p, ChannelHandler handler) {
        try {
            ChannelPipeline pipe = getNettyChannel(p).pipeline();
            pipe.remove(handler);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public HashMap<String, ChannelHandler> getHandlers() {
        return handlers;
    }

    public void initPlayer(Player p) {
        handlers.put(p.getName(), listen(p, new PacketReceivingHandler() {

            @Override
            public boolean handle(Player p, PacketPlayInAdvancements packet) {

                if (packet.c() == Status.a) {
                    NameKey name = new NameKey(packet.d());
                    AdvancementTabChangeEvent event = new AdvancementTabChangeEvent(p, name);
                    Bukkit.getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        CrazyAdvancementsAPI.clearActiveTab(p);
                        return false;
                    } else {
                        if (!event.getTabAdvancement().equals(name)) {
                            CrazyAdvancementsAPI.setActiveTab(p, event.getTabAdvancement());
                        } else {
                            CrazyAdvancementsAPI.setActiveTab(p, name, false);
                        }
                    }
                } else {
                    AdvancementScreenCloseEvent event = new AdvancementScreenCloseEvent(p);
                    Bukkit.getPluginManager().callEvent(event);
                }


                return true;
            }
        }));
    }

    interface PacketReceivingHandler {
        public boolean handle(Player p, PacketPlayInAdvancements packet);
    }

}