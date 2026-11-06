package ru.sportclub.club.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.sportclub.club.entity.ClubClass;
import ru.sportclub.club.entity.ClubTrainer;
import ru.sportclub.club.repository.ClubClassRepository;
import ru.sportclub.club.repository.ClubTrainerRepository;
import ru.sportclub.club.service.ClubService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "sportclub.demo.enabled", havingValue = "true")
public class ClubDemoData implements ApplicationRunner {
    private final ClubTrainerRepository trainers;
    private final ClubClassRepository classes;

    public ClubDemoData(ClubTrainerRepository trainers, ClubClassRepository classes) {
        this.trainers = trainers;
        this.classes = classes;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ClubTrainer alexey = seedTrainer("alexey", "Алексей Смирнов", "Силовая подготовка",
                "Помогает освоить технику базовых упражнений и выстроить регулярные занятия.", "АС");
        alexey.assignAccount(UUID.fromString("33333333-3333-4333-8333-333333333333"));
        ClubTrainer maria = seedTrainer("maria", "Мария Волкова", "Растяжка и мобильность",
                "Ведёт занятия на гибкость, подвижность и комфортное восстановление после нагрузки.", "МВ");
        ClubTrainer dmitry = seedTrainer("dmitry", "Дмитрий Орлов", "Функциональный тренинг",
                "Сочетает силовые упражнения и работу на выносливость в групповых тренировках.", "ДО");
        LocalDate today = LocalDate.now(ClubService.MOSCOW);
        for (int day = 0; day < 7; day++) {
            LocalDate date = today.plusDays(day);
            seedClass(date, 10, 0, 50, "Силовая база", "STRENGTH", alexey, "Силовая зона", 10);
            seedClass(date, 12, 30, 45, "Мягкая растяжка", "STRETCH", maria, "Студия 1", 12);
            seedClass(date, 19, 0, 50, "Функциональная тренировка", "FUNCTIONAL", dmitry, "Студия 2", 12);
            seedClass(date, 20, 0, 45, "Вечерняя растяжка", "STRETCH", maria, "Студия 1", 12);
        }
    }

    private ClubTrainer seedTrainer(String key, String name, String specialty, String bio, String initials) {
        UUID id = stableId("trainer:" + key);
        return trainers.findById(id).orElseGet(() -> trainers.save(new ClubTrainer(id, name, specialty, bio, initials)));
    }

    private void seedClass(LocalDate date, int hour, int minute, int duration, String title, String type,
                           ClubTrainer trainer, String room, int capacity) {
        OffsetDateTime start = date.atTime(hour, minute).atZone(ClubService.MOSCOW).toOffsetDateTime();
        if (!start.isAfter(OffsetDateTime.now())) return;
        UUID id = stableId("class:" + date + ":" + hour + ":" + minute);
        OffsetDateTime end = start.plusMinutes(duration);
        if (!classes.existsById(id) && !classes.existsByTrainer_IdAndCancelledFalseAndIdNotAndStartsAtLessThanAndEndsAtGreaterThan(
                trainer.getId(), id, end, start)) {
            ClubClass item = new ClubClass(id, title, type, trainer, room, start, end, capacity);
            item.markDemo();
            item.updateContent(trainer.getBio(), "STRENGTH".equals(type)
                    ? "Начни без веса. Стопы полностью на полу. Согни колени и отведи таз назад. Вернись вверх плавно." : "");
            classes.save(item);
        }
    }

    private static UUID stableId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
