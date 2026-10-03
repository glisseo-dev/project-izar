package dev.glisseo.izar.gradle;

import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/** The {@code izar { assemble { ... } }} block, the defaults for {@link IzarAssembleTask}. */
public abstract class AssembleOptions {

    private final NamedDomainObjectContainer<ReleaseSpec> releases;

    @Inject
    public AssembleOptions(ObjectFactory objects) {
        this.releases = objects.domainObjectContainer(ReleaseSpec.class);
    }

    /** The logical graph or enforcing endpoint this selection targets. Required. */
    public abstract Property<String> getGraph();

    /** The environment this selection applies to, for example {@code "production"}. Required. */
    public abstract Property<String> getEnvironment();

    public abstract DirectoryProperty getOutputDirectory();

    public abstract Property<Boolean> getSkip();

    /** Every client release this selection explicitly supports, named by client name. */
    public NamedDomainObjectContainer<ReleaseSpec> getReleases() {
        return releases;
    }

    public void releases(Action<? super NamedDomainObjectContainer<ReleaseSpec>> action) {
        action.execute(releases);
    }
}
