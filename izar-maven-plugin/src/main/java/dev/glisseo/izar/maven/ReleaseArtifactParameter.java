package dev.glisseo.izar.maven;

/**
 * One {@code <release>} element inside the {@code assemble} goal's {@code <releases>} list: a
 * client release identity paired with the Maven coordinates that supply its manifest.
 *
 * <p>{@code clientName} and {@code manifestVersion} are the domain identity {@link
 * dev.glisseo.izar.manifest.assembly.ClientRelease} carries; {@code groupId}, {@code artifactId}, {@code
 * version}, {@code classifier}, and {@code extension} are the independent Maven coordinates {@link
 * AssembleMojo} resolves to find that release's manifest file. The two need not correspond
 * one-to-one with a client project's own build coordinates: a deployment build names whatever
 * coordinates the client actually published its manifest under.
 *
 * <p>A plain bean, not a record, for the same reason as {@link ScalarMappingParameter}: Maven's
 * parameter configurator populates nested list elements through a public no-argument constructor
 * and setters, matching each XML child element's tag name to a property.
 */
public class ReleaseArtifactParameter {

    private String clientName;
    private String manifestVersion;
    private String groupId;
    private String artifactId;
    private String version;
    private String classifier = "izar-manifest";
    private String extension = "json";

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getManifestVersion() {
        return manifestVersion;
    }

    public void setManifestVersion(String manifestVersion) {
        this.manifestVersion = manifestVersion;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    /** The Maven coordinates this entry resolves, formatted the way Aether itself reports them. */
    String coordinates() {
        return groupId + ":" + artifactId + ":" + extension + ":" + classifier + ":" + version;
    }
}
