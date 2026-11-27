-keep class art.arcane.adapt.api.** { *; }
-keep class art.arcane.adapt.engine.** { *; }
-keep class art.arcane.adapt.platform.** { *; }
-keep class art.arcane.adapt.pack.link.** { public *; }
-keep class art.arcane.adapt.content.event.** { *; }
-keep class art.arcane.adapt.util.advancements.nms.** { *; }
-keep class **.volmlib.integration.** { *; }
-keepclassmembers class * {
    @art.arcane.adapt.util.reflect.events.api.ReflectiveHandler <methods>;
}
-keepclassmembers class * {
    @art.arcane.adapt.platform.event.Handle <methods>;
}
-keepclassmembers class art.arcane.adapt.command.AddonCommandRunner$Graft { void marker(); }
-keep class art.arcane.adapt.bukkit.platform.BukkitRefs { public static *; }
-keepclassmembers class * extends org.bukkit.event.Event {
    public static org.bukkit.event.HandlerList getHandlerList();
    public org.bukkit.event.HandlerList getHandlers();
}

-keep class art.arcane.adapt.util.arcane.volmlib.nativelib.** { *; }
-keep class art.arcane.adapt.util.advancements.nms.util.ReflectionUtil { *; }
-keep class art.arcane.adapt.bukkit.canvas.** { *; }

-keep class art.arcane.volmlib.nativelib.**.scoreboard.NativeScoreboardPackets { public <init>(); }
-keepclassmembers class art.arcane.adapt.content.item.**$Data { <fields>; }
-keepclassmembers class art.arcane.adapt.content.item.multiItems.MultiItem$MultiItemData { <fields>; }
