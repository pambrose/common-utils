package com.github.pambrose.json

import com.github.pambrose.common.json.booleanValue
import com.github.pambrose.common.json.doubleValue
import com.github.pambrose.common.json.getOrNull
import com.github.pambrose.common.json.intValue
import com.github.pambrose.common.json.jsonElementList
import com.github.pambrose.common.json.jsonElementListOrNull
import com.github.pambrose.common.json.jsonObjectValueOrNull
import com.github.pambrose.common.json.stringValue
import com.github.pambrose.common.json.stringValueOrNull
import com.github.pambrose.common.json.toJsonElement
import com.github.pambrose.common.json.toJsonString
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test

/**
 * Integration tests that test the interaction between JsonElementUtils and JsonContentUtils
 * and real-world usage scenarios.
 */
class JsonIntegrationTest {
  @Serializable
  data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
  )

  @Serializable
  data class User(
    val id: Long,
    val username: String,
    val email: String,
    val profile: UserProfile,
    val permissions: List<String> = emptyList(),
    val settings: UserSettings = UserSettings(),
  )

  @Serializable
  data class UserProfile(
    val firstName: String,
    val lastName: String,
    val bio: String? = null,
    val avatar: String? = null,
    val socialLinks: Map<String, String> = emptyMap(),
  )

  @Serializable
  data class UserSettings(
    val theme: String = "light",
    val notifications: NotificationSettings = NotificationSettings(),
    val privacy: PrivacySettings = PrivacySettings(),
  )

  @Serializable
  data class NotificationSettings(
    val email: Boolean = true,
    val push: Boolean = true,
    val sms: Boolean = false,
  )

  @Serializable
  data class PrivacySettings(
    val profileVisible: Boolean = true,
    val showEmail: Boolean = false,
    val allowMessages: Boolean = true,
  )

  private val sampleUser = User(
    id = 12345L,
    username = "johndoe",
    email = "john.doe@example.com",
    profile = UserProfile(
      firstName = "John",
      lastName = "Doe",
      bio = "Software developer passionate about Kotlin",
      avatar = "https://example.com/avatar/johndoe.jpg",
      socialLinks = mapOf(
        "github" to "https://github.com/johndoe",
        "twitter" to "https://twitter.com/johndoe",
        "linkedin" to "https://linkedin.com/in/johndoe",
      ),
    ),
    permissions = listOf("read", "write", "admin"),
    settings = UserSettings(
      theme = "dark",
      notifications = NotificationSettings(
        email = true,
        push = false,
        sms = true,
      ),
      privacy = PrivacySettings(
        profileVisible = false,
        showEmail = true,
        allowMessages = false,
      ),
    ),
  )

  private val successResponse = ApiResponse(
    success = true,
    data = sampleUser,
    metadata = mapOf(
      "version" to "1.0",
      "requestId" to "req-123",
      "server" to "api-01",
    ),
  )

  @Test
  fun `test complete API response serialization and parsing`() {
    // Serialize to JSON
    val json = successResponse.toJsonString(prettyPrint = true)
    json shouldNotBe null
    json.contains("success") shouldBe true
    json.contains("johndoe") shouldBe true

    // Parse back to JsonElement
    val jsonElement = json.toJsonElement()

    // Test top-level fields
    jsonElement.stringValueOrNull("error") shouldBe null
    jsonElement.booleanValue("success") shouldBe true
    jsonElement.stringValue("metadata.version") shouldBe "1.0"
    jsonElement.stringValue("metadata.requestId") shouldBe "req-123"
    jsonElement.stringValue("metadata.server") shouldBe "api-01"

    // Test deeply nested user data
    jsonElement.intValue("data.id").toLong() shouldBe 12345L
    jsonElement.stringValue("data.username") shouldBe "johndoe"
    jsonElement.stringValue("data.email") shouldBe "john.doe@example.com"

    // Test user profile
    jsonElement.stringValue("data.profile.firstName") shouldBe "John"
    jsonElement.stringValue("data.profile.lastName") shouldBe "Doe"
    jsonElement.stringValue("data.profile.bio") shouldBe "Software developer passionate about Kotlin"
    jsonElement.stringValue("data.profile.avatar") shouldBe "https://example.com/avatar/johndoe.jpg"

    // Test social links
    jsonElement.stringValue("data.profile.socialLinks.github") shouldBe "https://github.com/johndoe"
    jsonElement.stringValue("data.profile.socialLinks.twitter") shouldBe "https://twitter.com/johndoe"
    jsonElement.stringValue("data.profile.socialLinks.linkedin") shouldBe "https://linkedin.com/in/johndoe"

    // Test permissions array
    val permissions = jsonElement.jsonElementList("data.permissions")
    permissions.size shouldBe 3
    permissions[0].stringValue shouldBe "read"
    permissions[1].stringValue shouldBe "write"
    permissions[2].stringValue shouldBe "admin"

    // Test settings
    jsonElement.stringValue("data.settings.theme") shouldBe "dark"
    jsonElement.booleanValue("data.settings.notifications.email") shouldBe true
    jsonElement.booleanValue("data.settings.notifications.push") shouldBe false
    jsonElement.booleanValue("data.settings.notifications.sms") shouldBe true
    jsonElement.booleanValue("data.settings.privacy.profileVisible") shouldBe false
    jsonElement.booleanValue("data.settings.privacy.showEmail") shouldBe true
    jsonElement.booleanValue("data.settings.privacy.allowMessages") shouldBe false
  }

  @Test
  fun `test error response handling`() {
    val errorResponse = ApiResponse<User>(
      success = false,
      error = "User not found",
      metadata = mapOf("requestId" to "req-456"),
    )

    val jsonString = errorResponse.toJsonString()
    val jsonElement = jsonString.toJsonElement()

    jsonElement.getOrNull("data") shouldBe null
    jsonElement.booleanValue("success") shouldBe false
    jsonElement.stringValue("error") shouldBe "User not found"
    jsonElement.stringValue("metadata.requestId") shouldBe "req-456"
  }

  @Test
  fun `test real-world JSON parsing from external API`() {
    // Simulate response from external API
    val externalApiResponse = """
            {
                "status": "success",
                "code": 200,
                "data": {
                    "users": [
                        {
                            "id": 1,
                            "name": "Alice Johnson",
                            "email": "alice@example.com",
                            "role": "admin",
                            "lastLogin": "2023-12-01T10:30:00Z",
                            "preferences": {
                                "language": "en",
                                "timezone": "UTC",
                                "dateFormat": "ISO"
                            }
                        },
                        {
                            "id": 2,
                            "name": "Bob Smith",
                            "email": "bob@example.com",
                            "role": "user",
                            "lastLogin": "2023-12-02T14:15:00Z",
                            "preferences": {
                                "language": "fr",
                                "timezone": "Europe/Paris",
                                "dateFormat": "European"
                            }
                        }
                    ],
                    "pagination": {
                        "page": 1,
                        "pageSize": 10,
                        "total": 2,
                        "hasNext": false
                    }
                },
                "requestId": "api-req-789",
                "timestamp": 1701429600
            }
        """.trimIndent()

    val jsonElement = externalApiResponse.toJsonElement()

    // Test response metadata
    jsonElement.stringValue("status") shouldBe "success"
    jsonElement.intValue("code") shouldBe 200
    jsonElement.stringValue("requestId") shouldBe "api-req-789"
    jsonElement.intValue("timestamp").toLong() shouldBe 1701429600L

    // Test users array
    val users = jsonElement.jsonElementList("data.users")
    users.size shouldBe 2

    // Test first user
    val alice = users[0]
    alice.intValue("id") shouldBe 1
    alice.stringValue("name") shouldBe "Alice Johnson"
    alice.stringValue("email") shouldBe "alice@example.com"
    alice.stringValue("role") shouldBe "admin"
    alice.stringValue("lastLogin") shouldBe "2023-12-01T10:30:00Z"
    alice.stringValue("preferences.language") shouldBe "en"
    alice.stringValue("preferences.timezone") shouldBe "UTC"
    alice.stringValue("preferences.dateFormat") shouldBe "ISO"

    // Test second user
    val bob = users[1]
    bob.intValue("id") shouldBe 2
    bob.stringValue("name") shouldBe "Bob Smith"
    bob.stringValue("email") shouldBe "bob@example.com"
    bob.stringValue("role") shouldBe "user"
    bob.stringValue("preferences.language") shouldBe "fr"
    bob.stringValue("preferences.timezone") shouldBe "Europe/Paris"

    // Test pagination
    jsonElement.intValue("data.pagination.page") shouldBe 1
    jsonElement.intValue("data.pagination.pageSize") shouldBe 10
    jsonElement.intValue("data.pagination.total") shouldBe 2
    jsonElement.booleanValue("data.pagination.hasNext") shouldBe false
  }

  @Test
  fun `test JSON transformation and manipulation`() {
    val json = sampleUser.toJsonElement()

    // Extract and transform data
    val userSummary =
      buildJsonObject {
        put("id", json.intValue("id"))
        put(
          "displayName",
          "${json.stringValue("profile.firstName")} ${json.stringValue("profile.lastName")}",
        )
        put("contact", json.stringValue("email"))
        put(
          "role",
          if (json.jsonElementList("permissions").any { it.stringValue == "admin" })
            "Administrator"
          else
            "User",
        )
        put("theme", json.stringValue("settings.theme"))
        put("notificationsEnabled", json.booleanValue("settings.notifications.email"))
      }

    // Test the transformed data
    userSummary.intValue("id") shouldBe 12345
    userSummary.stringValue("displayName") shouldBe "John Doe"
    userSummary.stringValue("contact") shouldBe "john.doe@example.com"
    userSummary.stringValue("role") shouldBe "Administrator"
    userSummary.stringValue("theme") shouldBe "dark"
    userSummary.booleanValue("notificationsEnabled") shouldBe true
  }

  @Test
  fun `test bulk data processing`() {
    // Simulate processing a batch of users
    val users = listOf(
      sampleUser.copy(id = 1, username = "user1"),
      sampleUser.copy(id = 2, username = "user2", settings = sampleUser.settings.copy(theme = "light")),
      sampleUser.copy(id = 3, username = "user3", permissions = listOf("read")),
    )

    val batchResponse = ApiResponse(
      success = true,
      data = users,
      metadata = mapOf("batchSize" to "3", "processingTime" to "150ms"),
    )

    val jsonElement = batchResponse.toJsonString().toJsonElement()

    // Test batch metadata
    jsonElement.booleanValue("success") shouldBe true
    jsonElement.stringValue("metadata.batchSize") shouldBe "3"
    jsonElement.stringValue("metadata.processingTime") shouldBe "150ms"

    // Test users array processing
    val usersArray = jsonElement.jsonElementList("data")
    usersArray.size shouldBe 3

    // Process each user and verify
    usersArray.forEachIndexed { index, user ->
      val expectedId = index + 1
      user.intValue("id") shouldBe expectedId
      user.stringValue("username") shouldBe "user$expectedId"
      user.stringValue("email") shouldBe "john.doe@example.com"

      when (expectedId) {
        1 -> user.stringValue("settings.theme") shouldBe "dark"
        2 -> user.stringValue("settings.theme") shouldBe "light"
        3 -> {
          user.stringValue("settings.theme") shouldBe "dark"
          val permissions = user.jsonElementList("permissions")
          permissions.size shouldBe 1
          permissions[0].stringValue shouldBe "read"
        }
      }
    }
  }

  @Test
  fun `test error scenarios and edge cases`() {
    // Test with missing optional fields
    val minimalJson = """
            {
                "success": true,
                "data": {
                    "id": 999,
                    "username": "minimal",
                    "email": "minimal@example.com",
                    "profile": {
                        "firstName": "Min",
                        "lastName": "Mal"
                    }
                }
            }
        """.trimIndent()

    val jsonElement = minimalJson.toJsonElement()

    // Required fields should be present
    jsonElement.booleanValue("success") shouldBe true
    jsonElement.intValue("data.id") shouldBe 999
    jsonElement.stringValue("data.username") shouldBe "minimal"

    // Optional fields should be null or missing
    jsonElement.stringValueOrNull("data.profile.bio") shouldBe null
    jsonElement.stringValueOrNull("data.profile.avatar") shouldBe null

    // Arrays might be missing, should handle gracefully
    jsonElement.jsonElementListOrNull("data.permissions") shouldBe null

    // Nested objects might be missing
    jsonElement.jsonObjectValueOrNull("data.settings") shouldBe null
  }

  @Test
  fun `test format round-trip consistency`() {
    val originalData = successResponse

    // Test different format round trips
    val prettyJson = originalData.toJsonString(prettyPrint = true)
    val compactJson = originalData.toJsonString(prettyPrint = false)

    val prettyParsed = prettyJson.toJsonElement()
    val compactParsed = compactJson.toJsonElement()

    // Data should be identical regardless of formatting
    compactParsed.booleanValue("success") shouldBe prettyParsed.booleanValue("success")
    compactParsed.stringValue("data.username") shouldBe prettyParsed.stringValue("data.username")
    compactParsed.stringValue("data.profile.firstName") shouldBe prettyParsed.stringValue("data.profile.firstName")
    compactParsed.intValue("data.id") shouldBe prettyParsed.intValue("data.id")

    // Test array consistency
    val prettyPermissions = prettyParsed.jsonElementList("data.permissions")
    val compactPermissions = compactParsed.jsonElementList("data.permissions")
    compactPermissions.size shouldBe prettyPermissions.size
    prettyPermissions.indices.forEach { i ->
      compactPermissions[i].stringValue shouldBe prettyPermissions[i].stringValue
    }
  }

  @Test
  fun `test performance with complex nested structures`() {
    // Create a more complex structure for performance testing
    val complexStructure = buildJsonObject {
      put(
        "meta",
        buildJsonObject {
          put("version", "2.0")
          put("generated", System.currentTimeMillis())
          put(
            "config",
            buildJsonObject {
              repeat(20) { i ->
                put("setting$i", "value$i")
              }
            },
          )
        },
      )
      put(
        "users",
        buildJsonArray {
          repeat(50) { i ->
            add(
              buildJsonObject {
                put("id", i)
                put("name", "User $i")
                put(
                  "data",
                  buildJsonObject {
                    put("score", i * 10.5)
                    put("active", i % 2 == 0)
                    put(
                      "tags",
                      buildJsonArray {
                        repeat(5) { j ->
                          add("tag$j")
                        }
                      },
                    )
                  },
                )
              },
            )
          }
        },
      )
    }

    // Measure basic operations
    val startTime = System.currentTimeMillis()

    // Test various operations
    complexStructure.stringValue("meta.version") shouldBe "2.0"
    complexStructure.stringValue("meta.config.setting10") shouldBe "value10"

    val users = complexStructure.jsonElementList("users")
    users.size shouldBe 50

    // Test accessing nested data in array
    users[25].stringValue("name") shouldBe "User 25"
    users[25].doubleValue("data.score") shouldBe 262.5
    users[25].booleanValue("data.active") shouldBe false // 25 % 2 != 0

    val tags = users[10].jsonElementList("data.tags")
    tags.size shouldBe 5
    tags[2].stringValue shouldBe "tag2"

    val endTime = System.currentTimeMillis()

    // Should complete reasonably quickly (less than 1 second for this test)
    (endTime - startTime < 1000) shouldBe true
  }
}
