package graph.tree;

import java.util.HashMap;
//https://leetcode.com/problems/construct-binary-tree-from-inorder-and-postorder-traversal/
public class PostOrderInOrder {

  public static void main(String args[]){
    int inorder[] = {9,3,15,20,7};
    int postorder[] = {9,15,7,20,3};
    printInorder(buildTreePostIn(inorder,postorder));

  }

  static void printInorder(TreeNode node)
  {
    if (node == null){
      System.out.print("null");
    return;
    }
    printInorder(node.left);
    System.out.print(" "+node.val + " ");
    printInorder(node.right);
  }








  public static TreeNode buildTreePostIn(int[] inorder, int[] postorder) {
    if (inorder == null || postorder == null || inorder.length != postorder.length)
      return null;
    HashMap<Integer, Integer> hm = new HashMap<Integer,Integer>();
    for (int i=0;i<inorder.length;++i)
      hm.put(inorder[i], i);
    return buildTreePostIn(inorder, 0, inorder.length-1, postorder, 0,
        postorder.length-1,hm);
  }

  private static TreeNode buildTreePostIn(int[] inorder, int is, int ie, int[] postorder, int ps, int pe,
      HashMap<Integer,Integer> hm){
    if (ps>pe || is>ie) return null;
    TreeNode root = new TreeNode(postorder[pe]);
    int ri = hm.get(postorder[pe]);
    TreeNode leftchild = buildTreePostIn(inorder, is, ri-1, postorder, ps, ps+ri-is-1, hm);
    TreeNode rightchild = buildTreePostIn(inorder,ri+1, ie, postorder, ps+ri-is, pe-1, hm);
    root.left = leftchild;
    root.right = rightchild;
    return root;
  }
  static class TreeNode {
      int val;
      TreeNode left;
      TreeNode right;
      TreeNode() {}
      TreeNode(int val) { this.val = val; }
      TreeNode(int val, TreeNode left, TreeNode right) {
          this.val = val;
          this.left = left;
          this.right = right;
      }
  }
}
