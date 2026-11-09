package ua.lab.cafenet.nosql.mongo;

import org.bson.Document;
import ua.lab.cafenet.domain.Rows;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class ReviewDocuments {

    private ReviewDocuments() {
    }

    public static Document toDocument(Rows.Review review) {
        List<Document> aspects = new ArrayList<>(review.aspects().size());
        for (Rows.Aspect aspect : review.aspects()) {
            aspects.add(new Document().append("name", aspect.name()).append("score", aspect.score()));
        }

        List<Document> items = new ArrayList<>(review.items().size());
        for (Rows.ReviewItem item : review.items()) {
            items.add(new Document()
                    .append("menuItemId", item.menuItemId())
                    .append("title", item.title())
                    .append("rating", item.rating())
                    .append("comment", item.comment()));
        }

        Document document = new Document()
                .append("reviewNo", review.reviewNo())
                .append("orderId", review.orderId())
                .append("source", review.source())
                .append("channel", review.channel())
                .append("createdAt", date(review.createdAt()))
                .append("branch", new Document()
                        .append("id", review.branchId())
                        .append("name", review.branchName())
                        .append("city", review.branchCity()))
                .append("client", new Document()
                        .append("id", review.clientId())
                        .append("name", review.clientName())
                        .append("phone", review.clientPhone()))
                .append("rating", review.rating())
                .append("text", review.text())
                .append("aspects", aspects)
                .append("items", items)
                .append("tags", review.tags())
                .append("photos", review.photos());

        if (review.reply() != null) {
            document.append("reply", new Document()
                    .append("staffId", review.reply().staffId())
                    .append("staffName", review.reply().staffName())
                    .append("text", review.reply().text())
                    .append("repliedAt", date(review.reply().repliedAt())));
        }
        return document;
    }

    @SuppressWarnings("unchecked")
    public static Rows.Review toReview(Document document) {
        Document branch = sub(document, "branch");
        Document client = sub(document, "client");
        Document reply = document.get("reply", Document.class);

        List<Rows.Aspect> aspects = new ArrayList<>();
        List<Document> rawAspects = (List<Document>) document.get("aspects");
        if (rawAspects != null) {
            for (Document aspect : rawAspects) {
                aspects.add(new Rows.Aspect(aspect.getString("name"), aspect.getInteger("score", 0)));
            }
        }

        List<Rows.ReviewItem> items = new ArrayList<>();
        List<Document> rawItems = (List<Document>) document.get("items");
        if (rawItems != null) {
            for (Document item : rawItems) {
                items.add(new Rows.ReviewItem(
                        item.get("menuItemId", Number.class).longValue(),
                        item.getString("title"),
                        item.getInteger("rating", 0),
                        item.getString("comment")));
            }
        }

        List<String> tags = (List<String>) document.get("tags");
        List<String> photos = (List<String>) document.get("photos");

        return new Rows.Review(
                document.getString("reviewNo"),
                number(document.get("orderId")),
                document.getString("source"),
                document.getString("channel"),
                dateTime(document.getDate("createdAt")),
                branch.getInteger("id"),
                branch.getString("name"),
                branch.getString("city"),
                number(client.get("id")),
                client.getString("name"),
                client.getString("phone"),
                document.getInteger("rating", 0),
                document.getString("text"),
                aspects,
                items,
                tags == null ? List.of() : List.copyOf(tags),
                photos == null ? List.of() : List.copyOf(photos),
                reply == null ? null : new Rows.Reply(
                        number(reply.get("staffId")),
                        reply.getString("staffName"),
                        reply.getString("text"),
                        dateTime(reply.getDate("repliedAt"))));
    }

    private static Document sub(Document document, String key) {
        Document nested = document.get(key, Document.class);
        return nested == null ? new Document() : nested;
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Date date(LocalDateTime value) {
        return value == null ? null : Date.from(value.toInstant(ZoneOffset.UTC));
    }

    private static LocalDateTime dateTime(Date value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(value.getTime()), ZoneOffset.UTC);
    }
}
