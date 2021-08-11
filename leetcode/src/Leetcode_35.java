public class Leetcode_35 {
    /**
     *
     * 给定一个排序数组和一个目标值，在数组中找到目标值，并返回其索引。如果目标值不存在于数组中，返回它将会被按顺序插入的位置。
     *
     * 请必须使用时间复杂度为 O(log n) 的算法。
     *
     * 输入: nums = [1,3,5,6], target = 5
     * 输出: 2
     *
     * 输入: nums = [1,3,5,6], target = 2
     * 输出: 1
     *
     * 输入: nums = [1,3,5,6], target = 7
     * 输出: 4
     *
     * 输入: nums = [1,3,5,6], target = 0
     * 输出: 0
     *
     * 输入: nums = [1], target = 0
     * 输出: 0
     */
    public static void main(String[] args) {
        int[] nums=new int[]{1,3,5,6};
        int result=searchInsert(nums,5);
        System.out.println(result);
    }
    public static int searchInsert(int[] nums, int target) {
        int left=0;
        int right=nums.length-1;
        while(left<=right){
            int mid=left+(right-left)/2;
            if(nums[mid]==target){
                return mid;
            }else if(nums[mid]<target){
                left=mid+1;
            }else{
                right=mid-1;
            }
        }
        return left;
    }

}
