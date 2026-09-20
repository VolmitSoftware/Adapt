-keep class art.arcane.adapt.api.** { *; }
-keep class art.arcane.adapt.content.event.** { *; }
-keep class art.arcane.adapt.content.block.ScaffoldMatter { *; }
-keep class art.arcane.adapt.util.advancements.nms.** { *; }
-keep class **.volmlib.integration.** { *; }
-keepclassmembers class * {
    @art.arcane.adapt.util.reflect.events.api.ReflectiveHandler <methods>;
}
-keepclassmembers class * extends org.bukkit.event.Event {
    public static org.bukkit.event.HandlerList getHandlerList();
    public org.bukkit.event.HandlerList getHandlers();
}

-keep class art.arcane.adapt.util.arcane.volmlib.nativelib.** { *; }
-keep class art.arcane.adapt.util.advancements.nms.util.ReflectionUtil { *; }

-keep class art.arcane.volmlib.nativelib.**.scoreboard.NativeScoreboardPackets { public <init>(); }
