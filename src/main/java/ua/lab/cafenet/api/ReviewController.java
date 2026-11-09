package ua.lab.cafenet.api;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ua.lab.cafenet.domain.Rows;
import ua.lab.cafenet.service.ReviewService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @PostMapping("/from-order/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public Rows.Review leave(@PathVariable long orderId, @Valid @RequestBody Forms.ReviewForm form) {
        return service.leave(orderId, form);
    }

    @GetMapping("/{reviewNo}")
    public Rows.Review byNo(@PathVariable String reviewNo) {
        return service.byNo(reviewNo);
    }

    @GetMapping
    public List<Rows.Review> branchFeed(@RequestParam int branchId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                        LocalDateTime from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                        LocalDateTime to) {
        return service.branchFeed(branchId, from, to);
    }

    @GetMapping("/branch-ratings")
    public List<Rows.BranchRating> branchRatings(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                 LocalDateTime from,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                 LocalDateTime to) {
        return service.branchRatings(from, to);
    }

    @GetMapping("/aspect-stats")
    public List<Rows.AspectStat> aspectStats(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                             LocalDateTime from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                             LocalDateTime to) {
        return service.aspectStats(from, to);
    }

    @GetMapping("/by-tag")
    public List<Rows.ReviewRef> byTag(@RequestParam String tag,
                                      @RequestParam(defaultValue = "50") int limit) {
        return service.byTag(tag, limit);
    }

    @PostMapping("/{reviewNo}/reply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reply(@PathVariable String reviewNo, @Valid @RequestBody Forms.ReplyForm form) {
        service.reply(reviewNo, form);
    }
}
