package com.github.lhein.camelsanity.resolver;

import com.github.lhein.camelsanity.model.Coordinate;
import com.github.lhein.camelsanity.model.DependencyNode;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
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
            CollectRequest collect = new CollectRequest(
                    new Dependency(artifact, "compile"), repositories);
            DependencyRequest request = new DependencyRequest(collect, null);
            DependencyResult result = system.resolveDependencies(session, request);
            return convert(result.getRoot());
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
