package ua.lab.cafenet.persistence.repository;

import ua.lab.cafenet.domain.Rows;
import ua.lab.cafenet.persistence.StatementRunner;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReviewRepository {

    private static final String FULL_COLUMNS = """
            review_id, review_no, order_id, source, channel, created_at,
            branch_id, branch_name, branch_city, client_id, client_name, client_phone,
            rating, body_text, reply_staff_id, reply_staff_name, reply_text, replied_at,
            aspect_names, aspect_scores, tags, photos,
            item_id, menu_item_id, title, item_rating, comment
            """;

    private static final String CALL_ADD = """
            CALL sp_review_add(
                CAST(? AS TEXT), CAST(? AS BIGINT), CAST(? AS TEXT), CAST(? AS TEXT), CAST(? AS TIMESTAMP),
                CAST(? AS INT), CAST(? AS TEXT), CAST(? AS TEXT),
                CAST(? AS BIGINT), CAST(? AS TEXT), CAST(? AS TEXT),
                CAST(? AS SMALLINT), CAST(? AS TEXT),
                CAST(? AS TEXT[]), CAST(? AS SMALLINT[]),
                CAST(? AS BIGINT[]), CAST(? AS TEXT[]), CAST(? AS SMALLINT[]), CAST(? AS TEXT[]),
                CAST(? AS TEXT[]), CAST(? AS TEXT[]),
                NULL)
            """;

    private final StatementRunner runner;

    public ReviewRepository(StatementRunner runner) {
        this.runner = runner;
    }

    public long add(Rows.Review review) {
        int aspectCount = review.aspects().size();
        String[] aspectNames = new String[aspectCount];
        Short[] aspectScores = new Short[aspectCount];
        for (int i = 0; i < aspectCount; i++) {
            Rows.Aspect aspect = review.aspects().get(i);
            aspectNames[i] = aspect.name();
            aspectScores[i] = (short) aspect.score();
        }

        int itemCount = review.items().size();
        Long[] itemIds = new Long[itemCount];
        String[] itemTitles = new String[itemCount];
        Short[] itemRatings = new Short[itemCount];
        String[] itemComments = new String[itemCount];
        for (int i = 0; i < itemCount; i++) {
            Rows.ReviewItem item = review.items().get(i);
            itemIds[i] = item.menuItemId();
            itemTitles[i] = item.title();
            itemRatings[i] = (short) item.rating();
            itemComments[i] = item.comment();
        }

        Array names = runner.array("text", aspectNames);
        Array scores = runner.array("int2", aspectScores);
        Array ids = runner.array("int8", itemIds);
        Array titles = runner.array("text", itemTitles);
        Array ratings = runner.array("int2", itemRatings);
        Array comments = runner.array("text", itemComments);
        Array tags = runner.array("text", review.tags().toArray(String[]::new));
        Array photos = runner.array("text", review.photos().toArray(String[]::new));

        return runner.execId(CALL_ADD,
                review.reviewNo(), review.orderId(), review.source(), review.channel(), review.createdAt(),
                review.branchId(), review.branchName(), review.branchCity(),
                review.clientId(), review.clientName(), review.clientPhone(),
                (short) review.rating(), review.text(),
                names, scores,
                ids, titles, ratings, comments,
                tags, photos);
    }

    public Optional<Rows.Review> findByNo(String reviewNo) {
        List<Line> lines = runner.list(
                "SELECT " + FULL_COLUMNS + " FROM v_reviews_full WHERE review_no = CAST(? AS TEXT) ORDER BY item_id",
                ReviewRepository::line, reviewNo);
        List<Rows.Review> reviews = assemble(lines);
        return reviews.isEmpty() ? Optional.empty() : Optional.of(reviews.getFirst());
    }

    public List<Rows.Review> branchFeed(int branchId, LocalDateTime from, LocalDateTime to) {
        List<Line> lines = runner.list(
                "SELECT " + FULL_COLUMNS + """
                        FROM v_reviews_full
                        WHERE branch_id = CAST(? AS INT)
                          AND created_at >= CAST(? AS TIMESTAMP)
                          AND created_at < CAST(? AS TIMESTAMP)
                        ORDER BY created_at DESC, review_id, item_id
                        """,
                ReviewRepository::line, branchId, from, to);
        return assemble(lines);
    }

    public List<Rows.BranchRating> branchRatings(LocalDateTime from, LocalDateTime to) {
        return runner.list("""
                SELECT branch_id, branch, reviews, avg_rating
                FROM fn_branch_ratings(CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))
                """, ReviewRepository::branchRating, from, to);
    }

    public List<Rows.AspectStat> aspectStats(LocalDateTime from, LocalDateTime to) {
        return runner.list("""
                SELECT aspect, answers, avg_score
                FROM fn_aspect_stats(CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP))
                """, ReviewRepository::aspectStat, from, to);
    }

    public List<Rows.ReviewRef> byTag(String tag, int limit) {
        return runner.list("""
                SELECT review_no, created_at, rating, branch
                FROM fn_reviews_by_tag(CAST(? AS TEXT), CAST(? AS INT))
                """, ReviewRepository::reviewRef, tag, limit);
    }

    public void reply(String reviewNo, long staffId, String staffName, String text) {
        runner.exec("""
                CALL sp_review_reply(CAST(? AS TEXT), CAST(? AS BIGINT), CAST(? AS TEXT), CAST(? AS TEXT))
                """, reviewNo, staffId, staffName, text);
    }

    public long count() {
        return runner.scalar("SELECT count(*) AS total FROM v_reviews", rs -> rs.getLong("total"));
    }

    public void clear() {
        runner.exec("CALL sp_reviews_clear()");
    }

    public void analyze() {
        runner.exec("CALL sp_reviews_analyze()");
    }

    private List<Rows.Review> assemble(List<Line> lines) {
        Map<Long, Draft> drafts = new LinkedHashMap<>();
        for (Line line : lines) {
            Draft draft = drafts.computeIfAbsent(line.reviewId(), id -> new Draft(line));
            if (line.itemId() != null) {
                draft.items.add(new Rows.ReviewItem(
                        line.menuItemId(), line.title(), line.itemRating(), line.comment()));
            }
        }

        List<Rows.Review> reviews = new ArrayList<>(drafts.size());
        for (Draft draft : drafts.values()) {
            Line head = draft.head;
            List<Rows.Aspect> aspects = new ArrayList<>(head.aspectNames().size());
            for (int i = 0; i < head.aspectNames().size(); i++) {
                aspects.add(new Rows.Aspect(head.aspectNames().get(i), head.aspectScores().get(i)));
            }
            Rows.Reply reply = head.replyText() == null
                    ? null
                    : new Rows.Reply(head.replyStaffId(), head.replyStaffName(), head.replyText(), head.repliedAt());

            reviews.add(new Rows.Review(
                    head.reviewNo(), head.orderId(), head.source(), head.channel(), head.createdAt(),
                    head.branchId(), head.branchName(), head.branchCity(),
                    head.clientId(), head.clientName(), head.clientPhone(),
                    head.rating(), head.bodyText(),
                    aspects, List.copyOf(draft.items), head.tags(), head.photos(), reply));
        }
        return reviews;
    }

    private static final class Draft {

        private final Line head;
        private final List<Rows.ReviewItem> items = new ArrayList<>();

        private Draft(Line head) {
            this.head = head;
        }
    }

    private record Line(long reviewId, String reviewNo, Long orderId, String source, String channel,
                        LocalDateTime createdAt, Integer branchId, String branchName, String branchCity,
                        Long clientId, String clientName, String clientPhone, int rating, String bodyText,
                        Long replyStaffId, String replyStaffName, String replyText, LocalDateTime repliedAt,
                        List<String> aspectNames, List<Integer> aspectScores,
                        List<String> tags, List<String> photos,
                        Long itemId, long menuItemId, String title, int itemRating, String comment) {
    }

    private static Line line(ResultSet rs) throws SQLException {
        return new Line(
                rs.getLong("review_id"),
                rs.getString("review_no"),
                rs.getObject("order_id", Long.class),
                rs.getString("source"),
                rs.getString("channel"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("branch_id", Integer.class),
                rs.getString("branch_name"),
                rs.getString("branch_city"),
                rs.getObject("client_id", Long.class),
                rs.getString("client_name"),
                rs.getString("client_phone"),
                rs.getInt("rating"),
                rs.getString("body_text"),
                rs.getObject("reply_staff_id", Long.class),
                rs.getString("reply_staff_name"),
                rs.getString("reply_text"),
                rs.getObject("replied_at", LocalDateTime.class),
                strings(rs.getArray("aspect_names")),
                integers(rs.getArray("aspect_scores")),
                strings(rs.getArray("tags")),
                strings(rs.getArray("photos")),
                rs.getObject("item_id", Long.class),
                rs.getLong("menu_item_id"),
                rs.getString("title"),
                rs.getInt("item_rating"),
                rs.getString("comment"));
    }

    private static Rows.BranchRating branchRating(ResultSet rs) throws SQLException {
        return new Rows.BranchRating(
                rs.getInt("branch_id"),
                rs.getString("branch"),
                rs.getLong("reviews"),
                rs.getBigDecimal("avg_rating"));
    }

    private static Rows.AspectStat aspectStat(ResultSet rs) throws SQLException {
        return new Rows.AspectStat(
                rs.getString("aspect"),
                rs.getLong("answers"),
                rs.getBigDecimal("avg_score"));
    }

    private static Rows.ReviewRef reviewRef(ResultSet rs) throws SQLException {
        return new Rows.ReviewRef(
                rs.getString("review_no"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getInt("rating"),
                rs.getString("branch"));
    }

    private static List<String> strings(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }

    private static List<Integer> integers(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        for (Object value : (Object[]) array.getArray()) {
            values.add(((Number) value).intValue());
        }
        return values;
    }
}
