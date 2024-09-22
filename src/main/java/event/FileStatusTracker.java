package event;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class FileStatusTracker
{
    // Map to store file names and read statuses for three applications.
    // ApplicationStatus holds the read status for Primary, Secondary, and Failure
    private static final ConcurrentHashMap<String, ApplicationStatus> fileReadStatus = new ConcurrentHashMap<>();

    //rethink about it later if this should be there or not
    public static final List<String> files = new ArrayList<>();

    public static void markFileAsRead(String fileName, ApplicationType appType)
    {
        fileReadStatus.computeIfPresent(fileName, (key, status) ->
        {
            status.markAsRead(appType);
            return status;
        });
    }

    public static ApplicationStatus getFileReadStatus(String fileName)
    {
        return fileReadStatus.get(fileName);
    }

    public static boolean getFileReadStatus(String fileName, ApplicationType applicationType)
    {
        return fileReadStatus.get(fileName).getStatus(applicationType);
    }
    public static void addFile(String fileName)
    {
        fileReadStatus.computeIfAbsent(fileName, (filename) -> {
            files.add(filename);
            return new ApplicationStatus();
        });

    }

    public static boolean allAppsCompleted(String fileName)
    {
        ApplicationStatus status = fileReadStatus.get(fileName);
        return status != null && status.allRead();
    }

    public static void removeFile(String fileName)
    {
        fileReadStatus.remove(fileName);
    }

    public static class ApplicationStatus
    {
        private boolean primaryRead = false;
        private boolean secondaryRead = false;
        private boolean failureRead = false;

        public void markAsRead(ApplicationType applicationType)
        {
            switch (applicationType)
            {
                case PRIMARY -> primaryRead = true;
                case SECONDARY -> secondaryRead = true;
                case FAILURE -> failureRead = true;
            }
        }

        public boolean allRead()
        {
            return primaryRead && secondaryRead && failureRead;
        }

        public boolean getStatus(ApplicationType applicationType)
        {
            return switch (applicationType)
            {
                case PRIMARY -> this.primaryRead;
                case SECONDARY -> this.secondaryRead;
                case FAILURE -> this.failureRead;
            };
        }

        public boolean[] getStatus()
        {
            return new boolean[]{primaryRead, secondaryRead, failureRead};
        }
    }
}
