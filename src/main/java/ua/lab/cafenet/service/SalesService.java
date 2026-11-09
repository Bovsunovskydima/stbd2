package ua.lab.cafenet.service;

import org.springframework.stereotype.Service;
import ua.lab.cafenet.api.Forms;
import ua.lab.cafenet.domain.Rows;
import ua.lab.cafenet.persistence.NotFoundException;
import ua.lab.cafenet.persistence.UnitOfWorkFactory;
import ua.lab.cafenet.persistence.repository.SalesRepository;

@Service
public class SalesService {

    private final UnitOfWorkFactory factory;

    public SalesService(UnitOfWorkFactory factory) {
        this.factory = factory;
    }

    public Rows.OrderCard serve(Forms.OrderForm form) {
        return factory.inTransaction(work -> {
            SalesRepository sales = work.sales();

            long orderId = sales.open(form.branchId(), form.cashierId(), form.clientId(), form.channelOrDefault());
            for (Forms.OrderLineForm line : form.lines()) {
                sales.addLine(orderId, line.menuItemId(), line.quantity());
            }
            sales.close(orderId);

            Rows.Order order = sales.find(orderId)
                    .orElseThrow(() -> new NotFoundException("Замовлення " + orderId + " не знайдено"));
            return new Rows.OrderCard(order, sales.lines(orderId));
        });
    }
}
