package org.example;

import org.example.event.FileStatusTracker;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class PingScheduler extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(PingScheduler.class);

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private final static String baseDir = "ping_data";

    private final static long INTERVAL = 60000;

    private final static int NO_OF_PACKETS = 10;

    private final WorkerExecutor pingExecutor = Main.vertx.createSharedWorkerExecutor("ping-executor", 10, 2,
            TimeUnit.MINUTES);

    @Override
    public void start() throws Exception
    {
        vertx.eventBus().localConsumer("ping.address", message ->
        {
            ping(message.body().toString());
        });
    }

    public void ping(String ip)
    {
        vertx.setPeriodic(INTERVAL, id ->
        {
            try
            {
                pingExecutor.executeBlocking(
                                () -> Util.executeCommand("fping", "-c", String.valueOf(NO_OF_PACKETS), "-q", ip))
                        .onComplete(pingResult ->
                        {
                            try
                            {
                                if (pingResult.succeeded())
                                {
                                    var pingOutput = pingResult.result();

                                    if (!pingOutput.isEmpty())
                                    {
                                        var processedOutput = processPingOutput(pingOutput);

                                        if (processedOutput != null && !processedOutput.isEmpty())
                                        {
                                            var fileName = baseDir + "/" + LocalDateTime.now()
                                                    .format(FILE_NAME_FORMATTER) + ".txt";

                                            logger.info("Writing ping results for IP [{}] to file [{}]", ip, fileName);

                                            Util.writeToFile(fileName,
                                                            Buffer.buffer(processedOutput.put("ip",ip).put("timestamp",LocalDateTime.now().toString()).encode() + "\n"))
                                                    .onComplete(fileWriteResult ->
                                                    {
                                                        try
                                                        {
                                                            if (fileWriteResult.succeeded())
                                                            {
                                                                FileStatusTracker.addFile(fileName);
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
                            }
                            catch (Exception exception)
                            {
                                logger.error(exception.getMessage(), exception);
                            }

                        });
            }
            catch (Exception exception)
            {
                logger.error(exception.getMessage(), exception);
            }
        });
    }

    public JsonObject processPingOutput(String output)
    {
        try
        {
            var packetMatcher = Util.PING_OUTPUT_PATTERN.matcher(output);

            if (packetMatcher.find())
            {
                JsonObject pingData = new JsonObject().put("Packets transmitted", packetMatcher.group(1))
                        .put("Packets received", packetMatcher.group(2))
                        .put("Packet loss", packetMatcher.group(2))
                        .put("Minimum latency", packetMatcher.group(2))
                        .put("Average latency", packetMatcher.group(2))
                        .put("Maximum latency", packetMatcher.group(2));

                logger.debug("Processed ping output: {}", pingData.toString());

                return pingData;
            }
            else
            {
                logger.warn("No valid ping output format found in [{}]", output);

                return null;
            }
        }
        catch (Exception exception)
        {
            logger.error("Error processing ping output: ", exception);

            return null;
        }
    }
}
