package event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileStatusTracker
{
    // Map to store file names and read statuses for three applications.
    // ApplicationStatus holds the read status for Primary, Secondary, and Failure
    private static final ConcurrentHashMap<String, HashMap<ApplicationType, Boolean>> fileReadStatus = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(FileStatusTracker.class);

    public static void markFileAsRead(String fileName, ApplicationType appType)
    {
        logger.info("fileReadStatus : " + fileReadStatus.get(fileName));
        fileReadStatus.get(fileName).put(appType, true);
    }

    public static boolean getFileReadStatus(String fileName, ApplicationType applicationType)
    {
        try
        {
            var status = fileReadStatus.get(fileName).get(applicationType);

            if (status == null)
            {
                fileReadStatus.get(fileName).put(applicationType, false);

                return false;
            }
            return status;
        }
        catch (Exception exception)
        {
            return false;
        }
    }

    public static void addFile(String fileName)
    {
        fileReadStatus.putIfAbsent(fileName, new HashMap<>());
    }

    public static boolean allAppsCompleted(String fileName)
    {
        var applicationstatus = fileReadStatus.get(fileName);

        logger.info("application status : " + applicationstatus);

        if (ApplicationContextStore.getApplicationCount() != applicationstatus.size())
        {
            return false;
        }
        AtomicBoolean allAppsCompeted = new AtomicBoolean(true);

        applicationstatus.forEach((applicationType, status) ->
        {
            if (!status)
            {
                allAppsCompeted.set(false);
            }
        });
        return allAppsCompeted.get();
    }

    public static Boolean removeFile(String fileName)
    {
        if (allAppsCompleted(fileName))
        {
            fileReadStatus.remove(fileName);

            return true;
        }
        return false;
    }
}
