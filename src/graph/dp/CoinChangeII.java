package graph.dp;

import java.util.Arrays;
import java.util.stream.Collectors;

//number of ways to make the amount using coins
//https://leetcode.com/problems/coin-change-2/
public class CoinChangeII {


  public static void main(String ar[]){
    int [] coins= {1,2,5};
    int amount =5;
    System.out.println(change2(amount, coins));
  }


  public static int change2(int amount, int[] coins) {
    int[] dp = new int[amount + 1];
    dp[0] = 1;
    for (int coin : coins) {
      for (int indexAmount = coin; indexAmount <= amount; indexAmount++) {
        dp[indexAmount] += dp[indexAmount-coin];
      }
      for (int v: dp
           ) {
 System.out.print(v+"  ");
      }
      System.out.println();
    }
    return dp[amount];
  }
}




