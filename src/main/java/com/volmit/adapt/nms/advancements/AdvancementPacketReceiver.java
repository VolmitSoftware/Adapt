package com.volmit.adapt.nms.advancements;

import com.volmit.adapt.nms.advancements.event.AdvancementScreenCloseEvent;
import com.volmit.adapt.nms.advancements.event.AdvancementTabChangeEvent;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToMessageDecoder;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.PacketPlayInAdvancements;
import net.minecraft.network.protocol.game.PacketPlayInAdvancements.Status;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

public class AdvancementPacketReceiver {
	
	private static HashMap<String, ChannelHandler> handlers = new HashMap<>();
	private static Field channelField;
	private static Field networkManagerField;
	
	{
		for(Field f : NetworkManager.class.getDeclaredFields()) {
			if(f.getType().isAssignableFrom(Channel.class)) {
				channelField = f;
				channelField.setAccessible(true);
				break;
			}
		}
		
		for(Field f : ServerCommonPacketListenerImpl.class.getDeclaredFields()) {
			if(f.getType().isAssignableFrom(NetworkManager.class)) {
				networkManagerField = f;
				networkManagerField.setAccessible(true);
				break;
			}
		}
	}
	
	interface PacketReceivingHandler {
		public boolean handle(Player p, PacketPlayInAdvancements packet);
	}
	
	public ChannelHandler listen(final Player p, final PacketReceivingHandler handler) {
		Channel ch = getNettyChannel(p);
		ChannelPipeline pipe = ch.pipeline();
		
		ChannelHandler handle = new MessageToMessageDecoder<Packet<?>>() {
			@Override
			protected void decode(ChannelHandlerContext chc, Packet<?> packet, List<Object> out) throws Exception {
				
				if(packet instanceof PacketPlayInAdvancements) {
					if(!handler.handle(p, (PacketPlayInAdvancements) packet)) {
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
	    NetworkManager manager = getNetworkManager(p);
	    Channel channel = null;
	    try {
	        channel = (Channel) channelField.get(manager);
	    } catch (IllegalArgumentException | IllegalAccessException e) {
	        e.printStackTrace();
	    }
	    return channel;
	}
	
	public NetworkManager getNetworkManager(Player p) {
		PlayerConnection connection = ((CraftPlayer) p).getHandle().c;
		NetworkManager manager = null;
		try {
	        manager = (NetworkManager) networkManagerField.get(connection);
	    } catch (IllegalArgumentException | IllegalAccessException e) {
	        e.printStackTrace();
	    }
	    return manager;
	}
	
	public boolean close(Player p, ChannelHandler handler) {
	    try {
	        ChannelPipeline pipe = getNettyChannel(p).pipeline();
	        pipe.remove(handler);
	        return true;
	    } catch(Exception e) {
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
				
				if(packet.d() == Status.a) {
					NameKey name = new NameKey(packet.e());
					AdvancementTabChangeEvent event = new AdvancementTabChangeEvent(p, name);
					Bukkit.getPluginManager().callEvent(event);
					
					if(event.isCancelled()) {
						CrazyAdvancementsAPI.clearActiveTab(p);
						return false;
					} else {
						if(!event.getTabAdvancement().equals(name)) {
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
	
}