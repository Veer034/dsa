package graph.array;

//https://leetcode.com/problems/best-time-to-buy-and-sell-stock-ii/
//Solving using sensex stock up/low graph. pick smallest
// continuously going down to the last going up
public class BestTimeBuySellStockII {

    public static void main(String ar[]) {
        System.out.println(countProfit(new int[]{7,1,5,3,6,2,5}));
    }


public static int countProfit(int []prices) {
    int maxprofit = 0;
    for (int i = 1; i < prices.length; i++) {
        if (prices[i] > prices[i - 1])
            maxprofit += prices[i] - prices[i - 1];
    }
    return maxprofit;
}


}
