package ua.lab.cafenet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.lab.cafenet.api.Forms;
import ua.lab.cafenet.domain.Rows;
import ua.lab.cafenet.nosql.mongo.MongoReviewRepository;
import ua.lab.cafenet.nosql.redis.RedisReviewBoard;
import ua.lab.cafenet.persistence.NotFoundException;
import ua.lab.cafenet.persistence.RuleViolationException;
import ua.lab.cafenet.persistence.UnitOfWorkFactory;
import ua.lab.cafenet.persistence.repository.SalesRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final UnitOfWorkFactory factory;
    private final MongoReviewRepository reviews;
    private final RedisReviewBoard board;

    public ReviewService(UnitOfWorkFactory factory, MongoReviewRepository reviews, RedisReviewBoard board) {
        this.factory = factory;
        this.reviews = reviews;
        this.board = board;
    }

    public Rows.Review leave(long orderId, Forms.ReviewForm form) {
        if (!board.acquireSlot(orderId)) {
            throw new RuleViolationException(
                    "На замовлення " + orderId + " вже лишали відгук, спробуйте пізніше");
        }

        Rows.Review review = factory.inTransaction(work -> {
            SalesRepository sales = work.sales();
            Rows.Order order = sales.find(orderId)
                    .orElseThrow(() -> new NotFoundException("Замовлення " + orderId + " не знайдено"));
            if (!"CLOSED".equals(order.status())) {
                throw new RuleViolationException("Замовлення " + orderId + " ще не закрите");
            }
            return build(order, sales.lines(orderId), form);
        });

        if (reviews.findByNo(review.reviewNo()).isPresent()) {
            throw new RuleViolationException("Відгук на замовлення " + orderId + " вже збережено");
        }
        reviews.add(review);
        register(review);
        return review;
    }

    public Rows.Review byNo(String reviewNo) {
        Optional<Rows.Review> cached = cached(reviewNo);
        if (cached.isPresent()) {
            return cached.get();
        }
        Rows.Review review = reviews.findByNo(reviewNo)
                .orElseThrow(() -> new NotFoundException("Відгук " + reviewNo + " не знайдено"));
        try {
            board.cache(review);
        } catch (RuntimeException e) {
            log.warn("Відгук {} не потрапив у кеш: {}", reviewNo, e.getMessage());
        }
        return review;
    }

    public List<Rows.Review> branchFeed(int branchId, LocalDateTime from, LocalDateTime to) {
        return reviews.branchFeed(branchId, from, to);
    }

    public List<Rows.BranchRating> branchRatings(LocalDateTime from, LocalDateTime to) {
        return reviews.branchRatings(from, to);
    }

    public List<Rows.AspectStat> aspectStats(LocalDateTime from, LocalDateTime to) {
        return reviews.aspectStats(from, to);
    }

    public List<Rows.ReviewRef> byTag(String tag, int limit) {
        return reviews.byTag(tag, limit);
    }

    public void reply(String reviewNo, Forms.ReplyForm form) {
        if (reviews.findByNo(reviewNo).isEmpty()) {
            throw new NotFoundException("Відгук " + reviewNo + " не знайдено");
        }
        reviews.reply(reviewNo, form.staffId(), form.staffName(), form.text());
        board.evict(reviewNo);
    }

    private void register(Rows.Review review) {
        try {
            board.register(review);
        } catch (RuntimeException e) {
            log.warn("Відгук {} збережено, але табло не оновилось: {}", review.reviewNo(), e.getMessage());
        }
    }

    private Optional<Rows.Review> cached(String reviewNo) {
        try {
            return board.cached(reviewNo);
        } catch (RuntimeException e) {
            log.warn("Кеш для {} недоступний, читаємо з MongoDB: {}", reviewNo, e.getMessage());
            return Optional.empty();
        }
    }

    private static Rows.Review build(Rows.Order order, List<Rows.OrderLine> lines, Forms.ReviewForm form) {
        List<Rows.Aspect> aspects = new ArrayList<>();
        for (Forms.AspectForm aspect : form.aspectsOrEmpty()) {
            aspects.add(new Rows.Aspect(aspect.name(), aspect.score()));
        }

        List<Rows.ReviewItem> items = new ArrayList<>();
        for (Forms.ItemMarkForm mark : form.itemsOrEmpty()) {
            Rows.OrderLine line = lines.stream()
                    .filter(l -> l.menuItemId() == mark.menuItemId())
                    .findFirst()
                    .orElseThrow(() -> new RuleViolationException(
                            "Позиції " + mark.menuItemId() + " немає в замовленні " + order.id()));
            items.add(new Rows.ReviewItem(line.menuItemId(), line.title(), mark.rating(), mark.comment()));
        }

        return new Rows.Review(
                ReviewNumbers.of(order.id()),
                order.id(),
                form.sourceOrDefault(),
                order.channel(),
                LocalDateTime.now(),
                order.branchId(),
                order.branch(),
                order.branchCity(),
                order.clientId(),
                order.client(),
                order.clientPhone(),
                form.rating(),
                form.text(),
                aspects,
                items,
                List.copyOf(form.tagsOrEmpty()),
                List.copyOf(form.photosOrEmpty()),
                null);
    }

    private static final class ReviewNumbers {

        private ReviewNumbers() {
        }

        private static String of(long orderId) {
            return "RV-%07d".formatted(orderId);
        }
    }
}
