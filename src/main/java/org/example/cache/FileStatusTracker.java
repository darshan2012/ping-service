package org.example.cache;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.streams.ReadStream;
import org.example.Main;
import org.example.Constants.ApplicationType;
import org.example.store.ApplicationContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileStatusTracker
{
    // Map to store file names and read statuses for three applications.
    // ApplicationStatus holds the read status for Primary, Secondary, and Failure
    private static final ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>> fileStatuses = new ConcurrentHashMap<>();

    private static final String FILE_PATH = "data/data/filestatuses.txt";

    private static final Logger logger = LoggerFactory.getLogger(FileStatusTracker.class);

    public static void markFileAsRead(String fileName, ApplicationType appType)
    {
        logger.info("fileReadStatus : " + fileStatuses.get(fileName));

        if(fileStatuses.get(fileName) != null)
            fileStatuses.get(fileName).put(appType, true);
    }

    public static boolean getFileStatus(String fileName, ApplicationType applicationType)
    {
        try
        {
            if (fileStatuses.get(fileName) == null)
            {
                return true;
            }
            var status = fileStatuses.get(fileName).get(applicationType);

            if (status == null)
                return true;

            return status;
        }
        catch (Exception exception)
        {
            return true;
        }
    }

    public static void addFile(String fileName)
    {
        HashMap<ApplicationType, Boolean> appsWithStatus = new HashMap<>();

        var applications = ApplicationContextStore.getApplications();

        for (var app : applications)
        {
            appsWithStatus.put(app, false);
        }

        fileStatuses.putIfAbsent(fileName, appsWithStatus);
    }

    public static boolean readByAllApps(String fileName)
    {
        AtomicBoolean allAppsCompeted = new AtomicBoolean(true);

        fileStatuses.get(fileName).forEach((applicationType, status) ->
        {
            if (!status)
            {
                allAppsCompeted.set(false);
            }
        });

        return allAppsCompeted.get();
    }

    public static boolean removeFile(String fileName)
    {
        if (readByAllApps(fileName))
        {
            fileStatuses.remove(fileName);

            return true;
        }
        return false;
    }

    public static Future<Void> read()
    {
        Promise<Void> promise = Promise.promise();  // Create a promise to handle future completion

        Main.vertx.fileSystem().exists(FILE_PATH, existResult ->
        {
            try
            {
                if (existResult.succeeded() && existResult.result())
                {
                    var options = new OpenOptions().setRead(true);

                    Main.vertx.fileSystem().open(FILE_PATH, options, openResult ->
                    {
                        if (openResult.succeeded())
                        {
                            var file = openResult.result();

                            var totalBuffer = Buffer.buffer();

                            ReadStream<Buffer> readStream = file;

                            readStream.handler(totalBuffer::appendBuffer);

                            readStream.endHandler(v ->
                            {
                                try
                                {
                                    byte[] bytes = totalBuffer.getBytes();

                                    var byteArrayInputStream = new ByteArrayInputStream(bytes);

                                    var objectInputStream = new ObjectInputStream(byteArrayInputStream);

                                    ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>> loadedStatuses =
                                            (ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>>) objectInputStream.readObject();

                                    fileStatuses.clear();

                                    fileStatuses.putAll(loadedStatuses);

                                    logger.info("File status loaded from {}", FILE_PATH);

                                    promise.complete();
                                }
                                catch (IOException | ClassNotFoundException e)
                                {
                                    logger.error("Error loading file status from {}: {}", FILE_PATH, e.getMessage());

                                    promise.fail(e);
                                }
                                finally
                                {
                                    file.close();
                                }
                            });

                            readStream.exceptionHandler(throwable ->
                            {
                                logger.error("Error reading file {}: {}", FILE_PATH, throwable.getMessage());

                                file.close();

                                promise.fail(throwable);
                            });

                        }
                        else
                        {
                            logger.error("Failed to open file {}: {}", FILE_PATH, openResult.cause().getMessage());

                            promise.fail(openResult.cause());
                        }
                    });
                }
                else
                {
                    if (existResult.succeeded())
                    {
                        logger.warn("Status file does not exist. Starting with empty status.");

                        promise.complete();
                    }
                    else
                    {
                        logger.error("Failed to check if file exists: {}", existResult.cause().getMessage());

                        promise.fail(existResult.cause());
                    }
                }

            }
            catch (Exception exception)
            {
                logger.error(exception.getMessage(),exception);
            }
        });

        return promise.future();
    }

    public static void write()
    {
        try
        {
            var byteArrayOutputStream = new ByteArrayOutputStream();

            var objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);

            try
            {
                objectOutputStream.writeObject(fileStatuses);

                objectOutputStream.flush();

                var buffer = Buffer.buffer(byteArrayOutputStream.toByteArray());

                var options = new OpenOptions().setWrite(true).setCreate(true).setTruncateExisting(true);

                Main.vertx.fileSystem().open(FILE_PATH, options, openResult ->
                {
                    try
                    {
                        if (openResult.succeeded())
                        {
                            var file = openResult.result();
                            file.write(buffer, 0, writeResult ->
                            {
                                if (writeResult.succeeded())
                                {
                                    logger.info("File status written to {}", FILE_PATH);
                                }
                                else
                                {
                                    logger.error("Failed to write file status to {}: {}", FILE_PATH,
                                            writeResult.cause().getMessage());
                                }
                                file.close();
                            });
                        }
                        else
                        {
                            logger.error("Failed to open file for writing {}: {}", FILE_PATH,
                                    openResult.cause().getMessage());
                        }
                    }
                    catch (Exception exception)
                    {
                        logger.error(exception.getMessage(),exception);
                    }
                });
            }
            catch (Exception exception)
            {
                logger.error(exception.getMessage(), exception);
            }

        }
        catch (IOException exception)
        {
            logger.error("Error preparing file status for writing: {}", exception.getMessage());
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(),exception);
        }
    }


}
