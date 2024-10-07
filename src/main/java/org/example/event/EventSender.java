package org.example.event;

import io.vertx.core.AbstractVerticle;
import org.example.Constants;
import org.example.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

public class EventSender extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(EventSender.class);

    private final ZMQ.Socket pushSocket = Main.zContext.createSocket(SocketType.PUB);

    public EventSender(String ip, Long port)
    {
        pushSocket.bind("tcp://" + ip + ":" + port);

        logger.info("bind to tcp:// {} : {}", ip, port);
    }

    @Override
    public void start()
    {
        try
        {
            vertx.eventBus().<String>localConsumer(Constants.EVENT_SEND, message -> pushSocket.send(message.body()));
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