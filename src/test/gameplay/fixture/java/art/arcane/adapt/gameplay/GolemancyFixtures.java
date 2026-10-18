package art.arcane.adapt.gameplay;

import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.item.GolemItems;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;

import java.util.Optional;

public final class GolemancyFixtures {
    private static final String BINDING_STAGE = "golemancy-binding";
    private static final String GOLEM_TAG = "adapt-qa-golemancy-golem";

    private GolemancyFixtures() {
    }

    public static boolean stage(String name, World world, Player actor, Player opponent) {
        if (!BINDING_STAGE.equals(name)) {
            return false;
        }
        opponent.teleport(new Location(world, 0.5, 100, 8.5));
        actor.setLevel(0);
        actor.getInventory().addItem(GolemItems.blankCore());
        Snowman golem = world.spawn(new Location(world, 2.5, 100, 0.5), Snowman.class);
        golem.addScoreboardTag(GOLEM_TAG);
        return true;
    }

    public static JsonObject snapshot(Player player) {
        JsonObject result = new JsonObject();
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Snowman golem && golem.getScoreboardTags().contains(GOLEM_TAG)) {
                result.add("golem", state(golem, player));
            }
        }
        return result;
    }

    private static JsonObject state(Snowman golem, Player player) {
        JsonObject result = new JsonObject();
        Optional<GolemState> state = GolemState.read(golem);
        result.addProperty("entityId", golem.getEntityId());
        result.addProperty("bound", state.isPresent());
        result.addProperty("boundToPlayer", state.map(bound -> bound.owner().equals(player.getUniqueId())).orElse(false));
        result.addProperty("aware", golem.isAware());
        AttributeInstance scale = golem.getAttribute(Attribute.SCALE);
        result.addProperty("scale", scale == null ? 1D : scale.getValue());
        return result;
    }
}
