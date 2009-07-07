/*
Copyright 2008 WebAtlas
Authors : Mathieu Bastian, Mathieu Jacomy, Julian Bilcke
Website : http://www.gephi.org

This file is part of Gephi.

Gephi is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Gephi is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Gephi.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.gephi.graph.dhns.graph;

import org.gephi.graph.api.ClusteredGraph;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeIterable;
import org.gephi.graph.dhns.core.Dhns;
import org.gephi.graph.dhns.edge.AbstractEdge;
import org.gephi.graph.dhns.node.PreNode;
import org.gephi.graph.dhns.node.iterators.TreeListIterator;
import org.gephi.graph.dhns.node.iterators.ChildrenIterator;
import org.gephi.graph.dhns.node.iterators.DescendantIterator;
import org.gephi.graph.dhns.node.iterators.LevelIterator;
import org.gephi.graph.dhns.node.iterators.TreeIterator;
import org.gephi.graph.dhns.proposition.Proposition;
import org.gephi.graph.dhns.proposition.Tautology;

/**
 * Abstract Clustered graph class. Implements methods for both directed and undirected graphs.
 *
 * @author Mathieu Bastian
 */
public abstract class ClusteredGraphImpl extends AbstractGraphImpl implements ClusteredGraph {

    protected Proposition<PreNode> nodeProposition;
    protected Proposition<AbstractEdge> edgeProposition;

    public ClusteredGraphImpl(Dhns dhns, boolean visible) {
        this.dhns = dhns;

        if (visible) {
            nodeProposition = new Proposition<PreNode>() {

                public boolean evaluate(PreNode node) {
                    return node.isVisible();
                }

                public boolean isTautology() {
                    return false;
                }
            };
            edgeProposition = new Proposition<AbstractEdge>() {

                public boolean evaluate(AbstractEdge edge) {
                    return edge.isVisible();
                }

                public boolean isTautology() {
                    return false;
                }
            };
        } else {
            nodeProposition = new Tautology();
            edgeProposition = new Tautology();
        }
    }

    public boolean addNode(Node node, Node parent) {
        if (node == null) {
            throw new IllegalArgumentException("Node can't be null");
        }
        PreNode preNode = (PreNode) node;
        if (parent != null) {
            checkNode(parent);
        }
        if (preNode.isValid()) {
            return false;
        }
        if (!preNode.hasAttributes()) {
            preNode.setAttributes(dhns.getGraphFactory().newNodeAttributes());
        }
        dhns.getStructureModifier().addNode(node, parent);
        return true;
    }

    public boolean addNode(Node node) {
        return addNode(node, null);
    }

    public boolean contains(Node node) {
        if (node == null) {
            throw new NullPointerException();
        }
        PreNode preNode = (PreNode) node;
        readLock();
        if (!preNode.isValid()) {
            return false;
        }
        boolean res = false;
        if (nodeProposition.evaluate(preNode) && dhns.getTreeStructure().getTree().contains(preNode)) {
            res = true;
        }

        readUnlock();
        return res;
    }

    public NodeIterable getNodes() {
        readLock();
        return dhns.newNodeIterable(new TreeIterator(dhns.getTreeStructure(), nodeProposition));
    }

    public int getNodeCount() {
        readLock();
        int count = 0;
        if (nodeProposition.isTautology()) {
            count = dhns.getTreeStructure().getTreeSize() - 1;// -1 Exclude virtual root
        } else {
            for (TreeListIterator itr = new TreeListIterator(dhns.getTreeStructure().getTree(), 1); itr.hasNext();) {
                if (nodeProposition.evaluate(itr.next())) {
                    count++;
                }
            }
        }
        readUnlock();
        return count;
    }

    public NodeIterable getNodes(int level) {
        level += 1;     //Because we ignore the virtual root
        readLock();
        int height = dhns.getTreeStructure().treeHeight;
        if (level > height) {
            readUnlock();
            throw new IllegalArgumentException("Level must be between 0 and the height of the tree, currently height=" + (height - 1));
        }
        return dhns.newNodeIterable(new LevelIterator(dhns.getTreeStructure(), level, nodeProposition));
    }

