package com.volmit.adapt.nms.advancements;

import net.minecraft.resources.MinecraftKey;

import java.util.Objects;

/**
 * Represents a Unique Name
 * 
 * @author Axel
 *
 */
public class NameKey {
	
	private final String namespace;
	private final String key;
	
	private transient MinecraftKey mcKey;
	
	/**
	 * Constructor for creating a NameKey
	 * 
	 * @param namespace The namespace, choose something representing your plugin/project/subproject
	 * @param key The Unique key inside your namespace
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
		if(split.length < 2) {
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
		if(mcKey == null) mcKey = new MinecraftKey(namespace, key);
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