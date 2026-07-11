package art.arcane.adapt.api.mutation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MutationCatalogTest {
  @Test
  void catalogContainsEveryUniqueLineageInProductOrder() {
    List<MutationType> mutations = MutationCatalog.defaults().mutations();

    assertThat(mutations).extracting(MutationType::id).containsExactly(
        "gale-lung",
        "bastion-spine",
        "verdant-molt",
        "temperbound",
        "paradox-scar",
        "arsenal-cortex",
        "packmind",
        "trophy-crucible",
        "umbral-echo",
        "living-lattice",
        "masterwork-bond",
        "deepblood",
        "mycelial-nerve",
        "gravebloom",
        "resonant-formula"
    );
    assertThat(mutations).hasSize(15);
    assertThat(mutations).extracting(MutationType::lineage).doesNotHaveDuplicates();

    Set<MutationLineage> expected = new HashSet<>();
    for (MutationDomain first : MutationDomain.values()) {
      for (MutationDomain second : MutationDomain.values()) {
        if (first.ordinal() < second.ordinal()) {
          expected.add(MutationLineage.of(first, second));
        }
      }
    }
    assertThat(mutations).extracting(MutationType::lineage).containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void sixDomainsCoverTheTwentyTwoCanonicalSkillsExactlyOnce() {
    MutationCatalog catalog = MutationCatalog.defaults();
    ArrayList<String> skills = new ArrayList<>();
    for (MutationDomain domain : MutationDomain.values()) {
      skills.addAll(catalog.domainSkills(domain));
    }

    assertThat(MutationDomain.values()).hasSize(6);
    assertThat(skills).hasSize(22).doesNotHaveDuplicates();
    assertThat(skills).contains("pickaxe", "tragoul", "seaborne");
  }

  @Test
  void unknownIdsDoNotMutateTheCatalog() {
    MutationCatalog catalog = MutationCatalog.defaults();
    int before = catalog.mutations().size();

    assertThat(catalog.find("future-mutation")).isNull();
    assertThat(catalog.mutations()).hasSize(before);
  }
}
