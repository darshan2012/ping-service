package org.example.event;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.example.Main;
import org.example.cache.FileStatusTracker;
import org.example.store.ApplicationContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class FileManager extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);

    private static final int EVENT_INTERVAL = 60000 * 5; // 5 minutes

    private static final int MAX_EVENTS = 100; // 100 events in 5 minutes

    private static final int PING_TIMEOUT = 5000;

    private final static Pattern FILE_NAME_PATTERN = Pattern.compile("[^/]+$");

    private final Constants.ApplicationType applicationType;

    private JsonObject applicationContext;

    private long timeStamp = System.currentTimeMillis();

    private Queue<String> fileQueue = new ArrayDeque<>();

    private final ZMQ.Socket pingSocket = Main.zContext.createSocket(SocketType.PULL);

    public FileManager(Constants.ApplicationType applicationType, String ip, int port, int pingPort)
    {
        this.applicationType = applicationType;

        ApplicationContextStore.setAppContext(applicationType, ip, port, pingPort);

        applicationContext = ApplicationContextStore.getAppContext(applicationType);

        pingSocket.connect("tcp://" + ip + ":" + port);
    }

    public FileManager(Constants.ApplicationType applicationType, JsonObject applicationContext)
    {
        this(applicationType, applicationContext.getString("ip"), applicationContext.getInteger("port"), applicationContext.getInteger("ping.port"));
    }

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            vertx.deployVerticle(new HeartbeatReceiver(applicationType, applicationContext.getString("ip"), applicationContext.getInteger("ping.port")))
                    .compose(result -> vertx.deployVerticle(new EventSender(applicationType, applicationContext)))
                    .compose(result ->
                    {
                        try
                        {
                            vertx.eventBus().<Long>localConsumer(Constants.EVENT_HEARTBEAT, message -> timeStamp = message.body());

                            fileQueue = FileStatusTracker.getFiles(applicationType);

                            startProcessing();

                            vertx.eventBus().<String>localConsumer(Constants.EVENT_NEW_FILE, file -> fileQueue.add(file.body()));

                            startPromise.complete();

                            return Future.succeededFuture();
                        }
                        catch (Exception exception)
                        {
                            logger.error(exception.getMessage(), exception);

                            return Future.failedFuture(exception);
                        }
                    })
                    .onFailure(failure ->
                    {
                        logger.error("Error in setting up FileManager for app {}", applicationType, failure.getCause());

                        startPromise.fail(failure.getCause());
                    });

        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }

    private void startProcessing()
    {
        try
        {
            vertx.setPeriodic(EVENT_INTERVAL, id ->
            {
                try
                {
                    logger.info("periodic event sending started for {}", applicationType.toString());

                    if (isAlive())
                    {
                        logger.info("Connection is Alive {}", applicationType);

                        processNextFile(new AtomicInteger(0));
                    }
                    else
                    {
                        logger.info("Receiver is disconnected, stopping event sending.");
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
            logger.error("Error while connecting ZMQ Socket: ", exception);
        }
    }

    private void processNextFile(AtomicInteger events)
    {
        try
        {
            if (!fileQueue.isEmpty() && events.get() < MAX_EVENTS)
            {
                var currentFile = fileQueue.peek();

                if (currentFile != null)
                {
                    logger.info("Sending file read request {}", currentFile);

                    vertx.eventBus()
                            .request(Constants.EVENT_READ_FILE, new JsonObject().put("file.name", currentFile).put("offset",applicationContext.getLong("offset",0L)))
                            .onComplete(request ->
                            {
                                try
                                {
                                    if (request.succeeded())
                                    {
                                        var matcher = FILE_NAME_PATTERN.matcher(currentFile);

                                        if (matcher.find())
                                        {
                                            var filename = matcher.group();

                                            if (!send("filename " + filename))
                                            {
                                                logger.info("Stopped the send event before reading the file {} for app {}", currentFile, applicationType);

                                                return;
                                            }
                                        }

                                        var content = request.result().body().toString();

                                        logger.info("content received of file {} \n Content :{ \n {} \n}", currentFile,content);

                                        var lines = content.split("\n");

                                        for (String line : lines)
                                        {
                                            logger.info(" content line {} aaa",line);

                                            if (events.get() < MAX_EVENTS && send(line))
                                            {
                                                logger.info("sent {} aaa", line);

                                                events.incrementAndGet();

                                                applicationContext.put("offset", applicationContext.getLong("offset",0L) + line.length() + 1);
                                            }
                                            else
                                            {
                                                return;
                                            }
                                        }

                                        if (events.get() >= MAX_EVENTS)
                                        {
                                            send("completed");
                                        }
                                        else
                                        {
                                            if (isAlive())
                                            {
                                                send("completed");

                                                FileStatusTracker.markFileAsRead(currentFile, applicationType);

                                                if (FileStatusTracker.readByAllApps(currentFile))
                                                {
                                                    logger.info("Deleting file {}", currentFile);

                                                    vertx.eventBus().send(Constants.EVENT_CLOSE_FILE, currentFile);

                                                }
                                                fileQueue.poll();

                                                if (fileQueue.peek() != null)
                                                    applicationContext.put("current.file", fileQueue.peek()).put("offset", 0);

                                                processNextFile(events);
                                            }
                                        }
                                    }
                                    else
                                    {
                                        processNextFile(events);

                                        logger.error("{} {} Error while reading file", applicationType, currentFile);
                                    }
                                }
                                catch (Exception exception)
                                {
                                    logger.error(exception.getMessage(), exception);
                                }
                            });
                }
            }
            else
                logger.info("file queue is empty for app {} ", applicationType);
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }

    public boolean send(String line)
    {
        try
        {
            if (isAlive())
            {
                logger.info("sending {} aaa", line);

                vertx.eventBus().send(Constants.EVENT_SEND + applicationType, line);

                return true;
            }
            else
                return false;
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);

            return false;
        }
    }

    private boolean isAlive()
    {
        return System.currentTimeMillis() - timeStamp <= PING_TIMEOUT;
    }

    @Override
    public void stop() throws Exception
    {
        if (pingSocket != null)
        {
            pingSocket.close();
        }
        super.stop();
    }
}
