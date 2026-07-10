package com.protean.copilot.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.service.NodeProcessInfo;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NodeProcessHandlerTest {

    @Test
    public void getNodeProcessesPushesStructuredSnapshot() {
        CallbackRecorder callbacks = new CallbackRecorder();
        NodeProcessHandler handler = new NodeProcessHandler(
            createContext(callbacks),
            new FakeOperations(List.of(
                NodeProcessInfo.builder()
                    .kind(NodeProcessInfo.Kind.DAEMON)
                    .provider("claude")
                    .pid(1234L)
                    .alive(true)
                    .sessionId("session-1")
                    .tabName("Chat")
                    .activeRequestCount(2)
                    .build()
            )),
            Runnable::run,
            (task, delayMs) -> { },
            callbacks::record
        );

        assertTrue(handler.handle("get_node_processes", ""));

        assertEquals(1, callbacks.calls.size());
        assertEquals("window.updateNodeProcesses", callbacks.calls.get(0).functionName);
        JsonObject payload = JsonParser.parseString(callbacks.calls.get(0).payload).getAsJsonObject();
        assertEquals(1, payload.getAsJsonObject("totals").get("daemon").getAsInt());
        assertEquals(1, payload.getAsJsonObject("totals").get("all").getAsInt());
        assertEquals(1, payload.getAsJsonArray("processes").size());
        assertEquals(1234L, payload.getAsJsonArray("processes").get(0).getAsJsonObject().get("pid").getAsLong());
    }

    @Test
    public void killNodeProcessReportsResultAndRefreshesSnapshot() {
        CallbackRecorder callbacks = new CallbackRecorder();
        FakeOperations operations = new FakeOperations(List.of(
            NodeProcessInfo.builder()
                .kind(NodeProcessInfo.Kind.ORPHAN)
                .provider("codex")
                .pid(999L)
                .alive(false)
                .build()
        ));
        operations.killByPidResult = true;

        NodeProcessHandler handler = new NodeProcessHandler(
            createContext(callbacks),
            operations,
            Runnable::run,
            (task, delayMs) -> task.run(),
            callbacks::record
        );

        assertTrue(handler.handle("kill_node_process", "{\"pid\":999,\"id\":\"orphan-999\"}"));

        assertEquals(2, callbacks.calls.size());
        assertEquals("window.nodeProcessKillResult", callbacks.calls.get(0).functionName);
        JsonObject result = JsonParser.parseString(callbacks.calls.get(0).payload).getAsJsonObject();
        assertTrue(result.get("success").getAsBoolean());
        assertEquals("orphan-999", result.get("id").getAsString());

        assertEquals("window.updateNodeProcesses", callbacks.calls.get(1).functionName);
        assertEquals(999L, operations.lastKilledPid);
    }

    private static HandlerContext createContext(CallbackRecorder callbacks) {
        return new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override
            public void callJavaScript(String functionName, String... args) {
                callbacks.record(functionName, args[0]);
            }

            @Override
            public String escapeJs(String str) {
                return str;
            }
        });
    }

    private static final class FakeOperations implements NodeProcessHandler.Operations {

        private final List<NodeProcessInfo> snapshot;
        private boolean killByPidResult;
        private long lastKilledPid = -1L;

        private FakeOperations(List<NodeProcessInfo> snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public List<NodeProcessInfo> snapshot() {
            return snapshot;
        }

        @Override
        public boolean killByPid(long pid) {
            lastKilledPid = pid;
            return killByPidResult;
        }

        @Override
        public int killAllOrphans() {
            return 0;
        }

        @Override
        public boolean restartDaemonByPid(long pid) {
            return false;
        }
    }

    private static final class CallbackRecorder {
        private final List<Call> calls = new ArrayList<>();

        private void record(String functionName, String payload) {
            calls.add(new Call(functionName, payload));
        }
    }

    private static final class Call {
        private final String functionName;
        private final String payload;

        private Call(String functionName, String payload) {
            this.functionName = functionName;
            this.payload = payload;
        }
    }
}
