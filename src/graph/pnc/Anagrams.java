package graph.pnc;

import java.util.ArrayList;
import java.util.*;
//https://leetcode.com/problems/find-all-anagrams-in-a-string/
public class Anagrams {

  public static void main(String[] args) {
    System.out.println(findAnagramUsingArray("fabcd",
            "abc"));
    System.out.println(findAnagrams("fabcd",
        "abc"));
  }

  public static List<Integer> findAnagrams(String data, String search) {
    if(data.length() < search.length()) {
      return new ArrayList();
    }
    List<Integer> resultList = new LinkedList<>();
    char pa[] = search.toCharArray();
    Arrays.sort(pa);
    for(int index = 0; index < data.length() -search.length() + 1 ; index++) {
      // convert input string to char array
      String subData =data.substring(index,index + search.length());
      char tempArray[] = subData.toCharArray();
      Arrays.sort(tempArray);
      if(Arrays.equals(pa,tempArray)) {
        resultList.add(index);
      }
    }
    return resultList;
  }


  public static List<Integer> findAnagramUsingArray(String s, String p) {
    List<Integer> result = new ArrayList();

    if(s.length() < p.length()){
      return result;
    }


    int arr[] = new int[26];
    for(int index =0; index < p.length(); index++){
      arr[p.charAt(index)-'a']+=1;
    }

    int data[] = new int[26];
    for (int i =0; i < p.length()-1;i++){
      data[s.charAt(i)-'a']+=1;
    }

    for( int j = p.length()-1; j < s.length() ; j++){

      data[s.charAt(j)-'a']+=1;
      boolean flag =true;
      for (int range =0 ; range < 26 ; range++){
        if(arr[range]!= data[range]){
          flag = false;
        }
      }
      if(flag){
        result.add(j-(p.length()-1));
      }
      data[s.charAt(j-(p.length()-1))-'a']-=1;
    }
    return result;
  }
}
