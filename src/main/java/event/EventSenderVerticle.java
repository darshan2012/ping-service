package event;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventSenderVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(EventSenderVerticle.class);
    private static final int LINES_PER_EVENT = 7;
    private static final int EVENT_INTERVAL = 50000; // 5 minutes
    private static final int MAX_EVENTS = 100; // 100 events in 5 minutes
    private final static String FILE_NAME_REGEX = ".*?(ping_data/.*\\.txt)$";
    private final static Pattern FILE_NAME_PATTERN = Pattern.compile(FILE_NAME_REGEX);
    private ApplicationType applicationType;
    private ApplicationContext applicationContext;
    private Queue<String> fileQueue = new ArrayDeque<>();
    private ZMQ.Socket pushSocket;
    private ZContext context;

    @Override
    public void start() throws Exception
    {
        try
        {
            applicationType = ApplicationType.valueOf(vertx.getOrCreateContext().config().getString("type").toUpperCase());
            applicationContext = ApplicationContextStore.getAppContext(applicationType);


            initializeFileQueue();

            //case where after initializing the file if the new file is added
            vertx.eventBus().consumer("new-file",file -> {
               fileQueue.add(file.body().toString());
            });
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(),exception);
        }
    }

    private void initializeFileQueue()
    {
        //later change directory path in variable
        vertx.fileSystem().readDir("/ping_data", ".*\\.txt$").onComplete(dirResult ->
        {
            try
            {
                if (dirResult.succeeded())
                {
                    processDirectoryResults(dirResult.result());
                    initializeZMQAndStartProcessing();
                }
                else
                {
                    logger.error("Failed to read directory: " + dirResult.cause().getMessage());
                }
            }
            catch (Exception exception)
            {
                logger.error(exception.getMessage(),exception);
            }
        });
    }

    private void processDirectoryResults(List<String> files)
    {
        try
        {
            if (applicationContext.getCurrentFile() == null || applicationContext.getCurrentFile().isEmpty())
            {
                applicationContext.setCurrentFile(files.get(0));
                applicationContext.setOffset(0);
            }

            for (String file : files)
            {
                Matcher matcher = FILE_NAME_PATTERN.matcher(file);
                if (matcher.find())
                {
                    String extractedPath = matcher.group(1);

                    //check if the file is already read by the application and if it is then dont add it to queue
                    if (FileStatusTracker.getFileReadStatus(file, applicationType))
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
        }
        catch (Exception exception)
        {
            logger.error("Error while processing the read directory result: ",exception);
        }
    }

    private void initializeZMQAndStartProcessing()
    {
        try
        {
            JsonObject config = config();
            String ip = config.getString("ip");
            int port = config.getInteger("port");
            String bindAddress = "tcp://" + ip + ":" + port;

            // Initialize ZeroMQ
            context = new ZContext();
            pushSocket = context.createSocket(SocketType.PUSH);
            pushSocket.bind(bindAddress);

            startPeriodicProcessing();
        }
        catch (Exception exception)
        {
            logger.error("Error while initializing zmq: ",exception);
        }
    }

    private void startPeriodicProcessing()
    {
        vertx.setPeriodic(EVENT_INTERVAL, id ->
        {
            try
            {
                if (!fileQueue.isEmpty())
                {

                }
                else
                {
                    logger.warn("No files left to send.");
//                    vertx.cancelTimer(id);
                }
            }
            catch (Exception exception)
            {

            }
        });
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