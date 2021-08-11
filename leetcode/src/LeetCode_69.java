public class LeetCode_69 {
    /**
     * 实现int sqrt(int x)函数。
     *
     * 计算并返回x的平方根，其中x 是非负整数。
     *
     * 由于返回类型是整数，结果只保留整数的部分，小数部分将被舍去。
     *
     * 输入: 4
     * 输出: 2
     *
     * 输入: 8
     * 输出: 2
     * 说明: 8 的平方根是 2.82842...,
     *      由于返回类型是整数，小数部分将被舍去。
     */
    public static void main(String[] args) {
        int result=mySqrt(8);
        System.out.println(result);
    }
    public static int mySqrt(int x) {
        int left=0;
        int right=x;
        int ans=-1;
        while(left<=right){
            int mid=left+(right-left)/2;
            if((long)mid*mid<=x){
                ans=mid;
                left=mid+1;
            }else{
                right=mid-1;
            }
        }
        return ans;
    }
}
