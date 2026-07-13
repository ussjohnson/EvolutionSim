package evosim.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

import evosim.model.Species;
import evosim.model.SpeciesLineage;
import evosim.report.HtmlReportWriter;

/**
 * A browsable taxonomy for the results screen: species nested under the
 * species they evolved from, using the same lineage data as the family-tree
 * chart ({@link StreamChartPanel}) and the HTML report's stream chart/outline.
 */
public final class SpeciesTreePanel extends JPanel {
  public SpeciesTreePanel(List<Species> significant) {
    super(new BorderLayout());
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
    Map<Integer, List<Species>> children = SpeciesLineage.childrenByParent(significant);
    for (Species s : SpeciesLineage.roots(significant)) {
      root.add(buildNode(s, children));
    }

    JTree tree = new JTree(root);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.setCellRenderer(new SpeciesCellRenderer());
    tree.setRowHeight(20);
    int i = 0;
    while (i < tree.getRowCount()) {
      tree.expandRow(i);
      i++;
    }

    add(new JScrollPane(tree), BorderLayout.CENTER);
  }

  private static DefaultMutableTreeNode buildNode(Species s, Map<Integer, List<Species>> children) {
    DefaultMutableTreeNode node = new DefaultMutableTreeNode(s);
    for (Species child : children.getOrDefault(s.id(), List.of())) {
      node.add(buildNode(child, children));
    }
    return node;
  }

  private static final class SpeciesCellRenderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
        boolean expanded, boolean leaf, int row, boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      Object userObj = ((DefaultMutableTreeNode) value).getUserObject();
      if (userObj instanceof Species s) {
        String label = s.name() + (s.isExtant() ? "" : " (extinct)")
            + "  —  " + HtmlReportWriter.describe(s.founder())
            + "  ·  peak " + s.peakPopulation();
        setText(label);
        setIcon(new SwatchIcon(new Color(s.color())));
      }
      return this;
    }
  }

  private static final class SwatchIcon implements Icon {
    private final Color color;
    SwatchIcon(Color color) { this.color = color; }
    @Override public int getIconWidth() { return 12; }
    @Override public int getIconHeight() { return 12; }
    @Override public void paintIcon(Component c, Graphics g, int x, int y) {
      g.setColor(color);
      g.fillRect(x, y + 1, 10, 10);
      g.setColor(Color.DARK_GRAY);
      g.drawRect(x, y + 1, 10, 10);
    }
  }
}
