package cli;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandParser {
    public static List<String> tokenize(String input) {
        // Input broken into tokens of quoted strings and un-quoted strings.
        List<String> tokens = new ArrayList<>();
        Pattern pattern = Pattern.compile("\"([^\"]*)\"|(\\S+)");
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            if (matcher.group(1) != null)
                tokens.add(matcher.group(1));
            else
                tokens.add(matcher.group(2));
        }

        return tokens;
    }
}
