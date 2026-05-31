package spaghettichef.central;

import java.util.concurrent.CountDownLatch;

import spaghettichef.shared.OperationMessages;
import spaghettichef.central.api.CentralApiServer;
import spaghettichef.central.persistence.CentralDatabaseInitializer;
import spaghettichef.central.persistence.CentralFarmStore;
import spaghettichef.central.service.CentralFarmService;
import spaghettichef.shared.config.RuntimeDefaults;

public final class CentralMain {
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
        CentralFarmService service = new CentralFarmService(new CentralFarmStore());
        return new CentralApiServer(apiPort, service);
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
