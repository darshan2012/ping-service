package org.example.store;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.Main;
import org.example.event.ApplicationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class ApplicationContextStore
{
    private static final Logger logger = LoggerFactory.getLogger(ApplicationContextStore.class);

    private static Map<ApplicationType, JsonObject> contexts = new HashMap<>();

    private final static String CONTEXT_FILE = "data/context.txt";

    public static JsonObject getAppContext(ApplicationType applicationType)
    {
        return contexts.get(applicationType);
    }

    public static void setAppContext(ApplicationType applicationType, String ip, int port, int pingPort)
    {
        contexts.putIfAbsent(applicationType, new JsonObject());

        contexts.get(applicationType).put("ip", ip).put("port", port).put("pingPort", pingPort);
    }

    public static Set<ApplicationType> getApplications()
    {
        return contexts.keySet();
    }

    public static int getApplicationCount()
    {
        return contexts.size();
    }

    public static Future<Void> loadContextFromFile()
    {
        Promise<Void> promise = Promise.promise();  // Create a promise to handle future completion

        Main.vertx.fileSystem().exists(CONTEXT_FILE, existResult ->
        {
            if (existResult.succeeded() && existResult.result())
            {
                var options = new OpenOptions().setRead(true);

                Main.vertx.fileSystem().open(CONTEXT_FILE, options, openResult ->
                {
                    if (openResult.succeeded())
                    {
                        var file = openResult.result();

                        var totalBuffer = Buffer.buffer();

                        file.handler(totalBuffer::appendBuffer)
                                .endHandler(v ->
                                {
                                    try
                                    {
                                        byte[] bytes = totalBuffer.getBytes();

                                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

                                        ObjectInputStream objectInputStream = new ObjectInputStream(
                                                byteArrayInputStream);

                                        Map<ApplicationType, JsonObject> loadedContexts =
                                                (Map<ApplicationType, JsonObject>) objectInputStream.readObject();

                                        contexts.clear();

                                        contexts.putAll(loadedContexts);

                                        logger.info("Context loaded from {}", CONTEXT_FILE);

                                        promise.complete();
                                    }
                                    catch (IOException | ClassNotFoundException e)
                                    {
                                        logger.error("Error loading context from {}: {}", CONTEXT_FILE, e.getMessage());

                                        promise.fail(e);
                                    }
                                    finally
                                    {
                                        file.close();
                                    }
                                })
                                .exceptionHandler(throwable ->
                                {
                                    logger.error("Error reading file {}: {}", CONTEXT_FILE, throwable.getMessage());

                                    file.close();

                                    promise.fail(throwable);
                                });
                    }
                    else
                    {
                        logger.error("Failed to open file {}: {}", CONTEXT_FILE, openResult.cause().getMessage());

                        // Fail the promise if opening the file fails
                        promise.fail(openResult.cause());
                    }
                });
            }
            else
            {
                if (existResult.succeeded())
                {
                    logger.warn("Context file does not exist. Starting with empty context.");
                    promise.complete();  // No context file, but still successful (empty context)
                }
                else
                {
                    logger.error("Failed to check if file exists: {}", existResult.cause().getMessage());

                    // Fail the promise if file existence check fails
                    promise.fail(existResult.cause());
                }
            }
        });

        // Return the future from the promise
        return promise.future();
    }

    public static void writeContextToFile()
    {
        try
        {
            ByteArrayOutputStream ByteArrayOutputStream = new ByteArrayOutputStream();

            ObjectOutputStream objectOutputStream = new ObjectOutputStream(ByteArrayOutputStream);

            Main.vertx.setPeriodic(60000, id ->
            {
                try
                {
                    objectOutputStream.writeObject(contexts);

                    objectOutputStream.flush();

                    var buffer = Buffer.buffer(ByteArrayOutputStream.toByteArray());

                    var options = new OpenOptions().setWrite(true).setCreate(true).setTruncateExisting(true);

                    Main.vertx.fileSystem().open(CONTEXT_FILE, options, openResult ->
                    {
                        if (openResult.succeeded())
                        {
                            var file = openResult.result();

                            file.write(buffer, 0, writeResult ->
                            {
                                if (writeResult.succeeded())
                                {
                                    logger.info("Context written to {}", CONTEXT_FILE);
                                }
                                else
                                {
                                    logger.error("Failed to write context to {}: {}", CONTEXT_FILE,
                                            writeResult.cause().getMessage());
                                }
                                file.close();
                            });
                        }
                        else
                        {
                            logger.error("Failed to open file for writing {}: {}", CONTEXT_FILE,
                                    openResult.cause().getMessage());
                        }
                    });
                }
                catch (Exception exception)
                {
                    logger.error(exception.getMessage(), exception);
                }

            });
        }
        catch (IOException e)
        {
            logger.error("Error preparing context for writing: {}", e.getMessage());
        }
    }

}
