package graph.pnc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

//https://leetcode.com/problems/combination-sum-ii/
public class CombinationII {


  public static void main(String args[]){
    int[] candidates={10,1,2,7,6,1,5};

 int target =8;
    List<List<Integer>> list = new LinkedList<List<Integer>>();
    Arrays.sort(candidates);
    backtrack1(list, new ArrayList<>(), candidates, target, 0);
    System.out.println(list);
  }

  private static void backtrack(List<List<Integer>> list, List<Integer> tempList, int[] cand,
      int remain, int start) {

    if(remain < 0) return; /** no solution */
    else if(remain == 0) list.add(new ArrayList<>(tempList));
    else{
      for (int i = start; i < cand.length; i++) {
        if(i > start && cand[i] == cand[i-1]) continue; /** skip duplicates */
        tempList.add(cand[i]);
        backtrack(list, tempList, cand, remain - cand[i], i+1);
        tempList.remove(tempList.size() - 1);
      }
    }
  }


    private static void backtrack1(List<List<Integer>> list, List<Integer> tempList, int[] cand,
                                  int remain, int start){

      if(remain < 0){
          return;
      } else if ( remain == 0){
          list.add(new ArrayList<>(tempList));
      } else {

          for (int i = start; i < cand.length; i++) {
              if( i > start && cand[i]==cand[i-1])
                  continue;
              tempList.add(cand[i]);
              backtrack(list, tempList, cand, remain-cand[i], i+1);
              tempList.remove(tempList.size()-1);

          }
      }





    }
}
