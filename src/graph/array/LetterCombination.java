package graph.array;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

//https://leetcode.com/problems/letter-combinations-of-a-phone-number/
public class LetterCombination {

  public static void main(String args[]){
    System.out.println(letterCombinations("234"));
  }
  public static List<String> letterCombinations(String digits) {

    LinkedList<String> ans = new LinkedList<String>();
    if(digits.isEmpty()) return ans;
    String[] mapping =  {"0", "1", "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};
    ans.add("");

    while(ans.peek().length()!=digits.length()){
      String remove = ans.remove();
      String map = mapping[digits.charAt(remove.length())-'0'];
      for(char c: map.toCharArray()){
        ans.add(remove+c);
      }
    }
    return ans;
  }
}
