package graph.tree;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

//https://leetcode.com/problems/binary-tree-level-order-traversal/
//https://leetcode.com/problems/maximum-level-sum-of-a-binary-tree/
public class mWayTreeHorizontal {


  public static void main(String args[]) {


    Node master = new Node(1, null);
    List<Node> nodes1 = new LinkedList<>();
    Node node1 = new Node(1, null);
    Node node2 = new Node(2, null);
    Node node3 = new Node(3, null);
    nodes1.add(node1);
    nodes1.add(node2);
    nodes1.add(node3);
    master.setNodes(nodes1);

    nodes1 = new LinkedList<>();

    Node node4 = new Node(4, null);
    Node node5 = new Node(5, null);
    Node node6 = new Node(6, null);
    nodes1.add(node4);
    nodes1.add(node5);
    nodes1.add(node6);
    node1.setNodes(nodes1);

    nodes1 = new LinkedList<>();

    Node node7 = new Node(7, null);
    nodes1.add(node7);
    node2.setNodes(nodes1);


    Node node11 = new Node(11, null);
    nodes1 = new LinkedList<>();
    nodes1.add(node11);

    node7.setNodes(nodes1);


    Node node8 = new Node(8, null);
    Node node9 = new Node(9, null);
    Node node10 = new Node(10, null);

    nodes1 = new LinkedList<>();
    nodes1.add(node8);
    nodes1.add(node9);
    nodes1.add(node10);
    node3.setNodes(nodes1);

    test(master);
  }

  public static void test(Node master) {


    int max = 0;
    List<Node> tempList = new LinkedList();
    tempList.add(master);

    while (true) {
      int localMax = 0;
      List<Node> tempList1 = new LinkedList();

      for (Node node : tempList) {
        localMax += node.data;
        if (node.getNodes() != null)
          tempList1.addAll(node.getNodes());

      }
      tempList.clear();
      tempList.addAll(tempList1);
      System.out.println(localMax);
      if (localMax > max) {
        max = localMax;
      }

      if (tempList.isEmpty()) {
        break;
      }


    }
    System.out.println(max);
  }

}

 class Node {
  int data;
  List<Node> nodes;

  public Node(int data, List<Node> nodes) {
    this.data = data;
    this.nodes = nodes;
  }

  public int getData() {
    return data;
  }

  public void setData(int data) {
    this.data = data;
  }

  public List<Node> getNodes() {
    return nodes;
  }

  public void setNodes(List<Node> nodes) {
    this.nodes = nodes;
  }

}
