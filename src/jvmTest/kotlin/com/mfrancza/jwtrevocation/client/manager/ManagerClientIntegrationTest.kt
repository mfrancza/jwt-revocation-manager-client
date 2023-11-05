package com.mfrancza.jwtrevocation.client.manager

import com.mfrancza.jwtrevocation.rules.Rule
import com.mfrancza.jwtrevocation.rules.conditions.StringEquals
import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.test.runTest
import kotlin.test.assertNull
import java.time.Instant
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Tag("integration")
class ManagerClientIntegrationTest {
    private val serverUrl: String = System.getenv("JRM_TEST_SERVER_URL")
    private val accessToken: String = System.getenv("JRM_TEST_ACCESS_TOKEN")

    @Test
    fun createAndRetrieveRules() = runTest(timeout = 30.seconds){
        val client = ManagerClient(serverUrl, {
            loadTokens {
                BearerTokens(accessToken, "")
            }
        })

        val newRule = client.createRule(Rule(
            ruleExpires = Instant.now().epochSecond,
            iss = listOf(
                StringEquals(
                    value = "bad-issuer.mfrancza.com"
                )
            )
        ))

        val retrievedRule = client.getRule(
            ruleId = newRule.ruleId!!
        )

        assertEquals(newRule, retrievedRule, "Newly created rule should be returned")

        Thread.sleep(6000)

        val ruleSet = client.getRuleSet()
        assertContains(ruleSet.rules, newRule, "RuleSet should contain the new rule")
        assertTrue(ruleSet.timestamp < Instant.now().epochSecond + 5, "RuleSet should be returned with a timestamp around the present" )

        val deletedRule = client.deleteRule(newRule.ruleId!!)

        assertEquals(newRule, deletedRule, "Deleted rule should be returned")
        assertNull(client.getRule(ruleId = newRule.ruleId!!), "Rule should no longer be returned")

        Thread.sleep(6000)

        val updatedRuleSet = client.getRuleSet()
        assertFalse(updatedRuleSet.rules.contains(newRule), "RuleSet should contain the new rule")
        assertTrue(updatedRuleSet.timestamp < Instant.now().epochSecond + 5, "RuleSet should be returned with a timestamp around the present" )
    }
}