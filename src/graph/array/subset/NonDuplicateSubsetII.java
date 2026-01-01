package graph.array.subset;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//https://leetcode.com/problems/subsets-ii/
public class NonDuplicateSubsetII {
    public static void main(String args[]) {
        int[] nums = {1, 2, 2};
        Arrays.sort(nums);
        List<List<Integer>> result = new ArrayList<>();
        nonDuplicate(nums, 0, new ArrayList<>(),result);
        List<List<Integer>> result1 = new ArrayList<>();
        nonDuplicate1(nums, 0, new ArrayList<>(),result1);
        System.out.println(result);
        System.out.println(result1);

    }

    public static void nonDuplicate1(int nums[], int index, List<Integer> temp, List<List<Integer>> result) {
            result.add(new ArrayList<>(temp));

        for (int i = index; i < nums.length; i++) {

            if( i>index && nums[i] == nums[i-1]) {
                continue;
            }

             temp.add(nums[i]);
             nonDuplicate1(nums,i+1, temp,result);

             temp.remove(temp.size()-1);
        }


    }















    public static void nonDuplicate(int arr[], int index, List<Integer> temp, List<List<Integer>> result){
        result.add(new ArrayList<>(temp));
        for (int i = index; i < arr.length ; i++) {
            if(i > index && arr[i] == arr[i-1]){
                continue;
            }
            temp.add(arr[i]);
            nonDuplicate(arr,i+1, temp, result);
            temp.remove(temp.size()-1);

        }


    }

}
