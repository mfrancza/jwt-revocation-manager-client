package com.mfrancza.jwtrevocation.client.manager

import com.mfrancza.jwtrevocation.rules.PartialList
import com.mfrancza.jwtrevocation.rules.Rule
import com.mfrancza.jwtrevocation.rules.RuleSet
import com.mfrancza.jwtrevocation.rules.conditions.DateTimeAfter
import com.mfrancza.jwtrevocation.rules.conditions.StringEquals
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import io.ktor.util.Identity.decode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagerClientTest {

    private val referenceEpochSeconds : Long = 1673123605

    @Test
    fun getRuleSetRetrievedRuleSetIsCached() = runTest {
        val expectedRuleSet = RuleSet(
            rules = listOf(),
            timestamp = referenceEpochSeconds
        )
        val mockEngine = MockEngine { request ->
            respond(
                content = ByteReadChannel(Json.encodeToString(expectedRuleSet)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = ManagerClient("https://mfrancza.com/ruleset", {}, mockEngine)

        assertEquals(expectedRuleSet, client.getRuleSet(), "The returned rule set should match the one from the server")
    }

    @Test
    fun getRuleSetRetrievedRuleSetMatchesValueFromServer() = runTest {
        val expectedRuleSet = RuleSet(
            rules = listOf(),
            timestamp = referenceEpochSeconds
        )

        var timesCalled = 0
        val mockEngine = MockEngine { request ->
            timesCalled++
            respond(
                content = ByteReadChannel(Json.encodeToString(expectedRuleSet)),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    Pair(HttpHeaders.ContentType, listOf("application/json")),
                    Pair(HttpHeaders.CacheControl, listOf("max-age=604800"))
                )
            )
        }
        val client = ManagerClient("https://mfrancza.com/ruleset", {}, mockEngine)

        assertEquals(expectedRuleSet, client.getRuleSet(), "The returned rule set should match the one from the server")
        assertEquals(expectedRuleSet, client.getRuleSet(), "The returned rule set should still match the one from the server")

        assertEquals(1, timesCalled, "The result should be cached from the first call")
    }

    @Test
    fun getRulesWithNoLimit() = runTest {
        val expectedRules = PartialList(
            list = listOf(
                Rule(
                    ruleExpires =  1686433017,
                    exp = listOf(
                        DateTimeAfter(1686433017)
                    )
                ),
                Rule(
                    ruleExpires = 1686433017,
                    iss = listOf(
                        StringEquals("bad-iss.mfrancza.com")
                    )
                ),
                Rule(
                    ruleExpires = 1686433017,
                    aud = listOf(
                        StringEquals("bad-aud.mfrancza.com")
                    )
                )
            ),
            cursor = null
        )
        val mockEngine = MockEngine { request ->
            assertEquals("/jwt-revocation-manager/rules", request.url.encodedPath, "relative path should be /rules")
            val queryParameters = parseQueryString(request.url.encodedQuery)
            assertTrue(queryParameters.isEmpty(), "No query parameters should be passed when limit is not used")
            respond(
                content = ByteReadChannel(Json.encodeToString(expectedRules)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = ManagerClient("https://mfrancza.com/jwt-revocation-manager", {}, mockEngine)

        assertEquals(expectedRules, client.listRules(), "The expected rules should be deserialized")
    }

    @Test
    fun getRulesWithLimitAndCursor() = runTest {
        val allRules = listOf(
            Rule(
                ruleExpires =  1686433017,
                exp = listOf(
                    DateTimeAfter(1686433017)
                )
            ),
            Rule(
                ruleExpires = 1686433017,
                iss = listOf(
                    StringEquals("bad-iss.mfrancza.com")
                )
            ),
            Rule(
                ruleExpires = 1686433017,
                aud = listOf(
                    StringEquals("bad-aud.mfrancza.com")
                )
            )
        )

        val mockEngine = MockEngine { request ->
            val requestCursor = request.url.parameters["cursor"]?.toInt()
            val requestLimit = request.url.parameters["limit"]?.toInt()
            val start = requestCursor ?: 0
            val newCursor = if (requestLimit == null) {
                null
            } else {
                if (start + requestLimit > allRules.size) {
                    null
                } else {
                    start + requestLimit
                }
            }
            val end = newCursor ?: allRules.size

            respond(
                content = ByteReadChannel(Json.encodeToString(PartialList(
                    list = allRules.subList(start, end),
                    cursor = newCursor?.toString()
                ))),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = ManagerClient("https://mfrancza.com/jwt-revocation-manager", {}, mockEngine)

        val limit = 2

        val startOfListResponse = client.listRules(limit)
        assertEquals(allRules.subList(0, limit), startOfListResponse.list, "The first $limit rules should be in the list")
        assertNotNull(startOfListResponse.cursor, "There should be a cursor in the response")

        val endOfListResponse = client.listRules(limit, startOfListResponse.cursor)
        assertEquals(allRules.subList(limit, allRules.size), endOfListResponse.list, "The remaining rules should be in the list")
        assertNull(endOfListResponse.cursor, "There should not longer be a cursor in the response")
    }

    @Test
    fun getRuleRuleFound() = runTest {
        val rule = Rule(
            ruleId = "test-rule-id",
            ruleExpires = 1691085504,
            iss = listOf(
                StringEquals(
                    "bad-iss.mfrancza.com"
                )
            )
        )

        val managerUrl = "https://mfrancza.com/jwt-revocation-manager"

        val mockEngine = MockEngine { request ->
            assertEquals(managerUrl + "/rules/" + rule.ruleId!!, request.url.toString(), "Relative path should be rules/{ruleId}")
            respond(
                content = ByteReadChannel(Json.encodeToString(rule)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = ManagerClient(managerUrl, {}, mockEngine)

        assertEquals(rule, client.getRule(rule.ruleId!!), "Expected rule should be returned")
    }

    @Test
    fun getRuleRuleNotFound() = runTest {
        val invalidRuleId = "not-a-ruleId"

        val managerUrl = "https://mfrancza.com/jwt-revocation-manager"

        val mockEngine = MockEngine { request ->
            assertEquals("$managerUrl/rules/$invalidRuleId", request.url.toString(), "Relative path should be rules/{ruleId}")
            respond(
                content = "",
                status = HttpStatusCode.NotFound
            )
        }
        val client = ManagerClient(managerUrl, {}, mockEngine)

        assertNull(client.getRule(invalidRuleId), "Null should be returned when no Rule with ruleId is found")
    }

    @Test
    fun createRule() = runTest {
        val newRule = Rule(
            ruleExpires = 1691085504,
            iss = listOf(
                StringEquals(
                    "bad-iss.mfrancza.com"
                )
            )
        )

        val managerUrl = "https://mfrancza.com/jwt-revocation-manager"

        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method, "Request should be a POST since we are creating a new rule")
            assertEquals("$managerUrl/rules", request.url.toString(), "Relative path should be rules")
            val createdRule : Rule = Json.decodeFromString<Rule>(request.body.toByteArray().decodeToString()).copy(ruleId = "assigned-ruleId")
            respond(
                content = ByteReadChannel(Json.encodeToString(createdRule)),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = ManagerClient(managerUrl, {}, mockEngine)

        val createdRule = client.createRule(newRule)

        assertNotNull(createdRule.ruleId, "The created rule should be assigned a ruleId")

        assertEquals(newRule, createdRule.copy(ruleId = null), "The configuration of the created rule should match the request")
    }

    @Test
    fun deleteExistingRule() = runTest {
        val rule = Rule(
            ruleId = "test-rule-id",
            ruleExpires = 1691085504,
            iss = listOf(
                StringEquals(
                    "bad-iss.mfrancza.com"
                )
            )
        )

        val managerUrl = "https://mfrancza.com/jwt-revocation-manager"

        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method, "Method should be DELETE since we are deleting a record")
            assertEquals(managerUrl + "/rules/" + rule.ruleId!!, request.url.toString(), "Relative path should be rules/{ruleId}")
            respond(
                content = ByteReadChannel(Json.encodeToString(rule)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = ManagerClient(managerUrl, {}, mockEngine)

        assertEquals(rule, client.deleteRule(rule.ruleId!!), "Expected rule should be returned")
    }

    @Test
    fun deleteRuleNotFound() = runTest {
        val invalidRuleId = "not-a-ruleId"

        val managerUrl = "https://mfrancza.com/jwt-revocation-manager"

        val mockEngine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method, "Method should be DELETE since we are deleting a record")
            assertEquals("$managerUrl/rules/$invalidRuleId", request.url.toString(), "Relative path should be rules/{ruleId}")
            respond(
                content = "",
                status = HttpStatusCode.NotFound
            )
        }
        val client = ManagerClient(managerUrl, {}, mockEngine)

        assertNull(client.deleteRule(invalidRuleId), "Null should be returned when no Rule with ruleId is found")
    }
}