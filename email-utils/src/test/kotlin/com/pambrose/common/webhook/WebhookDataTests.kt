@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package com.pambrose.common.webhook

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class WebhookDataTests : StringSpec() {
  init {
    "bounce construction with required fields" {
      val bounce = Bounce(message = "550 User not found")
      bounce.message shouldBe "550 User not found"
    }

    "bounce JSON round-trip" {
      val bounce = Bounce(message = "550 User not found")
      val json = Json.encodeToString(bounce)
      val decoded = Json.decodeFromString<Bounce>(json)
      decoded shouldBe bounce
    }

    "click construction with required fields" {
      val click = Click(
        ipAddress = "192.168.1.1",
        link = "https://example.com",
        timestamp = "2026-01-01T00:00:00Z",
        userAgent = "Mozilla/5.0",
      )
      click.ipAddress shouldBe "192.168.1.1"
      click.link shouldBe "https://example.com"
      click.timestamp shouldBe "2026-01-01T00:00:00Z"
      click.userAgent shouldBe "Mozilla/5.0"
      click.linkTags shouldBe null
    }

    "click with optional linkTags" {
      val click = Click(
        ipAddress = "10.0.0.1",
        link = "https://example.com/page",
        linkTags = "promo",
        timestamp = "2026-03-15T12:00:00Z",
        userAgent = "Chrome/120",
      )
      click.linkTags shouldBe "promo"
    }

    "click JSON round-trip" {
      val click = Click(
        ipAddress = "192.168.1.1",
        link = "https://example.com",
        timestamp = "2026-01-01T00:00:00Z",
        userAgent = "Mozilla/5.0",
      )
      val json = Json.encodeToString(click)
      val decoded = Json.decodeFromString<Click>(json)
      decoded shouldBe click
    }

    "header construction" {
      val header = Header(name = "X-Custom", value = "test-value")
      header.name shouldBe "X-Custom"
      header.value shouldBe "test-value"
    }

    "header JSON round-trip" {
      val header = Header(name = "Content-Type", value = "text/html")
      val json = Json.encodeToString(header)
      val decoded = Json.decodeFromString<Header>(json)
      decoded shouldBe header
    }

    "data construction with required fields only" {
      val data = Data(
        createdAt = "2026-01-01T00:00:00Z",
        emailId = "email-123",
        from = "sender@example.com",
      )
      data.createdAt shouldBe "2026-01-01T00:00:00Z"
      data.emailId shouldBe "email-123"
      data.from shouldBe "sender@example.com"
      data.subject shouldBe null
      data.to shouldBe null
      data.headers shouldBe null
      data.bounce shouldBe null
      data.click shouldBe null
    }

    "data construction with all fields" {
      val headers = listOf(
        Header(name = "X-Tag", value = "important"),
        Header(name = "X-Priority", value = "high"),
      )
      val bounce = Bounce(message = "mailbox full")
      val click = Click(
        ipAddress = "10.0.0.1",
        link = "https://example.com",
        timestamp = "2026-01-01T00:00:00Z",
        userAgent = "Chrome/120",
      )
      val data = Data(
        createdAt = "2026-01-01T00:00:00Z",
        emailId = "email-456",
        from = "sender@example.com",
        subject = "Test Subject",
        to = listOf("recipient1@example.com", "recipient2@example.com"),
        headers = headers,
        bounce = bounce,
        click = click,
      )
      data.subject shouldBe "Test Subject"
      data.to shouldBe listOf("recipient1@example.com", "recipient2@example.com")
      data.headers shouldBe headers
      data.bounce shouldBe bounce
      data.click shouldBe click
    }

    "data JSON round-trip" {
      val data = Data(
        createdAt = "2026-01-01T00:00:00Z",
        emailId = "email-789",
        from = "sender@example.com",
        subject = "Hello",
        to = listOf("user@test.com"),
        headers = listOf(Header(name = "X-Test", value = "abc")),
      )
      val json = Json.encodeToString(data)
      val decoded = Json.decodeFromString<Data>(json)
      decoded shouldBe data
    }

    "resend webhook msg construction" {
      val data = Data(
        createdAt = "2026-01-01T00:00:00Z",
        emailId = "email-100",
        from = "noreply@example.com",
      )
      val msg = ResendWebhookMsg(
        createdAt = "2026-01-01T00:00:00Z",
        data = data,
        type = "email.delivered",
      )
      msg.createdAt shouldBe "2026-01-01T00:00:00Z"
      msg.data shouldBe data
      msg.type shouldBe "email.delivered"
    }

    "resend webhook msg JSON round-trip" {
      val data = Data(
        createdAt = "2026-01-01T00:00:00Z",
        emailId = "email-200",
        from = "sender@test.com",
        subject = "Welcome",
        to = listOf("new-user@test.com"),
      )
      val msg = ResendWebhookMsg(
        createdAt = "2026-01-01T00:00:00Z",
        data = data,
        type = "email.sent",
      )
      val json = Json.encodeToString(msg)
      val decoded = Json.decodeFromString<ResendWebhookMsg>(json)
      decoded shouldBe msg
    }

    "list fields serialize correctly" {
      val data = Data(
        createdAt = "2026-02-01T00:00:00Z",
        emailId = "email-300",
        from = "admin@example.com",
        to = listOf("a@test.com", "b@test.com", "c@test.com"),
        headers = listOf(
          Header(name = "X-First", value = "1"),
          Header(name = "X-Second", value = "2"),
        ),
      )
      val json = Json.encodeToString(data)
      val decoded = Json.decodeFromString<Data>(json)
      decoded.to?.size shouldBe 3
      decoded.headers?.size shouldBe 2
      decoded shouldBe data
    }

    "click JSON round-trip with linkTags present" {
      val click = Click(
        ipAddress = "172.16.0.9",
        link = "https://example.com/sale",
        linkTags = "campaign-42",
        timestamp = "2026-04-01T08:30:00Z",
        userAgent = "Safari/17.0",
      )
      val json = Json.encodeToString(click)
      json shouldContain "\"linkTags\":\"campaign-42\""
      val decoded = Json.decodeFromString<Click>(json)
      decoded shouldBe click
      decoded.linkTags shouldBe "campaign-42"
    }

    "click decodes from raw JSON with all fields" {
      val json =
        """
        {
          "ipAddress": "1.2.3.4",
          "link": "https://example.com/promo",
          "linkTags": "tag-1",
          "timestamp": "2026-05-01T00:00:00Z",
          "userAgent": "curl/8.5"
        }
        """.trimIndent()
      val click = Json.decodeFromString<Click>(json)
      click.ipAddress shouldBe "1.2.3.4"
      click.link shouldBe "https://example.com/promo"
      click.linkTags shouldBe "tag-1"
      click.timestamp shouldBe "2026-05-01T00:00:00Z"
      click.userAgent shouldBe "curl/8.5"
    }

    "click decoding fails when required fields are missing" {
      shouldThrow<SerializationException> {
        Json.decodeFromString<Click>("""{"link":"https://example.com"}""")
      }
    }

    "data JSON round-trip with bounce and click present" {
      val data = Data(
        createdAt = "2026-06-01T00:00:00Z",
        emailId = "email-400",
        from = "sender@example.com",
        subject = "Bounced and clicked",
        to = listOf("user@test.com"),
        headers = listOf(Header(name = "X-Env", value = "prod")),
        bounce = Bounce(message = "mailbox unavailable"),
        click = Click(
          ipAddress = "10.1.1.1",
          link = "https://example.com/cta",
          linkTags = "cta",
          timestamp = "2026-06-01T00:00:01Z",
          userAgent = "Firefox/126",
        ),
      )
      val json = Json.encodeToString(data)
      val decoded = Json.decodeFromString<Data>(json)
      decoded shouldBe data
      decoded.bounce?.message shouldBe "mailbox unavailable"
      decoded.click?.link shouldBe "https://example.com/cta"
    }

    "data decodes from raw JSON with only required fields" {
      val json = """{"created_at":"2026-01-01T00:00:00Z","email_id":"email-500","from":"a@b.com"}"""
      val data = Json.decodeFromString<Data>(json)
      data.createdAt shouldBe "2026-01-01T00:00:00Z"
      data.emailId shouldBe "email-500"
      data.from shouldBe "a@b.com"
      data.subject shouldBe null
      data.to shouldBe null
      data.headers shouldBe null
      data.bounce shouldBe null
      data.click shouldBe null
    }

    "data decoding fails when a required field is missing" {
      shouldThrow<SerializationException> {
        Json.decodeFromString<Data>("""{"email_id":"email-600","from":"a@b.com"}""")
      }
    }

    "data encodes with snake_case serial names" {
      val data = Data(
        createdAt = "2026-01-01T00:00:00Z",
        emailId = "email-700",
        from = "sender@example.com",
      )
      val json = Json.encodeToString(data)
      json shouldContain "\"created_at\""
      json shouldContain "\"email_id\""
      json shouldContain "\"from\""
    }
  }
}
