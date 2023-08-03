package com.mfrancza.jwtrevocation.client.manager

import com.mfrancza.jwtrevocation.rules.PartialList
import com.mfrancza.jwtrevocation.rules.Rule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerAuthConfig
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json

/**
 * Client for interacting with the JWT Revocation Manager service
 * @param managerUrl the URL of the manager server
 * @param bearerAuthConfig a block configuring the BearerAuthConfig to obtain the JWT authorizing access to the manager service
 * @param engine the HttpClientEngine to use to make requests
 */
class ManagerClient (private val managerUrl: String, bearerAuthConfig: BearerAuthConfig.() -> Unit, engine: HttpClientEngine? = null) {
    private val httpClient = run {
        val configBlock: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) {
                json()
            }
            install(HttpCache)
            install(Auth) {
                bearer(bearerAuthConfig)
            }
        }
        if (engine != null) {
            HttpClient(engine, configBlock)
        } else {
            HttpClient(configBlock)
        }
    }

    /**
     * Lists the rules defined in the manager service
     * @param limit the maximum number of rules to return
     * @param cursor indicates the position of next rule to return; cursor values are implementation specific and returned in PartialLists
     * @return the Rules in the range specified and a cursor if there are more rules to list
     */
    suspend fun listRules(limit: Int? = null, cursor: String? = null) : PartialList<Rule> = httpClient.get {
        url {
            takeFrom(managerUrl)
            appendPathSegments("rules")
            if (limit != null) {
                parameters.append("limit", limit.toString())
            }
            if (cursor != null) {
                parameters.append("cursor", cursor)
            }
        }
    }.body()

    /**
     * Retrieves a rule
     * @param ruleId the ID of the rule
     * @return the Rule with the ID or null if no matching rule is found
     */
    suspend fun getRule(ruleId: String) : Rule? = httpClient.get {
        url {
            takeFrom(managerUrl)
            appendPathSegments("rules", ruleId)
        }
    }.takeUnless { it.status == HttpStatusCode.NotFound }?.body()
}