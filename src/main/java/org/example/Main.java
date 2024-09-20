package org.example;

import io.vertx.core.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Main
{
    public static Vertx vertx = Vertx.vertx(new VertxOptions());

    public static WorkerExecutor pingExecutor = vertx.createSharedWorkerExecutor("ping-executor", 10, 2,
            TimeUnit.MINUTES);

    public static void main(String[] args)
    {
        try
        {
            var interval = 60000L;

            var baseDir = "ping_logs";

            var pingScheduler = new PingScheduler(interval, 10);

            var inputReader = new BufferedReader(new InputStreamReader(System.in));

            while (true)
            {
                try
                {
                    System.out.print("Enter valid IP: ");

                    String ip = inputReader.readLine();

                    if (!Util.isValidIp(ip))
                    {
                        System.out.println("Invalid IP[" + ip + "]");

                        continue;
                    }
                    if (isHostAlive(ip))
                    {
                        System.out.println("Host[" + ip + "] is up.");

                        System.out.print("Do you want to provision? [Y/N]: ");

                        String provision = inputReader.readLine();

                        if (provision.equalsIgnoreCase("y"))
                        {
                            var path = baseDir + "/" + ip;

                            if (!vertx.fileSystem().existsBlocking(path))
                            {
                                if (!vertx.fileSystem().mkdirBlocking(path).existsBlocking(path))
                                {
                                    System.out.println("could not provision...");

                                    continue;
                                }
                            }
                            pingScheduler.ping(ip);
                        }
                    } else
                    {
                        System.out.println("Host[" + ip + "] is down.");
                    }
                } catch (Exception exception)
                {
                    exception.printStackTrace();
                }
            }
        } catch (Exception exception)
        {
            exception.printStackTrace();
        }
    }

    private static boolean isHostAlive(String ip)
    {
        System.out.println("Checking if host is up...");
        try
        {
            var pingOutput = Util.executeCommand("fping", "-c", "5", "-q", ip);

            if (pingOutput.isEmpty())
                return false;

            var packetLossMatcher = Util.PING_OUTPUT_PATTERN.matcher(pingOutput);

            if (packetLossMatcher.find())
            {
                var packetLossPercent = Integer.parseInt(packetLossMatcher.group(3));

                if (packetLossPercent > 50)
                    return false;

                return true;
            } else
            {
                return false;
            }
        } catch (Exception exception)
        {
            System.out.println("Error: " + exception.getMessage());

            return false;
        }
    }
}
