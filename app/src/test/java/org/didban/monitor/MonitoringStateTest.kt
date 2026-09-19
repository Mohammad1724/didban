package org.didban.monitor

import org.junit.Assert.*
import org.junit.Test

class MonitoringStateTest {
    @Test fun `request is pending until service confirms and double taps do not start twice`() {
        val state = MonitoringState(); var starts = 0
        state.requestStart { starts++ }; state.requestStart { starts++ }
        assertEquals(1, starts)
        assertEquals(MonitoringPhase.STARTING, state.status.value.phase)
        assertFalse(state.status.value.running)
        state.started()
        assertTrue(state.status.value.running)
        state.requestStart { starts++ }
        assertEquals(1, starts)
    }
    @Test fun `startup rejection is not success and can be retried`() {
        val state = MonitoringState()
        state.requestStart { throw SecurityException("fixture") }
        assertEquals(MonitoringFailure.START, state.status.value.failure)
        assertFalse(state.status.value.running)
        state.stopped()
        assertEquals(MonitoringPhase.FAILED, state.status.value.phase)
        state.requestStart {}
        assertEquals(MonitoringPhase.STARTING, state.status.value.phase)
    }
    @Test fun `stop remains pending until real destruction`() {
        val state = MonitoringState(); state.started()
        state.requestStop { true }
        assertEquals(MonitoringPhase.STOPPING, state.status.value.phase)
        assertTrue(state.status.value.running)
        state.stopped()
        assertEquals(MonitoringStatus(), state.status.value)
    }
    @Test fun `stopping missing service is stopped not failed`() {
        val state = MonitoringState(); state.requestStart {}
        state.requestStop { false }
        assertEquals(MonitoringStatus(), state.status.value)
    }
    @Test fun `stop rejection preserves actual running state and supports retry`() {
        val state = MonitoringState(); state.started()
        state.requestStop { throw SecurityException("fixture") }
        assertTrue(state.status.value.running)
        assertEquals(MonitoringFailure.STOP, state.status.value.failure)
        state.requestStop { true }; state.stopped()
        assertFalse(state.status.value.running)
    }
    @Test fun `engine failure is retained after service cleanup`() {
        val state = MonitoringState(); state.started()
        state.failed(MonitoringFailure.ENGINE); state.stopped()
        assertFalse(state.status.value.running)
        assertEquals(MonitoringFailure.ENGINE, state.status.value.failure)
    }
    @Test fun `opening a state holder is inert and stopped stop is idempotent`() {
        val state = MonitoringState()
        state.requestStop { error("must not call") }
        assertEquals(MonitoringStatus(), state.status.value)
    }
}
