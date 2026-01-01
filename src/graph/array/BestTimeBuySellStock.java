package graph.array;

import java.util.Arrays;
import java.util.List;

//https://leetcode.com/problems/best-time-to-buy-and-sell-stock/
public class BestTimeBuySellStock {
  public static void main(String ar[]) {
    System.out.println(maxProfit(new int[]{7, 1, 5, 9, 3, 6, 4}));
    System.out.println(maxProfit1(new int[]{7, 1, 5, 9, 3, 6, 4}));

  }

  //      7, 1, 5, 9, 3, 6, 4
  //diff   -6  4  4 -6  3 -2
  public static int maxProfit1(int[] prices) {
    int maxCur = 0, maxSoFar = 0;
    for (int i = 1; i < prices.length; i++) {
      // calculate the difference between 2 concurrent and then assign on currentMax
      maxCur = Math.max(0, maxCur + (prices[i] - prices[i - 1]));
      maxSoFar = Math.max(maxCur, maxSoFar);
    }
    return maxSoFar;
  }


  public static int maxProfit(int[] nums) {
    int max = 0;
    int min = Integer.MAX_VALUE;

    for (int index = 0; index < nums.length; index++) {

      if (nums[index] < min) {
        min = nums[index];
      }

      if (max < nums[index] - min) {
        max = nums[index] - min;
      }
    }
    return max;
  }
}