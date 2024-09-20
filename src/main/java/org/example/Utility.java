package org.example;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.file.FileSystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class Utility
{
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

    public static Future<String> executeCommand(WorkerExecutor workerExecutor, String... commands)
    {
        try
        {
            Promise<String> pingPromise = Promise.promise();

            workerExecutor.executeBlocking(() ->
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

                    String str;

                    while ((str = stdInput.readLine()) != null)
                    {
                        commandOutput.append(str).append("\n");
                    }

                    stdInput.close();

                    return commandOutput.toString();

                } catch (Exception exception)
                {
                    exception.printStackTrace();

                    return exception.getMessage();
                }
            }, false).onComplete(result ->
            {
                if (result.succeeded())
                {
                    pingPromise.complete(result.result());
                } else
                {
                    pingPromise.fail(result.cause());
                }
            });

            return pingPromise.future();
        } catch (Exception exception)
        {
            throw new RuntimeException(exception.getMessage());
        }
    }

    public static Future<Void> createDirectoryIfDoesNotExist(FileSystem fileSystem, String path)
    {
        Promise<Void> promise = Promise.promise();

        fileSystem.exists(path).onComplete(existsResult ->
        {
            if (existsResult.succeeded())
            {
                if (!existsResult.result())
                {
                    // Directory does not exist, create it
                    fileSystem.mkdirs(path).onComplete(mkdirResult ->
                    {
                        if (mkdirResult.succeeded())
                        {
                            promise.complete(); // Directory created successfully
                        } else
                        {
                            promise.fail(mkdirResult.cause()); // Failed to create directory
                        }
                    });
                } else
                {
                    promise.complete(); // Directory already exists
                }
            } else
            {
                promise.fail(existsResult.cause()); // Failed to check if directory exists
            }
        });

        return promise.future();
    }
}
