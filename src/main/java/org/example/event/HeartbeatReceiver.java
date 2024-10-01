package org.example.event;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.example.Constants;
import org.example.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

public class HeartbeatReceiver extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatReceiver.class);

    private final ZMQ.Socket socket = Main.zContext.createSocket(SocketType.PULL);

    private final Constants.ApplicationType applicationType;

    public HeartbeatReceiver(Constants.ApplicationType applicationType, String ip, int pingPort)
    {
        this.applicationType = applicationType;

        socket.connect("tcp://" + ip + ":" + pingPort);
    }

    @Override
    public void start()
    {
        try
        {
            new Thread(() ->
            {
                while (true)
                {
                    try
                    {
                        var message = socket.recvStr();

                        if ("I am alive".equals(message))
                        {
                            vertx.eventBus().send(Constants.EVENT_HEARTBEAT + applicationType,System.currentTimeMillis());
                        }
                        else
                        {
                            logger.error("Unexpected response: {}", message);
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

    @Override
    public void stop(Promise<Void> stopPromise) throws Exception
    {
        if(socket != null)
            socket.close();
        super.stop(stopPromise);
    }
}
