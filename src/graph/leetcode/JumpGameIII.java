package graph.leetcode;

//https://leetcode.com/problems/jump-game-iii/
public class JumpGameIII {

    public static void main(String args[]) {
        int arr[] = new int[]{4,2,3,0,3,1,2};
        System.out.println(test(arr, 5));
    }

    public static boolean test(int arr[], int start){


        if(start < 0 || start >= arr.length || arr[start] <0) {
            return false;
        }

        if (arr[start] == 0){
            return true;
        }

        int val = arr[start];
        arr[start] =-arr[start];


        boolean flag1 = test(arr,start- val);
        boolean flag2 = test(arr, start+ val);

        return flag1 || flag2;



    }

}
