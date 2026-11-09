package ua.lab.cafenet.nosql.bench;

import org.springframework.stereotype.Component;
import ua.lab.cafenet.domain.Rows;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Component
public class ReviewGenerator {

    public static final String NUMBER_FORMAT = "RV-%07d";
    public static final String PROBE_TAG = "швидко";
    public static final int PROBE_BRANCH_ID = 1;

    private static final Branch[] BRANCHES = {
            new Branch(1, "Центральна", "Київ"),
            new Branch(2, "Подільська", "Київ"),
            new Branch(3, "Галицька", "Львів")
    };

    private static final Guest[] GUESTS = {
            new Guest(1, "Петро Іваненко", "+380501110011"),
            new Guest(2, "Дмитро Савченко", "+380501110012"),
            new Guest(3, "Юлія Романюк", "+380501110013"),
            new Guest(4, "Назар Поліщук", "+380501110014"),
            new Guest(5, "Софія Бондар", "+380501110015")
    };

    private static final Item[] MENU = {
            new Item(1, "Еспресо"),
            new Item(2, "Американо"),
            new Item(3, "Капучино"),
            new Item(4, "Лате"),
            new Item(5, "Флет-вайт"),
            new Item(6, "Чай обліпиховий"),
            new Item(7, "Чізкейк"),
            new Item(8, "Круасан"),
            new Item(9, "Сирники"),
            new Item(10, "Вівсянка")
    };

    private static final String[] ASPECTS = {"кава", "сервіс", "швидкість", "атмосфера", "чистота"};

    private static final String[] TAGS = {
            PROBE_TAG, "смачно", "затишно", "дорого", "черга", "ввічливо", "wifi"
    };

    private static final String[] SOURCES = {"APP", "QR", "SITE"};

    private static final String[] CHANNELS = {"HALL", "TAKEAWAY", "DELIVERY"};

    private static final String[] GOOD_TEXT = {
            "Кава як завжди на висоті, бариста привітний.",
            "Зайшов на каву перед роботою, все швидко і смачно.",
            "Затишно, зручно працювати з ноутбуком.",
            "Сирники сподобались, порція велика."
    };

    private static final String[] BAD_TEXT = {
            "Довго чекав замовлення, хоча черги майже не було.",
            "Кава виявилась ледь теплою.",
            "Столики не прибрані, довелося шукати вільний.",
            "Ціни піднялись, а порція менша ніж раніше."
    };

    private static final String[] ITEM_COMMENTS = {
            "саме те, що треба", "трохи гірчить", "ідеальна пінка", "задовго готували", null
    };

    public List<Rows.Review> generate(int count, long seed, int startFrom) {
        Random random = new Random(seed);
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 9, 0);
        List<Rows.Review> reviews = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            reviews.add(one(startFrom + i, base, random));
        }
        return reviews;
    }

    private Rows.Review one(int number, LocalDateTime base, Random random) {
        Branch branch = BRANCHES[random.nextInt(BRANCHES.length)];
        Guest guest = GUESTS[random.nextInt(GUESTS.length)];
        LocalDateTime createdAt = base
                .plusDays(random.nextInt(180))
                .plusMinutes(random.nextInt(600));

        int rating = 1 + random.nextInt(5);
        boolean positive = rating >= 4;

        List<Rows.Aspect> aspects = new ArrayList<>();
        for (String aspect : ASPECTS) {
            if (random.nextInt(100) < 70) {
                int score = Math.min(5, Math.max(1, rating + random.nextInt(3) - 1));
                aspects.add(new Rows.Aspect(aspect, score));
            }
        }

        int itemCount = 1 + random.nextInt(3);
        List<Rows.ReviewItem> items = new ArrayList<>(itemCount);
        Set<Long> usedItems = new LinkedHashSet<>();
        for (int i = 0; i < itemCount; i++) {
            Item item = MENU[random.nextInt(MENU.length)];
            if (!usedItems.add(item.id())) {
                continue;
            }
            items.add(new Rows.ReviewItem(
                    item.id(),
                    item.title(),
                    Math.min(5, Math.max(1, rating + random.nextInt(3) - 1)),
                    ITEM_COMMENTS[random.nextInt(ITEM_COMMENTS.length)]));
        }

        Set<String> tags = new LinkedHashSet<>();
        int tagCount = random.nextInt(100) < 60 ? 1 + random.nextInt(2) : 0;
        for (int i = 0; i < tagCount; i++) {
            tags.add(TAGS[random.nextInt(TAGS.length)]);
        }

        List<String> photos = new ArrayList<>();
        int photoCount = random.nextInt(100) < 25 ? 1 + random.nextInt(2) : 0;
        for (int i = 1; i <= photoCount; i++) {
            photos.add("https://cdn.cafenet.example/reviews/%07d-%d.jpg".formatted(number, i));
        }

        Rows.Reply reply = null;
        if (!positive && random.nextInt(100) < 40) {
            reply = new Rows.Reply(
                    2L,
                    "Андрій Шевчук",
                    "Дякуємо за відгук, розберемось із ситуацією.",
                    createdAt.plusHours(1 + random.nextInt(48)));
        }

        String[] texts = positive ? GOOD_TEXT : BAD_TEXT;

        return new Rows.Review(
                NUMBER_FORMAT.formatted(number),
                (long) number,
                SOURCES[random.nextInt(SOURCES.length)],
                CHANNELS[random.nextInt(CHANNELS.length)],
                createdAt,
                branch.id(),
                branch.name(),
                branch.city(),
                guest.id(),
                guest.name(),
                guest.phone(),
                rating,
                texts[random.nextInt(texts.length)],
                aspects,
                items,
                List.copyOf(tags),
                photos,
                reply);
    }

    private record Branch(int id, String name, String city) {
    }

    private record Guest(long id, String name, String phone) {
    }

    private record Item(long id, String title) {
    }
}
