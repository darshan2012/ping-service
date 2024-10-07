package org.example;

import io.vertx.core.json.JsonObject;

import java.io.Serializable;

public class Constants
{
    public enum ApplicationType implements Serializable
    {
        PRIMARY, SECONDARY, FAILOVER
    }

    public static final String BASE_DIR = System.getProperty("user.dir") + "/data";

    public static final String FILE_STATUS_PATH = Constants.BASE_DIR + "/data/filestatuses.txt";

    public static final Long FILE_STORE_INTERVAL = 60000L;

    public static final JsonObject POLL_INTERVALS = new JsonObject().put("ping", 10000L);

    public static final String EVENT_NEW_FILE = "event.new.file";

    public static final String EVENT_WRITE_FILE = "event.create.file";

    public static final String EVENT_OPEN_FILE = "event.open.file";

    public static final String EVENT_READ_FILE = "event.read.file";

    public static final String EVENT_SEND = "event.send";

    public static final String EVENT_CLOSE_FILE = "event.close.file";

    public static final String EVENT_HEARTBEAT = "event.heartbeat";

    public static final String EVENT_OBJECT_POLL = "start.polling";

    public final static String NEW_LINE_CHAR = "\n";

    public static final String OBJECT_PROVISION = "object.provision";

    public static final String IP = "127.0.0.1";

    public static final Long PORT = 3456L;
}
