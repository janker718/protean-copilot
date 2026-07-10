package com.protean.copilot.handler;

import com.protean.copilot.handler.core.HandlerContext;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WindowEventHandlerTest {

    @Test
    public void lifecycleEventsAreConsumedAndForwarded() {
        List<String> callbacks = new ArrayList<>();
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override public void callJavaScript(String name, String... args) { callbacks.add(name + ":" + args[0]); }
            @Override public String escapeJs(String value) { return value; }
        });
        AtomicBoolean loading = new AtomicBoolean();
        AtomicReference<String> status = new AtomicReference<>();
        AtomicInteger newSessionRequests = new AtomicInteger();
        WindowEventHandler handler = new WindowEventHandler(
            context, loading::set, status::set, newSessionRequests::incrementAndGet
        );

        assertTrue(handler.handle("tab_loading_changed", "{\"loading\":true}"));
        assertTrue(loading.get());
        assertTrue(handler.handle("tab_status_changed", "{\"status\":\"completed\"}"));
        assertEquals("completed", status.get());
        assertTrue(handler.handle("refresh_slash_commands", "{}"));
        assertEquals("updateSlashCommands:[]", callbacks.get(0));
        assertTrue(handler.handle("create_new_session", ""));
        assertEquals(1, newSessionRequests.get());
    }
}
