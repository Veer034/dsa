package graph.array.subset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//https://leetcode.com/problems/subsets/
public class AllPossibleSubset {

    public static void main(String args[]){
        int []nums = {1,2,3};
        List<List<Integer>> list = new ArrayList<>();
       // Arrays.sort(nums);
        backtrack(list, new ArrayList<>(), nums, 0);
        List<List<Integer>> list1 = new ArrayList<>();
        backtrack1(list1, new ArrayList<>(), nums, 0);
        System.out.println(list);
        System.out.println(list1);
    }


    private static  void backtrack1(List<List<Integer>> result, List<Integer> temp, int arr[], int init) {
            result.add(new ArrayList<>(temp));
        for (int i = init; i < arr.length; i++) {
                temp.add(arr[i]);
                backtrack1(result,temp,arr,i+1);
                temp.remove(temp.size()-1);
        }

    }


















    private static void backtrack(List<List<Integer>> list , List<Integer> tempList, int [] nums, int start){
        list.add(new ArrayList<>(tempList));
        for(int i = start; i < nums.length; i++){
            tempList.add(nums[i]);
            backtrack(list, tempList, nums, i + 1);
            tempList.remove(tempList.size() - 1);
        }
    }
}
