package graph.dp;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

//https://leetcode.com/problems/target-sum/
public class TargetSum {


  public static void main(String ar[]) {
    int arr[] = {1,2,3,4,5};
    int target = 3;
    HashMap<String,Integer>  map = new HashMap<>();
    System.out.println(sum(arr,target,0,0,map));
  }

  public static int sum(int arr[], int target, int initValue, int index, Map<String,Integer> map) {
    String key = index+":"+initValue;

    if (map.containsKey(key)){
      return map.get(key);
    }

    if(target == initValue && index == arr.length  ) {
      return 1;
    }
    if (index > arr.length - 1  )
      return 0;

    int count = sum(arr,target ,initValue+ arr[index],index+1,map)
            + sum(arr,target,initValue - arr[index],index+1,map);
    map.put(key,count);
    return count;
  }

}
