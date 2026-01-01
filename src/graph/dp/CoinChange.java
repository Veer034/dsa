package graph.dp;

import java.util.Arrays;
//minimum coin to make number
//https://leetcode.com/problems/coin-change/
public class CoinChange {


    public static void main(String at[]){
    int[] coins ={1,2,5};
    int amounts = 11;
        int[] dp = new int[amounts + 1];
        Arrays.fill(dp, amounts + 1);
        dp[0] = 0;
        for (int amount = 1; amount <= amounts; amount++) {
            for (int coin : coins) {
                if (coin <= amount) {
                    int valueWithoutCoin =  amount - coin;
                    dp[amount] = Math.min(dp[valueWithoutCoin] + 1, dp[amount]);
                }
            }
        }
        System.out.println((dp[amounts] > amounts) ? -1 : dp[amounts]);
    }

}
