package org.example.poll;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Scheduler extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(Scheduler.class);

    private static final long INTERVAL = 1000;

    private final Queue<JsonObject> objects = new LinkedList<>();

    private final List<JsonObject> lastPolledObjects = new ArrayList<>();

    @Override
    public void start()
    {
        try
        {
            EventBus eventBus = vertx.eventBus();

            eventBus.<JsonObject>localConsumer(Constants.OBJECT_PROVISION, message ->
                    objects.add(message.body()
                            .put("next.poll.time", Instant.now().plus(Constants.POLL_INTERVALS.getLong(message.body().getString("metric")), TimeUnit.MILLISECONDS.toChronoUnit()).toEpochMilli()))
            );

            schedule();
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(),exception);
        }
    }

    private void schedule()
    {
        vertx.setPeriodic(INTERVAL, id ->
        {
            try
            {
                while (!objects.isEmpty() && objects.peek().getLong("next.poll.time") <= Instant.now().getEpochSecond())
                {
                    lastPolledObjects.add(objects.peek());

                    vertx.eventBus().send(Constants.START_POLLING, objects.poll());
                }

                if (!lastPolledObjects.isEmpty())
                {
                    for (var object : lastPolledObjects)
                    {
                        object.put("next.poll.time", Instant.now()
                                .plus(Constants.POLL_INTERVALS.getLong(object.getString("metric")), TimeUnit.SECONDS.toChronoUnit()).toEpochMilli());

                        objects.add(object);
                    }
                    lastPolledObjects.clear();
                }
            }
            catch (Exception exception)
            {
                logger.error("Error in scheduling: ", exception);
            }
        });
    }
}