package ua.lab.cafenet.nosql.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import ua.lab.cafenet.domain.Rows;
import ua.lab.cafenet.persistence.StorageException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisReviewBoard {

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String prefix;
    private final Duration cacheTtl;
    private final Duration cooldown;

    public RedisReviewBoard(StringRedisTemplate redis,
                            ObjectMapper json,
                            @Value("${cafenet.redis.prefix}") String prefix,
                            @Value("${cafenet.redis.cache-ttl-seconds}") long cacheTtlSeconds,
                            @Value("${cafenet.redis.cooldown-seconds}") long cooldownSeconds) {
        this.redis = redis;
        this.json = json;
        this.prefix = prefix;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
        this.cooldown = Duration.ofSeconds(cooldownSeconds);
    }

    public void register(Rows.Review review) {
        String day = review.createdAt().toLocalDate().toString();

        redis.opsForValue().increment(key("stats", day, "count"));
        redis.opsForHash().increment(key("stats", day, "marks"), String.valueOf(review.rating()), 1);
        redis.opsForHash().increment(key("stats", day, "sources"), review.source(), 1);

        if (review.branchName() != null) {
            redis.opsForZSet().incrementScore(key("branch-score", day), review.branchName(), review.rating());
            redis.opsForZSet().incrementScore(key("branch-count", day), review.branchName(), 1);
        }

        String feed = key("feed");
        redis.opsForList().leftPush(feed, review.reviewNo() + " | " + review.branchName() + " | " + review.rating());
        redis.opsForList().trim(feed, 0, 49);

        cache(review);
    }

    public void cache(Rows.Review review) {
        try {
            redis.opsForValue().set(key("review", review.reviewNo()), json.writeValueAsString(review), cacheTtl);
        } catch (JacksonException e) {
            throw new StorageException("Не вдалося серіалізувати відгук " + review.reviewNo(), e);
        }
    }

    public Optional<Rows.Review> cached(String reviewNo) {
        String raw = redis.opsForValue().get(key("review", reviewNo));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(raw, Rows.Review.class));
        } catch (JacksonException e) {
            throw new StorageException("Не вдалося прочитати відгук з кешу " + reviewNo, e);
        }
    }

    public Long secondsToLive(String reviewNo) {
        return redis.getExpire(key("review", reviewNo));
    }

    public boolean evict(String reviewNo) {
        return Boolean.TRUE.equals(redis.delete(key("review", reviewNo)));
    }

    public boolean acquireSlot(long orderId) {
        Boolean created = redis.opsForValue()
                .setIfAbsent(key("cooldown", String.valueOf(orderId)), "1", cooldown);
        return Boolean.TRUE.equals(created);
    }

    public Long cooldownLeft(long orderId) {
        return redis.getExpire(key("cooldown", String.valueOf(orderId)));
    }

    public Map<String, Object> counters(LocalDate day) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("day", day.toString());
        result.put("reviews", redis.opsForValue().get(key("stats", day.toString(), "count")));
        result.put("byMark", redis.opsForHash().entries(key("stats", day.toString(), "marks")));
        result.put("bySource", redis.opsForHash().entries(key("stats", day.toString(), "sources")));
        return result;
    }

    public List<Map<String, Object>> branchBoard(LocalDate day, int limit) {
        Set<ZSetOperations.TypedTuple<String>> scores =
                redis.opsForZSet().reverseRangeWithScores(key("branch-score", day.toString()), 0, limit - 1L);
        if (scores == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(scores.size());
        for (ZSetOperations.TypedTuple<String> tuple : scores) {
            String branch = String.valueOf(tuple.getValue());
            Double count = redis.opsForZSet().score(key("branch-count", day.toString()), branch);
            double sum = tuple.getScore() == null ? 0 : tuple.getScore();
            double average = count == null || count == 0 ? 0 : sum / count;
            rows.add(Map.of(
                    "branch", branch,
                    "reviews", count == null ? 0L : count.longValue(),
                    "avgRating", Math.round(average * 100.0) / 100.0));
        }
        rows.sort((a, b) -> Double.compare(
                ((Number) b.get("avgRating")).doubleValue(),
                ((Number) a.get("avgRating")).doubleValue()));
        return rows;
    }

    public List<String> feed(int limit) {
        List<String> rows = redis.opsForList().range(key("feed"), 0, limit - 1L);
        return rows == null ? List.of() : rows;
    }

    private String key(String... parts) {
        return prefix + ":" + String.join(":", parts);
    }
}
