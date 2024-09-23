package event;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;


public class ApplicationContextStore
{
    private static Map<ApplicationType, JsonObject> applicationContexts = new HashMap<>();

    public static JsonObject getAppContext(ApplicationType applicationType)
    {
        return applicationContexts.get(applicationType);
    }

    public static void setAppContext(ApplicationType applicationType, String ip, int port,int pingPort)
    {
        applicationContexts.putIfAbsent(applicationType, new JsonObject());

        applicationContexts.get(applicationType).put("ip", ip).put("port", port).put("pingPort",pingPort);
    }

    public static int getApplicationCount()
    {
        return applicationContexts.size();
    }

}
