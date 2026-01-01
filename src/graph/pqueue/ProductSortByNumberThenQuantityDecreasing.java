package graph.pqueue;


import java.util.*;

//https://leetcode.com/discuss/interview-question/541160/twilio-oa-2020
public class ProductSortByNumberThenQuantityDecreasing {
    public static void main(String[] args) {
        int[] nums1 = {4,5,6,5,4,3};
        int[] nums2 = {8,5,5,5,5,1,1,1,4,4};
        System.out.println(Arrays.toString(solve(nums1)));
        System.out.println(Arrays.toString(solve(nums2)));
    }

    private static int[] solve(int[] nums) {
        Map<Integer, Integer> map = new HashMap<>();
        for(int n : nums)
            map.put(n, map.getOrDefault(n, 0) + 1);
        Queue<Map.Entry<Integer, Integer>> minHeap = new PriorityQueue<>((a, b)-> a.getValue() == b.getValue() ? a.getKey() - b.getKey() : a.getValue() - b.getValue());
        minHeap.addAll(map.entrySet());
        int cur = 0;
        while(!minHeap.isEmpty()) {
            Map.Entry<Integer, Integer> e = minHeap.poll();
            for(int i=0;i<e.getValue();i++) {
                nums[cur++] = e.getKey();
            }
        }
        return nums;
    }
}
