package graph.array;

//https://leetcode.com/problems/best-time-to-buy-and-sell-stock-iii/
public class BestTimeBuySellStockIII {
  public static void main(String ar[]) {
    System.out.println(maxProfitDpCompactFinal(new int[]{3,3,5,0,0,3,1,4}));
    System.out.println(t20wayDP(new int[]{3,3,5,0,0,3,1,4}));

  }

  public static int maxProfitDpCompactFinal(int[] prices)  {
    int t1BuyPrice = prices[0];
    int t2BuyPrice = prices[0];
    int t1Profit = 0;
    int t2Profit = 0;

    for(int i = 1; i < prices.length; i++){

      t1Profit = Math.max(t1Profit, prices[i] - t1BuyPrice);
      t1BuyPrice = Math.min(t1BuyPrice, prices[i]);

      t2BuyPrice = Math.min(t2BuyPrice, prices[i] - t1Profit);
      t2Profit = Math.max(t2Profit, prices[i] -  t2BuyPrice);

    }
    return t2Profit;
  }

  public static int t20wayDP(int[] prices){

    int [][] dp = new int[2][prices.length];
    int min = prices[0];
    int max = prices[prices.length-1];
    int len =prices.length;
    for (int i = 1; i < len; i++) {

      min = Math.min(prices[i],min);
      dp[0][i] = Math.max( prices[i]-min,dp[0][i-1]);
      max = Math.max(prices[len-i],max);
      dp[1][len-i-1] = Math.max(max- prices[len-i-1],dp[1][len-i]);
    }

    int profit =dp[1][0];
    for (int i = 1; i < dp[0].length ; i++) {
      profit = Math.max(profit,dp[0][i-1]+dp[1][i]);
    }
    return profit;
  }
}
