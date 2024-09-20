package org.example;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

public class HostPingScheduler
{
    private static final int DEFAULT_NO_OF_PACKETS = 10;

    private static final long DEFAULT_INTERVAL = 60000;

    private PingDataWriter fileWriterService;

    private long interval = DEFAULT_INTERVAL;

    private int noOfPackets = DEFAULT_NO_OF_PACKETS;

    private StringBuilder pingData = new StringBuilder();

    public HostPingScheduler(PingDataWriter fileWriterService, long interval, int noOfPackets)
    {
        this.fileWriterService = fileWriterService;

        this.interval = interval;

        this.noOfPackets = noOfPackets;
    }

    public void ping(String ip, Vertx vertx, WorkerExecutor workerExecutor)
    {
        vertx.setPeriodic(interval, id ->
        {
            Utility.executeCommand(workerExecutor, "fping", "-c", String.valueOf(noOfPackets), "-q", ip)
                    .onComplete(result ->
                    {
                        if (result.succeeded())
                        {
                            var pingOutput = result.result();

                            if (!pingOutput.isEmpty())
                            {
                                var processedOutput = processPingOutput(pingOutput);

                                if (!processedOutput.isEmpty())
                                {
                                    fileWriterService.writeToFile(ip, processedOutput);
                                }
                            }
                        }
                    });
        });
    }

    public String processPingOutput(String output)
    {
        try
        {
            var packetMatcher = Utility.PING_OUTPUT_PATTERN.matcher(output);

            if (packetMatcher.find())
            {
                this.pingData.append("Packets transmitted: ").append(packetMatcher.group(1)).append("\n")
                        .append("Packets received: ").append(packetMatcher.group(2)).append("\n")
                        .append("Packet loss: ").append(packetMatcher.group(3)).append("%\n")
                        .append("Minimum latency: ").append(packetMatcher.group(4)).append(" ms\n")
                        .append("Average latency: ").append(packetMatcher.group(5)).append(" ms\n")
                        .append("Maximum latency: ").append(packetMatcher.group(6)).append(" ms\n");

                return pingData.toString();
            } else
            {
                return "";
            }
        } finally
        {
            this.pingData.setLength(0);
        }
    }
}
