package org.example.event;

import org.example.Constants.ApplicationType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.example.cache.FileStatusTracker;
import org.example.store.ApplicationContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class EventSender extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(EventSender.class);
    private static final int EVENT_INTERVAL = 60000 * 5; // 5 minutes
    private static final int MAX_EVENTS = 100; // 100 events in 5 minutes
    private static final int PING_TIMEOUT = 5000;

    private final static Pattern FILE_NAME_PATTERN = Pattern.compile("[^/]+$");

    private final ApplicationType applicationType;
    private final JsonObject applicationContext;

    private final static ZContext context = new ZContext();
    private final ZMQ.Socket pushSocket = context.createSocket(SocketType.PUSH);
    private final ZMQ.Socket pingSocket = context.createSocket(SocketType.PULL);

    private long timeStamp = System.currentTimeMillis();

    private final Queue<String> fileQueue = new ArrayDeque<>();

    public EventSender(ApplicationType applicationType)
    {
        this.applicationType = applicationType;

        applicationContext = ApplicationContextStore.getAppContext(applicationType);
    }

    @Override
    public void start()
    {
        try
        {
            initializeFileQueue();

            vertx.eventBus().<String>localConsumer(Constants.EVENT_NEW_FILE, file -> fileQueue.add(file.body()));
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }

    private void initializeFileQueue()
    {
        vertx.fileSystem().readDir(Constants.BASE_DIR, Constants.TEXT_FILE_REGEX).onComplete(dirResult ->
        {
            try
            {
                if (dirResult.succeeded())
                {
                    processDirectoryResults(dirResult.result()).compose(context ->
                    {
                        try
                        {
                            checkIsAlive();

                            startProcessing();

                            return Future.succeededFuture();
                        }
                        catch (Exception exception)
                        {
                            logger.error(exception.getMessage(), exception);

                            return Future.failedFuture(exception);
                        }
                    });
                }
                else
                {
                    logger.error("Failed to read directory: {}", dirResult.cause().getMessage());
                }
            }
            catch (Exception exception)
            {
                logger.error(exception.getMessage(), exception);
            }
        });
    }

    private Future<Void> processDirectoryResults(List<String> files)
    {
        try
        {
            if (files == null || files.isEmpty())
            {
                logger.warn("directory is currently empty");

                return Future.succeededFuture();
            }

            Collections.sort(files);

            if (applicationContext.getString("currentFile") == null || applicationContext.getString("currentFile")
                    .isEmpty())
            {
                applicationContext.put("currentFile", files.get(0));

                applicationContext.put("offset", 0);
            }

            for (String file : files)
            {
                try
                {
                    var matcher = FILE_NAME_PATTERN.matcher(file);

                    if (matcher.find())
                    {
                        var extractedPath = matcher.group(1);

                        //check if the file is already read by the application and if it is then dont add it to queue
                        if (FileStatusTracker.getFileStatus(extractedPath, applicationType))
                        {
                            continue;
                        }

                        fileQueue.add(extractedPath);
                    }
                    else
                    {
                        logger.error("No match found for: {}", file);
                    }
                }
                catch (Exception exception)
                {
                    logger.error("Error while finding pattern for file {} ",file);
                }
            }
            return Future.succeededFuture();
        }
        catch (Exception exception)
        {
            logger.error("Error while processing the read directory result: ", exception);

            return Future.failedFuture("Error while reading directory");
        }
    }

    private void startProcessing()
    {
        try
        {
            pushSocket.connect(
                    "tcp://" + applicationContext.getString("ip") + ":" + applicationContext.getInteger("port"));

            logger.info(
                    "conntected to tcp:// {} : {}", applicationContext.getString("ip"),
                    applicationContext.getInteger("port"));

            // Start periodic task but process one file at a time
            vertx.setPeriodic(EVENT_INTERVAL, id ->
            {
                try
                {
                    logger.info("periodic event sending started for {}", applicationType.toString());

                    if (isAlive())
                    {
                        logger.info("Connection is Alive {}", applicationType);

                        var events = new AtomicInteger(0);

                        processNextFile(events);
                    }
                    else
                    {
                        logger.warn("Receiver is disconnected, stopping event sending.");
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
                    read(currentFile, events);
                }
            }
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }

    private void read(String fileName, AtomicInteger events)
    {
        vertx.fileSystem().open(Constants.BASE_DIR + "/" + fileName, new OpenOptions()).onComplete(fileResult ->
        {
            try
            {
                if (fileResult.succeeded())
                {
                    pushSocket.send("filename " + fileName);

                    var asyncFile = fileResult.result();

                    var buffer = Buffer.buffer();

                    var currentOffset = new AtomicInteger(applicationContext.getInteger("offset",
                            0));

                    asyncFile.setReadPos(currentOffset.get());

                    asyncFile.handler(fileBuffer ->
                    {
                        try
                        {
                            buffer.appendBuffer(fileBuffer);

                            var content = buffer.toString();

                            String[] lines = content.split("\n");

                            for (String line : lines)
                            {
                                if (events.get() < MAX_EVENTS && send(line))
                                {
                                    events.incrementAndGet();

                                    currentOffset.addAndGet(line.length() + 1);
                                }
                                else
                                {
                                    // If event limit is reached, store the current file and offset
                                    applicationContext.put("currentFile", fileName).put("offset", currentOffset.get());

                                    return;
                                }
                            }
                        }
                        catch (Exception exception)
                        {
                            logger.error(exception.getMessage(), exception);
                        }

                    }).endHandler(context ->
                    {
                        try
                        {
                            asyncFile.close();

                            pushSocket.send("completed");

                            if (events.get() >= MAX_EVENTS)
                            {
                                logger.info(
                                        "completed sending {} events to {}", MAX_EVENTS, applicationType.toString());
                                // hit the event limit, store file and offset
                                applicationContext.put("currentFile", fileName).put("offset", currentOffset.get());
                            }
                            else
                            {
                                logger.info("Completed reading file: {}", fileName);

                                // finished reading the file, mark it as done and remove it from the queue
                                FileStatusTracker.markFileAsRead(fileName, applicationType);

                                if (FileStatusTracker.readByAllApps(fileName))
                                {
                                    logger.info("Deleting file {}", fileName);

                                    vertx.fileSystem().delete(fileName).onComplete(result ->
                                    {
                                        if (result.succeeded())
                                        {
                                            FileStatusTracker.removeFile(fileName);

                                            logger.info("file {} deleted.", fileName);
                                        }
                                        else
                                        {
                                            logger.info("Error in deleting file {}", fileName);
                                        }
                                    });
                                }

                                fileQueue.poll();

                                if (fileQueue.peek() != null)
                                    applicationContext.put("currentFile", fileQueue.peek()).put("offset", 0);

                                processNextFile(events);
                            }
                        }
                        catch (Exception exception)
                        {
                            logger.error(exception.getMessage(), exception);
                        }
                    }).exceptionHandler(error ->
                    {
                        logger.error("Error reading file: ", error);

                        asyncFile.close();
                    });
                }
                else
                {
                    logger.error("Failed to open file: {}", fileResult.cause().getMessage());
                }
            }
            catch (Exception exception)
            {
                logger.error("Error while reading file ", exception);
            }
        });
    }

    private boolean send(String line)
    {
        try
        {
            var json = new JsonObject(line);

            // Check if the receiving application is connected before sending
            if (isAlive())
            {
                pushSocket.send(json.encode());

                logger.info("Sent JSON event: {}", json.encode());

                return true;
            }
            else
            {
                logger.warn("Receiver is disconnected. Stopping event sending.");

                return false;
            }
        }
        catch (Exception exception)
        {
            logger.error("Failed to process line as JSON: {}", line, exception);

            return false;
        }
    }

    private void checkIsAlive()
    {
        try
        {
            pingSocket.connect(
                    "tcp://" + applicationContext.getString("ip") + ":" + applicationContext.getInteger("pingPort"));

            logger.info("Connected to ping socket at tcp://{}:{}", applicationContext.getString(
                    "ip"), applicationContext.getInteger("pingPort"));

            new Thread(() ->
            {
                while (true)
                {
                    try
                    {
                        var pong = pingSocket.recvStr();

                        if ("pong".equals(pong))
                        {
                            timeStamp = System.currentTimeMillis();
                        }
                        else
                        {
                            logger.error("Unexpected response: {}", pong);
                        }
                    }
                    catch (Exception exception)
                    {
                        logger.error(exception.getMessage(), exception);
                    }
                }
            }).start();
        }
        catch (Exception exception)

        {
            logger.error("Error while setting up ping-pong check: ", exception);
        }
    }

    private boolean isAlive()
    {
        return System.currentTimeMillis() - timeStamp <= PING_TIMEOUT;
    }


    @Override
    public void stop() throws Exception
    {
        if (pushSocket != null)
        {
            pushSocket.close();
        }
        super.stop();
    }
}