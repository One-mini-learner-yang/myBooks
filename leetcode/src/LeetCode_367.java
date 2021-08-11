public class LeetCode_367 {
    /**
     * 给定一个 正整数 num ，编写一个函数，如果 num 是一个完全平方数，则返回 true ，否则返回 false 。
     *
     * 进阶：不要 使用任何内置的库函数，如 sqrt 。
     *
     *
     * 输入：num = 16
     * 输出：true
     *
     * 输入：num = 14
     * 输出：false
     */
    public static void main(String[] args) {
        boolean result=isPerfectSquare(16);
        System.out.println(result);
    }
    public static boolean isPerfectSquare(int num) {
        int left=0;
        int right=num;
        boolean result=false;
        while(left<=right){
            int mid=left+(right-left)/2;
            if((long)mid*mid==num){
                result=true;
                break;
            }else if((long)mid*mid<num){
                left=mid+1;
            }else{
                right=mid-1;
            }
        }
        return result;
    }
}
