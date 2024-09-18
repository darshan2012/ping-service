package org.example;

import java.util.regex.Pattern;

public class IpValidator
{
    private static String ipv4Regex = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";

    private static String ipv6Regex = "^([0-9a-fA-F]{1,4}:){7}([0-9a-fA-F]{1,4}|:)$";

    private static Pattern ipv4Pattern = Pattern.compile(ipv4Regex);

    private static Pattern ipv6Pattern = Pattern.compile(ipv6Regex);

    public static boolean isValidIp(String ip) {

        return ipv4Pattern.matcher(ip).matches() || ipv6Pattern.matcher(ip).matches();
    }

}
