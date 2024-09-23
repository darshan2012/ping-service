package event;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerVerticle extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(ServerVerticle.class);

    @Override
    public void start() throws Exception
    {
        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);

        logger.info("Starting HTTP server on port 8080...");

        router.post("/register").handler(BodyHandler.create()).handler(context ->
        {
            try
            {
                logger.info("Received a POST request to /connect");

                var requestBody = context.body().asJsonObject();
                var ip = requestBody.getString("ip");
                var port = requestBody.getInteger("port");
                var type = requestBody.getString("type");
                var pingPort = requestBody.getInteger("pingPort");
                System.out.println(pingPort + port);
                if (ip == null || port == null || type == null || pingPort == null)
                {
                    logger.warn("Invalid application context: {}", requestBody);

                    context.response().setStatusCode(400).end("Invalid request parameters");

                    return;
                }

                ApplicationType applicationType;
                try {
                    applicationType = ApplicationType.valueOf(type.toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid application type: {}", type);

                    context.response().setStatusCode(400).end("Invalid application type");

                    return;
                }

                logger.info("Application context received: {}", requestBody);

                ApplicationContextStore.setAppContext(applicationType, ip, port, pingPort);

                vertx.deployVerticle(new EventSenderVerticle(applicationType),
                        deployResult ->
                        {
                            if (deployResult.succeeded())
                            {
                                logger.info("EventSenderVerticle deployed successfully with deployment ID: {}",
                                        deployResult.result());
                            }
                            else
                            {
                                logger.error("Failed to deploy EventSenderVerticle", deployResult.cause());
                            }
                        });

                context.response().setStatusCode(200).end("Context set successfully, app will start sending data");

                logger.info("Response sent: Context set successfully");
            }
            catch (Exception exception)
            {
                logger.error("Error handling /connect request", exception);

                context.response().setStatusCode(500).end("Failed to set context");
            }
        });

        server.requestHandler(router).listen(8080, http ->
        {
            if (http.succeeded())
            {
                logger.info("HTTP server started successfully on port " + http.result().actualPort());
            }
            else
            {
                logger.error("Failed to start HTTP server", http.cause());
            }
        });
    }
}
