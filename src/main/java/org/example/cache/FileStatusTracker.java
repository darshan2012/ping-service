package org.example.cache;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.example.Constants;
import org.example.Main;
import org.example.Constants.ApplicationType;
import org.example.Util;
import org.example.store.ApplicationContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FileStatusTracker
{
    // Map to store file names and read statuses for three applications.
    // ApplicationStatus holds the read status for Primary, Secondary, and Failure
    private static final ConcurrentHashMap<String, Set<ApplicationType>> fileStatuses = new ConcurrentHashMap<>();

    private static final String FILE_PATH = Constants.BASE_DIR + "/data/filestatuses.txt";

    private static final Logger logger = LoggerFactory.getLogger(FileStatusTracker.class);

    public static void markFileAsRead(String fileName, ApplicationType appType)
    {
        try
        {
            if (fileStatuses.containsKey(fileName))
            {
                fileStatuses.get(fileName).remove(appType);
            }
        }
        catch (Exception exception)
        {
            logger.error("Error while removing the file from fileStatuses", exception);
        }
    }

    public static boolean getFileStatus(String fileName, ApplicationType applicationType)
    {
        try
        {
            if (fileStatuses.containsKey(fileName))
            {
                return true;
            }

            return fileStatuses.get(fileName).contains(applicationType);
        }
        catch (Exception exception)
        {
            return true;
        }
    }

    public static void addFile(String fileName)
    {
        fileStatuses.putIfAbsent(fileName, ApplicationContextStore.getApplications());
    }

    public static void addFile(String fileName, Set<ApplicationType> applicationTypes)
    {
        fileStatuses.putIfAbsent(fileName, applicationTypes);
    }

    public static boolean readByAllApps(String fileName)
    {
        if (fileStatuses.containsKey(fileName))
            return fileStatuses.get(fileName).isEmpty();

        return false;
    }

    //returns the Queue of unread file for that application
    public static Queue<String> getFiles(ApplicationType applicationType)
    {
        Queue<String> unreadFiles = new LinkedList<>();

        fileStatuses.forEach((fileName, appTypes) ->
        {
            if (appTypes.contains(applicationType))
            {
                unreadFiles.offer(fileName);
            }
        });

        return unreadFiles;
    }


    public static void removeFile(String fileName)
    {
        if (fileStatuses.contains(fileName))
        {
            fileStatuses.remove(fileName);
        }
    }

    public static void write()
    {
        try
        {
            Main.vertx.executeBlocking(() ->
            {
                try
                {
                    if (!Util.createFileIfNotExist(FILE_PATH))
                    {
                        logger.error("Error while writing fileStatuses: Could not create file");

                        return Future.failedFuture("Could not create file");
                    }

                    var buffer = Buffer.buffer();

                    var context = new JsonObject();

                    fileStatuses.forEach((fileName, appTypes) ->
                    {
                        var appTypesArray = new JsonArray();

                        appTypes.forEach(appType -> appTypesArray.add(appType.name()));

                        context.put(fileName, appTypesArray);
                    });

                    buffer.appendString(context.encodePrettily());

                    Main.vertx.fileSystem().writeFileBlocking(FILE_PATH, buffer);

                    logger.info("Successfully wrote fileStatuses to file {}", FILE_PATH);

                    return Future.succeededFuture();
                }
                catch (Exception exception)
                {
                    logger.error(exception.getMessage(), exception);

                    return Future.failedFuture(exception);
                }
            });
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }
}
