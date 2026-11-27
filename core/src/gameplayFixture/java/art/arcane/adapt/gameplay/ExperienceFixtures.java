package art.arcane.adapt.gameplay;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.engine.ComponentPort;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemMode;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ExperienceFixtures {
    private static final Map<UUID, Observation> observations = new HashMap<>();
    private static final DataKey<Integer> CHARGE = DataKey.of(Id.of("adapt", "echo-charge"), DataCodecs.INT);
    private static final Id EFFICIENCY = Id.minecraft("efficiency");

    public static void install() {
        Fixtures.register(new ExperienceFixtures());
    }

    public static JsonObject execute(PlayerRef player, String[] args) {
        if (args[2].equals("prepare")) {
            prepare(player);
        }
        Observation observation = observations.get(player.uuid());
        if (observation == null) {
            throw new IllegalStateException("Prepare the experience fixture first");
        }
        switch (args[2]) {
            case "prepare", "snapshot" -> {
            }
            case "award" -> experience().awardExperience(player, Integer.parseInt(args[3]));
            case "override" -> observation.override = Integer.parseInt(args[3]);
            case "natural" -> observation.override = null;
            case "refill" -> golem(observation).setEssence(60);
            case "release" -> GolemancyRuntime.registry().orElseThrow().release(golem(observation));
            case "ore" -> Fixtures.set(player.world(), 3, 100, 2, "diamond_ore");
            default -> throw new IllegalArgumentException("Unknown experience action: " + args[2]);
        }
        return snapshot(player, observation);
    }

    public static void cleanup() {
        observations.clear();
    }

    @Handle(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void modify(PlayerExpChangeEvent event) {
        Observation observation = observations.get(event.player().uuid());
        if (observation != null && observation.override != null) {
            event.setAmount(observation.override);
        }
    }

    @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void observe(PlayerExpChangeEvent event) {
        Observation observation = observations.get(event.player().uuid());
        if (observation != null) {
            observation.events++;
            observation.lastAmount = event.amount();
        }
    }

    private static ComponentPort experience() {
        return AdaptPorts.get(ComponentPort.class, ComponentPort.NONE);
    }

    private static void prepare(PlayerRef player) {
        if (observations.containsKey(player.uuid())) {
            throw new IllegalStateException("Experience fixture already prepared");
        }
        player.setLevel(0);
        player.setExpProgress(0F);
        experience().setTotalExperience(player, 0);
        Fixtures.clearInventory(player);
        ItemRef book = Fixtures.item("enchanted_book");
        book.setStoredEnchantment(EFFICIENCY, 1);
        player.inventory().setItem(0, book);
        player.inventory().setItem(1, Fixtures.item("diamond_pickaxe"));
        player.inventory().setItem(2, GolemItems.crook());
        player.inventory().setHeldSlot(0);
        MobRef golem = (MobRef) Fixtures.spawn(player.world(), "iron_golem", 2.5, 100, 0.5);
        GolemHandle handle = GolemancyRuntime.registry().orElseThrow().bind(player, golem, GolemBody.IRON, GolemCoreType.GATHERER);
        handle.setMode(GolemMode.STAY);
        handle.setEssence(60);
        Observation observation = new Observation();
        observation.golem = handle;
        observations.put(player.uuid(), observation);
    }

    /**
     * The registry's live handle of the bound golem. A chunk that unloads and reloads its entities (a fresh arena chunk
     * before the players' chunk tickets land) retires the handle bound in prepare and adopts a new one from the entity's
     * stored state; the menu works on that new handle.
     */
    private static GolemHandle golem(Observation observation) {
        GolemancyRuntime.registry().flatMap((GolemRegistry registry) -> registry.handle(observation.golem.entity()))
            .ifPresent((GolemHandle live) -> observation.golem = live);
        return observation.golem;
    }

    private static JsonObject snapshot(PlayerRef player, Observation observation) {
        PlayerData data = Fixtures.adaptPlayer(player).getData();
        JsonObject result = new JsonObject();
        result.addProperty("events", observation.events);
        result.addProperty("lastAmount", observation.lastAmount);
        result.addProperty("vanillaXp", experience().totalExperiencePoints(player));
        result.addProperty("unity", data.getStat("discovery.unity.orbs-distributed"));
        result.addProperty("discoveryXp", data.getSkillLine("discovery").getXp());
        GolemHandle golem = golem(observation);
        result.addProperty("golem", golem.entity().entityId());
        result.addProperty("essence", golem.state().essencePoints());
        int orbs = 0;
        for (EntityRef entity : player.world().entities()) {
            if (entity.type().equals(Id.minecraft("experience_orb"))) {
                orbs++;
            }
        }
        result.addProperty("orbs", orbs);
        ItemRef book = player.inventory().item(0);
        if (book != null && !book.isEmpty() && book.type().equals(Id.minecraft("enchanted_book"))) {
            result.addProperty("bookCharge", book.data().getOrDefault(CHARGE, 0));
            result.addProperty("bookLevel", book.storedEnchantments().getOrDefault(EFFICIENCY, 0));
        }
        return result;
    }

    private static final class Observation {
        private Integer override;
        private int events;
        private int lastAmount;
        private GolemHandle golem;
    }
}
