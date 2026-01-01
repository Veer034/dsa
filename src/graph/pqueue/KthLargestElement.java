package graph.pqueue;

import java.util.PriorityQueue;

//https://leetcode.com/problems/kth-largest-element-in-an-array/
public class KthLargestElement {

  public static void main(String ar[]) {



    int []nums = {3,2,1,5,6,4};
    int k = 2;
    // init heap 'the smallest element first'
    PriorityQueue<Integer> heap =
            new PriorityQueue<>((n1, n2) -> n1 - n2);

    // keep k largest elements in the heap
    for (int n: nums) {
      heap.add(n);

    }
    int size = heap.size();
    for (int i = 0; i <(size-k) ; i++) {
      heap.poll();
    }

    // output
   System.out.println(heap.poll());

    final PriorityQueue<Integer> pq = new PriorityQueue<>();
    for (int num: nums) {
      pq.offer(num);
      if (pq.size() > k) {
        pq.poll();
      }
    }

    System.out.println(pq.peek());
  }
}
