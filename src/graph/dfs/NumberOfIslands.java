package graph.dfs;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

//https://leetcode.com/problems/number-of-islands/
public class NumberOfIslands {
  public static void main(String ar[]) {

    NumberOfIslands  numberOfIslands = new NumberOfIslands();
    char[][] grid = {
        {'1', '1', '0', '0', '0'},
        {'1', '1', '0', '0', '0'},
        {'0', '0', '1', '0', '0'},
        {'0', '0', '0', '1', '1'}
    };
    int nr = grid.length;
    int nc = grid[0].length;
    int num_islands = 0;
    for (int r = 0; r < nr; ++r) {
      for (int c = 0; c < nc; ++c) {
        if (grid[r][c] == '1') {
          ++num_islands;
          numberOfIslands.numIslands(grid, r, c);
        }
      }
    }
    System.out.println(num_islands);
  }

  public void numIslands(char[][] grid,int x, int y) {

    if ( x <0 || x >= grid.length|| y < 0 || y>= grid[0].length || grid[x][y] == '0')
    {
      return ;
    }
    grid[x][y] ='0';
    numIslands(grid,x-1,y);
    numIslands(grid,x,y-1);
    numIslands(grid,x+1,y);
    numIslands(grid,x,y+1);
  }
}
