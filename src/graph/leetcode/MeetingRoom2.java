package graph.leetcode;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

//https://leetcode.com/problems/meeting-rooms-ii/
public class MeetingRoom2 {

  public static void main(String aa[]){

    int intervals[][] = new int[][]{{0, 30},{5, 10},{15, 20}};
      int[] starts = new int[intervals.length];
      int[] ends = new int[intervals.length];
      for(int i=0; i<intervals.length; i++) {
        starts[i] = intervals[i][0];
        ends[i] = intervals[i][1];
      }
      Arrays.sort(starts);
      Arrays.sort(ends);
      int rooms = 0;
      int endsItr = 0;
      for(int i=0; i<starts.length; i++) {
        if(starts[i]<ends[endsItr])
          rooms++;
        else
          endsItr++;
      }
      System.out.println(rooms);
      System.out.println(minMeetingRooms(intervals));
      System.out.println(minMeetingRooms1(intervals));

  }
  // This method use tree sorting to order the start and end of the meeting, set value 1:
  // representing start of meeting -1 represent end. when we iterate though the list, we just need
  // to find the the maximum 1 together.
  public static int minMeetingRooms(int [][] intervals) {
    Map<Integer, Integer> map = new TreeMap<>();
    for (int i = 0; i <intervals.length ; i++) {

      map.put(intervals[i][0], map.getOrDefault(intervals[i][0], 0) + 1);
      map.put(intervals[i][1], map.getOrDefault(intervals[i][1], 0) - 1);

    }
    int room = 0, k = 0;
    for (int v : map.values())
      k = Math.max(k, room += v);

    return k;
  }
  
  public static int minMeetingRooms1(int[][] intervals) {
	    Arrays.sort(intervals, Comparator.comparing((int[] itv) -> itv[0]));
	 
	    PriorityQueue<Integer> heap = new PriorityQueue<>();
	    int count = 0;
	    for (int[] itv : intervals) {
	        if (heap.isEmpty()) {
	            count++;
	            heap.offer(itv[1]);
	        } else {
	            if (itv[0] >= heap.peek()) {
	                heap.poll();
	            } else {
	                count++;
	            }
	 
	            heap.offer(itv[1]);
	        }
	    }
	 
	    return count;
	}
}
