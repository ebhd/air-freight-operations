package be.kdg.airfreight.partner.api;

import be.kdg.airfreight.partner.application.PartnerMessageService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/partner-messages")
public class PartnerMessageController {
    private static final int MAX_LIMIT = 500;

    private final PartnerMessageService partnerMessageService;
    private final JsonMapper jsonMapper;

    public PartnerMessageController(
            PartnerMessageService partnerMessageService, JsonMapper jsonMapper) {
        this.partnerMessageService = partnerMessageService;
        this.jsonMapper = jsonMapper;
    }

    // newest first like /api/partner-messages?limit=100
    @GetMapping
    public List<PartnerMessageResponse> findLatest(@RequestParam(defaultValue = "100") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT);
        }
        return partnerMessageService.findLatest(limit).stream()
                .map(message -> PartnerMessageResponse.from(message, jsonMapper))
                .toList();
    }
}
