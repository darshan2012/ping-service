package org.example;
import io.vertx.core.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class Main
{
    public static void main(String[] args)
    {
        var vertx = Vertx.vertx(new VertxOptions().setMaxWorkerExecuteTime(1000000000000000000L));

        var pingExecutor = vertx.createSharedWorkerExecutor("ping-executor", 10, 2, TimeUnit.MINUTES);

        var interval = 60000L;

        var pingDataWriter = new PingDataWriter(vertx, "ping_logs");

        var pingScheduler = new HostPingScheduler(pingDataWriter, interval, 10);

        var inputReader = new BufferedReader(new InputStreamReader(System.in));

        handleProvisioning(vertx, pingExecutor, pingScheduler, inputReader);
    }

    private static void handleProvisioning(Vertx vertx, WorkerExecutor pingExecutor, HostPingScheduler pingScheduler,
                                           BufferedReader inputReader)
    {
        vertx.executeBlocking(() ->
        {
            try
            {
                System.out.print("Enter valid IP: ");

                return inputReader.readLine();
            } catch (Exception e)
            {
                e.printStackTrace();

                throw e;
            }
        }, false).onComplete(result ->
        {
            if (result.succeeded())
            {
                var ip = result.result();

                if (!Utility.isValidIp(ip))
                {
                    System.out.println("Invalid IP[" + ip + "]");

                    handleProvisioning(vertx, pingExecutor, pingScheduler, inputReader);

                    return;
                }
                isHostAlive(pingExecutor, ip).onComplete(hostResult ->
                {
                    if (hostResult.succeeded() && hostResult.result())
                    {
                        System.out.println("Host[" + ip + "] is up.");

                        vertx.executeBlocking(() ->
                        {
                            try
                            {
                                System.out.print("Do you want to provision? [Y/N]: ");

                                return inputReader.readLine();
                            } catch (Exception e)
                            {
                                e.printStackTrace();

                                throw e;
                            }
                        }, false).onComplete(provisionResult ->
                        {
                            if (provisionResult.succeeded() && provisionResult.result().equalsIgnoreCase("y"))
                            {
                                var dirPath = "ping_logs/" + ip;

                                Utility.createDirectoryIfDoesNotExist(vertx.fileSystem(), dirPath)
                                        .onComplete(directoryResult ->
                                        {
                                            if (directoryResult.succeeded())
                                            {
                                                pingScheduler.ping(ip, vertx, pingExecutor);

                                                System.out.println(ip + " scheduled for provisioning...");

                                                handleProvisioning(vertx, pingExecutor, pingScheduler, inputReader);
                                            } else
                                            {
                                                System.out.println(
                                                        "Failed to create or verify directory: " + directoryResult.cause()
                                                                .getMessage());

                                                handleProvisioning(vertx, pingExecutor, pingScheduler, inputReader);
                                            }
                                        });
                            }
                        });
                    } else
                    {
                        System.out.println("Host[" + ip + "] is down.");

                        handleProvisioning(vertx, pingExecutor, pingScheduler, inputReader);
                    }
                });
            } else
            {
                System.out.println("Error: " + result.cause().getMessage());

                handleProvisioning(vertx, pingExecutor, pingScheduler, inputReader);
            }
        });
    }

    private static Future<Boolean> isHostAlive(WorkerExecutor workerExecutor, String ip)
    {
        Promise<Boolean> pingPromise = Promise.promise();
        try
        {
            System.out.println("checking if host is up...");

            Utility.executeCommand(workerExecutor, "fping", "-c", "5", "-q", ip).onComplete(result ->
            {
                if (result.succeeded())
                {
                    var pingOutput = result.result();

                    if (pingOutput.isEmpty())
                        pingPromise.complete(false);

                    var packetLossMatcher = Utility.PING_OUTPUT_PATTERN.matcher(pingOutput);

                    if (packetLossMatcher.find())
                    {
                        var packetLossPercent = Integer.parseInt(packetLossMatcher.group(3));

                        if (packetLossPercent > 50)
                            pingPromise.complete(false);

                        pingPromise.complete(true);
                    } else
                    {
                        pingPromise.complete(false);
                    }
                }
            });
        } catch (Exception exception)
        {
            pingPromise.fail(exception);
        }
        return pingPromise.future();
    }
}
