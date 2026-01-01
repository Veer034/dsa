package graph.leetcode;

import java.util.HashSet;
//https://leetcode.com/problems/word-break/
public class WordBreak {

  public static void main(String[] a) {

    String s ="aaaaaaa";

    HashSet<String> dict = new HashSet<>();
    dict.add("aaaa");
    dict.add("aaa");

    // dp[i] represents whether s[0...i] can be formed by dict
    boolean[] f = new boolean[s.length() + 1];

    f[0] = true;
    for(int i=1; i <= s.length(); i++){
      for(int j=0; j < i; j++){
        if(f[j] && dict.contains(s.substring(j, i))){
          f[i] = true;
          break;
        }
      }
    }

    System.out.println(f[s.length()]);
  }
}
