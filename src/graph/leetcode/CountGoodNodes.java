package graph.leetcode;

//https://leetcode.com/problems/count-good-nodes-in-binary-tree/
public class CountGoodNodes {


    public static void main(String args[]){

        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(1);
        root.left.left = new TreeNode(3);
        root.right = new TreeNode(4);
        root.right.left = new TreeNode(1);
        root.right.right = new TreeNode(5);

        System.out.println(goodNodes(root,root.val));
    }



    public static int goodNodes(TreeNode root, int high) {
        if( root == null){
            return 0;
        }

        int val= root.val >= high ? 1 :0;

        val+= goodNodes(root.left,Math.max(root.val,high));
        val+= goodNodes(root.right,Math.max(root.val,high));
        return val;

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
