package org.example;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Util
{
    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    private final static String IP_V4_REGEX = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";
    private final static String IP_V6_REGEX = "^([0-9a-fA-F]{1,4}:){7}([0-9a-fA-F]{1,4}|:)$";

    public final static String PING_OUTPUT_REGEX = ".* : xmt/rcv/%loss = (\\d+)/(\\d+)/(\\d+)%, min/avg/max = ([0-9.]+)/([0-9.]+)/([0-9.]+)";
    public final static Pattern PING_OUTPUT_PATTERN = Pattern.compile(PING_OUTPUT_REGEX);

    private final static Pattern IP_V4_PATTERN = Pattern.compile(IP_V4_REGEX);
    private final static Pattern IP_V6_PATTERN = Pattern.compile(IP_V6_REGEX);

    private final static long PROCESS_TIMEOUT = 20; // 20 seconds

    public static boolean isValidIp(String ip)
    {
        if (ip.length() > 15)
            return IP_V6_PATTERN.matcher(ip).matches();

        return IP_V4_PATTERN.matcher(ip).matches() || IP_V6_PATTERN.matcher(ip).matches();
    }

    public static String executeCommand(String... commands)
    {
        try
        {
            var processBuilder = new ProcessBuilder(commands);
            var process = processBuilder.start();
            var stdInput = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            var commandOutput = new StringBuilder();

            if (!process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS))
            {
                process.destroy();
                return "";
            }

            String outputLine;
            while ((outputLine = stdInput.readLine()) != null)
            {
                commandOutput.append(outputLine).append("\n");
            }

            stdInput.close();
            return commandOutput.toString();
        }
        catch (Exception exception)
        {
            logger.error("Error executing command: {}", String.join(" ", commands), exception);
            throw new RuntimeException("Error executing command: " + String.join(" ", commands), exception);
        }
    }

    public static Future<Boolean> writeToFile(String filename, Buffer data)
    {
        Promise<Boolean> promise = Promise.promise();

        Main.vertx.fileSystem().open(filename, new OpenOptions().setCreate(true).setAppend(true)).onComplete(result ->
        {
            try
            {
                if (result.succeeded())
                {
                    var file = result.result();

                    file.write(data).onComplete(writeResult ->
                    {
                        if (writeResult.succeeded())
                        {
                            promise.complete(true);
                            file.close();
                            logger.info("Successfully wrote data to file: {}", filename);
                        }
                        else
                        {
                            promise.complete(false);
                            logger.error("Error writing to file: {}", filename, writeResult.cause());
                        }
                    });
                }
                else
                {
                    promise.complete(false);
                    logger.error("Failed to open file: {}", filename, result.cause());
                }
            }
            catch (Exception exception)
            {
                promise.complete(false);
                logger.error(exception.getMessage(),exception);
            }
        });
        return promise.future();
    }

    public static boolean isHostAlive(String ip)
    {
        try
        {
            var pingOutput = Util.executeCommand("fping", "-c", "5", "-q", ip);

            if (pingOutput.isEmpty())
            {
                logger.warn("No response from host [{}].", ip);

                return false;
            }

            var packetLossMatcher = Util.PING_OUTPUT_PATTERN.matcher(pingOutput);

            if (packetLossMatcher.find())
            {
                var packetLossPercent = Integer.parseInt(packetLossMatcher.group(3));

                logger.debug("Packet loss for IP [{}] is {}%.", ip, packetLossPercent);

                return packetLossPercent < 50;
            }
            else
            {
                logger.warn("No packet loss information for IP [{}].", ip);

                return false;
            }
        }
        catch (Exception exception)
        {
            logger.error("Error while checking host [{}]: {}", ip, exception.getMessage());

            return false;
        }
    }
}
