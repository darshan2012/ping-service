package org.example;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Util
{
    private static final Logger logger = LoggerFactory.getLogger(Util.class);

    private final static String IP_V4_REGEX = "^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])(\\.(?!$)|$)){4}$";
    private final static String IP_V6_REGEX = "^([0-9a-fA-F]{1,4}:){7}([0-9a-fA-F]{1,4}|:)$";

    public final static Pattern PING_OUTPUT_PATTERN = Pattern.compile("(\\S+)\\s+:\\s+xmt/rcv/%loss = (\\d+)/(\\d+)/(\\d+)%, min/avg/max = ([0-9.]+)/([0-9.]+)/([0-9.]+)");

    public final static StringBuilder commandOutput = new StringBuilder();

    private final static Pattern IP_V4_PATTERN = Pattern.compile(IP_V4_REGEX);
    private final static Pattern IP_V6_PATTERN = Pattern.compile(IP_V6_REGEX);

    private final static long PROCESS_TIMEOUT = 20; // 20 seconds

    public static boolean isValidIp(String ip)
    {
        if (ip.length() > 15)
            return IP_V6_PATTERN.matcher(ip).matches();

        return IP_V4_PATTERN.matcher(ip).matches() || IP_V6_PATTERN.matcher(ip).matches();
    }

    public static String executeCommand(List<String> commands)
    {
        Process process = null;

        String outputLine;

        try
        {
            var processBuilder = new ProcessBuilder(commands);

            process = processBuilder.start();

            var stdInput = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            if (!process.waitFor(PROCESS_TIMEOUT, TimeUnit.SECONDS))
            {
                process.destroyForcibly();

                return "";
            }

            while ((outputLine = stdInput.readLine()) != null)
            {
                commandOutput.append(outputLine + Constants.NEW_LINE_CHAR);
            }

            outputLine = commandOutput.toString();

            commandOutput.setLength(0);
        }
        catch (Exception exception)
        {
            logger.error("Error executing command: {}", String.join(" ", commands), exception);

            throw new RuntimeException("Error executing command: " + String.join(" ", commands), exception);
        }
        finally
        {
            if (process != null)
            {
                process.destroyForcibly();
            }
        }

        return outputLine;
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

                logger.error(exception.getMessage(), exception);
            }
        });

        return promise.future();
    }

    public static boolean isHostAlive(String ip)
    {
        try
        {
            List<String> command = new ArrayList<>();
            command.add("fping");
            command.add("-c");
            command.add("5");
            command.add("-q");
            command.add(ip);

            var pingOutput = Util.executeCommand(command);

            if (pingOutput.isEmpty())
            {
                logger.warn("No response from host [{}].", ip);

                return false;
            }

            var packetLossMatcher = Util.PING_OUTPUT_PATTERN.matcher(pingOutput);

            if (packetLossMatcher.find())
            {
                var packetLossPercent = Integer.parseInt(packetLossMatcher.group(4));

                logger.info("Packet loss for IP [{}] is {}%.", ip, packetLossPercent);

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

    public static boolean createFileIfNotExist(String path)
    {
        try
        {
            if (!Main.vertx.fileSystem().existsBlocking(path))
            {
                Main.vertx.fileSystem().createFileBlocking(path);

                return true;
            }
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);

            return false;
        }

        return true;
    }

    public static Future<Boolean> isFileCreatedAtLeastNMinutesAgo(String fileName, int minutes)
    {
        Promise<Boolean> promise = Promise.promise();

        Main.vertx.fileSystem().props(fileName, result ->
        {
            if (result.succeeded())
            {
                long creationTime = result.result().creationTime();

                promise.complete(Instant.now().isAfter(Instant.ofEpochMilli(creationTime).plus(minutes, TimeUnit.MINUTES.toChronoUnit())));
            }
            else
            {
                promise.fail(result.cause()); // Handle the failure
            }
        });

        return promise.future();
    }
}
