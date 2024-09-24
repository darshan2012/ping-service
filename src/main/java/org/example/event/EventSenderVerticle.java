package org.example.event;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class EventSenderVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(EventSenderVerticle.class);
    private static final int EVENT_INTERVAL = 60000 ; // 5 minutes
    private static final int MAX_EVENTS = 10; // 100 events in 5 minutes
    private static final int PING_INTERVAL = 1000; // Ping every 1 seconds
    private static final int PING_TIMEOUT = 1000; // 1 seconds timeout for pong


    private final static String FILE_NAME_REGEX = ".*?(ping_data/.*\\.txt)$";
    private final static Pattern FILE_NAME_PATTERN = Pattern.compile(FILE_NAME_REGEX);

    private ApplicationType applicationType;
    private JsonObject applicationContext;

    private final static ZContext context = new ZContext();
    private ZMQ.Socket pushSocket = context.createSocket(SocketType.PUSH);
    private ZMQ.Socket pingSocket = context.createSocket(SocketType.REQ);

    private ZMQ.Poller poller = context.createPoller(1);

    private boolean isReceivingAppConnected = true;


    //think later if this should be moved to appcontext or keep it here
    private Queue<String> fileQueue = new ArrayDeque<>();

    public EventSenderVerticle(ApplicationType applicationType)
    {
        this.applicationType = applicationType;

        applicationContext = ApplicationContextStore.getAppContext(applicationType);
    }

    @Override
    public void start() throws Exception
    {
        try
        {
            initializeFileQueue();
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }

    private void initializeFileQueue()
    {
        //later change directory path in variable
        vertx.fileSystem().readDir("ping_data", ".*\\.txt$").onComplete(dirResult ->
        {
            try
            {
                if (dirResult.succeeded())
                {
                    processDirectoryResults(dirResult.result()).compose(context -> {

                        try
                        {
                            startPingPongCheck();

                            startPeriodicProcessing();

                            return Future.succeededFuture();
                        }
                        catch (Exception exception)
                        {
                            logger.error(exception.getMessage(),exception);

                            return Future.failedFuture(exception);
                        }
                    });
                }
                else
                {
                    logger.error("Failed to read directory: " + dirResult.cause().getMessage());
                }
            }
            catch (Exception exception)
            {
                logger.error(exception.getMessage(), exception);
            }
        });
    }

    private Future processDirectoryResults(List<String> files)
    {
        try
        {
            Collections.sort(files);

            if (files == null || files.isEmpty())
            {
                logger.warn("directory is currently empty");

                return Future.succeededFuture();
            }
            if (applicationContext.getString("currentFile") == null || applicationContext.getString("currentFile")
                    .isEmpty())
            {
                applicationContext.put("currentFile", files.get(0));

                applicationContext.put("offset", 0);
            }

            for (String file : files)
            {
                var matcher = FILE_NAME_PATTERN.matcher(file);
                if (matcher.find())
                {
                    var extractedPath = matcher.group(1);

                    //another way to do this can be matchint with currentfile
                    //check if the file is already read by the application and if it is then dont add it to queue
                    if (FileStatusTracker.getFileReadStatus(extractedPath, applicationType))
                    {
                        continue;
                    }
                    fileQueue.add(extractedPath);
                }
                else
                {
                    logger.error("No match found for: " + file);
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

    private void startPeriodicProcessing()
    {
        try
        {
            pushSocket.connect(
                    "tcp://" + applicationContext.getString("ip") + ":" + applicationContext.getInteger("port"));

            logger.info(
                    "conntected to tcp://" + applicationContext.getString("ip") + ":" + applicationContext.getInteger(
                            "port"));

            // Start periodic task but process one file at a time
            vertx.setPeriodic(EVENT_INTERVAL, id ->
            {
                try
                {
                    logger.info("periodic event sending started for " + applicationType.toString());

                    if (isReceivingAppConnected)
                    {
                        var eventsSent = new AtomicInteger(0); // Wrap the counter in AtomicInteger

                        processNextFile(eventsSent);
                    }
                    else
                    {
                        logger.warn("Receiver is disconnected, stopping event sending.");

                        vertx.undeploy(vertx.getOrCreateContext().deploymentID()).onComplete(result -> {
                            if (result.succeeded())
                            {
                                logger.info("EventSenderVerticle undeployed successfully for application type " + applicationType.toString());
                            }
                        });
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

    private void processNextFile(AtomicInteger eventsSent)
    {
        try
        {
            if (!fileQueue.isEmpty() && eventsSent.get() < MAX_EVENTS)
            {
                var currentFile = fileQueue.peek();

                if (currentFile != null)
                {
                    readAndSendEventsFromFile(currentFile, eventsSent);
                }
            }
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }

    private void readAndSendEventsFromFile(String fileName, AtomicInteger eventsSent)
    {
        vertx.fileSystem().open(fileName, new OpenOptions().setRead(true)).onComplete(fileResult ->
        {
            try
            {
                if (fileResult.succeeded())
                {
                    var asyncFile = fileResult.result();

                    var buffer = Buffer.buffer();

                    final int[] currentOffset = {applicationContext.getInteger("offset",
                            0)};

                    asyncFile.setReadPos(currentOffset[0]);

                    asyncFile.handler(fileBuffer ->
                    {
                        try
                        {
                            buffer.appendBuffer(fileBuffer);

                            var content = buffer.toString();

                            String[] lines = content.split("\n");

                            for (String line : lines)
                            {
                                if (eventsSent.get() < MAX_EVENTS && processLineAndSend(line))
                                {
                                    eventsSent.incrementAndGet();

                                    currentOffset[0] += line.length() + 1;
                                }
                                else
                                {
                                    // If event limit is reached, store the current file and offset
                                    applicationContext.put("currentFile", fileName).put("offset", currentOffset[0]);

                                    return;
                                }
                            }
                        }
                        catch (Exception exception)
                        {
                            logger.error(exception.getMessage(),exception);
                        }

                    }).endHandler(v ->
                    {
                        try
                        {
                            asyncFile.close();
                            if (eventsSent.get() >= MAX_EVENTS)
                            {
                                logger.info("completed sending " + MAX_EVENTS + " events to " + applicationType.toString());
                                // hit the event limit, store file and offset
                                applicationContext.put("currentFile", fileName).put("offset", currentOffset[0]);
                            }
                            else
                            {
                                logger.info("Completed reading file: " + fileName);

                                // finished reading the file, mark it as done and remove it from the queue
                                FileStatusTracker.markFileAsRead(fileName, applicationType);

                                if (FileStatusTracker.allAppsCompleted(fileName))
                                {
                                    logger.info("Deleting file " + fileName);

                                    FileStatusTracker.removeFile(fileName);

                                    vertx.fileSystem().delete(fileName).onComplete(fileDeleteResult ->
                                    {
                                        if (fileDeleteResult.succeeded())
                                        {
                                            logger.info("file " + fileName + " deleted.");
                                        }
                                        else
                                        {
                                            logger.info("Error in deleting file " + fileName);
                                        }
                                    });
                                }
                                fileQueue.poll(); // Remove from queue

                                applicationContext.put("currentFile", fileName).put("offset", 0);

                                processNextFile(eventsSent); // Continue processing the next file in the queue
                            }
                        }
                        catch (Exception exception)
                        {
                            logger.error(exception.getMessage(),exception);
                        }
                    }).exceptionHandler(error ->
                    {
                        logger.error("Error reading file: ", error);

                        asyncFile.close(); // Ensure the file is closed on error
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


    private boolean processLineAndSend(String line)
    {
        try
        {
            var json = new JsonObject(line);

            // Check if the receiving application is connected before sending
            if (isReceivingAppConnected)
            {
                pushSocket.send(json.encode());

                logger.info("Sent JSON event: {}", json.encode());

                return true;
            }
            else
            {
                logger.warn("Receiver is disconnected. Stopping event sending.");

                vertx.undeploy(vertx.getOrCreateContext().deploymentID()).onComplete(result -> {
                    if (result.succeeded())
                    {
                        logger.info("EventSenderVerticle undeployed successfully for application type " + applicationType.toString());
                    }
                });
                return false;
            }
        }
        catch (Exception e)
        {
            logger.error("Failed to process line as JSON: {}", line, e);

            return false; // Failed to send the event
        }
    }

    private void startPingPongCheck() {
        try {
            pingSocket.connect("tcp://" + applicationContext.getString("ip") + ":" + applicationContext.getInteger("pingPort"));

            logger.info("Connected to ping socket at tcp://" + applicationContext.getString("ip") + ":" + applicationContext.getInteger("pingPort"));

            poller.register(pingSocket, ZMQ.Poller.POLLIN);

            vertx.setPeriodic(PING_INTERVAL, id -> {
                try
                {
                    pingSocket.send("ping");

                    var pollResult = poller.poll(PING_TIMEOUT);

                    if (pollResult > 0 && poller.pollin(0))
                    {
                        var pong = pingSocket.recvStr();
                        if ("pong".equals(pong))
                        {
                            logger.info("Connection is alive.");
                        }
                        else
                        {
                            System.out.println("Unexpected response: " + pong);
                        }
                    }
                    else
                    {
                        System.out.println("No response from PULL socket, stopping tasks...");

                        isReceivingAppConnected = false;

                        vertx.cancelTimer(id);  // Stop sending tasks if disconnected
                    }
                }
                catch (ZMQException zmqException)
                {
                    System.out.println("ZeroMQ Error: " + zmqException.getMessage());
                }
                catch (Exception exception)
                {
                    logger.error(exception.getMessage(),exception);
                }
            });
        } catch (Exception exception) {
            logger.error("Error while setting up ping-pong check: ", exception);
        }
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