package io.github.younesic.backstage.maven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.classgraph.AnnotationEnumValue;
import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.AnnotationParameterValueList;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

import io.github.younesic.backstage.core.build.ResourceRequest;

/**
 * Reads {@code @BackstageComponent} from a module's compiled classes via ClassGraph (bytecode only —
 * the class is never loaded, no Spring side effects). Annotation values are copied into a plain holder
 * inside the scan scope so nothing touches the {@link ScanResult} after it closes.
 */
final class ModuleScanner {

    static final String ANNOTATION_FQN = "io.github.younesic.backstage.annotations.BackstageComponent";
    private static final String RESOURCE_FQN = "io.github.younesic.backstage.annotations.BackstageResource";
    private static final String RESOURCES_FQN = "io.github.younesic.backstage.annotations.BackstageResources";

    private ModuleScanner() {
    }

    /**
     * @return the single annotated component, or empty if the dir has no classes / no annotation.
     * @throws GenerationException if more than one class is annotated in the module.
     */
    static Optional<AnnotatedComponent> scan(Path outputDir) throws GenerationException {
        if (outputDir == null || !Files.isDirectory(outputDir)) {
            return Optional.empty();
        }
        try (ScanResult scan = new ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .overrideClasspath(outputDir.toString())
                .scan()) {
            ClassInfoList annotated = scan.getClassesWithAnnotation(ANNOTATION_FQN);
            if (annotated.isEmpty()) {
                return Optional.empty();
            }
            if (annotated.size() > 1) {
                throw new GenerationException(
                        "Multiple @BackstageComponent classes found: " + annotated.getNames()
                        + ". Exactly one is expected per module.");
            }
            ClassInfo ci = annotated.get(0);
            AnnotationInfo info = ci.getAnnotationInfo(ANNOTATION_FQN);
            AnnotationParameterValueList params = info.getParameterValues();

            AnnotatedComponent ac = new AnnotatedComponent();
            ac.className = ci.getName();
            ac.name = stringParam(params, "name", "");
            ac.type = stringParam(params, "type", "service");
            ac.owner = stringParam(params, "owner", "");
            ac.lifecycle = enumParam(params, "lifecycle");
            ac.system = stringParam(params, "system", null);
            ac.description = stringParam(params, "description", null);
            ac.tags = stringArrayParam(params, "tags");
            ac.dependsOn = stringArrayParam(params, "dependsOn");
            ac.consumesApis = stringArrayParam(params, "consumesApis");
            ac.providesApis = stringArrayParam(params, "providesApis");
            ac.annotations = stringArrayParam(params, "annotations");
            ac.resources = readResources(ci);
            ac.emitApi = boolParam(params, "emitApi", true);
            return Optional.of(ac);
        }
    }

    /** Read repeatable {@code @BackstageResource} (compiler wraps 2+ into the {@code @BackstageResources} container). */
    private static List<ResourceRequest> readResources(ClassInfo ci) {
        List<ResourceRequest> out = new ArrayList<>();
        AnnotationInfo container = ci.getAnnotationInfo(RESOURCES_FQN);
        if (container != null) {
            Object value = container.getParameterValues().getValue("value");
            if (value instanceof Object[]) {
                for (Object o : (Object[]) value) {
                    if (o instanceof AnnotationInfo) {
                        out.add(toResource((AnnotationInfo) o));
                    }
                }
            }
        } else {
            AnnotationInfo single = ci.getAnnotationInfo(RESOURCE_FQN);
            if (single != null) {
                out.add(toResource(single));
            }
        }
        return out;
    }

    private static ResourceRequest toResource(AnnotationInfo ai) {
        AnnotationParameterValueList p = ai.getParameterValues();
        // owner/system/description default to "" (INHERIT) → null so the Component value is inherited.
        return new ResourceRequest(
                stringParam(p, "name", ""),
                stringParam(p, "type", ""),
                stringParam(p, "owner", null),
                stringParam(p, "system", null),
                stringParam(p, "description", null));
    }

    private static String stringParam(AnnotationParameterValueList params, String name, String def) {
        Object v = params == null ? null : params.getValue(name);
        if (v == null) {
            return def;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? def : s;
    }

    private static boolean boolParam(AnnotationParameterValueList params, String name, boolean def) {
        Object v = params == null ? null : params.getValue(name);
        return (v instanceof Boolean) ? (Boolean) v : def;
    }

    private static String enumParam(AnnotationParameterValueList params, String name) {
        Object v = params == null ? null : params.getValue(name);
        if (v instanceof AnnotationEnumValue) {
            return ((AnnotationEnumValue) v).getValueName();
        }
        return v == null ? null : v.toString();
    }

    private static String[] stringArrayParam(AnnotationParameterValueList params, String name) {
        Object v = params == null ? null : params.getValue(name);
        if (v instanceof Object[]) {
            Object[] arr = (Object[]) v;
            String[] out = new String[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = String.valueOf(arr[i]);
            }
            return out;
        }
        return new String[0];
    }
}
