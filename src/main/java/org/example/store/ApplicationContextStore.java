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

    public static void setAppContext(ApplicationType applicationType, String ip, int pingPort)
    {
        contexts.putIfAbsent(applicationType, new JsonObject());

        contexts.get(applicationType).put("ip", ip).put("ping.port", pingPort);
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

    public static void write()
    {
        Main.vertx.executeBlocking(() ->
        {
            try
            {
                Util.createFileIfNotExist(FILE_PATH);

                var context = new JsonObject();

                for (Map.Entry<ApplicationType, JsonObject> entry : contexts.entrySet())
                {
                    context.put(entry.getKey().toString(), entry.getValue());
                }

                Main.vertx.fileSystem().writeFileBlocking(FILE_PATH, Buffer.buffer(context.encodePrettily()));

                logger.info("Contexts successfully written to file.{} {}", FILE_PATH, context.encode());

                return Future.succeededFuture();
            }
            catch (Exception exception)
            {
                logger.error(exception.getMessage(),exception);

                return Future.failedFuture(exception);
            }
        });
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

                logger.info("Contexts successfully read from file {} {}", FILE_PATH, content);

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
