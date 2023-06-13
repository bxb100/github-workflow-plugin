package com.github.yunabraska.githubworkflow.completion;

import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

@SuppressWarnings("unchecked")
public class YamlNode {
    protected final int index;
    protected final String name;
    protected final String value;
    protected final YamlNode parent;
    protected final List<YamlNode> children = new ArrayList<>();

    protected YamlNode(
            final String name,
            final String value,
            final YamlNode parent,
            final int index
    ) {
        this.name = name;
        this.value = value;
        this.parent = parent;
        this.index = index;
    }

    @SuppressWarnings("unused")
    public static YamlNode loadYaml(final Path path) {
        try (final FileReader fileReader = new FileReader(path.toFile())) {
            return yamlNodeOf(new Yaml().load(fileReader));
        } catch (IOException ignored) {
            //ignored
        }
        return null;
    }

    protected static YamlNode yamlNodeOf(final YamlNode parent, final Object key, final Object value, final int index) {
        if (value instanceof Map) {
            final YamlNode currentNode = new YamlNode(key == null ? null : key.toString(), null, parent, index);
            currentNode.children.addAll(yamlNodesOf(currentNode, (Map<Object, Object>) value));
            return currentNode;
        }
        if (value instanceof Collection) {
            final YamlNode currentNode = new YamlNode(key == null ? null : key.toString(), null, parent, index);
            currentNode.children.addAll(yamlNodesOf(currentNode, (Collection<Object>) value));
            return currentNode;
        }
        return new YamlNode(key == null ? null : key.toString(), value == null ? null : value.toString(), parent, index);
    }

    private static YamlNode yamlNodeOf(final Object node) {
        return yamlNodeOf(null, null, node, 0);
    }

    private static List<YamlNode> yamlNodesOf(final YamlNode parent, final Map<Object, Object> map) {
        final AtomicInteger index = new AtomicInteger(0);
        return map.entrySet().stream().map(item -> yamlNodeOf(parent, item.getKey(), item.getValue(), index.getAndIncrement())).collect(Collectors.toList());
    }

    private static List<YamlNode> yamlNodesOf(final YamlNode parent, final Collection<Object> collection) {
        final AtomicInteger index = new AtomicInteger(0);
        return collection.stream().filter(Objects::nonNull).map(item -> yamlNodeOf(parent, null, item, index.getAndIncrement())).collect(Collectors.toList());
    }

    private static List<YamlNode> filterNodesRecursive(final YamlNode currentNode, final Predicate<YamlNode> filter, final List<YamlNode> resultNodes) {
        if (filter.test(currentNode)) {
            resultNodes.add(currentNode);
        }
        for (YamlNode child : currentNode.children) {
            filterNodesRecursive(child, filter, resultNodes);
        }
        return resultNodes;
    }

    public WorkflowFile toWorkflowFile() {
        return new WorkflowFile(this);
    }

    public List<YamlNode> getAllChildren(final Predicate<YamlNode> filter) {
        return filterNodesRecursive(this, filter, new ArrayList<>());
    }

    public Optional<YamlNode> getChild(final String childName) {
        return children.stream().filter(node -> node.name() != null && node.name().equals(childName)).findFirst();
    }

    public boolean hasName(final String name) {
        return ofNullable(name()).filter(name::equals).isPresent();
    }

    public boolean hasParent(final String name) {
        return ofNullable(parent()).map(YamlNode::name).filter(name::equals).isPresent();
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public YamlNode parent() {
        return parent;
    }

    public int index() {
        return index;
    }

    public Optional<YamlNode> getChildByIndex(final int index) {
        return index < 0 || index > children.size() ? Optional.empty() : Optional.ofNullable(children().get(index));
    }

    public List<YamlNode> children() {
        return children;
    }

    @Override
    public String toString() {
        return "YamlNode{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                ", children=" + children.size() +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final YamlNode yamlNode = (YamlNode) o;
        return Objects.equals(name, yamlNode.name) && Objects.equals(children, yamlNode.children) && (
                //Value needs to be only particularly checked so that it's easier to find the position.
                Objects.equals(value, yamlNode.value) || (value != null
                        && yamlNode.value != null
                        && value.length() > 0
                        && yamlNode.value.length() > 0
                        && (value.startsWith(yamlNode.value) || yamlNode.value.startsWith(value))
                )
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, children);
    }
}
