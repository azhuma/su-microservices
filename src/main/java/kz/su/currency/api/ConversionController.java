package kz.su.currency.api;

import jakarta.validation.Valid;
import kz.su.currency.service.ConversionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conversions")
public class ConversionController {
    private final ConversionService service;

    public ConversionController(ConversionService service) {
        this.service = service;
    }

    @PostMapping
    public ConversionResponse convert(@Valid @RequestBody ConversionRequest request) {
        return service.convert(request);
    }
}
