package graph.greedy;

import java.util.*;

//https://leetcode.com/problems/minimum-deletions-to-make-character-frequencies-unique/
public class MinimumDeletion {
    public static void main(String args[]) {
        String str = "ceeeaabccb";

        int arr[] = new int[26];
        Set<Integer> hashSet = new HashSet<>();

        for (int i = 0; i < str.length() ; i++) {
            arr[str.charAt(i)-'a']++;
        }
        int res=0;
        for (int i = 0; i < 26; i++) {

            while (arr[i] > 0 && !hashSet.add(arr[i])){
                arr[i]--;
                res++;

            }

        }

        System.out.println(res);


    }
}
