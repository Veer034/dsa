package graph.search;

//https://www.geeksforgeeks.org/search-an-element-in-a-sorted-and-pivoted-array/
public class BinarySearch {

	public static void main(String args[]){
		
	/*System.out.println(search(new int[]{5,6,7,8,9,10,1
			,2,3},3));
*/
		System.out.println(search(new int[]{1,2,3,4,5,6,7,8,9,10},7));
		System.out.println(test(new int[]{1,2,3,4,5,6,7,8,9,10},7));
	}
	
	 public static int search(int[] nums, int target) {
	        int start = 0;
	        int end = nums.length - 1;
	        while (start <= end){
	            int mid = (start + end) / 2;
	            if (nums[mid] == target)
	                return mid;
	        
	            if (nums[start] <= nums[mid]){
	                 if (target < nums[mid] && target >= nums[start]) 
	                    end = mid - 1;
	                 else
	                    start = mid + 1;
	            } else
	        
	            if (nums[mid] <= nums[end]){
	                if (target > nums[mid] && target <= nums[end])
	                    start = mid + 1;
	                 else
	                    end = mid - 1;
	            }
	        }
	        return -1;
	    }




		private static  int test (int arr[], int val){

		if ( arr.length ==0 ){return -1;}

		int start = 0;
		int end = arr.length-1;


		while (start <= end) {

			int mid =  (end+start)/2;

			if( arr[mid] == val){
				return mid;
			}


			if(arr[start] <= arr[mid] ){

				if( val < arr[mid] && val >= arr[start]){
					end = mid-1;
				}else {
					start = mid +1;
				}


			} else


			if(arr[mid] <= arr[end] ){

				if( val > arr[mid] && val <= arr[end]){
					start = mid+1;
				}else {
					end = mid -1;
				}

			}






		}








		return -1;
		}
}



