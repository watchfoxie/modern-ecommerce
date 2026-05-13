package md.services.api_gateway.config;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;

import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class GatewayObservabilityConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(GatewayObservabilityConfiguration.class);

	private final RouteDefinitionLocator routeDefinitionLocator;

	private final Environment environment;

	private final ApplicationInfoManager applicationInfoManager;

	private final EurekaClient eurekaClient;

	private final int renewalIntervalSeconds;

	public GatewayObservabilityConfiguration(
			RouteDefinitionLocator routeDefinitionLocator,
			Environment environment,
			ApplicationInfoManager applicationInfoManager,
			EurekaClient eurekaClient,
			@Value("${eureka.instance.lease-renewal-interval-in-seconds:30}") int renewalIntervalSeconds) {
		this.routeDefinitionLocator = routeDefinitionLocator;
		this.environment = environment;
		this.applicationInfoManager = applicationInfoManager;
		this.eurekaClient = eurekaClient;
		this.renewalIntervalSeconds = renewalIntervalSeconds;
	}

	@Bean
	ApplicationRunner gatewayStartupLogger() {
		return args -> {
			List<RouteDefinition> routeDefinitions = routeDefinitionLocator.getRouteDefinitions()
					.collectList()
					.block(Duration.ofSeconds(5));

			if (routeDefinitions == null) {
				logger.warn("[API-GATEWAY] Route definitions could not be collected within the startup timeout.");
			}
			else {
				logger.info("[API-GATEWAY] Loaded {} route definitions.", routeDefinitions.size());
				for (RouteDefinition routeDefinition : routeDefinitions) {
					logger.info(
							"[API-GATEWAY] Route '{}' -> uri={} predicates={} filters={}",
							routeDefinition.getId(),
							routeDefinition.getUri(),
							summarize(routeDefinition.getPredicates()),
							summarize(routeDefinition.getFilters()));
				}
			}

			String[] activeProfiles = environment.getActiveProfiles();
			logger.info(
					"[API-GATEWAY] Active profiles: {}.",
					(activeProfiles.length == 0) ? "<default>" : String.join(", ", activeProfiles));
			if (List.of(activeProfiles).contains("local")) {
				logger.info(
						"[API-GATEWAY] The 'local' profile is intended for developer workstations; use an environment-specific profile outside local development.");
			}
			else if (activeProfiles.length > 0) {
				logger.info("[API-GATEWAY] Environment-specific operational profile detected.");
			}

			if (!Boolean.parseBoolean(environment.getProperty("spring.cloud.config.enabled", "false"))) {
				logger.info(
						"[API-GATEWAY] Spring Cloud Config is not configured; using packaged application YAML files plus optional .env imports.");
			}

			InstanceInfo instanceInfo = applicationInfoManager.getInfo();
			logger.info(
					"[API-GATEWAY] Eureka registration metadata -> app={}, status={}, version={}.",
					instanceInfo.getAppName(),
					instanceInfo.getStatus(),
					instanceInfo.getMetadata().getOrDefault("version", "unknown"));
		};
	}

	@Scheduled(
			initialDelayString = "${app.eureka-observability.initial-delay-ms}",
			fixedDelayString = "${app.eureka-observability.log-interval-ms}")
	void logEurekaHeartbeatSummary() {
		InstanceInfo instanceInfo = applicationInfoManager.getInfo();
		int registeredApplications = eurekaClient.getApplications().getRegisteredApplications().size();
		logger.info(
				"[API-GATEWAY] Eureka heartbeat summary -> localStatus={}, registeredApplications={}, renewalIntervalSeconds={}.",
				instanceInfo.getStatus(),
				registeredApplications,
				renewalIntervalSeconds);
	}

	private String summarize(List<?> definitions) {
		return definitions.stream()
				.map(Object::toString)
				.filter(StringUtils::hasText)
				.collect(Collectors.joining(", "));
	}

}
