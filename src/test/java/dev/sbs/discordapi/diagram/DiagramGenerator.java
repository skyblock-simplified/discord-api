package dev.sbs.discordapi.diagram;

import dev.sbs.discordapi.component.Component;
import dev.sbs.discordapi.component.capability.UserInteractable;
import dev.sbs.discordapi.context.EventContext;
import dev.simplified.reflection.diagram.DiagramConfig;
import dev.simplified.reflection.diagram.DiagramConfig.LayeringOption;
import dev.simplified.reflection.diagram.DiagramConfig.LayeringStrategy;
import dev.simplified.reflection.diagram.TypeHierarchyDiagram;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Generates SVG type-hierarchy diagrams for the discord-api module's context and component
 * packages using the {@link TypeHierarchyDiagram} engine.
 *
 * <p>
 * Run via Gradle: {@code ./gradlew :discord-api:generateDiagrams}
 *
 * @see EventContext
 * @see Component
 */
public class DiagramGenerator {

    /**
     * Generates all SVG diagrams and writes them to their respective {@code doc-files/}
     * directories under the source tree.
     *
     * @param args unused
     * @throws IOException if a file write fails
     */
    public static void main(String[] args) throws IOException {
        Path base = Path.of("src/main/java/dev/sbs/discordapi");
        contextDiagram().writeTo(base);
        componentDiagram().writeTo(base);
        System.out.println("Generated context and component diagrams.");
    }

    private static TypeHierarchyDiagram contextDiagram() {
        return DiagramConfig.builder()
            .withDocFilesPath(Path.of("context/doc-files"))
            .withFileName("ContextDiagram")
            .withSuffix("Context")
            .withScanPackage(EventContext.class)
            .withRoots(EventContext.class)
            .build()
            .render();
    }

    private static TypeHierarchyDiagram componentDiagram() {
        return DiagramConfig.builder()
            .withDocFilesPath(Path.of("component/doc-files"))
            .withFileName("ComponentDiagram")
            .withSuffix("Component")
            .withScanPackage(Component.class)
            .withRoots(Component.class, UserInteractable.class)
            .withTypeFilter(cls -> !cls.getPackageName().equals("dev.sbs.discordapi.component.capability"))
            .withLayeringOption(LayeringOption.STRATEGY, LayeringStrategy.MIN_WIDTH)
            .withLayeringOption(LayeringOption.MIN_WIDTH_UPPER_BOUND_ON_WIDTH, 2)
            .build()
            .render();
    }
}
