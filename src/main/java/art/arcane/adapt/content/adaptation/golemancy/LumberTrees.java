package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LumberTrees {
  static final int CROWN_REACH = 2;
  static final int CAP_REACH = 3;
  static final int MAX_FUNGUS_BASES = 9;
  static final int MIN_TRUNK_RUN = 3;
  static final int MAX_RUN = 64;
  static final int MAX_CAP_BLOCKS = 512;
  static final int SWEEP_DEPTH = 6;
  static final int MAX_SWEEP_LEAVES = 1024;

  private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
  private static final int[][] AROUND = around();
  private static final Comparator<Part> TOP_DOWN = Comparator.comparingInt(Part::y).reversed()
      .thenComparingInt(part -> TreeBlocks.cap(part.type()) ? 0 : 1)
      .thenComparingInt(Part::x)
      .thenComparingInt(Part::z);
  private static final Comparator<Part> NORTH_WEST = Comparator.comparingInt(Part::x).thenComparingInt(Part::z).thenComparingInt(Part::y);

  private LumberTrees() {
  }

  static boolean candidate(TreeView view, int x, int y, int z, boolean nether) {
    if (!view.owned(x, y, z)) {
      return false;
    }
    Material type = view.type(x, y, z);
    if (!TreeBlocks.trunk(type, nether) || !footing(view, x, y, z, type)) {
      return false;
    }
    int run = run(view, x, y, z, type);
    return !outranked(view, x - 1, y, z, type, run, true) && !outranked(view, x, y, z - 1, type, run, true)
        && !outranked(view, x + 1, y, z, type, run, false) && !outranked(view, x, y, z + 1, type, run, false);
  }

  static Survey survey(TreeView view, int x, int y, int z, Reach reach) {
    if (!view.owned(x, y, z)) {
      return Survey.of(Verdict.UNOWNED);
    }
    Material start = view.type(x, y, z);
    if (!TreeBlocks.trunk(start, reach.nether()) || !footing(view, x, y, z, start)) {
      return Survey.of(Verdict.NOT_A_TREE);
    }
    List<Part> logs = new ArrayList<>(16);
    List<Part> grounded = new ArrayList<>(4);
    Verdict grown = grow(view, new Part(x, y, z, start), reach, logs, grounded);
    if (grown != Verdict.NATURAL) {
      return Survey.of(grown);
    }
    if (logs.size() == grounded.size()) {
      return Survey.of(Verdict.NOT_A_TREE);
    }
    boolean nether = TreeBlocks.stem(start);
    Trunk trunk = trunk(view, grounded);
    Verdict spread = spread(view, trunk.bases(), reach, nether);
    if (spread != Verdict.NATURAL) {
      return Survey.of(spread);
    }
    if (placedBeside(view, grounded, trunk.bases())) {
      return Survey.of(Verdict.PLACED);
    }
    if (!crowned(view, logs, nether)) {
      return Survey.of(Verdict.NO_LEAVES);
    }
    List<Part> parts = new ArrayList<>(logs);
    if (nether && cap(view, logs, parts) != Verdict.NATURAL) {
      return Survey.of(Verdict.UNOWNED);
    }
    parts.sort(TOP_DOWN);
    Material species = trunk.base().type();
    return new Survey(Verdict.NATURAL, new Tree(species, trunk.base(), trunk.bases(), List.copyOf(parts), logs.size(),
        sapling(view, logs, species)));
  }

  static Verdict intact(TreeView view, Part log) {
    if (!view.owned(log.x(), log.y(), log.z())) {
      return Verdict.UNOWNED;
    }
    if (!footing(view, log.x(), log.y(), log.z(), log.type()) && view.placed(log.x(), log.y(), log.z())) {
      return Verdict.PLACED;
    }
    for (int[] face : FACES) {
      int nx = log.x() + face[0];
      int ny = log.y() + face[1];
      int nz = log.z() + face[2];
      if (!view.owned(nx, ny, nz)) {
        return Verdict.UNOWNED;
      }
      Material type = view.type(nx, ny, nz);
      if (!member(type, log.type()) && !natural(view, nx, ny, nz, type)) {
        return Verdict.BUILT;
      }
    }
    return Verdict.NATURAL;
  }

  static List<Part> leafCandidates(TreeView view, List<Part> felled) {
    Map<Long, Integer> depth = new HashMap<>(64);
    Deque<Part> pending = new ArrayDeque<>();
    List<Part> leaves = new ArrayList<>(64);
    for (Part log : felled) {
      depth.put(key(log.x(), log.y(), log.z()), 0);
      pending.add(log);
    }
    while (!pending.isEmpty() && leaves.size() < MAX_SWEEP_LEAVES) {
      Part at = pending.poll();
      int next = depth.get(key(at.x(), at.y(), at.z())) + 1;
      if (next > SWEEP_DEPTH) {
        continue;
      }
      for (int[] face : FACES) {
        int nx = at.x() + face[0];
        int ny = at.y() + face[1];
        int nz = at.z() + face[2];
        long neighbor = key(nx, ny, nz);
        if (depth.containsKey(neighbor) || !view.owned(nx, ny, nz)) {
          continue;
        }
        Material type = view.type(nx, ny, nz);
        if (!TreeBlocks.leaves(type) || view.placed(nx, ny, nz)) {
          continue;
        }
        depth.put(neighbor, next);
        Part leaf = new Part(nx, ny, nz, type);
        leaves.add(leaf);
        pending.add(leaf);
        if (leaves.size() >= MAX_SWEEP_LEAVES) {
          break;
        }
      }
    }
    return leaves;
  }

  static boolean sweepable(TreeView view, Part leaf) {
    return view.owned(leaf.x(), leaf.y(), leaf.z()) && TreeBlocks.leaves(view.type(leaf.x(), leaf.y(), leaf.z()))
        && !view.placed(leaf.x(), leaf.y(), leaf.z()) && view.decaying(leaf.x(), leaf.y(), leaf.z());
  }

  static boolean footing(TreeView view, int x, int y, int z, Material species) {
    if (!view.owned(x, y - 1, z)) {
      return false;
    }
    Material below = view.type(x, y - 1, z);
    if (TreeBlocks.stem(species)) {
      return TreeBlocks.nylium(below);
    }
    if (TreeBlocks.ground(below)) {
      return true;
    }
    return below == Material.MANGROVE_ROOTS && TreeBlocks.sapling(species) == Material.MANGROVE_PROPAGULE;
  }

  private static Verdict grow(TreeView view, Part start, Reach reach, List<Part> logs, List<Part> grounded) {
    Material species = start.type();
    Set<Long> seen = new HashSet<>(64);
    Deque<Part> pending = new ArrayDeque<>();
    seen.add(key(start.x(), start.y(), start.z()));
    pending.add(start);
    while (!pending.isEmpty()) {
      Part log = pending.poll();
      boolean footed = footing(view, log.x(), log.y(), log.z(), log.type());
      if (!footed && view.placed(log.x(), log.y(), log.z())) {
        return Verdict.PLACED;
      }
      logs.add(log);
      if (footed) {
        grounded.add(log);
      }
      if (logs.size() > reach.cap()) {
        return Verdict.TOO_BIG;
      }
      for (int[] offset : AROUND) {
        int nx = log.x() + offset[0];
        int ny = log.y() + offset[1];
        int nz = log.z() + offset[2];
        if (!view.owned(nx, ny, nz)) {
          return Verdict.UNOWNED;
        }
        Material type = view.type(nx, ny, nz);
        if (member(type, species)) {
          if (seen.add(key(nx, ny, nz))) {
            pending.add(new Part(nx, ny, nz, type));
          }
          continue;
        }
        if (face(offset) && !natural(view, nx, ny, nz, type)) {
          return Verdict.BUILT;
        }
      }
    }
    return Verdict.NATURAL;
  }

  private static Trunk trunk(TreeView view, List<Part> grounded) {
    int[] runs = new int[grounded.size()];
    int best = 0;
    for (int index = 0; index < grounded.size(); index++) {
      Part log = grounded.get(index);
      runs[index] = run(view, log.x(), log.y(), log.z(), log.type());
      if (runs[index] > runs[best] || (runs[index] == runs[best] && NORTH_WEST.compare(log, grounded.get(best)) < 0)) {
        best = index;
      }
    }
    int threshold = Math.max(MIN_TRUNK_RUN, (runs[best] + 1) / 2);
    List<Part> bases = new ArrayList<>(4);
    for (int index = 0; index < grounded.size(); index++) {
      if (index == best || runs[index] >= threshold) {
        bases.add(grounded.get(index));
      }
    }
    bases.sort(NORTH_WEST);
    return new Trunk(grounded.get(best), List.copyOf(bases));
  }

  private static Verdict spread(TreeView view, List<Part> bases, Reach reach, boolean nether) {
    if (bases.size() == 1) {
      return Verdict.NATURAL;
    }
    if (!reach.mega() || (nether && (bases.size() > MAX_FUNGUS_BASES || !compact(bases, 2)))) {
      return Verdict.TOO_WIDE;
    }
    int placed = 0;
    for (Part base : bases) {
      if (view.placed(base.x(), base.y(), base.z())) {
        placed++;
      }
    }
    boolean planted = placed == 0 || (!nether && placed == bases.size());
    return planted ? Verdict.NATURAL : Verdict.PLACED;
  }

  private static boolean placedBeside(TreeView view, List<Part> grounded, List<Part> bases) {
    for (Part log : grounded) {
      if (!bases.contains(log) && view.placed(log.x(), log.y(), log.z())) {
        return true;
      }
    }
    return false;
  }

  private static int run(TreeView view, int x, int y, int z, Material species) {
    int run = 1;
    while (run < MAX_RUN && view.owned(x, y + run, z) && member(view.type(x, y + run, z), species)) {
      run++;
    }
    return run;
  }

  private static boolean outranked(TreeView view, int x, int y, int z, Material species, int run, boolean northWest) {
    if (!view.owned(x, y, z)) {
      return false;
    }
    Material type = view.type(x, y, z);
    if (!member(type, species) || !footing(view, x, y, z, type)) {
      return false;
    }
    int other = run(view, x, y, z, type);
    return northWest ? other >= run : other > run;
  }

  private static boolean member(Material type, Material species) {
    return TreeBlocks.stem(species) ? type == species : TreeBlocks.trunk(type, false);
  }

  private static boolean natural(TreeView view, int x, int y, int z, Material type) {
    if (TreeBlocks.leaves(type) || TreeBlocks.cap(type)) {
      return !view.placed(x, y, z);
    }
    return !TreeBlocks.building(type);
  }

  private static boolean compact(List<Part> bases, int span) {
    Part first = bases.getFirst();
    int minX = first.x();
    int maxX = first.x();
    int minZ = first.z();
    int maxZ = first.z();
    for (Part base : bases) {
      if (base.y() != first.y()) {
        return false;
      }
      minX = Math.min(minX, base.x());
      maxX = Math.max(maxX, base.x());
      minZ = Math.min(minZ, base.z());
      maxZ = Math.max(maxZ, base.z());
    }
    return maxX - minX <= span && maxZ - minZ <= span;
  }

  private static boolean crowned(TreeView view, List<Part> logs, boolean nether) {
    int top = top(logs);
    for (Part log : logs) {
      if (log.y() == top && crownNear(view, log, nether)) {
        return true;
      }
    }
    return false;
  }

  private static boolean crownNear(TreeView view, Part log, boolean nether) {
    for (int dx = -CROWN_REACH; dx <= CROWN_REACH; dx++) {
      for (int dy = -CROWN_REACH; dy <= CROWN_REACH; dy++) {
        for (int dz = -CROWN_REACH; dz <= CROWN_REACH; dz++) {
          int x = log.x() + dx;
          int y = log.y() + dy;
          int z = log.z() + dz;
          if (!view.owned(x, y, z)) {
            continue;
          }
          Material type = view.type(x, y, z);
          boolean crown = nether ? TreeBlocks.cap(type) && !view.placed(x, y, z) : TreeBlocks.leaves(type) && !view.placed(x, y, z);
          if (crown) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static Verdict cap(TreeView view, List<Part> stems, List<Part> parts) {
    Set<Long> columnKeys = new HashSet<>(stems.size());
    List<int[]> columns = new ArrayList<>(4);
    Set<Long> seen = new HashSet<>(stems.size() * 4);
    Deque<Part> pending = new ArrayDeque<>(stems);
    for (Part stem : stems) {
      seen.add(key(stem.x(), stem.y(), stem.z()));
      if (columnKeys.add(key(stem.x(), 0, stem.z()))) {
        columns.add(new int[]{stem.x(), stem.z()});
      }
    }
    int taken = 0;
    while (!pending.isEmpty() && taken < MAX_CAP_BLOCKS) {
      Part at = pending.poll();
      for (int[] face : FACES) {
        int nx = at.x() + face[0];
        int ny = at.y() + face[1];
        int nz = at.z() + face[2];
        if (!nearColumn(columns, nx, nz) || !seen.add(key(nx, ny, nz))) {
          continue;
        }
        if (!view.owned(nx, ny, nz)) {
          return Verdict.UNOWNED;
        }
        Material type = view.type(nx, ny, nz);
        if (!TreeBlocks.cap(type) || view.placed(nx, ny, nz)) {
          continue;
        }
        Part block = new Part(nx, ny, nz, type);
        parts.add(block);
        pending.add(block);
        taken++;
        if (taken >= MAX_CAP_BLOCKS) {
          break;
        }
      }
    }
    return Verdict.NATURAL;
  }

  private static Material sapling(TreeView view, List<Part> logs, Material species) {
    Material sapling = TreeBlocks.sapling(species);
    if (sapling != Material.OAK_SAPLING) {
      return sapling;
    }
    int top = top(logs);
    int oak = 0;
    int azalea = 0;
    int flowering = 0;
    for (Part log : logs) {
      if (log.y() != top) {
        continue;
      }
      for (int dx = -CROWN_REACH; dx <= CROWN_REACH; dx++) {
        for (int dy = -CROWN_REACH; dy <= CROWN_REACH; dy++) {
          for (int dz = -CROWN_REACH; dz <= CROWN_REACH; dz++) {
            int x = log.x() + dx;
            int y = log.y() + dy;
            int z = log.z() + dz;
            if (!view.owned(x, y, z) || view.placed(x, y, z)) {
              continue;
            }
            Material type = view.type(x, y, z);
            if (type == Material.OAK_LEAVES) {
              oak++;
            } else if (type == Material.AZALEA_LEAVES) {
              azalea++;
            } else if (type == Material.FLOWERING_AZALEA_LEAVES) {
              flowering++;
            }
          }
        }
      }
    }
    if (azalea + flowering <= oak) {
      return sapling;
    }
    return flowering >= azalea ? Material.FLOWERING_AZALEA : Material.AZALEA;
  }

  private static int top(List<Part> logs) {
    int top = Integer.MIN_VALUE;
    for (Part log : logs) {
      top = Math.max(top, log.y());
    }
    return top;
  }

  private static boolean nearColumn(List<int[]> columns, int x, int z) {
    for (int[] column : columns) {
      if (Math.abs(column[0] - x) <= CAP_REACH && Math.abs(column[1] - z) <= CAP_REACH) {
        return true;
      }
    }
    return false;
  }

  private static boolean face(int[] offset) {
    return Math.abs(offset[0]) + Math.abs(offset[1]) + Math.abs(offset[2]) == 1;
  }

  private static int[][] around() {
    int[][] offsets = new int[26][];
    int index = 0;
    for (int dx = -1; dx <= 1; dx++) {
      for (int dy = -1; dy <= 1; dy++) {
        for (int dz = -1; dz <= 1; dz++) {
          if (dx != 0 || dy != 0 || dz != 0) {
            offsets[index++] = new int[]{dx, dy, dz};
          }
        }
      }
    }
    return offsets;
  }

  private static long key(int x, int y, int z) {
    return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
  }

  enum Verdict {
    NATURAL,
    NOT_A_TREE,
    UNOWNED,
    PLACED,
    BUILT,
    NO_LEAVES,
    TOO_BIG,
    TOO_WIDE
  }

  record Reach(boolean mega, boolean nether, int cap) {
  }

  record Part(int x, int y, int z, Material type) {
  }

  record Tree(Material species, Part base, List<Part> bases, List<Part> parts, int logs, Material sapling) {
    boolean nether() {
      return TreeBlocks.stem(species);
    }

    boolean mega() {
      return bases.size() > 1;
    }

    boolean square() {
      return bases.size() == 4 && compact(bases, 1);
    }

    List<Part> logParts() {
      List<Part> logs = new ArrayList<>(this.logs);
      for (Part part : parts) {
        if (!TreeBlocks.cap(part.type())) {
          logs.add(part);
        }
      }
      return logs;
    }
  }

  private record Trunk(Part base, List<Part> bases) {
  }

  record Survey(Verdict verdict, Tree tree) {
    static Survey of(Verdict verdict) {
      return new Survey(verdict, null);
    }
  }
}
