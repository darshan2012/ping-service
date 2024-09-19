package org.example;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HostPingScheduler
{

    private static final int DEFAULT_NO_OF_PACKETS = 10;

    private static final long DEFAULT_INTERVAL = 60000;

    private FileWriterService fileWriterService;

    private long interval = DEFAULT_INTERVAL;

    private int noOfPackets = DEFAULT_NO_OF_PACKETS;

    public HostPingScheduler(FileWriterService fileWriterService, long interval, int noOfPackets)
    {

        this.fileWriterService = fileWriterService;

        this.interval = interval;

        this.noOfPackets = noOfPackets;

    }


    public void ping(String ip, Vertx vertx, WorkerExecutor workerExecutor)
    {

        vertx.setPeriodic(interval, id ->
        {
            workerExecutor.executeBlocking(() ->
            {
                try
                {
                    var pingOutput = Utility.executeCommand("fping", "-c", String.valueOf(noOfPackets), "-q", ip);

                    if (pingOutput.isEmpty())
                    {
                        throw new Exception("Host[" + ip + "] may not be reachable");
                    }

                    return pingOutput;
                } catch (Exception e)
                {
                    throw new Exception(e.getMessage());
                }

            }).onSuccess(output ->
            {

                String processedOutput = Utility.processPingOutput(output);

                if (!processedOutput.isEmpty())
                {
                    fileWriterService.writeToFile(ip, processedOutput);
                }

            }).onFailure(err ->
            {

                System.err.println("Error during provisioning: " + err.getMessage());

            });
        });
    }


}
