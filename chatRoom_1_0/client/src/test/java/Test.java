import java.io.FileInputStream;
import java.nio.CharBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.util.Calendar;
import java.util.Date;

public class Test {
    public static void testDate(){
        Date d = new Date();
        System.out.println (d);
        Long l = d.getTime ();
        System.out.println (l);
        System.out.println (new Date(l));
        Date now = Calendar.getInstance().getTime();
        System.out.println (now);
    }
    public static void testAssert(){
        int t =1;
        assert(t == 2)  ;
        System.out.println (11);
    }

    public static void main(String[] args){
        testDate();
    }
}
