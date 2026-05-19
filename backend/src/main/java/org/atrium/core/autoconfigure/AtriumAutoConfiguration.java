package org.atrium.core.autoconfigure;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.atrium.core.domain.model.DefaultGameSettings;
import org.atrium.core.domain.model.GameSettings;
import org.atrium.core.redis.config.RedisAtriumConfiguration;
import org.atrium.core.spi.listener.GameLifecycleListener;
import org.atrium.core.spi.listener.NoOpGameLifecycleListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.web.cors.CorsConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Top-level Atrium autoconfiguration entry point.
 *
 * <p>Activates automatically when Spring Data Redis Reactive is on the classpath. A
 * downstream project can either let Spring Boot discover this auto-config (via the
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * file) or {@code @Import(AtriumAutoConfiguration.class)} explicitly.
 *
 * <p>To plug in a custom {@link GameSettings} subtype, the downstream project should
 * declare a {@link Module} bean that {@code registerSubtypes(...)} their concrete
 * class — Spring Boot will pick it up and Jackson will use it for both REST and Redis
 * serialisation.
 */
@AutoConfiguration(after = RedisReactiveAutoConfiguration.class)
@ConditionalOnClass(ReactiveRedisTemplate.class)
@EnableConfigurationProperties(AtriumProperties.class)
@ComponentScan(basePackages = {
	"org.atrium.core.api.controller",
	"org.atrium.core.api.error",
	"org.atrium.core.redis.repository",
	"org.atrium.core.redis.stream",
	"org.atrium.core.domain.service",
	"org.atrium.core.websocket",
})
@Import(RedisAtriumConfiguration.class)
public class AtriumAutoConfiguration {

	/**
	 * Default Jackson module that registers {@link DefaultGameSettings} as a subtype of
	 * {@link GameSettings}. Downstream projects can contribute their own additional
	 * module — both will be picked up by Spring Boot's autoconfigured {@code ObjectMapper}.
	 */
	@Bean
	@ConditionalOnMissingBean(name = "atriumDefaultGameSettingsModule")
	public Module atriumDefaultGameSettingsModule() {
		final SimpleModule module = new SimpleModule("AtriumDefaultGameSettingsModule");
		module.registerSubtypes(DefaultGameSettings.class);
		return module;
	}

	/**
	 * Default no-op SPI listener. Host projects can override with their own
	 * {@link GameLifecycleListener} bean.
	 */
	@Bean
	@ConditionalOnMissingBean(GameLifecycleListener.class)
	public GameLifecycleListener gameLifecycleListener() {
		return new NoOpGameLifecycleListener();
	}

	/**
	 * CORS shared between the REST controller and the WebSocket handler mapping.
	 */
	public static Map<String, CorsConfiguration> corsConfigurations(AtriumProperties properties) {
		final CorsConfiguration cors = new CorsConfiguration();
		properties.getCorsAllowedOrigins().forEach(origin -> {
			if ("*".equals(origin)) {
				cors.addAllowedOriginPattern("*");
			} else {
				cors.addAllowedOrigin(origin);
			}
		});
		cors.addAllowedHeader("*");
		cors.addAllowedMethod("*");
		cors.setAllowCredentials(true);
		final Map<String, CorsConfiguration> map = new HashMap<>();
		map.put("/**", cors);
		return map;
	}
}
