package com.protean.copilot.session;

import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SessionLoadServiceTest {

    @After
    public void tearDown() {
        SessionLoadService.clearListener();
    }

    @Test
    public void replaysPendingRequestWhenListenerRegisters() {
        SessionLoadService.triggerLoad(HistorySessionLoadRequest.of("session-1", "/tmp/project", "claude"));

        AtomicReference<HistorySessionLoadRequest> received = new AtomicReference<>();
        SessionLoadService.setListener(received::set);

        assertEquals("session-1", received.get().sessionId());
        assertEquals("/tmp/project", received.get().projectPath());
        assertEquals("claude", received.get().provider());
    }

    @Test
    public void dispatchesImmediatelyWhenListenerExists() {
        AtomicReference<HistorySessionLoadRequest> received = new AtomicReference<>();
        SessionLoadService.setListener(received::set);

        SessionLoadService.triggerLoad("session-2", "/tmp/project");

        assertEquals("session-2", received.get().sessionId());
        assertEquals("/tmp/project", received.get().projectPath());
        assertEquals("claude", received.get().provider());
    }

    @Test
    public void clearsListenerWithoutDispatching() {
        SessionLoadService.setListener(request -> {
        });
        SessionLoadService.clearListener();

        AtomicReference<HistorySessionLoadRequest> received = new AtomicReference<>();
        SessionLoadService.setListener(received::set);

        assertNull(received.get());
    }
}
