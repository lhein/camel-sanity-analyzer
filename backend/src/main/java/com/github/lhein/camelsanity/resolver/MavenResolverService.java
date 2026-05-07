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
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
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

    public DependencyNode resolveTree(Coordinate root, boolean includeTest) {
        try {
            DefaultRepositorySystemSession session = newSession(includeTest);
            DefaultArtifact artifact = new DefaultArtifact(
                    root.groupId(), root.artifactId(), "jar", root.version());
            CollectRequest collect = new CollectRequest(
                    new Dependency(artifact, "compile"), repositories);
            // collectDependencies builds the graph from POMs only — it does NOT
            // download the actual JARs. That's all we need for analysis and
            // avoids spurious failures on artifacts that exist in the dependency
            // graph but whose JARs are not on Maven Central (old JEE jars,
            // OS-specific classifiers like ${os.detected.name}-${os.detected.arch},
            // tests-classifier artifacts, etc.).
            CollectResult result = system.collectDependencies(session, collect);
            return convert(result.getRoot());
        } catch (Exception e) {
            log.error("Failed to resolve {}: {}", root.gav(), e.getMessage());
            throw new RuntimeException("Failed to resolve " + root.gav() + ": " + e.getMessage(), e);
        }
    }

    private DefaultRepositorySystemSession newSession(boolean includeTest) {
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
        if (includeTest) {
            // Drop the default exclusion of test/provided so test-scope artifacts
            // appear in the tree. Keep optional and exclusion selectors active.
            session.setDependencySelector(new AndDependencySelector(
                    new ScopeDependencySelector(),
                    new OptionalDependencySelector(),
                    new ExclusionDependencySelector()));
        }
        return session;
    }

    private DependencyNode convert(org.eclipse.aether.graph.DependencyNode aetherNode) {
        if (aetherNode == null || aetherNode.getArtifact() == null) {
            return null;
        }
        var a = aetherNode.getArtifact();
        Coordinate coord = new Coordinate(a.getGroupId(), a.getArtifactId(), a.getVersion());
        Dependency dep = aetherNode.getDependency();
        String scope = dep != null ? dep.getScope() : "compile";
        boolean optional = dep != null && dep.isOptional();
        List<DependencyNode> children = new ArrayList<>();
        for (var child : aetherNode.getChildren()) {
            DependencyNode converted = convert(child);
            if (converted != null) {
                children.add(converted);
            }
        }
        return new DependencyNode(coord, scope, optional, children);
    }
}
