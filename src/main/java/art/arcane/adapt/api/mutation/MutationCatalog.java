package art.arcane.adapt.api.mutation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MutationCatalog {
  private static final MutationCatalog DEFAULTS = new MutationCatalog();

  private final List<MutationType> mutations;
  private final Map<String, MutationType> byId;
  private final Map<MutationDomain, List<String>> domainSkills;

  private MutationCatalog() {
    mutations = List.of(MutationType.values());
    LinkedHashMap<String, MutationType> index = new LinkedHashMap<>(mutations.size());
    for (MutationType type : mutations) {
      index.put(type.id(), type);
    }
    byId = Collections.unmodifiableMap(index);

    EnumMap<MutationDomain, List<String>> domains = new EnumMap<>(MutationDomain.class);
    domains.put(MutationDomain.BODY, List.of("agility", "blocking", "unarmed", "kinetics"));
    domains.put(MutationDomain.HUNT, List.of("swords", "ranged", "hunter", "stealth"));
    domains.put(MutationDomain.INDUSTRY, List.of("architect", "axes", "excavation", "pickaxe"));
    domains.put(MutationDomain.WILD, List.of("herbalism", "taming", "seaborne"));
    domains.put(MutationDomain.CRAFT, List.of("crafting", "brewing", "enchanting", "discovery"));
    domains.put(MutationDomain.ANOMALY, List.of("nether", "rift", "chronos", "tragoul"));
    domainSkills = Collections.unmodifiableMap(domains);
  }

  public static MutationCatalog defaults() {
    return DEFAULTS;
  }

  public List<MutationType> mutations() {
    return mutations;
  }

  public MutationType find(String id) {
    MutationType direct = id == null ? null : byId.get(id);
    return direct == null ? MutationType.find(id) : direct;
  }

  public List<String> domainSkills(MutationDomain domain) {
    List<String> skills = domainSkills.get(domain);
    return skills == null ? List.of() : skills;
  }

  public Map<MutationDomain, List<String>> domainSkills() {
    EnumMap<MutationDomain, List<String>> copy = new EnumMap<>(MutationDomain.class);
    for (Map.Entry<MutationDomain, List<String>> entry : domainSkills.entrySet()) {
      copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
    }
    return Collections.unmodifiableMap(copy);
  }
}
