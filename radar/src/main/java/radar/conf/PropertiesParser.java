package radar.conf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import radar.Bootstrap;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class PropertiesParser {

    private static final Logger logger = LoggerFactory.getLogger(PropertiesParser.class);

    public final List<String> inputArguments;
    public final Map<String, Map<String, String>> nodeToProperties;
    public final Set<String> selectedNodes;
    public List<String> nodeSequences;

    public PropertiesParser(final String[] args) {

        inputArguments = Arrays.asList(args);
        logger.trace("input arguments: " + inputArguments);

        final Map<String, String> properties = loadProperties(args);
        logger.trace("properties: " + properties);

        selectedNodes = Arrays.asList(properties.get("command").split(",|;|\\(|\\)|\\{|}")).stream().filter(e -> e.length() > 0).collect(Collectors.toSet());
        logger.trace("selectedNodes: " + selectedNodes);

        final Map<String, String> variables = filterProperties(properties, "env.").entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().substring("env.".length()), e -> e.getValue()));
        logger.trace("variables: " + variables);

        final Map<String, String> selectedProperties = filterProperties(properties, selectedNodes.stream().map(e -> e + ".").collect(Collectors.toSet()));
        logger.trace("selectedProperties: " + selectedProperties);

        final Map<String, String> resolvedProperties = replaceVariables(selectedProperties, variables, new VariableResolver());
        logger.trace("resolvedProperties: " + resolvedProperties);

        nodeToProperties = wrapProperties(resolvedProperties);
        logger.trace("nodeToProperties: " + nodeToProperties);

        nodeSequences = Arrays.asList(properties.get("command").split(";"));
        logger.trace("nodeSequences: " + nodeSequences);
    }

    public static Map<String, String> loadProperties(String[] arguments) {
        Optional<Properties> properties = Arrays.stream(arguments)
                .filter((String s) -> !s.startsWith("-"))
                .map((String s) -> {
                    Path p = Paths.get(s);
                    Path path;
                    if (p.isAbsolute()) {
                        path = p;
                    } else {
                        try {
                            Path root = Paths.get(Bootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                            path = root.getParent().resolveSibling(p);
                        } catch (URISyntaxException e) {
                            System.err.println(e); //TODO log
                            path = p;
                        }
                    }
                    return path;
                })
                .map((Path p) -> {
                    Properties prop = new Properties();
                    try {
                        prop.load(Files.newBufferedReader(p, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        System.err.println(e);//TODO log
                    }
                    return prop;
                })
                .reduce((Properties p, Properties r) -> {
                    p.putAll(r);
                    return p;
                });
        Properties propertiesFromArguments = Arrays.stream(arguments)
                .filter((String s) -> s.startsWith("-") && s.contains("="))
                .map((String s) -> s.substring(1))
                .collect(() -> new Properties(),
                        (Properties p, String s) -> p.put(s.substring(0, s.indexOf("=")), s.substring(s.indexOf("=") + 1)),
                        (Properties p, Properties r) -> p.putAll(r));
        properties.get().putAll(propertiesFromArguments);

        return properties.get().entrySet().stream()
                .collect(Collectors.toMap(e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue())));
        // (new TreeMap(fileProperties.get())).forEach((k, v) -> System.out.println(k + "=" + v));//TreeMap to order elements, Properties is a hashmap
        //  Arrays.asList(fileProperties.get().getProperty("run").split("->|,|;")).forEach(s -> System.out.println(s.trim()));
    }

    public static Map<String, String> filterProperties(Map<String, String> properties, String prefix) {
        Set<String> prefixes = new HashSet<>(1);
        prefixes.add(prefix);
        return filterProperties(properties, prefixes);
    }

    public static Map<String, String> filterProperties(Map<String, String> properties, Set<String> prefixes) {
        return properties.entrySet().stream()
                .filter(property -> prefixes.stream().anyMatch(prefix -> property.getKey().startsWith(prefix)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static Map<String, String> replaceVariables(Map<String, String> properties, Map<String, String> variables, BiFunction<String, Map<String, String>, String> replacer) {
        return properties.entrySet().stream()
                .collect(Collectors.toMap(e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(replacer.apply(e.getValue().toString(), variables))));
    }

    public static Map<String, Map<String, String>> wrapProperties(Map<String, String> properties) {
        return properties.entrySet().stream()
                .filter(e -> e.getKey().contains("."))
                .collect(Collectors.groupingBy(e -> e.getKey().substring(0, e.getKey().indexOf(".")),
                        Collectors.toMap(e -> e.getKey().substring(e.getKey().indexOf(".") + 1),
                                e -> e.getValue())));
    }

    public static Map<String, String> getParentKeyToChildProperty(Map<String, Map<String, String>> properties, Set<String> parentKeys, String childKey) {
        return properties.entrySet().stream().filter(e -> parentKeys.contains(e.getKey()))
                .filter(e -> e.getValue().keySet().contains(childKey))
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().get(childKey)));
    }

    class VariableResolver implements BiFunction<String, Map<String, String>, String> {
        @Override
        public String apply(String text, Map<String, String> variables) {
            for (Map.Entry<String, String> var : variables.entrySet()) {
                final String newVal;
                if (var.getValue().startsWith("today.format(")) {
                    int start = "today.format('".length();
                    int end = var.getValue().length() - 2;//get rid of last ')
                    String format = var.getValue().substring(start, end);
                    newVal = (new SimpleDateFormat(format)).format(Calendar.getInstance().getTime()); //today
                } else {
                    newVal = var.getValue();
                }
                text = text.replace("${" + var.getKey() + "}", newVal); //variable use is enclosed in '${' '}'
            }
            return text;
        }
    }

    public String toString() {
        return "input arguments: " + inputArguments
                + "\nnodeToProperties: " + nodeToProperties
                + "\nselectedNodes: " + selectedNodes
                + "\nnodeSequences: " + nodeSequences;
    }
}
