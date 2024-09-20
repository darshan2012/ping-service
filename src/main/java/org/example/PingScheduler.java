package org.example;

import io.vertx.core.buffer.Buffer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PingScheduler
{
    private static final int DEFAULT_NO_OF_PACKETS = 10;

    private static final long DEFAULT_INTERVAL = 60000;

    private String baseDir = "";

    private long interval = DEFAULT_INTERVAL;

    private int noOfPackets = DEFAULT_NO_OF_PACKETS;

    private StringBuilder pingData = new StringBuilder();

    public PingScheduler(long interval, int noOfPackets, String baseDir)
    {
        this.interval = interval;

        this.noOfPackets = noOfPackets;

        this.baseDir = baseDir;
    }

    public void ping(String ip)
    {
        Main.vertx.setPeriodic(interval, id ->
        {
            Main.pingExecutor.executeBlocking(() ->
                    Util.executeCommand("fping", "-c", String.valueOf(noOfPackets), "-q", ip)
            ).onComplete(pingResult ->
            {
                try
                {
                    if (pingResult.succeeded())
                    {
                        var pingOutput = pingResult.result();

                        if (!pingOutput.isEmpty())
                        {
                            var processedOutput = processPingOutput(pingOutput);

                            if (!processedOutput.isEmpty())
                            {
                                Util.writeToFile(baseDir + "/"  + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".txt",
                                        Buffer.buffer("IP : " + ip + "\n" +  processedOutput));
                            }
                        }
                    }
                } catch (Exception exception)
                {
                    exception.printStackTrace();
                }

            });
        });
    }

    public String processPingOutput(String output)
    {
        try
        {
            var packetMatcher = Util.PING_OUTPUT_PATTERN.matcher(output);

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
        } catch (Exception exception)
        {
            exception.printStackTrace();
            return "";
        } finally
        {
            this.pingData.setLength(0);
        }
    }
}
