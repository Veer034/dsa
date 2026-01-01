package graph.trie;
//https://leetcode.com/problems/word-search/
public class WordSearch {

  public static void main(String arg[]){
    char[][] board = {{'A','B','C','E'},{'S','F','C','S'},{'A','D','E','E'}};

    String word = "ABCCED";
   boolean flag = new WordSearch().exist(board,word);
  System.out.println(flag);
  }

  public boolean exist(char[][] board, String word) {
    for( int i=0; i<board.length; i++) {
      for(int j =0; j<board[0].length; j++){
        if(test(board,word,i,j,0)) {
          return true;
        }
      }
    }
    return false;

  }

  public boolean test(char[][] board, String word,int i, int j, int index){
    if (index == word.length()) {
      return true;
    }
    if(i< 0 || j < 0 || i >= board.length || j >= board[0].length || board[i][j]=='-'
        ||  index >= word.length()) {
      return false;
    }



    if(board[i][j] != word.charAt(index)) {
      return false;
    }

    char temp = board[i][j];
    board[i][j] = '-';

    boolean val = test(board,word,i+1,j,index+1) ||
        test(board,word,i,j+1,index+1) ||
        test(board,word,i-1,j,index+1) ||
        test(board,word,i,j-1,index+1);
    board[i][j] = temp;

    return val;

  }
}