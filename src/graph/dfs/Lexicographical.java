package graph.dfs;

import java.util.ArrayList;
import java.util.List;

//https://leetcode.com/problems/lexicographical-numbers/
public class Lexicographical {
  public static List<Integer> lexicalOrder(int n) {
    List<Integer> res = new ArrayList<>();
    for(int i=1;i<10;++i){
      dfs(i, n, res);
    }
    return res;
  }

  public static void dfs(int value, int limit, List<Integer> res){
    if(value>limit)
      return;
    else{
      res.add(value);
      for(int i=0;i<10;++i){
        if(10*value+i>limit)
          return;
        dfs(10*value+i, limit, res);
      }
    }
  }

  public static void main(String args[]){
    int n=20;
    List<Integer> ans;
    ans=lexicalOrder(n);
    System.out.println(ans.size());
    System.out.println(ans);
  }

}
