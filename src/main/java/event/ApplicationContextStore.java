package event;

import java.util.HashMap;
import java.util.Map;


public class ApplicationContextStore {
    private static Map<ApplicationType, ApplicationContext> applicationContexts = new HashMap<>();

    static {
        applicationContexts.put(ApplicationType.PRIMARY,new ApplicationContext());
        applicationContexts.put(ApplicationType.SECONDARY,new ApplicationContext());
        applicationContexts.put(ApplicationType.FAILURE,new ApplicationContext());
    }

    public static ApplicationContext getAppContext(ApplicationType applicationType) {
        return applicationContexts.get(applicationType);
    }

    public static void setAppContext(ApplicationType applicationType, String ip, int port) {
        ApplicationContext applicationContext = applicationContexts.get(applicationType);
        applicationContext.setIp(ip);
        applicationContext.setPort(port);
    }

}
