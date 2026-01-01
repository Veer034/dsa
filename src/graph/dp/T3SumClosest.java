package graph.dp;

import java.util.Arrays;
//https://leetcode.com/problems/3sum-closest/
public class T3SumClosest {

  public static void main(String args[]){

    int num[] ={-1,2,1,-4};
    int target =1;
    int result = num[0] + num[1] + num[num.length - 1];
    Arrays.sort(num);
    for (int i = 0; i < num.length - 2; i++) {
      int start = i + 1, end = num.length - 1;
      while (start < end) {
        int sum = num[i] + num[start] + num[end];
        if (sum > target) {
          end--;
        } else {
          start++;
        }
        if (Math.abs(sum - target) < Math.abs(result - target)) {
          result = sum;
        }
      }
    }
    System.out.println(result);
  }
}
