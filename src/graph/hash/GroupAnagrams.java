package graph.hash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//https://leetcode.com/problems/group-anagrams/
public class GroupAnagrams {

  public static void main(String ar[]){
    String strs[] = new String[]{"eat", "tea", "tan", "ate", "nat", "bat"};
    if (strs == null || strs.length == 0)
      System.out.println("void");
    Map<String, List<String>> map = new HashMap<>();
    for (String s : strs) {
      char[] ca = s.toCharArray();
      Arrays.sort(ca);
      String keyStr = String.valueOf(ca);
      if (!map.containsKey(keyStr)) map.put(keyStr, new ArrayList<>());
      map.get(keyStr).add(s);
    }
    System.out.println(map.values().stream().collect(Collectors.toList()));

    System.out.println(groupAnagrams(strs));
  }

  public static List<List<String>> groupAnagrams(String[] strs) {

    Map<String, List<String>> map = new HashMap<>();
    for(String w : strs){
      String key = hash(w);
      if(!map.containsKey(key)) map.put(key, new LinkedList<>());
      map.get(key).add(w);
    }

    return new ArrayList<>(map.values());
  }

  static String hash(String s){
    int[] a = new int[26];
    for(char c : s.toCharArray()) a[c-'a']++;
    return Arrays.toString(a);
  }
}
