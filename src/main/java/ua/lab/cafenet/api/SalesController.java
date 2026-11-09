package ua.lab.cafenet.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.lab.cafenet.domain.Rows;
import ua.lab.cafenet.service.SalesService;

@RestController
@RequestMapping("/api/orders")
public class SalesController {

    private final SalesService service;

    public SalesController(SalesService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Rows.OrderCard serve(@Valid @RequestBody Forms.OrderForm form) {
        return service.serve(form);
    }
}
