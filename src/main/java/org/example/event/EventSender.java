package org.example.event;

import org.example.Constants.ApplicationType;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.example.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

public class EventSender extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(EventSender.class);

    private final ApplicationType applicationType;

    private final ZMQ.Socket pushSocket = Main.zContext.createSocket(SocketType.PUSH);

    public EventSender(ApplicationType applicationType, String ip, int port)
    {
        this.applicationType = applicationType;

        pushSocket.connect("tcp://" + ip + ":" + port);

        logger.info("conntected to tcp:// {} : {} for app {}", ip, port, applicationType);
    }

    public EventSender(ApplicationType applicationType, JsonObject context)
    {
        this(applicationType,context.getString("ip"),context.getInteger("port"));
    }

    @Override
    public void start()
    {
        try
        {
            vertx.eventBus().<String>localConsumer(Constants.EVENT_SEND + applicationType, message -> pushSocket.send(message.body()));
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
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