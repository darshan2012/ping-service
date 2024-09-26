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

    public static int getApplicationCount() {
        return contexts.size();
    }

    public static boolean contains(ApplicationType applicationType){
        return contexts.containsKey(applicationType);
    }

    public static Set<ApplicationType> getApplications()
    {
        return contexts.keySet();
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
                        logger.debug("Contexts successfully written to file.{} {}",FILE_PATH, context.encodePrettily());

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
        Main.vertx.executeBlocking(future ->
        {
            Main.vertx.fileSystem().readFile(FILE_PATH, result ->
            {
                try
                {
                    if (result.succeeded())
                    {
                        try
                        {
                            if (result.result().toString().isEmpty())
                            {
                                promise.complete();

                                return;
                            }

                            var fileContent = result.result().toJsonObject();

                            contexts.clear();

                            for (String key : fileContent.fieldNames())
                            {
                                contexts.put(ApplicationType.valueOf(key), fileContent.getJsonObject(key));
                            }

                            logger.info("Contexts successfully read from file.");

                            future.complete();
                        }
                        catch (Exception exception)
                        {
                            logger.error("Error while reading contexts from file: ", e);

                            future.fail(exception);
                        }
                    }
                    else
                    {
                        logger.error("Failed to read file: ", result.cause());

                        future.fail(result.cause());
                    }
                }
                catch (Exception exception)
                {
                    logger.error("Error while reading file {}", FILE_PATH);
                }
            });
        }, false, promise);

        return promise.future();
    }
}
