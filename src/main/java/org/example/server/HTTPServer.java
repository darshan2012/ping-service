package org.example.server;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.example.Constants;
import org.example.Constants.ApplicationType;
import org.example.event.FileManager;
import org.example.store.ApplicationContextStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HTTPServer extends AbstractVerticle
{
    private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

    @Override
    public void start(Promise<Void> startPromise)
    {
        try
        {
            var server = vertx.createHttpServer();

            var router = Router.router(vertx);

            logger.info("Starting HTTP server on port 8080...");

            router.post("/register").handler(BodyHandler.create()).handler(context ->
            {
                try
                {
                    logger.info("Received a POST request to /register");

                    var requestBody = context.body().asJsonObject();

                    var ip = requestBody.getString("ip");

                    var type = requestBody.getString("type");

                    var pingPort = requestBody.getInteger("ping.port");

                    if (ip == null || type == null || pingPort == null)
                    {
                        logger.warn("Invalid application context: {}", requestBody);

                        context.response().setStatusCode(400).end("Invalid request parameters");

                        return;
                    }

                    ApplicationType applicationType;

                    try
                    {
                        applicationType = ApplicationType.valueOf(type.toUpperCase());
                    }
                    catch (IllegalArgumentException exception)
                    {
                        logger.warn("Invalid application type: {}", type, exception);

                        context.response().setStatusCode(400).end("Invalid application type");

                        return;
                    }

                    logger.info("Application context received: {}", requestBody);

                    if (!ApplicationContextStore.contains(applicationType))
                    {
                        vertx.deployVerticle(new FileManager(applicationType, ip,pingPort), deployResult ->
                        {
                            if (deployResult.succeeded())
                            {
                                logger.info("FileManager deployed successfully with deployment ID: {}", deployResult.result());
                            }
                            else
                            {
                                logger.error("Failed to deploy FileManager", deployResult.cause());

                                context.response().setStatusCode(500).end("Internal Server Error");
                            }
                        });
                    }

                    context.response().setStatusCode(200).end(new JsonObject().put("port", Constants.PORT).put("ip",Constants.IP).encode());

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
                    logger.info("HTTP server started successfully on port {}", http.result().actualPort());

                    startPromise.complete();
                }
                else
                {
                    logger.error("Failed to start HTTP server", http.cause());

                    startPromise.fail(http.cause());
                }
            });
        }
        catch (Exception exception)
        {
            logger.error(exception.getMessage(), exception);
        }
    }
}
