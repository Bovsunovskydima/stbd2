package ua.lab.cafenet.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.lab.cafenet.nosql.bench.BenchmarkReport;
import ua.lab.cafenet.nosql.bench.BenchmarkService;

@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private final BenchmarkService service;

    public BenchmarkController(BenchmarkService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public BenchmarkReport run(@RequestParam(required = false) Integer reviews,
                               @RequestParam(required = false) Integer iterations,
                               @RequestParam(required = false) Integer warmup) {
        return service.run(reviews, iterations, warmup);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        service.reset();
    }
}
