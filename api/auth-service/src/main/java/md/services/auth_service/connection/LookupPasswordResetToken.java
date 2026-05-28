package md.services.auth_service.connection;

import java.util.HashMap;
import java.util.Map;

import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import com.mongodb.client.MongoClient;

@SpringBootApplication
@ConditionalOnProperty(value = "app.auth.password-reset-lookup.enabled", havingValue = "true")
public class LookupPasswordResetToken implements ExitCodeGenerator {

	private static final int EXIT_CODE_SUCCESS = 0;
	private static final int EXIT_CODE_BAD_INPUT = 10;
	private static final int EXIT_CODE_NOT_FOUND = 11;

	private int exitCode = EXIT_CODE_SUCCESS;

	@Value("${AUTH_SERVICE_DB_NAME:}")
	private String dbName;

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(LookupPasswordResetToken.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		application.setDefaultProperties(defaultProperties());

		ConfigurableApplicationContext context = application.run(args);
		System.exit(SpringApplication.exit(context));
	}

	@Bean
	ApplicationRunner passwordResetTokenLookupRunner(MongoClient mongoClient) {
		return args -> {
			String email = requiredOption(args, "email");
			if (email == null) {
				exitCode = EXIT_CODE_BAD_INPUT;
				return;
			}

			Document user = mongoClient.getDatabase(dbName)
					.getCollection("users")
					.find(new Document("email", email.trim().toLowerCase()))
					.projection(new Document("passwordResetToken", 1).append("email", 1).append("_id", 0))
					.first();

			if (user == null || !StringUtils.hasText(user.getString("passwordResetToken"))) {
				exitCode = EXIT_CODE_NOT_FOUND;
				return;
			}

			System.out.println(user.getString("passwordResetToken"));
			exitCode = EXIT_CODE_SUCCESS;
		};
	}

	@Override
	public int getExitCode() {
		return exitCode;
	}

	private static String requiredOption(ApplicationArguments args, String name) {
		if (!args.containsOption(name)) {
			return null;
		}

		return args.getOptionValues(name).stream()
				.filter(StringUtils::hasText)
				.findFirst()
				.orElse(null);
	}

	private static Map<String, Object> defaultProperties() {
		Map<String, Object> properties = new HashMap<>();
		properties.put("spring.main.banner-mode", "off");
		properties.put("spring.main.web-application-type", "none");
		properties.put("spring.cloud.discovery.enabled", "false");
		properties.put("eureka.client.enabled", "false");
		properties.put("eureka.client.fetch-registry", "false");
		properties.put("eureka.client.register-with-eureka", "false");
		properties.put("spring.main.lazy-initialization", "true");
		properties.put("app.auth.password-reset-lookup.enabled", "true");
		return properties;
	}
}
