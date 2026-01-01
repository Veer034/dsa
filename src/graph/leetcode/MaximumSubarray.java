package graph.leetcode;

import java.util.*;

//https://leetcode.com/problems/maximum-subarray/
public class MaximumSubarray {

  public static void main(String args[]) {

    // int[] nums = {-2, 1, -3, 4, -1, 2, 1, -5, 4};
    int[] nums = {-2, 1, -3, 4, -1, 2, 1, -5, 4};

    int mid = 0;
    int sum = Integer.MIN_VALUE;
    for (int index = 0; index < nums.length; index++) {
      mid += nums[index];
      if (nums[index] > mid) {
        mid = nums[index];
      }

      if (sum < mid) {
        sum = mid;
      }
    }

    System.out.println(sum);

  }
}