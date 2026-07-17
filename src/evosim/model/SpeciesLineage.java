package evosim.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tree-shaped view over a flat {@link Species} list, built from each species'
 * {@link Species#parentSpeciesId()}. Shared by the results screen's family
 * tree/taxonomy views and the HTML report's stream chart and nested species
 * outline — both need the same "children grouped under their parent" shape.
 */
public final class SpeciesLineage {
  private SpeciesLineage() {}

  /** Species with no in-sim ancestor (founded from the initial population). */
  public static List<Species> roots(List<Species> all) {
    List<Species> roots = new ArrayList<>();
    for (Species s : all) {
      if (s.parentSpeciesId() == SpeciesRegistry.NO_PARENT || !containsId(all, s.parentSpeciesId())) {
        roots.add(s);
      }
    }
    roots.sort(Comparator.comparingInt(Species::birthTick));
    return roots;
  }

  /** Direct descendants of every species in {@code all}, keyed by parent id, birth-ordered. */
  public static Map<Integer, List<Species>> childrenByParent(List<Species> all) {
    Map<Integer, List<Species>> byParent = new HashMap<>();
    for (Species s : all) {
      if (s.parentSpeciesId() == SpeciesRegistry.NO_PARENT || !containsId(all, s.parentSpeciesId())) continue;
      byParent.computeIfAbsent(s.parentSpeciesId(), k -> new ArrayList<>()).add(s);
    }
    for (List<Species> kids : byParent.values()) {
      kids.sort(Comparator.comparingInt(Species::birthTick));
    }
    return byParent;
  }

  /**
   * Pre-order depth-first traversal of the lineage forest: every species
   * appears immediately after its parent and before its siblings' subtrees,
   * so a stacked chart or indented list built from this order keeps related
   * species visually adjacent.
   */
  public static List<Species> dfsOrder(List<Species> all) {
    Map<Integer, List<Species>> children = childrenByParent(all);
    List<Species> out = new ArrayList<>();
    for (Species root : roots(all)) {
      dfs(root, children, out);
    }
    return out;
  }

  private static void dfs(Species s, Map<Integer, List<Species>> children, List<Species> out) {
    out.add(s);
    for (Species c : children.getOrDefault(s.id(), List.of())) {
      dfs(c, children, out);
    }
  }

  private static boolean containsId(List<Species> all, int id) {
    for (Species s : all) {
      if (s.id() == id) return true;
    }
    return false;
  }

  /** Left-pads a population history to a shared sample-tick axis length (zeros before the species existed). */
  public static int[] leftPad(List<Integer> history, int target) {
    int[] out = new int[target];
    int offset = target - history.size();
    for (int i = 0; i < history.size(); i++) {
      int idx = offset + i;
      if (idx >= 0 && idx < target) out[idx] = history.get(i);
    }
    return out;
  }
}
