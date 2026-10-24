package com.starter.webhook

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

/** Reviewed receivers run only after the endpoint's HMAC/bearer authentication and rate limit. */
interface IncomingWebhookExtension {fun receive(endpointId:UUID,deliveryId:String,payload:JsonNode)}
