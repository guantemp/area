import com.hoprxi.domain.model.Area;
import com.hoprxi.infrastructure.query.ESAreaQuery;

import java.util.EnumSet;
import java.util.regex.Pattern;

/***
 * @author <a href="www.hoprxi.com/authors/guan xiangHuan">guan xiangHuang</a>
 * @since JDK21
 * @version 0.0.1 builder 2025/8/11
 */

public class App {
    public static void main(String[] args) {
        EnumSet < Area.Level > sets = EnumSet.noneOf(Area.Level.class);
        System.out.println(sets);

        String[] filters = new String[]{"COUNTRY", "PROVINCE", "CITY"};
        for (String filter : filters) {
            sets.add(Area.Level.of(filter));
        }
        System.out.println(sets);
        System.out.println(Integer.parseInt("788123"));
        System.out.println(Pattern
                .compile("[+-]?\\d+").matcher("123").matches());
        System.out.println("".split(",").length);
    }
}
