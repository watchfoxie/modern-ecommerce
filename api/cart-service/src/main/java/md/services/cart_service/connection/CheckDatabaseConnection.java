package md.services.cart_service.connection;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLException;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;

import com.mongodb.ConnectionString;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoConfigurationException;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;

@SpringBootApplication
@ConditionalOnProperty(value = "app.database.connection-check.enabled", havingValue = "true")
public class CheckDatabaseConnection implements ExitCodeGenerator {

    private static final Logger logger = LoggerFactory.getLogger(CheckDatabaseConnection.class);
    private static final String SERVICE_NAME = "CART-SERVICE";
    private static final String URI_ENV_NAME = "CART_MONGODB_URI";
    private static final String DATABASE_ENV_NAME = "CART_SERVICE_DB_NAME";
    private static final String COLLECTION_ENV_NAME = "COMMON_COLLECTION_NAME";

    private int exitCode = 0;

    @Value("${CART_MONGODB_URI:}")
    private String mongodbUri;

    @Value("${CART_SERVICE_DB_NAME:}")
    private String dbName;

    @Value("${COMMON_COLLECTION_NAME:init}")
    private String collectionName;

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CheckDatabaseConnection.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(defaultProperties());

        try {
            ConfigurableApplicationContext context = application.run(args);
            System.exit(SpringApplication.exit(context));
        } catch (Throwable failure) {
            DiagnosticFailure diagnosticFailure = classifyFailure(failure);
            logFailure(diagnosticFailure);
            System.exit(diagnosticFailure.exitCode());
        }
    }

    @Bean
    ApplicationRunner connectionChecker(MongoClient mongoClient) {
        return args -> {
            DiagnosticFailure configurationFailure = validateConfiguration();
            if (configurationFailure != null) {
                exitCode = configurationFailure.exitCode();
                logFailure(configurationFailure);
                return;
            }

            ConnectionString connectionString = new ConnectionString(mongodbUri);
            logTarget(connectionString);

            try {
                Document pingResponse = mongoClient
                        .getDatabase(dbName)
                        .runCommand(new Document("ping", 1));

                List<String> collectionNames = mongoClient
                        .getDatabase(dbName)
                        .listCollectionNames()
                        .into(new ArrayList<>());

                boolean collectionPresent = collectionNames.contains(collectionName);
                Long estimatedDocumentCount = null;

                if (!collectionPresent) {
                    logger.warn(
                            "[{}] [COLLECTION_NOT_FOUND] Conexiunea la MongoDB a reușit, dar colecția inițială '{}' nu este prezentă în baza de date '{}'. Conectivitatea Atlas este validă; verificarea colecției rămâne informativă.",
                            SERVICE_NAME,
                            collectionName,
                            dbName);
                } else {
                    estimatedDocumentCount = mongoClient
                            .getDatabase(dbName)
                            .getCollection(collectionName)
                            .estimatedDocumentCount();
                }

                logger.info(
                        "[{}] [SUCCESS] Conexiunea MongoDB este operațională pentru baza de date '{}'{}.",
                        SERVICE_NAME,
                        dbName,
                        collectionPresent ? " și colecția '" + collectionName + "'" : "");
                logger.info(
                        "[{}] [SUCCESS] Ping response: {} | collection='{}' | collectionPresent={} | collectionCountEstimate={}",
                        SERVICE_NAME,
                        pingResponse.toJson(),
                        collectionName,
                        collectionPresent,
                        estimatedDocumentCount);
                exitCode = 0;
            } catch (Throwable failure) {
                DiagnosticFailure diagnosticFailure = classifyFailure(failure);
                exitCode = diagnosticFailure.exitCode();
                logFailure(diagnosticFailure);
            }
        };
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    private DiagnosticFailure validateConfiguration() {
        if (!StringUtils.hasText(mongodbUri)) {
            return new DiagnosticFailure(
                    "CONFIGURATION_ERROR",
                    10,
                    false,
                    "Variabila de configurare '" + URI_ENV_NAME + "' nu este definită sau este goală.",
                    "Definiți URI-ul MongoDB în .env.local sau în mediul de execuție înainte de rulare.",
                    null);
        }

        if (!StringUtils.hasText(dbName)) {
            return new DiagnosticFailure(
                    "CONFIGURATION_ERROR",
                    10,
                    false,
                    "Variabila de configurare '" + DATABASE_ENV_NAME + "' nu este definită sau este goală.",
                    "Definiți numele bazei de date în .env.local sau în mediul de execuție înainte de rulare.",
                    null);
        }

        if (!StringUtils.hasText(collectionName)) {
            return new DiagnosticFailure(
                    "CONFIGURATION_ERROR",
                    10,
                    false,
                    "Numele colecției inițiale este gol.",
                    "Configurați o valoare validă pentru colecția inițială sau utilizați fallback-ul implicit 'init'.",
                    null);
        }

        try {
            ConnectionString connectionString = new ConnectionString(mongodbUri);
            String databaseNameFromUri = connectionString.getDatabase();

            if (StringUtils.hasText(databaseNameFromUri) && !dbName.equals(databaseNameFromUri)) {
                return new DiagnosticFailure(
                        "CONFIGURATION_ERROR",
                        10,
                        false,
                        "Numele bazei de date din URI ('" + databaseNameFromUri + "') nu corespunde valorii din '"
                                + DATABASE_ENV_NAME + "' ('" + dbName + "').",
                        "Aliniați numele bazei de date din URI și din variabila de mediu pentru a indica aceeași bază.",
                        null);
            }

            if (!StringUtils.hasText(databaseNameFromUri)) {
                logger.warn(
                        "[{}] URI-ul MongoDB nu include explicit numele bazei de date. Verificarea va folosi valoarea din '{}': '{}'.",
                        SERVICE_NAME,
                        DATABASE_ENV_NAME,
                        dbName);
            }
        } catch (RuntimeException failure) {
            return classifyFailure(failure);
        }

        return null;
    }

    private void logTarget(ConnectionString connectionString) {
        String hosts = connectionString.getHosts().isEmpty()
                ? "<unspecified>"
                : String.join(", ", connectionString.getHosts());
        String databaseNameFromUri = StringUtils.hasText(connectionString.getDatabase())
                ? connectionString.getDatabase()
                : "<unspecified>";
        String sslMode = connectionString.getSslEnabled() == null
                ? "auto"
                : connectionString.getSslEnabled().toString();

        logger.info(
                "[{}] Diagnostic target -> hosts=[{}], expectedDatabase='{}', uriDatabase='{}', initialCollection='{}', ssl={}",
                SERVICE_NAME,
                hosts,
                dbName,
                databaseNameFromUri,
                collectionName,
                sslMode);
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
        properties.put("app.database.connection-check.enabled", "true");
        return properties;
    }

    private static void logFailure(DiagnosticFailure failure) {
        logger.error("[{}] [{}] {}", SERVICE_NAME, failure.category(), failure.summary());
        logger.error("[{}] Recomandare: {}", SERVICE_NAME, failure.recommendation());

        Throwable rootCause = rootCause(failure.rootCause());
        if (rootCause != null && rootCause != failure.rootCause()) {
            logger.error(
                    "[{}] Cauză rădăcină: {}: {}",
                    SERVICE_NAME,
                    rootCause.getClass().getName(),
                    safeMessage(rootCause));
        } else if (failure.rootCause() != null) {
            logger.error(
                    "[{}] Cauză: {}: {}",
                    SERVICE_NAME,
                    failure.rootCause().getClass().getName(),
                    safeMessage(failure.rootCause()));
        }

        if (failure.userActionRequired()) {
            logger.error(
                    "[{}] Remedierea automată este exclusă pentru această categorie. Corectarea credențialelor rămâne în responsabilitatea utilizatorului.",
                    SERVICE_NAME);
        }
    }

    private static DiagnosticFailure classifyFailure(Throwable failure) {
        Throwable rootCause = rootCause(failure);
        String combinedMessage = (safeMessage(failure) + " " + safeMessage(rootCause)).toLowerCase(Locale.ROOT);

        if (rootCause instanceof UnknownHostException) {
            return new DiagnosticFailure(
                    "DNS_RESOLUTION_ERROR",
                    21,
                    false,
                    "Hostname-ul MongoDB nu a putut fi rezolvat.",
                    "Verificați hostname-ul din URI, conectivitatea DNS și eventualele restricții locale de rețea.",
                    failure);
        }

        if (rootCause instanceof SSLException
                || combinedMessage.contains("ssl")
                || combinedMessage.contains("tls")
                || combinedMessage.contains("certificate")) {
            return new DiagnosticFailure(
                    "TLS_SSL_ERROR",
                    22,
                    false,
                    "Conexiunea către MongoDB a eșuat din cauza negocierii TLS/SSL.",
                    "Verificați opțiunile TLS din URI, certificatul serverului și intercepțiile locale de trafic HTTPS.",
                    failure);
        }

        if (failure instanceof MongoSecurityException
                || rootCause instanceof MongoSecurityException
                || combinedMessage.contains("authentication failed")
                || combinedMessage.contains("bad auth")
                || combinedMessage.contains("requires authentication")
                || combinedMessage.contains("not authorized")
                || combinedMessage.contains("unauthorized")) {
            return new DiagnosticFailure(
                    "AUTHENTICATION_ERROR",
                    30,
                    true,
                    "Autentificarea la MongoDB a eșuat.",
                    "Actualizați credențialele MongoDB (username/parolă) în afara acestui proces și relansați verificarea.",
                    failure);
        }

        if (failure instanceof MongoConfigurationException
                || failure instanceof IllegalArgumentException
                || combinedMessage.contains("could not resolve placeholder")
                || combinedMessage.contains("connection string")
                || combinedMessage.contains("uri")) {
            return new DiagnosticFailure(
                    "CONFIGURATION_ERROR",
                    10,
                    false,
                    "Configurația MongoDB este invalidă sau incompletă.",
                    "Verificați valorile pentru URI, numele bazei de date și proprietățile Spring încărcate din .env.local.",
                    failure);
        }

        if (failure instanceof MongoTimeoutException || combinedMessage.contains("timed out")) {
            return new DiagnosticFailure(
                    "NETWORK_TIMEOUT",
                    20,
                    false,
                    "Conexiunea către MongoDB a expirat înainte de selectarea unui server disponibil.",
                    "Verificați accesul la rețea, whitelist-ul IP din MongoDB Atlas și disponibilitatea clusterului.",
                    failure);
        }

        if (failure instanceof MongoSocketException
                || combinedMessage.contains("connection refused")
                || combinedMessage.contains("network is unreachable")
                || combinedMessage.contains("socket")) {
            return new DiagnosticFailure(
                    "NETWORK_SOCKET_ERROR",
                    23,
                    false,
                    "Conexiunea de rețea către MongoDB nu a putut fi stabilită.",
                    "Verificați accesul la internet, firewall-ul local și accesul rețelei către nodurile clusterului MongoDB Atlas.",
                    failure);
        }

        if (failure instanceof MongoCommandException || failure instanceof MongoException) {
            return new DiagnosticFailure(
                    "DATABASE_ACCESS_ERROR",
                    24,
                    false,
                    "MongoDB a răspuns cu o eroare la comanda de diagnostic.",
                    "Verificați existența bazei de date, permisiunile asociate utilizatorului și starea colecției inițiale.",
                    failure);
        }

        return new DiagnosticFailure(
                "UNEXPECTED_ERROR",
                1,
                false,
                "A apărut o eroare neașteptată în timpul verificării conectivității MongoDB.",
                "Inspectați cauza raportată și adaptați configurația sau mediul de execuție înainte de relansare.",
                failure);
    }

    private static Throwable rootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || !StringUtils.hasText(throwable.getMessage())) {
            return "<no message>";
        }
        return throwable.getMessage();
    }

    private record DiagnosticFailure(
            String category,
            int exitCode,
            boolean userActionRequired,
            String summary,
            String recommendation,
            Throwable rootCause) {
    }
}
