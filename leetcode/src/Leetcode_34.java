import java.util.Arrays;
import java.util.List;

public class Leetcode_34 {
    /**
     *给定一个按照升序排列的整数数组 nums，和一个目标值 target。找出给定目标值在数组中的开始位置和结束位置。
     *
     * 如果数组中不存在目标值 target，返回[-1, -1]。
     *
     * 输入：nums = [5,7,7,8,8,10], target = 8
     * 输出：[3,4]
     *
     * 输入：nums = [5,7,7,8,8,10], target = 6
     * 输出：[-1,-1]
     *
     *
     * 输入：nums = [], target = 0
     * 输出：[-1,-1]
     */
    public static void main(String[] args) {
        int[] nums=new int[]{1};
        int[] res=searchRange(nums,1);
        Arrays.stream(res).forEach(result->{
            System.out.println(String.valueOf(result));
        });
    }

    public static int[] searchRange(int[] nums, int target) {

        int l = 0, r = nums.length - 1;
        int start = -2,end = 0;
        while(l <= r) {
            //获取中间下标
            int mid = (l + r) / 2;
            //若中间的数比target小，从右边找
            if(nums[mid] < target) {
                l = mid + 1;
            } else if(nums[mid] > target) {//否则，从左边开始找
                r = mid - 1;
            } else {
                //令start和end都扥归mid
                start = mid;
                end = mid;
                //只要start和end满足边界条件，并且等于target值，都可以进行循环
                while(start >= 0 && nums[start] == target)
                    start --;
                while(end < nums.length && nums[end] == target)
                    end ++;
                break;
            }
        }
        //由于循环出来之后nums[start] != target nums[end] != target，所以需要start+1，end-1
        return new int[]{start + 1, end - 1};
    }
}
