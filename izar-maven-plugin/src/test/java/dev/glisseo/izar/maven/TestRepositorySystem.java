package dev.glisseo.izar.maven;

import java.nio.file.Path;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

/**
 * Bootstraps a real Aether {@link RepositorySystem}, outside any Maven build, for {@link
 * DeployMojo} and {@link AssembleMojo} tests. A real Maven run never needs this: Maven core
 * already injects a fully wired {@code RepositorySystem} and {@code RepositorySystemSession}, the
 * same way it injects {@code project} or {@code settings}. Tests stand in for that injection so
 * these Mojos are exercised against a genuine local or {@code file://} repository, not a mock.
 */
final class TestRepositorySystem {

    private TestRepositorySystem() {}

    // DefaultServiceLocator and newServiceLocator() are deprecated in favor of an out-of-the-box DI
    // container or the maven-resolver-supplier module's RepositorySystemSupplier; this plugin adds
    // no DI framework of its own, and pulling in maven-resolver-supplier solely for a test fixture
    // isn't worth the extra dependency.
    @SuppressWarnings("deprecation")
    static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    static DefaultRepositorySystemSession newSession(RepositorySystem system, Path localRepository) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(localRepository.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    static RemoteRepository fileRepository(String id, Path directory) {
        return new RemoteRepository.Builder(id, "default", directory.toUri().toString()).build();
    }
}
