package org.didban.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandOperationGateTest {

    @Test
    fun `a newer operation owns the state and invalidates the previous one`() {
        val gate = CommandOperationGate()
        val first = gate.begin()
        val second = gate.begin()

        assertFalse(gate.owns(first))
        assertTrue(gate.owns(second))
    }

    @Test
    fun `cancel invalidates the active operation`() {
        val gate = CommandOperationGate()
        val operation = gate.begin()

        gate.cancel()

        assertFalse(gate.owns(operation))
    }

    @Test
    fun `a later operation remains owned after an old completion arrives`() {
        val gate = CommandOperationGate()
        val oldOperation = gate.begin()
        val currentOperation = gate.begin()

        // This models a late finally/result from oldOperation. It must not
        // change ownership of the current operation.
        assertFalse(gate.owns(oldOperation))
        assertTrue(gate.owns(currentOperation))
    }
}
