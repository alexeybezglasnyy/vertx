package com.example.demo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import lombok.extern.log4j.Log4j2;


@Log4j2
public class MainVerticle extends AbstractVerticle {

    public final String ADDRESS = "ADDRESS";
    public static MessageProducer<byte[]> sender;
    public static MessageConsumer<byte[]> consumer;


    @Override
    public void start() {
        // Create a Router
        Router router = Router.router(vertx);

        // Mount the handler for all incoming requests at every path and HTTP method
        router.route().handler(context -> {
            log.info("request received");
            // Get the address of the request
            String address = context.request().connection().remoteAddress().toString();
            // Get the query parameter "name"
            MultiMap queryParams = context.queryParams();
            String name = queryParams.contains("name") ? queryParams.get("name") : "unknown";

            JsonObject jsonObject = new JsonObject()
                    .put("name", name)
                    .put("address", address)
                    .put("message", "Hello " + name + " connected from " + address);

            vertx.eventBus().send(ADDRESS, jsonObject);

            // Write a json response
            context.json(jsonObject);
        });

        // Create the HTTP server
        vertx.createHttpServer()
                // Handle every request using the router
                .requestHandler(router)
                // Start listening
                .listen(8888)
                // Print the port
                .onSuccess(server -> log.info("HTTP server started on port " + server.actualPort()));

        sender = vertx.eventBus().sender(ADDRESS, new DeliveryOptions().setLocalOnly(true));
        consumer = vertx.eventBus().localConsumer(ADDRESS, msg -> {
            JsonObject obj = JsonObject.mapFrom(msg.body());
            log.info("Object name: {}, address: {}", obj.getString("name"), obj.getString("address"));
        });

        consumer.endHandler(v -> log.info("Consumer endHandler called"));
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        Future<Void> f = sender.close()
                .onSuccess(v -> {
                    log.info("Sender closed, try to unregister consumer");
                    consumer.unregister()
                        .onSuccess(u -> {
                            log.info("Consumer unregistered");
                            stopPromise.complete();
                        })
                        .onFailure(u -> {
                            log.info("Consumer unregister failed");
                            stopPromise.fail("Consumer unregister failed");
                        });}
                )
                .onFailure(v -> {
                    log.info("sender.close failed");
                    stopPromise.fail("sender.close failed");
                });
    }
}
