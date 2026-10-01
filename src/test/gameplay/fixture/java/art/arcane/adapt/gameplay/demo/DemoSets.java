package art.arcane.adapt.gameplay.demo;

import art.arcane.adapt.gameplay.demo.sets.AgilitySets;
import art.arcane.adapt.gameplay.demo.sets.ArchitectSets;
import art.arcane.adapt.gameplay.demo.sets.AxesSets;
import art.arcane.adapt.gameplay.demo.sets.BlockingSets;
import art.arcane.adapt.gameplay.demo.sets.BrewingSets;
import art.arcane.adapt.gameplay.demo.sets.ChronosSets;
import art.arcane.adapt.gameplay.demo.sets.CraftingSets;
import art.arcane.adapt.gameplay.demo.sets.DiscoverySets;
import art.arcane.adapt.gameplay.demo.sets.EnchantingSets;
import art.arcane.adapt.gameplay.demo.sets.ExcavationSets;
import art.arcane.adapt.gameplay.demo.sets.HerbalismSets;
import art.arcane.adapt.gameplay.demo.sets.HunterSets;
import art.arcane.adapt.gameplay.demo.sets.IrisFellerSets;
import art.arcane.adapt.gameplay.demo.sets.KineticsSets;
import art.arcane.adapt.gameplay.demo.sets.KineticsComparisonSets;
import art.arcane.adapt.gameplay.demo.sets.NetherSets;
import art.arcane.adapt.gameplay.demo.sets.PickaxeSets;
import art.arcane.adapt.gameplay.demo.sets.RangedSets;
import art.arcane.adapt.gameplay.demo.sets.RiftSets;
import art.arcane.adapt.gameplay.demo.sets.SeaborneSets;
import art.arcane.adapt.gameplay.demo.sets.StealthSets;
import art.arcane.adapt.gameplay.demo.sets.StructureDiscoverySets;
import art.arcane.adapt.gameplay.demo.sets.SwordsSets;
import art.arcane.adapt.gameplay.demo.sets.TamingSets;
import art.arcane.adapt.gameplay.demo.sets.TragoulSets;
import art.arcane.adapt.gameplay.demo.sets.UnarmedSets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class DemoSets {
    private static final List<DemoSetProvider> PROVIDERS = List.of(
            new AgilitySets(),
            new ArchitectSets(),
            new AxesSets(),
            new BlockingSets(),
            new BrewingSets(),
            new ChronosSets(),
            new CraftingSets(),
            new DiscoverySets(),
            new EnchantingSets(),
            new ExcavationSets(),
            new HerbalismSets(),
            new HunterSets(),
            new IrisFellerSets(),
            new KineticsSets(),
            new KineticsComparisonSets(),
            new NetherSets(),
            new PickaxeSets(),
            new RangedSets(),
            new RiftSets(),
            new SeaborneSets(),
            new StealthSets(),
            new StructureDiscoverySets(),
            new SwordsSets(),
            new TamingSets(),
            new TragoulSets(),
            new UnarmedSets()
    );

    private DemoSets() {
    }

    public static DemoSet get(String name) {
        Map<String, DemoSet> sets = index();
        DemoSet set = sets.get(name);
        if (set == null) {
            throw new IllegalArgumentException("Unknown demo set " + name + "; known: " + sets.keySet());
        }
        return set;
    }

    private static Map<String, DemoSet> index() {
        Map<String, DemoSet> sets = new TreeMap<>();
        Map<String, String> owners = new HashMap<>();
        for (DemoSetProvider provider : PROVIDERS) {
            for (DemoSet set : provider.sets()) {
                String owner = owners.putIfAbsent(set.name(), provider.skill());
                if (owner != null) {
                    throw new IllegalStateException("Duplicate demo set name " + set.name() + ": provided by both "
                            + owner + " and " + provider.skill() + "; set names must be unique across DemoSets.PROVIDERS");
                }
                sets.put(set.name(), set);
            }
        }
        return sets;
    }
}