    public int getLevelSize(int level) {
        level += 1;     //Because we ignore the virtual root
        readLock();
        int height = dhns.getTreeStructure().treeHeight;
        if (level > height) {
            readUnlock();
            throw new IllegalArgumentException("Level must be between 0 and the height of the tree, currently height=" + (height - 1));
        }
        int res = 0;
        for (LevelIterator itr = new LevelIterator(dhns.getTreeStructure(), level, nodeProposition); itr.hasNext();) {
            itr.next();
            res++;
        }

        readUnlock();
        return res;
    }

    public boolean isSelfLoop(Edge edge) {
        checkEdge(edge);
        return edge.getSource() == edge.getTarget();
    }

    public boolean isAdjacent(Edge edge1, Edge edge2) {
        if (edge1 == edge2) {
            throw new IllegalArgumentException("Edges can't be the same");
        }
        checkEdge(edge1);
        checkEdge(edge2);
        return edge1.getSource() == edge2.getSource() ||
                edge1.getSource() == edge2.getTarget() ||
                edge1.getTarget() == edge2.getSource() ||
                edge1.getTarget() == edge2.getTarget();
    }

    public Node getOpposite(Node node, Edge edge) {
        checkNode(node);
        checkEdge(edge);
        if (edge.getSource() == node) {
            return edge.getTarget();
        } else if (edge.getTarget() == node) {
            return edge.getSource();
        }
        throw new IllegalArgumentException("Node must be either source or target of the edge.");
    }

    public boolean removeNode(Node node) {
        PreNode preNode = checkNode(node);
        if (!preNode.isValid()) {
            return false;
        }
        dhns.getStructureModifier().deleteNode(node);
        return true;
    }

    public void clear() {
        dhns.getStructureModifier().clear();
    }

    public void clearEdges() {
        dhns.getStructureModifier().clearEdges();
    }

    public void clearEdges(Node node) {
        checkNode(node);
        dhns.getStructureModifier().clearEdges(node);
    }

    public void clearMetaEdges(Node node) {
        checkNode(node);
        dhns.getStructureModifier().clearMetaEdges(node);
    }

    public int getChildrenCount(Node node) {
        PreNode preNode = checkNode(node);
        readLock();
        int count = 0;
        ChildrenIterator itr = new ChildrenIterator(dhns.getTreeStructure(), preNode, nodeProposition);
        for (; itr.hasNext();) {
            itr.next();
            count++;
        }
        readUnlock();
        return count;
    }

    public Node getParent(Node node) {
        PreNode preNode = checkNode(node);
        readLock();
        Node parent = null;
        if (preNode.parent != dhns.getTreeStructure().getRoot()) {
            parent = preNode.parent;
        }
        readUnlock();
        return parent;
    }

    public NodeIterable getChildren(Node node) {
        PreNode preNode = checkNode(node);
        readLock();
        return dhns.newNodeIterable(new ChildrenIterator(dhns.getTreeStructure(), preNode, nodeProposition));
    }

    public NodeIterable getDescendant(Node node) {
        PreNode preNode = checkNode(node);
        readLock();
        return dhns.newNodeIterable(new DescendantIterator(dhns.getTreeStructure(), preNode, nodeProposition));
    }

    public NodeIterable getTopNodes() {
        readLock();
        return dhns.newNodeIterable(new ChildrenIterator(dhns.getTreeStructure(), nodeProposition));
    }

    public boolean isDescendant(Node node, Node descendant) {
        PreNode preNode = checkNode(node);
        PreNode preDesc = checkNode(descendant);
        readLock();
        boolean res = preDesc.getPre() > preNode.getPre() && preDesc.getPost() < preNode.getPost();
        readUnlock();
        return res;
    }

