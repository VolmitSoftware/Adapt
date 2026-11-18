package art.arcane.adapt.gameplay;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.world.WorldRef;

public final class ArchitectFixtures {
    private ArchitectFixtures() {
    }

    public static boolean stage(String name, WorldRef arena, PlayerRef actor) {
        if (name.equals("build-foundation")) {
            Fixtures.set(arena, 0, 104, 0, "stone");
            Fixtures.teleport(actor, arena, 0.5, 105, 0.5);
            return true;
        }
        if (!name.equals("build-bridge") && !name.equals("build-refill") && !name.equals("build-demolition")) {
            return false;
        }
        Fixtures.set(arena, 2, 101, 2, "stone");
        Fixtures.set(arena, 2, 100, 1, "air");
        actor.inventory().setHeldSlot(0);
        actor.inventory().setItem(0, Fixtures.item("stone", name.equals("build-refill") ? 1 : 4));
        if (name.equals("build-refill")) {
            actor.inventory().setItem(9, Fixtures.item("stone", 32));
        }
        return true;
    }
}
