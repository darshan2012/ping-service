package org.example.poll;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.example.Main;
import org.example.Util;
import org.example.store.ApplicationContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Poller extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(Poller.class);

    private static final int NO_OF_PACKETS = 5;

    private static final WorkerExecutor executor = Main.vertx.createSharedWorkerExecutor("ping-executor", 10, 2, TimeUnit.MINUTES);

    @Override
    public void start()
    {
        vertx.eventBus().<JsonObject>localConsumer(Constants.START_POLLING, message ->
        {
            try
            {
                var context = message.body();

                if ("ping".equals(context.getString("metric")))
                {
                    if (ApplicationContextStore.getApplicationCount() > 0 && context.getString("ip") != null)
                    {
                        ping(context.getString("ip"));
                    }
                }
                else
                {
                    logger.warn("Unsupported metric type: {}", context.getString("metric"));
                }
            }
            catch (Exception exception)
            {
                logger.error(exception.getMessage(),exception);
            }
        });
    }

    private void ping(String ip)
    {
        try
        {
            executor.executeBlocking(promise ->
            {
                try
                {
                    String output = Util.executeCommand(List.of("fping", "-c", String.valueOf(NO_OF_PACKETS), "-q", ip));

                    if (!output.isEmpty())
                    {
                        StringBuilder content = new StringBuilder();

                        String[] outputLines = output.split(Constants.NEW_LINE_CHAR);

                        for (String line : outputLines)
                        {
                            JsonObject processedOutput = processPingResult(line);

                            if (processedOutput != null)
                            {
                                content.append(processedOutput.encode()).append(Constants.NEW_LINE_CHAR);
                            }
                        }

                        logger.info("Sending data to write {}", content);

                        vertx.eventBus().send(Constants.EVENT_WRITE_FILE, new JsonObject()
                                .put("file.name", Constants.BASE_DIR + "/" + LocalDateTime.now()
                                        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".txt")
                                .put("file.content", content.toString()));

                        promise.complete();
                    }
                    else
                    {
                        promise.fail("Ping output is empty for IP: " + ip);
                    }
                }
                catch (Exception exception)
                {
                    logger.error("Error during ping execution for IP: {}", ip, exception);

                    promise.fail(exception);
                }
            });
        }
        catch (Exception exception)
        {
            logger.error("Error in ping method for IP: {}", ip, exception);
        }
    }

    private JsonObject processPingResult(String output)
    {
        try
        {
            var packetMatcher = Util.PING_OUTPUT_PATTERN.matcher(output);

            if (packetMatcher.find())
            {
                JsonObject result = new JsonObject()
                        .put("IP", packetMatcher.group(1))
                        .put("Packets transmitted", packetMatcher.group(2))
                        .put("Packets received", packetMatcher.group(3))
                        .put("Packet loss", packetMatcher.group(4))
                        .put("Minimum latency", packetMatcher.group(5))
                        .put("Average latency", packetMatcher.group(6))
                        .put("Maximum latency", packetMatcher.group(7));

                logger.info("Processed ping output: {}", result.toString());

                return result;
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
