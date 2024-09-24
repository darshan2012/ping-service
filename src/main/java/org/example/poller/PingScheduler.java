package org.example.poller;

import io.vertx.core.Future;
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

public class PingScheduler extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(PingScheduler.class);

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final static String BASE_DIR = Constants.BASE_DIR;

    private final static long INTERVAL = 10000;

    private final static int NO_OF_PACKETS = 10;

    private static Queue<JsonObject> objects = new LinkedList<>();

    private static List<JsonObject> lastPolledObjects = new ArrayList<>();

    private final WorkerExecutor executor = Main.vertx.createSharedWorkerExecutor("ping-executor", 10, 2,
            TimeUnit.MINUTES);

    @Override
    public void start() throws Exception
    {
        vertx.eventBus().localConsumer("object.provision", message ->
        {
            objects.add(new JsonObject().put("ip", message.body().toString())
                    .put("nextPollTime", Instant.now().plus(INTERVAL,
                            TimeUnit.MILLISECONDS.toChronoUnit()).toEpochMilli()));
        });

        startPolling();
    }

    private void startPolling()
    {
        vertx.setPeriodic(INTERVAL, id ->
        {
            try
            {
                String fileName="";

                boolean createNewFile = true;

                while (!objects.isEmpty() && objects.peek().getLong("nextPollTime") <= Instant.now().toEpochMilli())
                {
                    if(createNewFile)
                    {
                        fileName = BASE_DIR + "/" + LocalDateTime.now().format(FILE_NAME_FORMATTER) + ".txt";

                        createNewFile = false;

                        vertx.fileSystem().createFileBlocking(fileName);

                        final String []fileNames = new String[]{fileName};

                        vertx.setTimer(INTERVAL, timerId -> {
                            vertx.eventBus().publish("new-file",fileNames[0]);
                        });
                    }
                    lastPolledObjects.add(objects.peek());

                    ping(objects.poll().getString("ip"),fileName);
                }

                if (!lastPolledObjects.isEmpty())
                {
                    for (var IP : lastPolledObjects)
                    {
                        Instant.ofEpochMilli(IP.getLong("nextPollTime")).plus(INTERVAL, TimeUnit.MILLISECONDS.toChronoUnit());

                        objects.add(IP);
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

    public void ping(String ip, String fileName)
    {
        try
        {
            executor.executeBlocking(() ->
            {
                var output = Util.executeCommand("fping", "-c", String.valueOf(NO_OF_PACKETS), "-q",
                        ip);

                if (!output.isEmpty())
                {
                    var result = processPingResult(output);

                    if (result != null && !result.isEmpty())
                    {
                        logger.info("Writing ping results for IP [{}] to file [{}]", ip, fileName);

                        Util.writeToFile(fileName,
                                        Buffer.buffer(result.put("ip", ip)
                                                .put("timestamp", LocalDateTime.now().toString())
                                                .encode() + "\n"))
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

                return Future.succeededFuture();
            });
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }

    }

    public JsonObject processPingResult(String output)
    {
        try
        {
            var packetMatcher = Util.PING_OUTPUT_PATTERN.matcher(output);

            if (packetMatcher.find())
            {
                JsonObject result = new JsonObject().put("Packets transmitted", packetMatcher.group(1))
                        .put("Packets received", packetMatcher.group(2))
                        .put("Packet loss", packetMatcher.group(2))
                        .put("Minimum latency", packetMatcher.group(2))
                        .put("Average latency", packetMatcher.group(2))
                        .put("Maximum latency", packetMatcher.group(2));

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
