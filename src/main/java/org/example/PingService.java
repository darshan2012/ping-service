package org.example;

import io.vertx.core.Vertx;
import io.vertx.core.WorkerExecutor;

public interface PingService
{
    void ping(String ip, Vertx vertx, WorkerExecutor workerExecutor);
}
