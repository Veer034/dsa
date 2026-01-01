package graph.array;

//https://leetcode.com/problems/trapping-rain-water/
public class TrappingRainWater {

  public static void main(String args[]) {
    int A[] = {0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1};
    int leftMax = -1;
    int rightMax = -1;
    int leftIndex = 0;
    int rightIndex = A.length - 1;
    int result = 0;
    while (leftIndex <=rightIndex) {
      if (leftMax >= rightMax) {
        result += Math.max(0, rightMax - (A[rightIndex]));
        rightMax = Math.max(rightMax, A[rightIndex]);
        rightIndex--;
      } else {
        result += Math.max(0, leftMax - (A[leftIndex]));
        leftMax = Math.max(leftMax, A[leftIndex]);
        leftIndex++;
      }
    }
      System.out.println(result);

    int left[] = new int[A.length];
    int right[] = new int[A.length];
    left[0] = A[0];
    right[A.length-1]= A[A.length-1];
    for (int i = 1; i < A.length ; i++) {
      left[i]= Math.max(left[i-1],A[i]);
    }

    for (int i = A.length-2; i >=0 ; i--) {
      right[i]= Math.max(right[i+1],A[i]);
    }

    int data=0;
    for (int i = 0; i < A.length ; i++) {
      data+=Math.min(left[i],right[i])-A[i];
      System.out.println(data);
    }

  }





}
