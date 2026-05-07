package com.github.lhein.camelsanity.model;

import java.util.List;

/**
 * Tree node representing a dependency. Children are direct transitive dependencies.
 */
public record DependencyNode(
        Coordinate coordinate,
        String scope,
        boolean optional,
        List<DependencyNode> children) {
}
