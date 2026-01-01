package graph.array;

//https://leetcode.com/problems/container-with-most-water/
public class ContainerWithMostWater {

  public static void main(String ar[]) {
    int [] height = {1,8,6,2,5,4,8,3,7};
    int maxarea = 0, l = 0, r = height.length - 1;
    while (l < r) {
      maxarea = Math.max(maxarea, Math.min(height[l], height[r]) * (r - l));
      if (height[l] < height[r])
        l++;
      else
        r--;
    }
    System.out.println(maxarea);
  }
}
