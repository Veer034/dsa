package graph.pnc;

import java.util.ArrayList;
import java.util.List;

//https://leetcode.com/problems/combination-sum-iii/
public class CombinationIII {

  public static void main(String args[]){

   int size =3,  target=9;
    List<List<Integer>> list = new ArrayList<>();
    backtrack(list, new ArrayList<>(), size, target, 1);
    System.out.println(list);
  }

  private static void backtrack(List<List<Integer>> list, List<Integer> tempList, int k, int target,
      int index) {
    if(tempList.size() > k) return; /** no solution */
    else if(tempList.size() == k && target == 0) list.add(new ArrayList<>(tempList));
    else{
      for (int i = index; i <= 9; i++) {
        tempList.add(i);
        backtrack(list, tempList, k, target-i, i+1);
        tempList.remove(tempList.size() - 1);
      }
    }
  }

}
