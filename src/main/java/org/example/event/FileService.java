package org.example.event;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.example.cache.FileStatusTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;


public class FileService extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    private final HashMap<String, AsyncFile> files = new HashMap<>();

    private final HashMap<String, Long> timers = new HashMap<>();

    private static final Long TIMER_CHECK_INTERVAL = 60000L;

    private static final Long FILE_OPEN_TIMEOUT = 60000L * 60;

    @Override
    public void start()
    {
        try
        {
            vertx.eventBus()
                    .<JsonObject>localConsumer(Constants.EVENT_OPEN_FILE, message -> open(message.body()));

            vertx.eventBus()
                    .<JsonObject>localConsumer(Constants.EVENT_WRITE_FILE, message -> write(message.body()));

            vertx.eventBus()
                    .<JsonObject>localConsumer(Constants.EVENT_READ_FILE, context -> read(context, context.body()));

            vertx.eventBus().<String>localConsumer(Constants.EVENT_CLOSE_FILE, message -> close(message.body()));

            vertx.setPeriodic(TIMER_CHECK_INTERVAL, id ->
            {
                if (!timers.isEmpty())
                {
                    List<String> filesToClose = new ArrayList<>();

                    timers.forEach((filename, time) ->
                    {
                        if (Instant.now().toEpochMilli() - time >= FILE_OPEN_TIMEOUT)
                        {
                            filesToClose.add(filename);
                        }
                    });

                    filesToClose.forEach(this::close);
                }
            });

        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }

    private void close(String fileName)
    {
        try
        {
            logger.info("Closing file {}...", fileName);

            if (files.containsKey(fileName) && files.get(fileName) != null)
            {
                files.get(fileName).close()
                        .compose(closeResult ->
                        {
                            logger.info("File {} closed successfully.", fileName);

                            // After closing, delete the file
                            return vertx.fileSystem().delete(fileName);
                        })
                        .onSuccess(deleteResult ->
                        {
                            logger.info("File {} deleted successfully.", fileName);

                            // Clean up maps after successful delete
                            FileStatusTracker.removeFile(fileName);

                            files.remove(fileName);

                            timers.remove(fileName);

                        })
                        .onFailure(error ->
                        {
                            // Handle failure either in closing or deleting the file
                            logger.error("Error in closing or deleting file {}: {}", fileName, error.getMessage(), error);
                        });
            }
            else
            {
                // If the file isn't found, just remove the timer entry
                timers.remove(fileName);
            }

        }
        catch (Exception exception)
        {
            logger.error("Error while closing the file {}: {}", fileName, exception.getMessage(), exception);
        }
    }


//    private void close(String fileName)
//    {
//        try
//        {
//            logger.info("closing file {} ", fileName);
//
//            if (files.containsKey(fileName) && files.get(fileName) != null)
//            {
//                files.get(fileName).close().onComplete(result ->
//                {
//                    if (result.succeeded())
//                    {
//                        logger.info("Closed file {}", fileName);
//
//                        vertx.fileSystem().delete(fileName).onComplete(deleteResult ->
//                        {
//                            try
//                            {
//                                if (deleteResult.succeeded())
//                                {
//                                    FileStatusTracker.removeFile(fileName);
//
//                                    files.remove(fileName);
//
//                                    timers.remove(fileName);
//
//                                    logger.info("file {} deleted.", fileName);
//                                }
//                                else
//                                {
//                                    logger.info("Error in deleting file {}", fileName, deleteResult.cause());
//                                }
//                            }
//                            catch (Exception exception)
//                            {
//                                logger.error(exception.getMessage(), exception);
//                            }
//                        });
//                    }
//                    else
//                    {
//                        logger.error("Error in closing file {} ", fileName, result.cause());
//                    }
//                });
//            }
//            else
//                timers.remove(fileName);
//        }
//        catch (Exception exception)
//        {
//            logger.error("Error while closing the file {} ", fileName, exception);
//        }
//    }

    public void write(JsonObject context)
    {
        try
        {
            var file = files.get(context.getString("file.name"));

            if (file != null)
            {
                file.write(Buffer.buffer(context.getString("file.content"))).onComplete(result ->
                {
                    try
                    {
                        if (result.succeeded())
                        {
                            file.flush();

                            timers.put(context.getString("file.name"), Instant.now().toEpochMilli());

                            logger.info("File {} written successfully.", context.getString("file.name"));
                        }
                        else
                        {
                            logger.error("Error while writing to file: {}", context.getString("file.name"), result.cause());
                        }
                    }
                    catch (Exception exception)
                    {
                        logger.error("Exception while writing to file {}: ", context.getString("file.name"), exception);
                    }
                });
            }
            else
            {
                vertx.fileSystem()
                        .open(context.getString("file.name"), new OpenOptions().setAppend(true)
                                .setCreate(true)
                                .setRead(true)
                                .setWrite(true))
                        .onComplete(openFile ->
                        {
                            try
                            {
                                if (openFile.succeeded())
                                {
                                    var newFile = openFile.result();

                                    newFile.write(Buffer.buffer(context.getString("file.content"))).onComplete(result ->
                                    {
                                        try
                                        {
                                            if (result.succeeded())
                                            {
                                                newFile.flush();

                                                timers.put(context.getString("file.name"), Instant.now()
                                                        .toEpochMilli());

                                                files.put(context.getString("file.name"), newFile);

                                                FileStatusTracker.addFile(context.getString("file.name"));

                                                vertx.eventBus()
                                                        .publish(Constants.EVENT_NEW_FILE, context.getString("file.name"));

                                                logger.info("File written successfully {}", context.getString("file.name"));
                                            }
                                            else
                                            {
                                                logger.error("Error while writing in file: {} ", context.getString("path"), result.cause());

                                                newFile.close();
                                            }
                                        }
                                        catch (Exception exception)
                                        {
                                            logger.error(exception.getMessage(), exception);
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
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }

    }

    private void read(io.vertx.core.eventbus.Message<JsonObject> request, JsonObject context)
    {
        logger.info("read file starting {} ", context.toString());

        try
        {
            var file = files.get(context.getString("file.name"));

            if (file == null)
            {
                logger.warn("file is null {} ", context.getString("file.name"));

                return;
            }

            var content = Buffer.buffer();

            var offset = context.getLong("offset", 0L);

            file.setReadPos(offset);

            logger.info("reading file {} ", context.getString("file.name"));

            file.handler(fileBuffer ->
            {
                try
                {
                    content.appendBuffer(fileBuffer);
                }
                catch (Exception exception)
                {
                    logger.error(exception.getMessage(), exception);
                }
            }).endHandler(handler ->
            {
                if (content.length() > 0)
                {
                    logger.info("Read complete for file {}. Data: {}", context.getString("file.name"), content);

                    request.reply(content);
                }
                else
                {
                    logger.warn("No data read from file: {}", context.getString("file.name"));

                    request.fail(204, "No content found at the given offset");
                }
            }).exceptionHandler(exception ->
            {
                logger.error("Error while reading file {}: {}", context.getString("file.name"), exception.getMessage(), exception);

                request.fail(500, "Error reading file");
            });

        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }

    private void open(JsonObject context)
    {
        try
        {
            logger.info("opening file {}", context.getString("file.name"));

            String fileName = context.getString("file.name");

            if (files.containsKey(fileName) && files.get(fileName) != null)
            {
                timers.put(fileName, Instant.now().toEpochMilli());

                logger.info("File {} is already open, timer reset.", fileName);

                return;
            }

            vertx.fileSystem()
                    .open(fileName, new OpenOptions().setAppend(true).setRead(true).setWrite(true))
                    .onComplete(openFile ->
                    {
                        try
                        {
                            if (openFile.succeeded())
                            {
                                var file = openFile.result();

                                files.put(fileName, file);

                                timers.put(fileName, Instant.now().toEpochMilli());

                                FileStatusTracker.addFile(fileName);

                                logger.info("File {} opened successfully.", fileName);
                            }
                            else
                            {
                                logger.error("Failed to open file {}", fileName, openFile.cause());
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
            logger.error(exception.getMessage(), exception);
        }

    }

}
