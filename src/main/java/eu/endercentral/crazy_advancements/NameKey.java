package eu.endercentral.crazy_advancements;

import net.minecraft.resources.MinecraftKey;

public class NameKey {
	
	private final String namespace;
	private final String key;
	
	private transient MinecraftKey mcKey;
	
	/**
	 * 
	 * @param namespace The namespace, choose something representing your plugin/project
	 * @param key The Unique key inside your namespace
	 */
	public NameKey(String namespace, String key) {
		this.namespace = namespace.toLowerCase();
		this.key = key.toLowerCase();
	}
	
	/**
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
	 * @param from
	 */
	public NameKey(MinecraftKey from) {
		this.namespace = from.getNamespace().toLowerCase();
		this.key = from.getKey().toLowerCase();
	}
	
	/**
	 * 
	 * @return The namespace
	 */
	public String getNamespace() {
		return namespace;
	}
	
	/**
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
	
}