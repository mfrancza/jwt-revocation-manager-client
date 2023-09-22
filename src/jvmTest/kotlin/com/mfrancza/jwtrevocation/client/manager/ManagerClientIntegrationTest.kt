package com.mfrancza.jwtrevocation.client.manager

import io.ktor.client.plugins.auth.providers.BearerTokens
import kotlinx.coroutines.test.runTest
import java.time.Instant
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertTrue

@Tag("integration")
class ManagerClientIntegrationTest {
    private val serverUrl: String = System.getenv("JRM_TEST_SERVER_URL")
    private val accessToken: String = System.getenv("JRM_TEST_ACCESS_TOKEN")

    @Test

    fun rulesetCanBeRetrievedFromServer() = runTest {
        val client = ManagerClient(serverUrl, {
            loadTokens {
                BearerTokens(accessToken, "")
            }
        })

        assertTrue(client.getRuleSet().timestamp < Instant.now().epochSecond + 5, "Rule Set should be returned with a timestamp around the present" )
    }


}