package graph.pnc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//https://leetcode.com/problems/combination-sum/
public class CombinationI {

  public static void main(String args[]){
      int[] candidates=new int[]{2,3,6,7};
      int target= 7;
    List<List<Integer>> list = new ArrayList<>();
    Arrays.sort(candidates);
    backtrack1(list, new ArrayList<>(), candidates, target, 0);
    System.out.println(list);

  }

  private static void backtrack(List<List<Integer>> list, List<Integer> tempList, int[] cand,
      int remain, int start) {
    if (remain < 0) return; /** no solution */
    else if (remain == 0) list.add(new ArrayList<>(tempList));
    else{
      for (int i = start; i < cand.length; i++) {
        tempList.add(cand[i]);
        backtrack(list, tempList, cand, remain-cand[i], i);
        tempList.remove(tempList.size()-1);
      }
    }

  }



  private static void backtrack1(List<List<Integer>> list, List<Integer> tempList, int[] cand, int rem, int index) {
    if (rem < 0 ){
      return;
    }else if(rem ==0  ){
      list.add(new ArrayList<>(tempList));
    }else{

      for (int i = index; i < cand.length; i++) {
        tempList.add(cand[i]);
        backtrack1(list, tempList, cand, rem-cand[i], i);
        tempList.remove(tempList.size()-1);
      }


    }


  }





}
