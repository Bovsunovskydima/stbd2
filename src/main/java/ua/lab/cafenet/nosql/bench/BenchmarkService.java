package ua.lab.cafenet.nosql.bench;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ua.lab.cafenet.domain.Rows;
import ua.lab.cafenet.nosql.mongo.MongoReviewRepository;
import ua.lab.cafenet.persistence.UnitOfWork;
import ua.lab.cafenet.persistence.UnitOfWorkFactory;
import ua.lab.cafenet.persistence.repository.ReviewRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class BenchmarkService {

    private static final LocalDateTime PERIOD_FROM = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime PERIOD_TO = PERIOD_FROM.plusDays(181);
    private static final long DATA_SEED = 20_260_207L;
    private static final int TAG_SEARCH_LIMIT = 50;
    private static final int LOAD_COMMIT_CHUNK = 500;
    private static final int MONGO_INSERT_CHUNK = 1000;

    private final UnitOfWorkFactory factory;
    private final MongoReviewRepository mongo;
    private final ReviewGenerator generator;
    private final int defaultReviews;
    private final int defaultIterations;
    private final int defaultWarmup;

    public BenchmarkService(UnitOfWorkFactory factory,
                            MongoReviewRepository mongo,
                            ReviewGenerator generator,
                            @Value("${cafenet.benchmark.reviews}") int defaultReviews,
                            @Value("${cafenet.benchmark.iterations}") int defaultIterations,
                            @Value("${cafenet.benchmark.warmup}") int defaultWarmup) {
        this.factory = factory;
        this.mongo = mongo;
        this.generator = generator;
        this.defaultReviews = defaultReviews;
        this.defaultIterations = defaultIterations;
        this.defaultWarmup = defaultWarmup;
    }

    public BenchmarkReport run(Integer reviewsParam, Integer iterationsParam, Integer warmupParam) {
        int reviews = reviewsParam == null ? defaultReviews : reviewsParam;
        int iterations = iterationsParam == null ? defaultIterations : iterationsParam;
        int warmup = warmupParam == null ? defaultWarmup : warmupParam;

        LocalDateTime startedAt = LocalDateTime.now();
        long preparationStart = System.nanoTime();

        List<Rows.Review> dataset = generator.generate(reviews, DATA_SEED, 1);
        List<Rows.Review> fresh = generator.generate(warmup + iterations, DATA_SEED + 1, reviews + 1);

        reset();
        loadSql(dataset);
        loadMongo(dataset);

        long preparationMs = (System.nanoTime() - preparationStart) / 1_000_000;

        String[] keys = probeKeys(reviews, warmup + iterations);

        List<ScenarioComparison> scenarios = new ArrayList<>();
        scenarios.add(readReview(warmup, iterations, keys));
        scenarios.add(branchFeed(warmup, iterations));
        scenarios.add(branchRatings(warmup, iterations));
        scenarios.add(tagSearch(warmup, iterations));
        scenarios.add(aspectStats(warmup, iterations));
        scenarios.add(replyReview(warmup, iterations, keys));
        scenarios.add(writeReview(warmup, iterations, fresh));

        return new BenchmarkReport(startedAt, reviews, iterations, warmup, preparationMs, scenarios);
    }

    public void reset() {
        factory.run(work -> work.reviews().clear());
        mongo.clear();
        mongo.ensureIndexes();
    }

    public long loadSql(List<Rows.Review> reviews) {
        try (UnitOfWork work = factory.open()) {
            ReviewRepository repository = work.reviews();
            int pending = 0;
            for (Rows.Review review : reviews) {
                repository.add(review);
                if (++pending == LOAD_COMMIT_CHUNK) {
                    work.commit();
                    pending = 0;
                }
            }
            work.commit();
            repository.analyze();
            work.commit();
            return repository.count();
        }
    }

    public long loadMongo(List<Rows.Review> reviews) {
        for (int from = 0; from < reviews.size(); from += MONGO_INSERT_CHUNK) {
            int to = Math.min(from + MONGO_INSERT_CHUNK, reviews.size());
            mongo.addMany(reviews.subList(from, to));
        }
        return mongo.count();
    }

    private ScenarioComparison readReview(int warmup, int iterations, String[] keys) {
        Counter sqlKey = new Counter();
        Counter mongoKey = new Counter();

        Measurement postgres = measureSql(warmup, iterations, repository ->
                repository.findByNo(keys[sqlKey.next(keys.length)])
                        .map(review -> (long) review.items().size())
                        .orElse(0L));

        Measurement mongodb = measure(warmup, iterations, () ->
                mongo.findByNo(keys[mongoKey.next(keys.length)])
                        .map(review -> (long) review.items().size())
                        .orElse(0L));

        return compare("read-review",
                "Повний відгук за номером: шапка, аспекти, позиції, мітки, фото, відповідь",
                "SELECT з v_reviews_full — reviews ⋈ review_items плюс три підзапити з array_agg, складання відгуку в коді",
                "find({reviewNo}) — документ повертається цілком",
                postgres, mongodb);
    }

    private ScenarioComparison branchFeed(int warmup, int iterations) {
        Measurement postgres = measureSql(warmup, iterations, repository ->
                repository.branchFeed(ReviewGenerator.PROBE_BRANCH_ID, PERIOD_FROM, PERIOD_TO).size());

        Measurement mongodb = measure(warmup, iterations, () ->
                mongo.branchFeed(ReviewGenerator.PROBE_BRANCH_ID, PERIOD_FROM, PERIOD_TO).size());

        return compare("branch-feed",
                "Усі відгуки однієї філії за півроку разом із вкладеними даними",
                "індекс (branch_id, created_at DESC), з'єднання з позиціями і три підзапити на кожен відгук",
                "індекс {branch.id, createdAt} — документи повертаються готовими",
                postgres, mongodb);
    }

    private ScenarioComparison branchRatings(int warmup, int iterations) {
        Measurement postgres = measureSql(warmup, iterations, repository ->
                repository.branchRatings(PERIOD_FROM, PERIOD_TO).size());

        Measurement mongodb = measure(warmup, iterations, () ->
                mongo.branchRatings(PERIOD_FROM, PERIOD_TO).size());

        return compare("branch-ratings",
                "Середня оцінка та кількість відгуків по філіях за період",
                "fn_branch_ratings — GROUP BY branch_id по таблиці reviews",
                "aggregate: $match → $group по branch.id",
                postgres, mongodb);
    }

    private ScenarioComparison tagSearch(int warmup, int iterations) {
        Measurement postgres = measureSql(warmup, iterations, repository ->
                repository.byTag(ReviewGenerator.PROBE_TAG, TAG_SEARCH_LIMIT).size());

        Measurement mongodb = measure(warmup, iterations, () ->
                mongo.byTag(ReviewGenerator.PROBE_TAG, TAG_SEARCH_LIMIT).size());

        return compare("tag-search",
                "Останні 50 відгуків із міткою «" + ReviewGenerator.PROBE_TAG + "»",
                "fn_reviews_by_tag — з'єднання review_tags з reviews",
                "find({tags: ...}) по багатоключовому індексу",
                postgres, mongodb);
    }

    private ScenarioComparison aspectStats(int warmup, int iterations) {
        Measurement postgres = measureSql(warmup, iterations, repository ->
                repository.aspectStats(PERIOD_FROM, PERIOD_TO).size());

        Measurement mongodb = measure(warmup, iterations, () ->
                mongo.aspectStats(PERIOD_FROM, PERIOD_TO).size());

        return compare("aspect-stats",
                "Середня оцінка за кожним аспектом: кава, сервіс, швидкість, атмосфера, чистота",
                "fn_aspect_stats — з'єднання review_aspects з reviews і GROUP BY name",
                "aggregate: $match → $unwind aspects → $group",
                postgres, mongodb);
    }

    private ScenarioComparison replyReview(int warmup, int iterations, String[] keys) {
        Counter sqlKey = new Counter();
        Counter mongoKey = new Counter();

        Measurement postgres;
        try (UnitOfWork work = factory.open()) {
            ReviewRepository repository = work.reviews();
            postgres = measure(warmup, iterations, () -> {
                int index = sqlKey.next(keys.length);
                repository.reply(keys[index], 2L, "Андрій Шевчук", "Дякуємо, врахуємо.");
                work.commit();
                return 1L;
            });
        }

        Measurement mongodb = measure(warmup, iterations, () -> {
            int index = mongoKey.next(keys.length);
            mongo.reply(keys[index], 2L, "Андрій Шевчук", "Дякуємо, врахуємо.");
            return 1L;
        });

        return compare("reply-review",
                "Менеджер відповідає на відгук",
                "CALL sp_review_reply(...) — UPDATE одного рядка за унікальним індексом",
                "updateOne({reviewNo}, $set) — зміна вкладеного об'єкта всередині документа",
                postgres, mongodb);
    }

    private ScenarioComparison writeReview(int warmup, int iterations, List<Rows.Review> fresh) {
        Counter sqlIndex = new Counter();
        Counter mongoIndex = new Counter();

        Measurement postgres;
        try (UnitOfWork work = factory.open()) {
            ReviewRepository repository = work.reviews();
            postgres = measure(warmup, iterations, () -> {
                Rows.Review review = fresh.get(sqlIndex.next(fresh.size()));
                repository.add(review);
                work.commit();
                return (long) review.items().size();
            });
        }

        Measurement mongodb = measure(warmup, iterations, () -> {
            Rows.Review review = fresh.get(mongoIndex.next(fresh.size()));
            mongo.add(review);
            return (long) review.items().size();
        });

        return compare("write-review",
                "Запис нового відгуку з аспектами, позиціями, мітками та фото",
                "CALL sp_review_add(...) — вставка у 5 таблиць однією транзакцією, одне звернення",
                "insertOne(document) — один документ, одне звернення",
                postgres, mongodb);
    }

    private Measurement measureSql(int warmup, int iterations, SqlOperation operation) {
        try (UnitOfWork work = factory.open()) {
            ReviewRepository repository = work.reviews();
            Measurement measurement = measure(warmup, iterations, () -> operation.execute(repository));
            work.commit();
            return measurement;
        }
    }

    private Measurement measure(int warmup, int iterations, Operation operation) {
        for (int i = 0; i < warmup; i++) {
            operation.execute();
        }
        long[] nanos = new long[iterations];
        long elements = 0;
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            elements = operation.execute();
            nanos[i] = System.nanoTime() - start;
        }
        return Measurement.of(nanos, elements);
    }

    private static ScenarioComparison compare(String scenario, String description, String sqlPlan,
                                              String mongoPlan, Measurement postgres, Measurement mongodb) {
        double speedup = mongodb.avgMs() == 0 ? 0 : postgres.avgMs() / mongodb.avgMs();
        speedup = Math.round(speedup * 100.0) / 100.0;
        String faster = postgres.avgMs() <= mongodb.avgMs() ? "postgres" : "mongodb";
        return new ScenarioComparison(scenario, description, sqlPlan, mongoPlan, postgres, mongodb, speedup, faster);
    }

    private static String[] probeKeys(int reviews, int count) {
        Random random = new Random(DATA_SEED);
        String[] keys = new String[count];
        for (int i = 0; i < count; i++) {
            keys[i] = ReviewGenerator.NUMBER_FORMAT.formatted(1 + random.nextInt(reviews));
        }
        return keys;
    }

    @FunctionalInterface
    private interface Operation {

        long execute();
    }

    @FunctionalInterface
    private interface SqlOperation {

        long execute(ReviewRepository repository);
    }

    private static final class Counter {

        private int value;

        private int next(int bound) {
            int current = value;
            value = (value + 1) % bound;
            return current;
        }
    }
}
