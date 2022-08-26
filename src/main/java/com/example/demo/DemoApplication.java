package com.example.demo;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;

@SpringBootApplication
@Log4j2
public class DemoApplication {

	@Autowired
	ConfigurableApplicationContext applicationContext;

	@Autowired
	private Vertx vertx;

	public String verticleId;

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(DemoApplication.class);
		app.setRegisterShutdownHook(false);
		app.run(args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void appReady() {
		deployHttpServer()
				.onSuccess(v -> {
					Runtime.getRuntime().addShutdownHook(new ShutdownHook());
					log.info("Deploy completed");
				})
				.onFailure(v -> log.info("Deploy failed"));
	}

	private Future<Void> deployHttpServer() {
		var promise = Promise.<Void>promise();
		log.info("Deploying HTTP server");
		vertx.deployVerticle(new MainVerticle(), v -> {
			if (v.succeeded()) {
				promise.complete();
				verticleId = v.result();
				log.info("Deploy verticle completed, id: {}", v.result());
			} else {
				promise.fail("Deploy verticle failed");
				log.info("Deploy verticle failed");
			}
		});
		return promise.future();
	}

	private class ShutdownHook extends Thread {

		public void run() {
			log.info("ShutdownHook started");
			Future f = undeployVerticle(verticleId)
					.onSuccess(v -> log.info("Undeploy onSuccess"))
					.onFailure(v -> log.info("Undeploy onFailure"))
					.andThen(v -> {
						log.info("Closing vertx");
						vertx.close();
					});

			while(!f.isComplete()) {
				try {
					log.info("waiting");
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			log.info("*** Closing app context ***");
			applicationContext.close();
		}
	}

	public Future<Void> undeployVerticle(String verticleId) {
		var promise = Promise.<Void>promise();
		log.info("Undeploying verticle, id: {}", verticleId);
		vertx.undeploy(verticleId,
				r -> {
					if (r.succeeded()) {
						log.info("Verticle has been undeployed");
						promise.complete();
					} else {
						log.info("Undeploy failed: {}", r.cause().getMessage());
						promise.fail(r.cause());
					}
				});
		return promise.future();
	}
}
