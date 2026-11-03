package com.fiap.mechanicalhub.billing.infrastructure.security

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GatewayAuthenticationFilterTest {
    private val filter = GatewayAuthenticationFilter()

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun run(headers: Map<String, String>): MockFilterChain {
        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest()
        headers.forEach { (name, value) -> request.addHeader(name, value) }
        val chain = MockFilterChain()
        filter.doFilter(request, MockHttpServletResponse(), chain)
        return chain
    }

    @Test
    fun `given valid headers when filtering then authenticates with the role authority`() {
        val id = UUID.randomUUID()

        val chain =
            run(
                mapOf(
                    GatewayAuthenticationFilter.USER_ID_HEADER to id.toString(),
                    GatewayAuthenticationFilter.USER_ROLE_HEADER to "admin",
                    GatewayAuthenticationFilter.USER_NAME_HEADER to "Maria",
                ),
            )

        val authentication = assertNotNull(SecurityContextHolder.getContext().authentication)
        val principal = authentication.principal as GatewayPrincipal
        assertEquals(id, principal.id)
        assertEquals("Maria", principal.name)
        assertEquals("admin", principal.role)
        assertEquals(id.toString(), principal.toString())
        assertEquals(listOf("ROLE_ADMIN"), authentication.authorities.map { it.authority })
        assertNotNull(chain.request)
    }

    @Test
    fun `given no headers when filtering then stays anonymous and continues the chain`() {
        val chain = run(emptyMap())

        assertNull(SecurityContextHolder.getContext().authentication)
        assertNotNull(chain.request)
    }

    @Test
    fun `given invalid user id when filtering then stays anonymous`() {
        run(
            mapOf(
                GatewayAuthenticationFilter.USER_ID_HEADER to "not-a-uuid",
                GatewayAuthenticationFilter.USER_ROLE_HEADER to "admin",
            ),
        )

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `given user id without role when filtering then stays anonymous`() {
        run(mapOf(GatewayAuthenticationFilter.USER_ID_HEADER to UUID.randomUUID().toString()))

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `given blank headers when filtering then stays anonymous`() {
        run(
            mapOf(
                GatewayAuthenticationFilter.USER_ID_HEADER to " ",
                GatewayAuthenticationFilter.USER_ROLE_HEADER to " ",
            ),
        )

        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
