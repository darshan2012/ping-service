package org.example;

import event.FileStatusTracker;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PingScheduler
{
    private static final Logger logger = LoggerFactory.getLogger(PingScheduler.class);

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private final String baseDir;

    private final long interval;

    private final int noOfPackets;

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
            Main.pingExecutor.executeBlocking(
                            () -> Util.executeCommand("fping", "-c", String.valueOf(noOfPackets), "-q", ip))
                    .onComplete(pingResult ->
                    {
                        if (pingResult.succeeded())
                        {
                            var pingOutput = pingResult.result();

                            if (!pingOutput.isEmpty())
                            {
                                String processedOutput = processPingOutput(pingOutput);

                                if (!processedOutput.isEmpty())
                                {
                                    String fileName = baseDir + "/" + LocalDateTime.now()
                                            .format(FILE_NAME_FORMATTER) + ".txt";

                                    logger.info("Writing ping results for IP [{}] to file [{}]", ip, fileName);

                                    Util.writeToFile(fileName, Buffer.buffer("IP: " + ip + "\n" + processedOutput))
                                            .onComplete(fileWriteResult ->
                                            {
                                                try
                                                {
                                                    if (fileWriteResult.succeeded())
                                                    {
                                                        FileStatusTracker.addFile(fileName);
                                                        Main.vertx.eventBus().publisher("new-file").write(fileName);

                                                    }
                                                }
                                                catch (Exception exception)
                                                {
                                                    logger.error(exception.getMessage(), exception);
                                                }
                                            });
                                }
                            }
                        }
                        else
                        {
                            logger.error("Failed to execute ping for IP [{}]", ip, pingResult.cause());
                        }
                    });
        });
    }

    public String processPingOutput(String output)
    {
        var pingData = new StringBuilder();
        try
        {
            var packetMatcher = Util.PING_OUTPUT_PATTERN.matcher(output);

            if (packetMatcher.find())
            {
                pingData.append("Packets transmitted: ").append(packetMatcher.group(1)).append("\n")
                        .append("Packets received: ").append(packetMatcher.group(2)).append("\n")
                        .append("Packet loss: ").append(packetMatcher.group(3)).append("%\n")
                        .append("Minimum latency: ").append(packetMatcher.group(4)).append(" ms\n")
                        .append("Average latency: ").append(packetMatcher.group(5)).append(" ms\n")
                        .append("Maximum latency: ").append(packetMatcher.group(6)).append(" ms\n");

                logger.debug("Processed ping output: {}", pingData.toString());

                return pingData.toString();
            }
            else
            {
                logger.warn("No valid ping output format found in [{}]", output);

                return "";
            }
        }
        catch (Exception exception)
        {
            logger.error("Error processing ping output: ", exception);

            return "";
        }
    }
}
