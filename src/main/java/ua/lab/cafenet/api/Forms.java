package ua.lab.cafenet.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class Forms {

    private Forms() {
    }

    public record OrderLineForm(@Positive long menuItemId,
                                @Positive int quantity) {
    }

    public record OrderForm(@Positive int branchId,
                            @Positive long cashierId,
                            Long clientId,
                            @Pattern(regexp = "HALL|TAKEAWAY|DELIVERY") String channel,
                            @NotEmpty @Valid List<OrderLineForm> lines) {

        public String channelOrDefault() {
            return channel == null || channel.isBlank() ? "HALL" : channel;
        }
    }

    public record AspectForm(@NotBlank @Size(max = 30) String name,
                             @Min(1) @Max(5) int score) {
    }

    public record ItemMarkForm(@Positive long menuItemId,
                               @Min(1) @Max(5) int rating,
                               @Size(max = 200) String comment) {
    }

    public record ReviewForm(@Min(1) @Max(5) int rating,
                             String text,
                             @Pattern(regexp = "APP|QR|SITE") String source,
                             @Valid List<AspectForm> aspects,
                             @Valid List<ItemMarkForm> items,
                             List<String> tags,
                             List<String> photos) {

        public String sourceOrDefault() {
            return source == null || source.isBlank() ? "APP" : source;
        }

        public List<AspectForm> aspectsOrEmpty() {
            return aspects == null ? List.of() : aspects;
        }

        public List<ItemMarkForm> itemsOrEmpty() {
            return items == null ? List.of() : items;
        }

        public List<String> tagsOrEmpty() {
            return tags == null ? List.of() : tags;
        }

        public List<String> photosOrEmpty() {
            return photos == null ? List.of() : photos;
        }
    }

    public record ReplyForm(@Positive long staffId,
                            @NotBlank @Size(max = 150) String staffName,
                            @NotBlank String text) {
    }
}
