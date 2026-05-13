package md.services.auth_service.config;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import com.mongodb.client.MongoClient;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;

@Configuration(proxyBeanMethods = false)
public class AuthServiceStartupConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(AuthServiceStartupConfiguration.class);

	private final ObjectProvider<ApplicationInfoManager> applicationInfoManagerProvider;

	public AuthServiceStartupConfiguration(ObjectProvider<ApplicationInfoManager> applicationInfoManagerProvider) {
		this.applicationInfoManagerProvider = applicationInfoManagerProvider;
	}

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	@ConditionalOnProperty(
			value = "app.startup.mongo-verification.enabled",
			havingValue = "true",
			matchIfMissing = true)
	ApplicationRunner mongoStartupVerifier(
			MongoClient mongoClient,
			@Value("${AUTH_SERVICE_DB_NAME:}") String databaseName) {
		return args -> {
			if (!StringUtils.hasText(databaseName)) {
				throw new IllegalStateException(
						"AUTH_SERVICE_DB_NAME must be configured before auth-service is marked ready.");
			}

			try {
				Document pingResponse = mongoClient.getDatabase(databaseName).runCommand(new Document("ping", 1));
				logger.info(
						"[AUTH-SERVICE] MongoDB startup verification succeeded for database '{}' with response {}.",
						databaseName,
						pingResponse.toJson());
			} catch (Exception ex) {
				// Non-fatal: allow startup to continue even if the primary is transiently
				// unreachable (e.g., Atlas shard SSL negotiation failure from Docker).
				// The MongoDB driver will auto-reconnect once the primary becomes available.
				logger.warn(
						"[AUTH-SERVICE] MongoDB startup verification failed for database '{}'. "
								+ "Service will start and retry connections in the background. Cause: {}",
						databaseName,
						ex.getMessage());
			}
		};
	}

	@EventListener(ApplicationReadyEvent.class)
	void markInstanceReady() {
		ApplicationInfoManager applicationInfoManager = applicationInfoManagerProvider.getIfAvailable();
		if (applicationInfoManager == null) {
			return;
		}

		applicationInfoManager.setInstanceStatus(InstanceStatus.UP);
		logger.info("[AUTH-SERVICE] Eureka instance status transitioned to UP after application readiness.");
	}

}
