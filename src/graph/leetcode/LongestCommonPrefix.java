package graph.leetcode;

//https://leetcode.com/problems/longest-common-prefix/
public class LongestCommonPrefix {

  public static void main(String ar[]){
    String[] strs = {"flower","flow","floght"};
    if (strs.length == 0)
      System.out.println("do data");
    String prefix = strs[0];
    for (int i = 1; i < strs.length; i++)
      while (strs[i].indexOf(prefix) != 0) {
        prefix = prefix.substring(0, prefix.length() - 1);
        if (prefix.isEmpty())
          System.out.println("empty");
      }
    System.out.println(prefix);
  }
}
