package scot.gov.publications;

import dagger.ObjectGraph;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import org.flywaydb.core.Flyway;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.InetSocketAddress;

/**
 * Main for the Publications service
 */
public class Publications {

    private static final Logger LOG = LoggerFactory.getLogger(Publications.class);

    @Inject
    PublicationsConfiguration config;

    @Inject
    PublicationsApplication application;

    public static final void main(String[] args) {
        ObjectGraph graph = ObjectGraph.create(new PublicationsModule());
        try {
            Publications publications = graph.get(Publications.class);
            publications.run();
        } catch (Throwable ex) {
            LOG.error("Publications service failed to start", ex);
            System.exit(1);
        }
    }

    public void run() {
        runDatabaseMigrations();
        startServer();
    }

    private void runDatabaseMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        config.getDatasource().getUrl(),
                        config.getDatasource().getUsername(),
                        config.getDatasource().getPassword())
                .load();
        flyway.migrate();
    }

    private void startServer() {
        Server server = new Server();
        server.deploy(application);
        server.start(Undertow.builder()
                .setServerOption(UndertowOptions.MAX_ENTITY_SIZE, 50 * 1024 * 1024L)
                .addHttpListener(config.getPort(), "::"));
        LOG.info("Listening on port {}", server.port());
    }

    public static class Server extends UndertowJaxrsServer {
        public int port() {
            InetSocketAddress address = (InetSocketAddress) server
                    .getListenerInfo()
                    .get(0)
                    .getAddress();
            return address.getPort();
        }
    }
}
