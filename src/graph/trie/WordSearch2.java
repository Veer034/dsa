package graph.trie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

//https://leetcode.com/problems/word-search-ii/
class WordSearch2 {

  public static void main(String ar[]){

   char[][] board = {{'o','a','a','n'},{'e','t','a','e'},{'i','h','k','r'},{'i','f','l','v'}};
   String [] words = {"oath","pea","eat","rain"};
//
//    char[][] board = {{'a'}};
 //   String [] words = {"a"};

    Trie trie =  createTree(words);
    List<String> result = new ArrayList<>();

    for (int i = 0; i < board.length ; i++) {
      for (int j = 0; j < board[0].length; j++) {
        getValue(board,i,j,trie,result);
      }
    }
    System.out.println(result);
  }


  private  static void getValue(char[][] board, int i, int j ,Trie p,  List<String> res){
      char c = board[i][j];
      if (c == '#' || p.next[c - 'a'] == null) return;
      p = p.next[c - 'a'];
      if (p.value != null) {   // found one
        res.add(p.value);
        p.value = null;     // de-duplicate
      }

      board[i][j] = '#';
      if (i > 0) getValue(board, i - 1, j ,p, res);
      if (j > 0) getValue(board, i, j - 1, p, res);
      if (i < board.length - 1) getValue(board, i + 1, j, p, res);
      if (j < board[0].length - 1) getValue(board, i, j + 1, p, res);
      board[i][j] = c;


  }

  private static Trie createTree(String word[]){

    Trie base = new Trie();
    Trie start = base;

    for (int i = 0; i< word.length; i++) {

      int count =0;
      for (char ch : word[i].toCharArray()) {
        base.next[ch -'a']  = new Trie();
        base =  base.next[ch-'a'];
        if(count == word[i].length()-1){
          base.value=word[i];
        }
        count++;
      }
      base = start;

    }
    return start;
  }


}


class Trie {
  String value;
  Trie next[] = new Trie[26];

}

