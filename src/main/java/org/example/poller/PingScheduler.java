package org.example.poller;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.example.Constants;
import org.example.Main;
import org.example.Util;
import org.example.cache.FileStatusTracker;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PingScheduler extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(PingScheduler.class);

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final static long INTERVAL = 10000;

    private final static int NO_OF_PACKETS = 5;

    private static final Queue<JsonObject> objects = new LinkedList<>();

    private static final List<JsonObject> lastPolledObjects = new ArrayList<>();

    private final WorkerExecutor executor = Main.vertx.createSharedWorkerExecutor("ping-executor", 10, 2,
            TimeUnit.MINUTES);

    @Override
    public void start()
    {
        vertx.eventBus().localConsumer(Constants.OBJECT_PROVISION, message ->

                objects.add(new JsonObject().put("ip", message.body().toString())
                        .put("next.poll.time", Instant.now().plus(INTERVAL,
                                TimeUnit.MILLISECONDS.toChronoUnit()).toEpochMilli()))
        );

        startPolling();
    }

    private void startPolling()
    {
        vertx.setPeriodic(INTERVAL, id ->
        {
            try
            {
                List<Future<Void>> futures = new ArrayList<>();

                List<String> batch = new LinkedList<>();

                while (!objects.isEmpty() && objects.peek().getLong("next.poll.time") <= Instant.now().toEpochMilli())
                {
                    lastPolledObjects.add(objects.peek());

                    batch.add(objects.poll().getString("ip"));

                }
                ping(batch).onComplete(result ->
                {
                    try
                    {
                        if (result.succeeded())
                        {
                            FileStatusTracker.addFile(result.result());

                            vertx.eventBus().publish(Constants.EVENT_NEW_FILE, result.result());
                        }
                        else
                        {
                            logger.error("Error in processing batch: ", result.cause());
                        }
                    }
                    catch (Exception exception)
                    {
                        logger.error(exception.getMessage(), exception);
                    }

                });
                if (!lastPolledObjects.isEmpty())
                {
                    for (var object : lastPolledObjects)
                    {
                        Instant.ofEpochMilli(object.getLong("next.poll.time"))
                                .plus(INTERVAL, TimeUnit.MILLISECONDS.toChronoUnit());

                        objects.add(object);
                    }
                    lastPolledObjects.clear();
                }
            }
            catch (Exception exception)
            {
                logger.error(exception.getMessage(), exception);
            }
        });
    }

    private Future<String> ping(List<String> batch)
    {
        Promise<String> promise = Promise.promise();
        try
        {
            batch.add(0, "fping");
            batch.add(1, "-c");
            batch.add(2, String.valueOf(NO_OF_PACKETS));
            batch.add(3, "-q");

            executor.executeBlocking(() ->
            {
                try
                {
                    var outputs = Util.executeCommand(batch);

                    if (!outputs.isEmpty())
                    {
                        Buffer buffer = Buffer.buffer();
                        for (var output : outputs)
                        {
                            buffer.appendBuffer(Buffer.buffer(processPingResult(output).encode() + "\n"));
                        }

                        String fileName = LocalDateTime.now().format(FILE_NAME_FORMATTER);

                        Util.writeToFile(fileName, buffer).onComplete(result ->
                        {
                            if (result.succeeded())
                            {
                                logger.info("object poll result added to file ");

                                promise.complete(fileName);
                            }
                            else
                            {
                                promise.fail(result.cause());
                            }
                        });
                    }
                    else
                    {
                        promise.fail("output is empty");
                    }
                }
                catch (Exception exception)
                {
                    logger.error(exception.getMessage(), exception);
                }

                return Future.succeededFuture();
            });
        }
        catch (Exception exception)
        {
            promise.fail(exception);
            logger.error(exception.getMessage(), exception);
        }

        return promise.future();
    }

    public JsonObject processPingResult(String output)
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

                logger.debug("Processed ping output: {}", result.toString());

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
