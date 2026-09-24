package dev.glisseo.izar.maven;

/**
 * One {@code <scalarMapping>} element inside the {@code generate} goal's {@code
 * <scalarMappings>} list. A plain bean, not a record: Maven's parameter configurator populates
 * nested list elements through a public no-argument constructor and setters, matching each XML
 * child element's tag name to a property.
 */
public class ScalarMappingParameter {

    private String graphqlScalarName;
    private String javaTypeName;
    private String codecClassName;

    public String getGraphqlScalarName() {
        return graphqlScalarName;
    }

    public void setGraphqlScalarName(String graphqlScalarName) {
        this.graphqlScalarName = graphqlScalarName;
    }

    public String getJavaTypeName() {
        return javaTypeName;
    }

    public void setJavaTypeName(String javaTypeName) {
        this.javaTypeName = javaTypeName;
    }

    public String getCodecClassName() {
        return codecClassName;
    }

    public void setCodecClassName(String codecClassName) {
        this.codecClassName = codecClassName;
    }
}
