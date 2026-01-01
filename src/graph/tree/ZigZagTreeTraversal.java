package graph.tree;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

//https://leetcode.com/problems/binary-tree-zigzag-level-order-traversal/
public class ZigZagTreeTraversal {

    public static void main(String ar[]) {


        TreeNode root = new TreeNode(3);

        root.left = new TreeNode(9);    root.right = new TreeNode(20);

        root.left.left = new TreeNode(5); root.left.right = new TreeNode(8);
        root.right.left = new TreeNode(15); root.right.right = new TreeNode(7);

        List<List<Integer>> sol = new ArrayList<>();
        travel(root, sol, 0);
        System.out.println(sol);
    }

    public static void travel(TreeNode curr, List<List<Integer>> sol, int level)
    {
        if(curr == null) return;

        if(sol.size() <= level)
        {
            List<Integer> newLevel = new LinkedList<>();
            sol.add(newLevel);
        }


        List<Integer> collection  = sol.get(level);
        if(level % 2 == 0) collection.add(curr.val);
        else collection.add(0, curr.val);

        travel(curr.left, sol, level + 1);
        travel(curr.right, sol, level + 1);
    }
    public static class TreeNode {
      int val;
      TreeNode left;
      TreeNode right;
      
      TreeNode(int val) { this.val = val; }
  }
}
