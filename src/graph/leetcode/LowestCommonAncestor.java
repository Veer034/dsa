package graph.leetcode;


//https://leetcode.com/problems/lowest-common-ancestor-of-a-binary-tree/
public class LowestCommonAncestor {

  public static TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) return root;
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    return left == null ? right : right == null ? left : root;
  }
  
  
  public static void main(String a[]){

    TreeNode node = new TreeNode(1);
    node.left = new TreeNode(2);
    node.right = new TreeNode(3);
    node.left.left = new TreeNode(4);
    node.left.right = new TreeNode(5);
    node.right.left = new TreeNode(6);
    node.right.right = new TreeNode(7);
    System.out.println(lowestCommonAncestor(node,node.left.left,node.right.left).val);
  }
  public static class TreeNode {
     int val;
      TreeNode left;
      TreeNode right;
      TreeNode(int x) { val = x; }
  }
}