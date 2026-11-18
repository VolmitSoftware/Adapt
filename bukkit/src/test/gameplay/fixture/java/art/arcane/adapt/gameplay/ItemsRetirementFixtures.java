package art.arcane.adapt.gameplay;

import art.arcane.adapt.bukkit.platform.BukkitRefs;
import art.arcane.adapt.content.adaptation.architect.ArchitectScaffolder;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.J;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemsRetirementFixtures {
    private static final Map<UUID, RetiredRefund> RETIREMENTS = new ConcurrentHashMap<>();

    private ItemsRetirementFixtures() {
    }

    public static boolean handles(String[] args) {
        return args.length >= 3 && args[0].equals("items-audit") && args[2].startsWith("retirement-");
    }

    public static JsonObject execute(Player player, String[] args) {
        JsonObject result = new JsonObject();
        if (args[2].equals("retirement-arm")) {
            ItemRef box = ItemsAuditFixtures.shulker("Retired Retention Box");
            RETIREMENTS.put(player.getUniqueId(), new RetiredRefund(player, player.getLocation(), box));
            result.addProperty("owner", player.getUniqueId().toString());
            result.add("original", ItemsAuditFixtures.item(box));
        } else if (args[2].equals("retirement-refund")) {
            RetiredRefund refund = Objects.requireNonNull(RETIREMENTS.remove(UUID.fromString(args[3])));
            if (refund.owner().isOnline()) {
                throw new IllegalStateException("Refund owner must have logged out");
            }
            if (!J.isOwnedByCurrentRegion(refund.source())) {
                throw new IllegalStateException("Observer must own the original refund region");
            }
            try {
                Method method = ArchitectScaffolder.class.getDeclaredMethod("restitute", PlayerRef.class, Loc.class, List.class, Runnable.class);
                method.setAccessible(true);
                method.invoke(null, BukkitRefs.player(refund.owner()), BukkitRefs.loc(refund.source()),
                        List.of(refund.item()), (Runnable) () -> {
                    throw new IllegalStateException("Retired owner cannot receive inventory delivery");
                });
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot invoke Scaffolder restitution", failure);
            }
            result.addProperty("ownerOnline", false);
        } else if (!args[2].equals("retirement-snapshot")) {
            throw new IllegalArgumentException("Unknown retirement operation " + args[2]);
        }
        JsonArray drops = new JsonArray();
        for (Entity entity : player.getNearbyEntities(8, 8, 8)) {
            if (entity instanceof Item drop) {
                drops.add(ItemsAuditFixtures.item(BukkitRefs.item(drop.getItemStack())));
            }
        }
        result.add("drops", drops);
        return result;
    }

    private record RetiredRefund(Player owner, Location source, ItemRef item) {
    }
}
