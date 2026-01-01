package graph.leetcode;

//https://leetcode.com/problems/vertical-order-traversal-of-a-binary-tree/

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

class TNode {

  int data;
  TNode left, right;

  TNode(int item) {
    data = item;
    left = right = null;
  }
}

class Values {

  int max, min;
}

public class BinaryTreeVerticalOrder {

  TNode root;
  Values val = new Values();

  // A utility function to find min and max distances with respect
  // to root.
  void findMinMax(TNode TNode, Values val, int hd) {
    // Base case
    if (TNode == null) {
      return;
    }

    // Update min and max
    if (hd < val.min) {
      val.min = hd;
    } else if (hd > val.max) {
      val.max = hd;
    }

    // Recur for left and right subtrees
    findMinMax(TNode.left, val, hd - 1);
    findMinMax(TNode.right, val, hd + 1);
  }

  // A utility function to print all TNodes on a given line_no.
  // hd is horizontal distance of current TNode with respect to root.
  void printVerticalLine(TNode TNode, int line_no, int hd,PriorityQueue<Order> queue, int height) {
    // Base case
    if (TNode == null) {
      return;
    }

    // If this TNode is on the given line number
    if (hd == line_no) {
      queue.add( new Order(TNode.data,height));
    }

    // Recur for left and right subtrees
    printVerticalLine(TNode.left, line_no, hd - 1,queue,height +1);
    printVerticalLine(TNode.right, line_no, hd + 1,queue,height+1);
  }

  // The main function that prints a given binary tree in
  // vertical order
  void verticalOrder(TNode TNode) {
    // Find min and max distances with resepect to root
    findMinMax(TNode, val, 0);

    List<List<Integer>> result = new ArrayList<>();


    // Iterate through all possible vertical lines starting
    // from the leftmost line and print TNodes line by line
    for (int line_no = val.min; line_no <= val.max; line_no++) {
      PriorityQueue<Order> priorityQueue = new PriorityQueue<>(new Comparator<Order>() {
        @Override
        public int compare(Order e1, Order e2) {
          if (e1.level - e2.level == 0) {
            return e1.val - e2.val;
          }
          return e1.level - e2.level;
        }
      });

      printVerticalLine(TNode, line_no, 0,priorityQueue,0);
      List<Integer> record = new ArrayList<>();
      int size = priorityQueue.size();
      for (int i = 0; i < size ; i++) {
        record.add(priorityQueue.poll().val);

      }
      result.add(record);
    }


    System.out.println(result);


  }

  // Driver program to test the above functions
  public static void main(String[] args) {
    BinaryTreeVerticalOrder tree = new BinaryTreeVerticalOrder();

    /* Let us construct the tree shown in above diagram */
/*    tree.root = new TNode(0);
    tree.root.left = new TNode(8);
    tree.root.right = new TNode(1);
    tree.root.right.left = new TNode(3);
    tree.root.right.right = new TNode(2);
    tree.root.right.left.right = new TNode(4);
    tree.root.right.right.left = new TNode(5);
    tree.root.right.left.right.right = new TNode(7);
    tree.root.right.left.right.left = new TNode(6);*/

    tree.root = new TNode(0);
    tree.root.left = new TNode(1);
    tree.root.right = new TNode(2);
    tree.root.left.right = new TNode(13);
    tree.root.right.left = new TNode(4);
    System.out.println("vertical order traversal is :");
    tree.verticalOrder(tree.root);
  }
}
class Order{
  int val,level;

  public Order(int val, int level) {
    this.val = val;
    this.level = level;
  }
}
