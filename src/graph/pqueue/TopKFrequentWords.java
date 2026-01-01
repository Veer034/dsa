package graph.pqueue;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
//https://leetcode.com/problems/top-k-frequent-words/
public class TopKFrequentWords {

  public static void main(String args[]) {
    System.out.println(topKFrequent(new String[]{"i", "coding", "coding","love", "leetcode", "i", "love"}, 2));
  }
  public static List<String> topKFrequent(String[] words, int k) {

    List<String> result = new LinkedList<>();
    Map<String, Integer> map = new HashMap<>();

    for(int i=0; i<words.length; i++)
    {
      if(map.containsKey(words[i]))
        map.put(words[i], map.get(words[i])+1);
      else
        map.put(words[i], 1);
    }

    PriorityQueue<Entry<String, Integer>> pq = new PriorityQueue<>(
        (a,b) -> a.getValue()==b.getValue() ? b.getKey().compareTo(a.getKey()) : a.getValue()-b.getValue()
    );

    for(Map.Entry<String, Integer> entry: map.entrySet())
    {
      pq.offer(entry);
      if(pq.size()>k)
        pq.poll();
    }

    while(!pq.isEmpty())
      result.add(0, pq.poll().getKey());

    return result;
  }
}
