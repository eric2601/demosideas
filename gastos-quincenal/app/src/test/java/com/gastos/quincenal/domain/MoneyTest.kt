package com.gastos.quincenal.domain

import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.model.sumOfMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `las sumas repetidas no acumulan error de punto flotante`() {
        // 0.1 + 0.2 con Double no da exactamente 0.3; con centavos si.
        val total = listOf(Money.ofPesos(0.1), Money.ofPesos(0.2)).sumOfMoney()
        assertEquals(30L, total.cents)
    }

    @Test
    fun `sumar mil veces un centavo da exactamente diez pesos`() {
        val total = List(1_000) { Money(1L) }.sumOfMoney()
        assertEquals(Money.ofPesos(10.0), total)
    }

    @Test
    fun `parse acepta separador de miles y decimales`() {
        assertEquals(Money(1_360_000L), Money.parse("13,600.00"))
        assertEquals(Money(1_360_000L), Money.parse("$13,600"))
        assertEquals(Money(123_456L), Money.parse("1234.56"))
        assertEquals(Money(50L), Money.parse("0.5"))
    }

    @Test
    fun `parse acepta la coma como separador decimal`() {
        assertEquals(Money(123_450L), Money.parse("1234,50"))
    }

    @Test
    fun `parse acepta negativos y espacios`() {
        assertEquals(Money(-77_800L), Money.parse(" -$778.00 "))
    }

    @Test
    fun `parse rechaza texto invalido`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("12.3.4x"))
        assertNull(Money.parse("-"))
    }

    @Test
    fun `las operaciones basicas son exactas`() {
        val income = Money.ofPesos(13_600.0)
        val expenses = Money.ofPesos(12_434.0)
        assertEquals(Money.ofPesos(1_166.0), income - expenses)
        assertTrue((expenses - income).isNegative)
        assertEquals(Money.ofPesos(1_166.0), (expenses - income).abs())
    }

    @Test
    fun `ofPesos convierte pesos a centavos`() {
        assertEquals(Money(1_360_000L), Money.ofPesos(13_600.0))
        assertEquals(Money(111_400L), Money.ofPesos(1_114.0))
        assertEquals(Money(25L), Money.ofPesos(0.25))
    }
}