    public boolean isAncestor(Node node, Node ancestor) {
        return isDescendant(ancestor, node);
    }

    public boolean isFollowing(Node node, Node following) {
        PreNode preNode = checkNode(node);
        PreNode preFoll = checkNode(following);
        readLock();
        boolean res = preFoll.getPre() > preNode.getPre() && preFoll.getPost() > preNode.getPost();
        readUnlock();
        return res;
    }

    public boolean isPreceding(Node node, Node preceding) {
        return isFollowing(preceding, node);
    }

    public boolean isParent(Node node, Node parent) {
        PreNode preNode = checkNode(node);
        checkNode(parent);
        readLock();
        boolean res = preNode.parent == parent;
        readUnlock();
        return res;
    }

    public int getHeight() {
        readLock();
        int res = dhns.getTreeStructure().treeHeight - 1;
        readUnlock();
        return res;
    }

    public int getLevel(Node node) {
        PreNode preNode = checkNode(node);
        readLock();
        int res = preNode.level - 1;
        readUnlock();
        return res;
    }

    public boolean expand(Node node) {
        PreNode preNode = checkNode(node);
        if (preNode.size == 0 || !preNode.isEnabled()) {
            return false;
        }
        dhns.getStructureModifier().expand(node);
        return true;
    }

    public boolean retract(Node node) {
        PreNode preNode = checkNode(node);
        if (preNode.size == 0 || preNode.isEnabled()) {
            return false;
        }
        dhns.getStructureModifier().retract(node);
        return true;
    }

    public void moveToGroup(Node node, Node nodeGroup) {
        checkNode(node);
        checkNode(nodeGroup);
        if (isDescendant(node, nodeGroup)) {
            throw new IllegalArgumentException("nodeGroup can't be a descendant of node");
        }
        dhns.getStructureModifier().moveToGroup(node, nodeGroup);
    }

    public void removeFromGroup(Node node) {
        PreNode preNode = checkNode(node);
        if (preNode.parent.parent == null) {   //Equal root
            throw new IllegalArgumentException("Node parent can't be the root of the tree");
        }
        dhns.getStructureModifier().moveToGroup(node, preNode.parent.parent);
    }

    public Node groupNodes(Node[] nodes) {
        if (nodes == null || nodes.length == 0) {
            throw new IllegalArgumentException("nodes can't be null or empty");
        }
        PreNode parent = null;
        for (int i = 0; i < nodes.length; i++) {
            PreNode node = checkNode(nodes[i]);
            if (parent == null) {
                parent = node.parent;
            } else if (parent != node.parent) {
                throw new IllegalArgumentException("All nodes must have the same parent");
            }
        }
        Node group = dhns.getStructureModifier().group(nodes);
        return group;
    }

    public void ungroupNodes(Node nodeGroup) {
        PreNode preNode = checkNode(nodeGroup);
        if (preNode.size == 0) {
            throw new IllegalArgumentException("nodeGroup can't be empty");
        }

        dhns.getStructureModifier().ungroup(preNode);
    }

    public boolean isInView(Node node) {
        PreNode preNode = checkNode(node);
        readLock();
        boolean res = preNode.isEnabled();
        readUnlock();
        return res;
    }

    public void resetView() {
        dhns.getStructureModifier().resetView();
    }

    public void setVisible(Node node, boolean visible) {
        PreNode preNode = checkNode(node);
        writeLock();
        preNode.setVisible(visible);
        writeUnlock();
    }

    public void setVisible(Edge edge, boolean visible) {
        AbstractEdge absEdge = checkEdge(edge);
        writeLock();
        absEdge.setVisible(visible);
        writeUnlock();
    }

    public boolean isDirected() {
        return dhns.isDirected();
    }

    public boolean isUndirected() {
        return dhns.isUndirected();
    }

    public boolean isMixed() {
        return dhns.isMixed();
    }

    public boolean isClustered() {
        return getHeight() > 0;
    }
}
