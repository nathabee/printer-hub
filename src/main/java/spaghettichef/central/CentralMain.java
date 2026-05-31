package spaghettichef.central;

import spaghettichef.central.api.CentralApiServer;
import spaghettichef.central.persistence.CentralDatabaseInitializer;
import spaghettichef.central.persistence.CentralFarmStore;
import spaghettichef.central.persistence.CentralReplayPackageStore;
import spaghettichef.central.service.CentralFarmService;
import spaghettichef.central.service.CentralReplayPackageService;
import spaghettichef.shared.OperationMessages;
import spaghettichef.shared.config.RuntimeDefaults;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class CentralMain {
    public static final String REPLAY_STORAGE_DIR_PROPERTY = "spaghettichef.central.replayStorageDir";
    public static final String REPLAY_STORAGE_DIR_ENV = "CENTRAL_REPLAY_STORAGE_DIR";
    public static final String DEFAULT_REPLAY_STORAGE_DIR = "central-replay-storage";

    private CentralMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        try {
            startAndWait();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } catch (Exception exception) {
            System.err.println(OperationMessages.runtimeStartupFailed(
                    OperationMessages.safeDetail(exception.getMessage(), OperationMessages.UNKNOWN_STARTUP_ERROR)));
            exception.printStackTrace(System.err);
            System.exit(RuntimeDefaults.ERROR_EXIT_CODE);
        }
    }

    public static void startAndWait() throws InterruptedException {
        int apiPort = readIntProperty(RuntimeDefaults.API_PORT_PROPERTY, RuntimeDefaults.DEFAULT_API_PORT);
        CentralApiServer server = createServer(apiPort);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
        System.out.println("SpaghettiChef central read-only VPS viewer started");
        System.out.println(OperationMessages.healthEndpoint(apiPort));
        System.out.println("Central dashboard: http://localhost:" + apiPort + "/central-dashboard");
        new CountDownLatch(1).await();
    }

    public static CentralApiServer createServer(int apiPort) {
        CentralDatabaseInitializer initializer = new CentralDatabaseInitializer();
        initializer.initialize();
        CentralFarmStore farmStore = new CentralFarmStore();
        CentralFarmService service = new CentralFarmService(farmStore);
        CentralReplayPackageService replayService = new CentralReplayPackageService(
                farmStore,
                new CentralReplayPackageStore());
        return new CentralApiServer(apiPort, service, replayService, readReplayStorageDir(), readRegistrationToken());
    }

    private static String readRegistrationToken() {
        String propertyValue = System.getProperty(CentralApiServer.REGISTRATION_TOKEN_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        String envValue = System.getenv(CentralApiServer.REGISTRATION_TOKEN_ENV);
        return envValue == null || envValue.isBlank() ? null : envValue.trim();
    }

    private static Path readReplayStorageDir() {
        String propertyValue = System.getProperty(REPLAY_STORAGE_DIR_PROPERTY);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Path.of(propertyValue.trim());
        }
        String envValue = System.getenv(REPLAY_STORAGE_DIR_ENV);
        if (envValue != null && !envValue.isBlank()) {
            return Path.of(envValue.trim());
        }
        return Path.of(DEFAULT_REPLAY_STORAGE_DIR);
    }

    private static int readIntProperty(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(OperationMessages.invalidIntegerSystemProperty(key, value), exception);
        }
    }
}
