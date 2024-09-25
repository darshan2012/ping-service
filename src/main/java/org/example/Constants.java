package org.example;

import java.io.Serializable;

public class Constants
{
    public enum ApplicationType implements Serializable
    {
        PRIMARY, SECONDARY, FAILOVER
    }

    public static final String BASE_DIR = "data";

    public static final int FILE_STORE_INTERVAL = 60000;

    public static final String EVENT_ADDRESS = "event.new-file";

    public static final String TEXT_FILE_REGEX = ".*\\.txt$";
}
