package ua.lab.cafenet.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ua.lab.cafenet.domain.Rows;
import ua.lab.cafenet.nosql.redis.RedisReviewBoard;
import ua.lab.cafenet.persistence.NotFoundException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/live")
public class LiveController {

    private final RedisReviewBoard board;

    public LiveController(RedisReviewBoard board) {
        this.board = board;
    }

    @GetMapping("/counters")
    public Map<String, Object> counters(@RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        return board.counters(day == null ? LocalDate.now() : day);
    }

    @GetMapping("/branch-board")
    public List<Map<String, Object>> branchBoard(@RequestParam(required = false)
                                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                                 @RequestParam(defaultValue = "5") int limit) {
        return board.branchBoard(day == null ? LocalDate.now() : day, limit);
    }

    @GetMapping("/feed")
    public List<String> feed(@RequestParam(defaultValue = "10") int limit) {
        return board.feed(limit);
    }

    @GetMapping("/cache/{reviewNo}")
    public Map<String, Object> cached(@PathVariable String reviewNo) {
        Rows.Review review = board.cached(reviewNo)
                .orElseThrow(() -> new NotFoundException("Відгук " + reviewNo + " у кеші відсутній"));
        return Map.of(
                "secondsToLive", board.secondsToLive(reviewNo),
                "review", review);
    }

    @GetMapping("/cooldown/{orderId}")
    public Map<String, Object> cooldown(@PathVariable long orderId) {
        Long left = board.cooldownLeft(orderId);
        return Map.of(
                "orderId", orderId,
                "secondsLeft", left == null ? -2 : left,
                "canLeaveReview", left == null || left < 0);
    }
}
