package com.protean.copilot.permission;

import com.intellij.openapi.project.Project;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertSame;

public class PermissionSessionRegistryTest {

    @After
    public void tearDown() throws Exception {
        clearRegistry();
    }

    @Test
    public void findAnyInstanceForProjectPrefersMostRecentActiveSession() throws Exception {
        Project project = createProjectProxy();
        PermissionService olderService = createService(project, 100L);
        PermissionService newerService = createService(project, 200L);

        Map<String, PermissionService> instances = getInstancesBySessionId();
        instances.put("older", olderService);
        instances.put("newer", newerService);

        assertSame(newerService, PermissionSessionRegistry.findAnyInstanceForProject(project));
    }

    @Test
    public void findAnyInstanceForProjectPrefersNewerSessionOverLegacyInstance() throws Exception {
        Project project = createProjectProxy();
        PermissionService legacyService = createService(project, 100L);
        PermissionService activeSessionService = createService(project, 200L);

        setLegacyInstance(legacyService);
        getInstancesBySessionId().put("active", activeSessionService);

        assertSame(activeSessionService, PermissionSessionRegistry.findAnyInstanceForProject(project));
    }

    private static PermissionService createService(Project project, long lastActivityTime) throws Exception {
        PermissionService service = (PermissionService) getUnsafe().allocateInstance(PermissionService.class);
        setField(service, PermissionService.class, "project", project);
        setField(service, PermissionService.class, "lastActivityTime", lastActivityTime);
        return service;
    }

    private static Project createProjectProxy() {
        return (Project) Proxy.newProxyInstance(
            PermissionSessionRegistryTest.class.getClassLoader(),
            new Class<?>[]{Project.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "isDisposed" -> false;
                case "getBasePath" -> "/tmp/project";
                case "toString" -> "ProjectProxy";
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, PermissionService> getInstancesBySessionId() throws Exception {
        Field field = PermissionSessionRegistry.class.getDeclaredField("INSTANCES_BY_SESSION_ID");
        field.setAccessible(true);
        return (Map<String, PermissionService>) field.get(null);
    }

    private static void setLegacyInstance(PermissionService service) throws Exception {
        Field field = PermissionSessionRegistry.class.getDeclaredField("legacyInstance");
        field.setAccessible(true);
        field.set(null, service);
    }

    private static void clearRegistry() throws Exception {
        getInstancesBySessionId().clear();
        setLegacyInstance(null);
    }

    private static void setField(Object target, Class<?> owner, String fieldName, Object value) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
