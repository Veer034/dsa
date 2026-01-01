package graph.pnc;

import java.util.ArrayList;
import java.util.List;

//https://leetcode.com/problems/permutations/
public class Permutations {
    public static void main(String args[]){
        int arr[] = {1,2,3};
        List<List<Integer>> result = new ArrayList<>();
        allPermutations(arr,new ArrayList<>(),result);
        System.out.println(result);
    }
    
    public static  void allPermutations(int arr[], List<Integer> temp, List<List<Integer>> result){

        if(temp.size() == arr.length)
            result.add(new ArrayList<>(temp));
        else
            for (int i = 0; i < arr.length; i++) {
                if(temp.contains(arr[i]))
                    continue;
                temp.add(arr[i]);
                allPermutations(arr, temp, result);
                temp.remove(temp.size() - 1);
            }
    }

}
