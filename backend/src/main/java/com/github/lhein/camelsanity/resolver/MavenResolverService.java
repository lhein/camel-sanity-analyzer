package com.github.lhein.camelsanity.resolver;

import com.github.lhein.camelsanity.model.Coordinate;
import com.github.lhein.camelsanity.model.DependencyNode;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.repository.DefaultMirrorSelector;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class MavenResolverService {

    private static final Logger log = LoggerFactory.getLogger(MavenResolverService.class);
    private static final String CENTRAL_URL = "https://repo.maven.apache.org/maven2/";

    private final RepositorySystem system;
    private final List<RemoteRepository> repositories;

    public MavenResolverService() {
        this.system = new RepositorySystemSupplier().get();
        this.repositories = List.of(
                new RemoteRepository.Builder("central", "default", CENTRAL_URL).build()
        );
    }

    public DependencyNode resolveTree(Coordinate root) {
        try {
            DefaultRepositorySystemSession session = newSession();
            DefaultArtifact artifact = new DefaultArtifact(
                    root.groupId(), root.artifactId(), "jar", root.version());

            // Read the artifact's POM to get the dependencies it declares —
            // exactly what `mvn dependency:tree` shows as direct children.
            // Going via setRoot(new Dependency(artifact, "compile")) would apply
            // Maven's consumer→root scope composition, which silently drops
            // declared test/provided deps because compile+test = (omitted).
            ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest(
                    artifact, repositories, null);
            ArtifactDescriptorResult descriptor = system.readArtifactDescriptor(
                    session, descriptorRequest);

            // Then collect with setRootArtifact + the declared deps as direct
            // children. Aether resolves transitive graphs for each child using
            // its standard selector — test/provided are excluded transitively
            // (matching mvn dependency:tree), while direct test/provided deps
            // remain visible.
            CollectRequest collect = new CollectRequest();
            collect.setRootArtifact(artifact);
            collect.setDependencies(descriptor.getDependencies());
            collect.setManagedDependencies(descriptor.getManagedDependencies());
            collect.setRepositories(repositories);

            // collectDependencies builds the graph from POMs only — it does NOT
            // download the actual JARs. That's all we need for analysis and
            // avoids spurious failures on artifacts whose JARs are not on Central
            // (old JEE jars, OS-specific classifiers, tests-classifier jars).
            CollectResult result = system.collectDependencies(session, collect);
            return convert(result.getRoot(), artifact);
        } catch (Exception e) {
            log.error("Failed to resolve {}: {}", root.gav(), e.getMessage());
            throw new RuntimeException("Failed to resolve " + root.gav() + ": " + e.getMessage(), e);
        }
    }

    private DefaultRepositorySystemSession newSession() {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        File localRepo = new File(System.getProperty("user.home"), ".m2/repository");
        session.setLocalRepositoryManager(
                system.newLocalRepositoryManager(session, new LocalRepository(localRepo)));
        session.setSystemProperties(System.getProperties());
        // Be lenient about unresolvable artifact descriptors. Old transitive
        // test deps often reference dead repositories (Glassfish, Typesafe etc.)
        // and a single 403 / 404 should not abort the whole analysis.
        session.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(true, true));
        // Redirect every repository declared in transitive POMs to Maven Central.
        // Old POMs reference defunct repos (Glassfish, Restlet, Typesafe etc.) that
        // either return 403 or — worse — return HTML 404 pages that fail checksum
        // validation. Forcing all lookups to Central means a missing artifact is a
        // simple 404 which the lenient descriptor policy can swallow.
        DefaultMirrorSelector mirrorSelector = new DefaultMirrorSelector();
        mirrorSelector.add(
                "central-mirror-everything",
                "https://repo.maven.apache.org/maven2/",
                "default", false, false, "*", null);
        session.setMirrorSelector(mirrorSelector);
        return session;
    }

    private DependencyNode convert(org.eclipse.aether.graph.DependencyNode aetherNode,
                                   org.eclipse.aether.artifact.Artifact rootArtifactFallback) {
        if (aetherNode == null) return null;
        var a = aetherNode.getArtifact() != null ? aetherNode.getArtifact() : rootArtifactFallback;
        if (a == null) return null;
        Coordinate coord = new Coordinate(a.getGroupId(), a.getArtifactId(), a.getVersion());
        Dependency dep = aetherNode.getDependency();
        String scope = dep != null ? dep.getScope() : "compile";
        boolean optional = dep != null && dep.isOptional();
        List<DependencyNode> children = new ArrayList<>();
        for (var child : aetherNode.getChildren()) {
            DependencyNode converted = convert(child, null);
            if (converted != null) {
                children.add(converted);
            }
        }
        return new DependencyNode(coord, scope, optional, children);
    }
}
