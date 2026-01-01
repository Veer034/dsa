package graph.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

//https://leetcode.com/problems/word-ladder/
public class StartToEndTransform {
   public static void main(String[] arr) {

    String start = "hit";
    String end = "cog";
     System.out.println(
             wayladderLength(start,end,new HashSet<>(
                     Arrays.asList("ait","oit","oij","ooj","oop","hog","pog","hot","dot","dog","lot","log","cog"))));
  }

  public static int wayladderLength(String beginWord, String endWord, Set<String> wordList) {
    Set<String> beginSet = new HashSet<String>(), endSet = new HashSet<String>();

    int len = 1;
    HashSet<String> visited = new HashSet<String>();

    beginSet.add(beginWord);
    endSet.add(endWord);
    while (!beginSet.isEmpty() && !endSet.isEmpty()) {
      if (beginSet.size() > endSet.size()) {
        Set<String> set = beginSet;
        beginSet = endSet;
        endSet = set;
      }

      Set<String> temp = new HashSet<String>();
      for (String word : beginSet) {
        char[] chs = word.toCharArray();

        for (int i = 0; i < chs.length; i++) {
          for (char c = 'a'; c <= 'z'; c++) {
            char old = chs[i];
            chs[i] = c;
            String target = String.valueOf(chs);

            if (endSet.contains(target)) {
              return len + 1;
            }

            if (!visited.contains(target) && wordList.contains(target)) {
              temp.add(target);
              visited.add(target);
            }
            chs[i] = old;
          }
        }
      }

      beginSet = temp;
      len++;
    }



    return 0;
  }

}

