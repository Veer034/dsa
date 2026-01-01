package graph.sequence;

import java.util.Arrays;

//https://leetcode.com/problems/longest-increasing-subsequence/
public class LongestIncreasingSubsequence {

    public static void main(String ar[]) {
        int arr[] = { 10, 22, 9, 33, 21, 50, 41, 60 };
        int n = arr.length;
        System.out.println("Length of lis is " + lis(arr, n) + "\n");
    }

    //https://www.youtube.com/watch?v=CE2b_-XfVDk&ab_channel=Pepcoding
    static int lis(int arr[], int n)
    {
        int []len = new int[arr.length];

        Arrays.fill(len, 1);

        int result =1;
        for(int i = 0; i < arr.length; i++) {
            for( int j =0; j < i; j++){
                if(arr[i] > arr [j] && len[i] < len[j]+1){
                    len[i] = len[j]+1;
                    if(len[i] > result){
                        result = len[i];
                    }
                }
            }
        }
        return result;
    }

//https://www.youtube.com/watch?v=S9oUiVYEq7E&ab_channel=TusharRoy-CodingMadeSimple
    static int LongestIncreasingSubsequenceLength(int v[])
    {
        if (v.length == 0) // boundary case
            return 0;

        int[] tail = new int[v.length];
        int length = 1; // always points empty slot in tail
        tail[0] = v[0];

        for (int i = 1; i < v.length; i++) {

            if (v[i] > tail[length - 1]) {
                // v[i] extends the largest subsequence
                tail[length++] = v[i];
            }
            else {
                // v[i] will extend a subsequence and
                // discard older subsequence

                // find the largest value just smaller than
                // v[i] in tail

                // to find that value do binary search for
                // the v[i] in the range from begin to 0 +
                // length
                int idx = Arrays.binarySearch(
                        tail, 0, length - 1, v[i]);

                // binarySearch in java returns negative
                // value if searched element is not found in
                // array

                // this negative value stores the
                // appropriate place where the element is
                // supposed to be stored
                if (idx < 0)
                    idx = -1 * idx - 1;

                // replacing the existing subsequence with
                // new end value
                tail[idx] = v[i];
            }
        }
        return length;
    }

}
