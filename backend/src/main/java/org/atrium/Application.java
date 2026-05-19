package org.atrium;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


/**
 * Entry point for the Atrium Core reference host application.
 *
 * <p>The lobby system itself lives under {@link org.atrium.core} and is wired up via
 * Spring Boot auto-configuration ({@link org.atrium.core.autoconfigure.LobbyAutoConfiguration}),
 * so a downstream project that depends on this module as a library only has to declare a
 * concrete {@link org.atrium.core.domain.model.GameSettings} subtype and provide its own game
 * logic.
 */
@SpringBootApplication
@EnableScheduling
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
