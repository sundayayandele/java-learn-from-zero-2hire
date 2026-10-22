package ng.itlearning.fxrates.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import ng.itlearning.fxrates.rates.RateRepository;
import ng.itlearning.fxrates.rates.RateRepository.CurrencyCoverage;
import ng.itlearning.fxrates.rates.RateService;
import ng.itlearning.fxrates.rates.RateService.Conversion;
import ng.itlearning.fxrates.rates.RateService.HistoryPoint;
import ng.itlearning.fxrates.rates.RateService.RateQuote;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RatesController {

    private final RateService service;
    private final RateRepository repository;

    public RatesController(RateService service, RateRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @GetMapping("/rates/{base}/{quote}")
    public RateQuote rate(@PathVariable String base, @PathVariable String quote,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.rate(base, quote, date);
    }

    @GetMapping("/rates/{base}/{quote}/history")
    public List<HistoryPoint> history(@PathVariable String base, @PathVariable String quote,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.history(base, quote, from, to);
    }

    @GetMapping("/conversions")
    public Conversion convert(@RequestParam String from, @RequestParam String to,
                              @RequestParam @DecimalMin("0.00") @Digits(integer = 15, fraction = 4) BigDecimal amount,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.convert(from, to, amount, date);
    }

    @GetMapping("/currencies")
    public List<CurrencyCoverage> currencies() {
        return repository.coverage();
    }
}
