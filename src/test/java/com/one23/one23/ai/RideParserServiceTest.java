package com.one23.one23.ai;

import com.one23.one23.dto.RideParseRequest;
import com.one23.one23.dto.RideParseResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// GeminiClient is mocked, so these tests never call the real Gemini API.
@ExtendWith(MockitoExtension.class)
class RideParserServiceTest {

    @Mock
    private GeminiClient geminiClient;

    @InjectMocks
    private RideParserService rideParserService;

    private RideParseRequest request(String message) {
        RideParseRequest request = new RideParseRequest();
        request.setMessage(message);
        return request;
    }

    // ---------- Input validation (Gemini must not be called) ----------

    @Test
    void nullRequest_isRejected() {
        assertThatThrownBy(() -> rideParserService.parseRideRequest(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(geminiClient);
    }

    @Test
    void nullMessage_isRejected() {
        assertThatThrownBy(() -> rideParserService.parseRideRequest(request(null)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(geminiClient);
    }

    @Test
    void blankMessage_isRejected() {
        assertThatThrownBy(() -> rideParserService.parseRideRequest(request("   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ride request message cannot be empty");
        verifyNoInteractions(geminiClient);
    }

    // ---------- Missing required fields ----------

    @Test
    void missingPickupHub_isRejected() {
        when(geminiClient.generate(anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"pickupHub\": null, \"destination\": \"Divyasree Technopark\", \"time\": \"09:00\"}");

        assertThatThrownBy(() -> rideParserService.parseRideRequest(request("go to Divyasree at 9")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pickup hub and destination are required");
    }

    @Test
    void missingDestination_isRejected() {
        when(geminiClient.generate(anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"pickupHub\": \"Kadugodi\", \"destination\": null, \"time\": \"09:00\"}");

        assertThatThrownBy(() -> rideParserService.parseRideRequest(request("from Kadugodi at 9")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pickup hub and destination are required");
    }

    @Test
    void blankPickupHub_isRejected() {
        when(geminiClient.generate(anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"pickupHub\": \"  \", \"destination\": \"Divyasree Technopark\", \"time\": null}");

        assertThatThrownBy(() -> rideParserService.parseRideRequest(request("somewhere to Divyasree")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Valid Gemini JSON ----------

    @Test
    void validJson_isParsedCorrectly() {
        when(geminiClient.generate(anyString(), eq("Kadugodi to Divyasree at 9am"), eq(true)))
                .thenReturn("{\"pickupHub\": \"Kadugodi\", \"destination\": \"Divyasree Technopark\", \"time\": \"09:00\"}");

        RideParseResponse response =
                rideParserService.parseRideRequest(request("Kadugodi to Divyasree at 9am"));

        assertThat(response.getPickupHub()).isEqualTo("Kadugodi");
        assertThat(response.getDestination()).isEqualTo("Divyasree Technopark");
        assertThat(response.getTime()).isEqualTo("09:00");
    }

    @Test
    void validJson_withoutTime_isAccepted() {
        when(geminiClient.generate(anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"pickupHub\": \"Kadugodi\", \"destination\": \"Divyasree Technopark\", \"time\": null}");

        RideParseResponse response =
                rideParserService.parseRideRequest(request("Kadugodi to Divyasree"));

        assertThat(response.getTime()).isNull();
        assertThat(response.getPickupHub()).isEqualTo("Kadugodi");
    }

    // ---------- Gemini failures ----------

    @Test
    void geminiClientFailure_isPassedOn() {
        when(geminiClient.generate(anyString(), anyString(), anyBoolean()))
                .thenThrow(new AiUnavailableException("Gemini chat call failed"));

        assertThatThrownBy(() -> rideParserService.parseRideRequest(request("Kadugodi to Divyasree")))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage("Gemini chat call failed");
    }

    @Test
    void invalidJsonFromGemini_becomesAiUnavailable() {
        when(geminiClient.generate(anyString(), anyString(), anyBoolean()))
                .thenReturn("this is not json");

        assertThatThrownBy(() -> rideParserService.parseRideRequest(request("Kadugodi to Divyasree")))
                .isInstanceOf(AiUnavailableException.class)
                .hasMessage("Could not process Gemini response");
    }

    @Test
    void emptyReplyFromGemini_becomesAiUnavailable() {
        when(geminiClient.generate(anyString(), anyString(), anyBoolean()))
                .thenReturn("");

        assertThatThrownBy(() -> rideParserService.parseRideRequest(request("Kadugodi to Divyasree")))
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void geminiIsCalledOnceInJsonMode() {
        when(geminiClient.generate(anyString(), anyString(), anyBoolean()))
                .thenReturn("{\"pickupHub\": \"A\", \"destination\": \"B\", \"time\": null}");

        rideParserService.parseRideRequest(request("A to B"));

        verify(geminiClient).generate(anyString(), eq("A to B"), eq(true));
        verify(geminiClient, never()).generate(anyString(), anyString(), eq(false));
    }
}
