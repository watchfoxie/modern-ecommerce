package md.services.user_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;

@Configuration(proxyBeanMethods = false)
public class UserServiceStartupConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(UserServiceStartupConfiguration.class);

	private final ObjectProvider<ApplicationInfoManager> applicationInfoManagerProvider;

	public UserServiceStartupConfiguration(ObjectProvider<ApplicationInfoManager> applicationInfoManagerProvider) {
		this.applicationInfoManagerProvider = applicationInfoManagerProvider;
	}

	@EventListener(ApplicationReadyEvent.class)
	void markInstanceReady() {
		ApplicationInfoManager applicationInfoManager = applicationInfoManagerProvider.getIfAvailable();
		if (applicationInfoManager == null) {
			return;
		}

		applicationInfoManager.setInstanceStatus(InstanceStatus.UP);
		logger.info("[USER-SERVICE] Eureka instance status transitioned to UP after application readiness.");
	}

}
