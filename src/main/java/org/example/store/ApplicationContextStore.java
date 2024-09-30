package org.example.store;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.example.Main;
import org.example.Util;
import org.example.Constants.ApplicationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class ApplicationContextStore
{
    private static final Logger logger = LoggerFactory.getLogger(ApplicationContextStore.class);

    private static final Map<ApplicationType, JsonObject> contexts = new HashMap<>();

    private final static String FILE_PATH = Constants.BASE_DIR + "/data/context.txt";

    public static JsonObject getAppContext(ApplicationType applicationType)
    {
        return contexts.get(applicationType);
    }

    public static void setAppContext(ApplicationType applicationType, String ip, int port, int pingPort)
    {
        contexts.putIfAbsent(applicationType, new JsonObject());

        contexts.get(applicationType).put("ip", ip).put("port", port).put("ping.port", pingPort);
    }

    public static int getApplicationCount()
    {
        return contexts.size();
    }

    public static boolean contains(ApplicationType applicationType)
    {
        return contexts.containsKey(applicationType);
    }

    public static Set<ApplicationType> getApplications()
    {
        return contexts.keySet();
    }

    public static void removeApplication(ApplicationType applicationType)
    {
        if (contexts.containsKey(applicationType))
        {
            contexts.remove(applicationType);
        }
    }

    public static Future<Void> write()
    {
        Promise<Void> promise = Promise.promise();

        Main.vertx.executeBlocking(future ->
        {
            try
            {
                Util.createFileIfNotExist(FILE_PATH);

                var context = new JsonObject();

                for (Map.Entry<ApplicationType, JsonObject> entry : contexts.entrySet())
                {
                    context.put(entry.getKey().toString(), entry.getValue());
                }

                Main.vertx.fileSystem().writeFile(FILE_PATH, Buffer.buffer(context.encodePrettily()), result ->
                {
                    if (result.succeeded())
                    {
                        logger.info("Contexts successfully written to file.{} {}", FILE_PATH, context.encodePrettily());

                        future.complete();
                    }
                    else
                    {
                        logger.error("Failed to write contexts to file: ", result.cause());

                        future.fail(result.cause());
                    }
                });
            }
            catch (Exception exception)
            {
                future.fail(exception);
            }
        }, false, promise);

        return promise.future();
    }

    public static Future<Void> read()
    {
        Promise<Void> promise = Promise.promise();

        if (!Main.vertx.fileSystem().existsBlocking(FILE_PATH))
        {
            promise.complete();

            return promise.future();
        }

        Main.vertx.executeBlocking(() ->
        {
            try
            {
                var content = Main.vertx.fileSystem().readFileBlocking(FILE_PATH).toJsonObject();

                contexts.clear();

                for (String key : content.fieldNames())
                {
                    contexts.put(ApplicationType.valueOf(key), content.getJsonObject(key));
                }

                logger.info("Contexts successfully read from file {} ", FILE_PATH, content);

                return Future.succeededFuture();
            }
            catch (Exception exception)
            {
                logger.error("Error while reading contexts from file: ", exception);

                return Future.failedFuture(exception);
            }
        }).onComplete(result ->
        {
            if (result.succeeded())
            {
                promise.complete();

                logger.info("{} file read successfully", FILE_PATH);
            }
            else
            {
                logger.error("Error while reading file {}", FILE_PATH, result.cause());

                promise.fail(result.cause());
            }
        });

        return promise.future();
    }
}
