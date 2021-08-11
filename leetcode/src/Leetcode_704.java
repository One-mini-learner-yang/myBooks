import java.util.Arrays;
import java.util.List;

public class Leetcode_704 {
    /**
     *给定一个n个元素有序的（升序）整型数组nums 和一个目标值target，
     *写一个函数搜索nums中的 target，如果目标值存在返回下标，否则返回 -1。
     *
     *
     * 输入: nums = [-1,0,3,5,9,12], target = 9
     * 输出: 4
     * 解释: 9 出现在 nums 中并且下标为 4
     *
     *
     * 输入: nums = [-1,0,3,5,9,12], target = 2
     * 输出: -1
     * 解释: 2 不存在 nums 中因此返回 -1
     */
    public static void main(String[] args) {
        List<Integer> list= Arrays.asList(-1,0,3,5,9,12);
        Integer[] nums=list.toArray(new Integer[0]);
        System.out.printf(String.valueOf(search(nums,2)));
    }
    public static int search(Integer[] nums, int target) {
        int left=0;
        int right=nums.length-1;
        while(left<=right){
            int mid=left+(right-left)/2;
            if(nums[mid]==target){
                return mid;
            }else if(nums[mid]<target){
                left=mid+1;
                continue;
            }else{
                right=mid-1;
                continue;
            }
        }
        return -1;
    }
}
