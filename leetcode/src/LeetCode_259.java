import java.util.Arrays;

public class LeetCode_259 {
    /**
     *
     * 给定一个长度为 n 的整数数组和一个目标值 target，寻找能够使条件 nums[i] + nums[j] + nums[k] < target 成立的三元组  i, j, k 个数（0 <= i < j < k < n）。
     *
     * 示例：
     *
     * 输入: nums = [-2,0,1,3], target = 2
     * 输出: 2
     * 解释: 因为一共有两个三元组满足累加和小于 2:
     *      [-2,0,1]
     *      [-2,0,3]
     * 进阶：是否能在 O(n2) 的时间复杂度内解决？
     */

    public static void main(String[] args) {
        int[] nums=new int[]{-2,0,1,3};
        int result=threeSumSmaller(nums,0);
        System.out.println(result);
    }
    public static int threeSumSmaller(int[] nums,int target){
        Arrays.sort(nums);
        int num=0;
        for(int i=0;i<nums.length-2;i++){
            int left=i+1;
            int right=nums.length-1;
            while (left<=right){
                if(nums[i]+nums[left]+nums[right]<target){
                    num++;
                    left++;
                }else {
                    right--;
                }
            }
        }
        return num;
    }
}
