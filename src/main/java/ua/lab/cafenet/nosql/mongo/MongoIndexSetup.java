package ua.lab.cafenet.nosql.mongo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MongoIndexSetup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MongoIndexSetup.class);

    private final MongoReviewRepository reviews;

    public MongoIndexSetup(MongoReviewRepository reviews) {
        this.reviews = reviews;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            reviews.ensureIndexes();
            log.info("Індекси колекції відгуків на місці, документів: {}", reviews.count());
        } catch (RuntimeException e) {
            log.warn("MongoDB недоступна на старті: {}", e.getMessage());
        }
    }
}
