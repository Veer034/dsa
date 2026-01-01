package graph.pnc;

import java.util.ArrayList;
import java.util.List;

//https://leetcode.com/problems/permutations-ii/
public class NonDuplicatePermutationsII {
    public static void main(String args[]){
        int arr[] = {1,1,2};
        List<List<Integer>> result = new ArrayList<>();
        allNonDuplicatePermutations(arr,new ArrayList<>(),result, new boolean[arr.length]);
        System.out.println(result);
    }

    private static void allNonDuplicatePermutations(int[] arr, List<Integer> objects, List<List<Integer>> result, boolean used[]) {

        if(objects.size() == arr.length){
            result.add(new ArrayList<>(objects));
        }else
        {
        for (int i = 0; i < arr.length ; i++) {
            if(used[i] || i > 0 && arr[i] == arr[i-1] && !used[i - 1]) continue;
            used[i] = true;
            objects.add(arr[i]);
            allNonDuplicatePermutations(arr, objects, result, used);
            used[i] = false;
            objects.remove(objects.size() - 1);

        }

        }


    }

}
