package ua.lab.cafenet.nosql.mongo;

import com.mongodb.WriteConcern;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;
import ua.lab.cafenet.domain.Rows;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class MongoReviewRepository {

    private final MongoTemplate mongo;
    private final String collectionName;

    public MongoReviewRepository(MongoTemplate mongo,
                                 @Value("${cafenet.reviews.collection}") String collectionName) {
        this.mongo = mongo;
        this.collectionName = collectionName;
    }

    public void ensureIndexes() {
        MongoCollection<Document> reviews = collection();
        reviews.createIndex(Indexes.ascending("reviewNo"), new IndexOptions().unique(true));
        reviews.createIndex(Indexes.compoundIndex(Indexes.ascending("branch.id"), Indexes.descending("createdAt")));
        reviews.createIndex(Indexes.descending("createdAt"));
        reviews.createIndex(Indexes.ascending("rating"));
        reviews.createIndex(Indexes.ascending("tags"));
        reviews.createIndex(Indexes.ascending("items.menuItemId"));
        reviews.createIndex(Indexes.ascending("aspects.name"));
    }

    public String add(Rows.Review review) {
        Document document = ReviewDocuments.toDocument(review);
        durable().insertOne(document);
        return document.getObjectId("_id").toHexString();
    }

    public void addMany(List<Rows.Review> reviews) {
        List<Document> documents = new ArrayList<>(reviews.size());
        for (Rows.Review review : reviews) {
            documents.add(ReviewDocuments.toDocument(review));
        }
        durable().insertMany(documents);
    }

    public Optional<Rows.Review> findByNo(String reviewNo) {
        Document document = collection().find(Filters.eq("reviewNo", reviewNo)).first();
        return Optional.ofNullable(document).map(ReviewDocuments::toReview);
    }

    public List<Rows.Review> branchFeed(int branchId, LocalDateTime from, LocalDateTime to) {
        Bson filter = Filters.and(
                Filters.eq("branch.id", branchId),
                Filters.gte("createdAt", date(from)),
                Filters.lt("createdAt", date(to)));

        List<Rows.Review> reviews = new ArrayList<>();
        for (Document document : collection().find(filter).sort(Sorts.descending("createdAt"))) {
            reviews.add(ReviewDocuments.toReview(document));
        }
        return reviews;
    }

    public List<Rows.BranchRating> branchRatings(LocalDateTime from, LocalDateTime to) {
        List<Bson> pipeline = List.of(
                Aggregates.match(period(from, to)),
                Aggregates.group("$branch.id",
                        Accumulators.first("branch", "$branch.name"),
                        Accumulators.sum("reviews", 1),
                        Accumulators.avg("avgRating", "$rating")),
                Aggregates.sort(Sorts.orderBy(Sorts.descending("avgRating"), Sorts.descending("reviews"))));

        List<Rows.BranchRating> rows = new ArrayList<>();
        for (Document document : collection().aggregate(pipeline)) {
            rows.add(new Rows.BranchRating(
                    document.get("_id", Number.class).intValue(),
                    document.getString("branch"),
                    document.get("reviews", Number.class).longValue(),
                    round(document.get("avgRating"))));
        }
        return rows;
    }

    public List<Rows.AspectStat> aspectStats(LocalDateTime from, LocalDateTime to) {
        List<Bson> pipeline = List.of(
                Aggregates.match(period(from, to)),
                Aggregates.unwind("$aspects"),
                Aggregates.group("$aspects.name",
                        Accumulators.sum("answers", 1),
                        Accumulators.avg("avgScore", "$aspects.score")),
                Aggregates.sort(Sorts.descending("avgScore")));

        List<Rows.AspectStat> rows = new ArrayList<>();
        for (Document document : collection().aggregate(pipeline)) {
            rows.add(new Rows.AspectStat(
                    document.getString("_id"),
                    document.get("answers", Number.class).longValue(),
                    round(document.get("avgScore"))));
        }
        return rows;
    }

    public List<Rows.ReviewRef> byTag(String tag, int limit) {
        List<Rows.ReviewRef> rows = new ArrayList<>();
        var cursor = collection()
                .find(Filters.eq("tags", tag))
                .sort(Sorts.descending("createdAt"))
                .limit(limit);
        for (Document document : cursor) {
            Document branch = document.get("branch", Document.class);
            rows.add(new Rows.ReviewRef(
                    document.getString("reviewNo"),
                    LocalDateTime.ofInstant(document.getDate("createdAt").toInstant(), ZoneOffset.UTC),
                    document.getInteger("rating", 0),
                    branch == null ? null : branch.getString("name")));
        }
        return rows;
    }

    public void reply(String reviewNo, long staffId, String staffName, String text) {
        durable().updateOne(
                Filters.eq("reviewNo", reviewNo),
                Updates.combine(
                        Updates.set("reply.staffId", staffId),
                        Updates.set("reply.staffName", staffName),
                        Updates.set("reply.text", text),
                        Updates.set("reply.repliedAt", new Date())));
    }

    public long count() {
        return collection().countDocuments();
    }

    public void clear() {
        collection().deleteMany(new Document());
    }

    private MongoCollection<Document> collection() {
        return mongo.getCollection(collectionName);
    }

    private MongoCollection<Document> durable() {
        return collection().withWriteConcern(WriteConcern.JOURNALED);
    }

    private static Bson period(LocalDateTime from, LocalDateTime to) {
        return Filters.and(
                Filters.gte("createdAt", date(from)),
                Filters.lt("createdAt", date(to)));
    }

    private static Date date(LocalDateTime value) {
        return Date.from(value.toInstant(ZoneOffset.UTC));
    }

    private static BigDecimal round(Object value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }
}
