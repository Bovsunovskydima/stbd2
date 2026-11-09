package ua.lab.cafenet.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class Rows {

    private Rows() {
    }

    public record Order(long id, String status, String channel, BigDecimal total,
                        LocalDateTime createdAt, LocalDateTime closedAt,
                        int branchId, String branch, String branchCity,
                        long cashierId, String cashier,
                        Long clientId, String client, String clientPhone,
                        int linesCount) {
    }

    public record OrderLine(long lineId, long orderId, long menuItemId, String title, String section,
                            int quantity, BigDecimal priceAtSale, BigDecimal lineTotal) {
    }

    public record OrderCard(Order order, List<OrderLine> lines) {
    }

    public record Aspect(String name, int score) {
    }

    public record ReviewItem(long menuItemId, String title, int rating, String comment) {
    }

    public record Reply(Long staffId, String staffName, String text, LocalDateTime repliedAt) {
    }

    public record Review(String reviewNo,
                         Long orderId,
                         String source,
                         String channel,
                         LocalDateTime createdAt,
                         Integer branchId,
                         String branchName,
                         String branchCity,
                         Long clientId,
                         String clientName,
                         String clientPhone,
                         int rating,
                         String text,
                         List<Aspect> aspects,
                         List<ReviewItem> items,
                         List<String> tags,
                         List<String> photos,
                         Reply reply) {
    }

    public record BranchRating(int branchId, String branch, long reviews, BigDecimal avgRating) {
    }

    public record AspectStat(String aspect, long answers, BigDecimal avgScore) {
    }

    public record ReviewRef(String reviewNo, LocalDateTime createdAt, int rating, String branch) {
    }
}
