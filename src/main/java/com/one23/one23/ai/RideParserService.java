
package com.one23.one23.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.one23.one23.dto.RideParseRequest;
import com.one23.one23.dto.RideParseResponse;
import org.springframework.stereotype.Service;

@Service
public class RideParserService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public RideParserService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
        this.objectMapper = new ObjectMapper();
    }

    public RideParseResponse parseRideRequest(
            RideParseRequest request) {

        // Validate the user's input
        if (request == null
                || request.getMessage() == null
                || request.getMessage().isBlank()) {
            throw new IllegalArgumentException(
                    "Ride request message cannot be empty");
        }

        // Tell Gemini what information to extract
        String prompt = """
                Extract the pickup hub, destination, and requested time
                from the user's ride request.

                Return only JSON with these fields:
                pickupHub, destination, time.

                If a field is not provided, return null for that field.
                Do not guess missing information.

                Example:
                {
                  "pickupHub": "Kadugodi",
                  "destination": "Divyasree Technopark",
                  "time": "09:00"
                }
                """;

        // Ask Gemini to parse the user's sentence
        String result = geminiClient.generate(
                prompt,
                request.getMessage(),
                true
        );

        try {
            // Convert Gemini's JSON into a Java object
            RideParseResponse response =
                    objectMapper.readValue(
                            result, RideParseResponse.class);

            // Ensure required fields are present
            if (response.getPickupHub() == null
                    || response.getPickupHub().isBlank()
                    || response.getDestination() == null
                    || response.getDestination().isBlank()) {
                throw new IllegalArgumentException(
                        "Pickup hub and destination are required");
            }

            return response;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new AiUnavailableException(
                    "Could not process Gemini response", e);
        }
    }
}
